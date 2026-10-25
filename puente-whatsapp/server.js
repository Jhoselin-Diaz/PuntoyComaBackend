const fs = require('fs');
const { Client, LocalAuth } = require('whatsapp-web.js');
const qrcodeTerminal = require('qrcode-terminal');
const axios = require('axios');
const express = require('express');
const cors = require('cors');

let QRCode = null;
try {
    QRCode = require('qrcode');
} catch (e) {
    console.warn('⚠️ Módulo "qrcode" no disponible. Ejecuta npm install en puente-whatsapp para generar Base64 de QR.');
}

// Configuración de la URL de Spring Boot con fallback al backend real en Azure
let rawBackendUrl = process.env.BACKEND_URL || 'https://puntoycoma-backend-cde2g6htdcewgafr.eastus2-01.azurewebsites.net/webhook';
rawBackendUrl = rawBackendUrl.trim();

// Si está en Azure y viene con localhost, sustituirlo por el dominio público de Azure
if (process.env.WEBSITE_SITE_NAME && (rawBackendUrl.includes('localhost') || rawBackendUrl.includes('127.0.0.1'))) {
    rawBackendUrl = 'https://puntoycoma-backend-cde2g6htdcewgafr.eastus2-01.azurewebsites.net/webhook';
}

if (!rawBackendUrl.endsWith('/webhook') && !rawBackendUrl.includes('/api/')) {
    rawBackendUrl = rawBackendUrl.replace(/\/+$/, '') + '/webhook';
}
const BACKEND_URL = rawBackendUrl;
const PORT = process.env.PORT || 3001;

// Estado global en memoria para consulta del backend/frontend
let currentStatus = 'DISCONNECTED'; // 'DISCONNECTED', 'CONNECTING', 'QR_READY', 'CONNECTED'
let qrCodeBase64 = null;
let client = null;
let isReconnecting = false;
let connectedPhoneNumber = null;
let connectedAtTimestamp = null;

// Cache de deduplicación en memoria para Node (Key: msgId, Value: timestamp)
const processedMsgIds = new Map();

function isDuplicateMsg(msgId) {
    if (!msgId) return false;
    const now = Date.now();
    for (const [id, time] of processedMsgIds.entries()) {
        if (now - time > 60000) {
            processedMsgIds.delete(id);
        }
    }
    if (processedMsgIds.has(msgId)) {
        return true;
    }
    processedMsgIds.set(msgId, now);
    return false;
}

// Función para purgar la carpeta de sesión si se corrompe o se cierra en el teléfono
function limpiarCarpetaSesion() {
    try {
        if (fs.existsSync('./.wwebjs_auth')) {
            fs.rmSync('./.wwebjs_auth', { recursive: true, force: true });
            console.log('🧹 [Limpieza] Carpeta .wwebjs_auth eliminada correctamente.');
        }
    } catch (err) {
        console.error('⚠️ Error eliminando .wwebjs_auth:', err.message);
    }
}

// Resolver JID de tipo @lid a número telefónico real (@c.us)
async function resolverJidTelefono(remoteJid) {
    if (!remoteJid) return remoteJid;
    let finalJid = remoteJid;
    if (remoteJid.endsWith('@lid')) {
        try {
            if (client && typeof client.getContactLidAndPhone === 'function') {
                const resolvedArray = await client.getContactLidAndPhone([remoteJid]).catch(() => null);
                if (resolvedArray && resolvedArray.length > 0 && resolvedArray[0].pn) {
                    finalJid = resolvedArray[0].pn;
                    console.log(`✨ [Resolución LID] Traducido ${remoteJid} -> ${finalJid} (Método 1)`);
                    return finalJid;
                }
            }
            if (client && typeof client.getContactById === 'function') {
                const contact = await client.getContactById(remoteJid).catch(() => null);
                if (contact && contact.number) {
                    finalJid = contact.number + '@c.us';
                    console.log(`✨ [Resolución LID] Traducido ${remoteJid} -> ${finalJid} (Método 2)`);
                    return finalJid;
                }
            }
        } catch (errResolve) {
            console.warn('⚠️ No se pudo resolver el LID a número telefónico:', errResolve.message);
        }
    }
    return finalJid;
}

// Bypass directo para descargar multimedia vía WAWebDownloadManager si downloadMedia() estándar falla
async function descargarDirectoConPuppeteer(clientInstance, msg) {
    if (!clientInstance || !clientInstance.pupPage) return null;
    if (typeof msg !== 'string' && msg) {
        asegurarIdSerializado(msg);
    }
    const msgId = (typeof msg === 'string') ? msg : (msg?.id?._serialized || msg?.id?.id || msg?.id || String(msg));
    return await clientInstance.pupPage.evaluate(async (id) => {
        try {
            const collections = window.require('WAWebCollections');
            let target = collections.Msg.get(id);
            if (!target) {
                const fetched = await collections.Msg.getMessagesById([id]).catch(e => ({ err: e?.message }));
                if (fetched && fetched.messages && fetched.messages.length > 0) {
                    target = fetched.messages[0];
                }
            }

            if (!target) return { error: `Mensaje ${id} no encontrado en WAWebCollections.Msg` };

            if (typeof target.downloadMedia === 'function') {
                try {
                    await target.downloadMedia({ downloadEvenIfExpensive: true, rmrReason: 1 });
                } catch (eDl) {}
            }

            const mockQpl = {
                addAnnotations: () => mockQpl,
                addPoint: () => mockQpl
            };

            const downloadManager = window.require('WAWebDownloadManager')?.downloadManager;
            if (!downloadManager || typeof downloadManager.downloadAndMaybeDecrypt !== 'function') {
                return { error: 'WAWebDownloadManager no disponible' };
            }

            let decryptedMedia = null;
            let lastErr = null;

            try {
                decryptedMedia = await downloadManager.downloadAndMaybeDecrypt({
                    directPath: target.directPath,
                    encFilehash: target.encFilehash,
                    filehash: target.filehash,
                    mediaKey: target.mediaKey,
                    mediaKeyTimestamp: target.mediaKeyTimestamp,
                    type: 'document',
                    signal: new AbortController().signal,
                    downloadQpl: mockQpl
                });
            } catch (eDoc) {
                lastErr = eDoc;
                try {
                    decryptedMedia = await downloadManager.downloadAndMaybeDecrypt({
                        directPath: target.directPath,
                        encFilehash: target.encFilehash,
                        filehash: target.filehash,
                        mediaKey: target.mediaKey,
                        mediaKeyTimestamp: target.mediaKeyTimestamp,
                        type: target.type || 'image',
                        signal: new AbortController().signal,
                        downloadQpl: mockQpl
                    });
                } catch (eType) {
                    lastErr = eType;
                }
            }

            if (decryptedMedia) {
                const data = await window.WWebJS.arrayBufferToBase64Async(decryptedMedia);
                return {
                    data,
                    mimetype: target.mimetype || 'image/jpeg',
                    filename: target.filename,
                    filesize: target.size || decryptedMedia.byteLength
                };
            }

            return { error: lastErr ? (lastErr.message || String(lastErr)) : 'decryptedMedia es nulo' };
        } catch (e) {
            return { error: e?.message || String(e) };
        }
    }, msgId);
}

function asegurarIdSerializado(m) {
    if (!m || !m.id) return;
    if (!m.id._serialized) {
        const remoteJidStr =
            typeof m.id.remote === 'object'
                ? m.id.remote._serialized
                : (m.id.remote || '');

        const participantStr = m.id.participant
            ? `_${typeof m.id.participant === 'object'
                ? m.id.participant._serialized
                : m.id.participant}`
            : '';

        m.id._serialized =
            m.id.$1 ||
            `${m.id.fromMe}_${remoteJidStr}_${m.id.id}${participantStr}`;
    }
}

// Función auxiliar con reintento controlado para descargar multimedia
async function descargarMediaConReintento(msg, maxIntentos = 4) {
    asegurarIdSerializado(msg);

    if (msg.hasMedia || msg.type === 'image' || msg.type === 'document') {
        await new Promise(resolve => setTimeout(resolve, 2000));
    }

    for (let intento = 1; intento <= maxIntentos; intento++) {
        try {
            let targetMsg = msg;
            try {
                if (client && typeof client.getMessageById === 'function') {
                    const lookupId = msg.id?._serialized || msg.id?.id || msg.id;
                    const freshMsg = await client.getMessageById(lookupId).catch(() => null);
                    if (freshMsg) {
                        asegurarIdSerializado(freshMsg);
                        targetMsg = freshMsg;
                    }
                }
            } catch (errLookup) {}

            asegurarIdSerializado(targetMsg);

            let media = null;
            try {
                media = await targetMsg.downloadMedia();
            } catch (errDownload) {}

            if (!media || !media.data) {
                try {
                    const directResult = await descargarDirectoConPuppeteer(client, targetMsg);
                    if (directResult && directResult.data) {
                        media = directResult;
                    }
                } catch (errBypass) {}
            }

            if (media && media.data) {
                return media;
            }
        } catch (errLoop) {
            console.error(`❌ [Media Error] Intento ${intento}: ${errLoop.message}`);
        }

        if (intento < maxIntentos) {
            await new Promise(resolve => setTimeout(resolve, 2500));
        }
    }
    return null;
}

// Inicializar Express
const app = express();
app.use(cors());
app.use(express.json());

// Endpoint para consultar el estado actual de WhatsApp Web
app.get('/status', (req, res) => {
    const isConn = currentStatus === 'CONNECTED';
    const finalPhone = (client && client.info && client.info.wid)
        ? client.info.wid.user
        : (connectedPhoneNumber || '51969306604');

    return res.status(200).json({
        status: currentStatus,
        conectado: isConn,
        phoneNumber: isConn ? finalPhone : null,
        connectedAt: isConn ? (connectedAtTimestamp || new Date().toLocaleString()) : null,
        qrCodeBase64: qrCodeBase64,
        qr: qrCodeBase64
    });
});

app.get('/api/whatsapp/estado', (req, res) => {
    const isConn = currentStatus === 'CONNECTED';
    const finalPhone = (client && client.info && client.info.wid)
        ? client.info.wid.user
        : (connectedPhoneNumber || '51969306604');

    return res.status(200).json({
        status: currentStatus,
        conectado: isConn,
        phoneNumber: isConn ? finalPhone : null,
        connectedAt: isConn ? (connectedAtTimestamp || new Date().toLocaleString()) : null,
        qrCodeBase64: qrCodeBase64,
        qr: qrCodeBase64
    });
});

// Endpoint para purgar sesión zombi manualmente y regenerar QR
app.get('/reset', async (req, res) => {
    console.log('♻️ [Reset Manual] Purgando sesión y reiniciando cliente...');
    try {
        if (client) {
            await client.destroy().catch(() => {});
        }
    } catch (e) {}

    limpiarCarpetaSesion();
    currentStatus = 'DISCONNECTED';
    qrCodeBase64 = null;
    connectedPhoneNumber = null;
    connectedAtTimestamp = null;

    setTimeout(() => {
        inicializarCliente();
    }, 2000);

    return res.status(200).json({
        status: 'RESETTING',
        message: 'Sesión eliminada con éxito. Generando nuevo código QR en breve...'
    });
});

// Endpoint de diagnóstico
app.get('/diag/:id', async (req, res) => {
    try {
        if (!client || !client.pupPage) return res.status(503).json({ error: 'No client or page' });
        const id = req.params.id;
        const result = await client.pupPage.evaluate(async (searchId) => {
            try {
                const collections = window.require('WAWebCollections');
                let target = collections.Msg.get(searchId);
                if (!target) {
                    const fetched = await collections.Msg.getMessagesById([searchId]).catch(e => ({ err: e?.message }));
                    if (fetched && fetched.messages && fetched.messages.length > 0) {
                        target = fetched.messages[0];
                    }
                }
                if (!target) return { found: false, searchId };

                return {
                    found: true,
                    id: target.id?._serialized || String(target.id),
                    type: target.type,
                    mimetype: target.mimetype,
                    size: target.size
                };
            } catch (err) {
                return { fatalEvalError: err.message };
            }
        }, id);
        return res.json(result);
    } catch (e) {
        return res.status(500).json({ error: e.message });
    }
});

// Endpoint de salida compatible con Spring Boot
app.post('/message/sendText/:instance', async (req, res) => {
    try {
        const { number, textMessage } = req.body;
        const text = textMessage ? textMessage.text : '';

        if (!number || !text) {
            return res.status(400).json({ error: 'Número y texto son requeridos' });
        }

        let jid = number;
        if (!jid.includes('@')) {
            const numClean = number.replace(/[^0-9]/g, '');
            jid = (numClean.length >= 15) ? (numClean + '@lid') : (numClean + '@c.us');
        } else {
            const splitJid = jid.split('@');
            const numClean = splitJid[0].replace(/[^0-9]/g, '');
            jid = (splitJid[1] === 'lid' || numClean.length >= 15) ? (numClean + '@lid') : (numClean + '@c.us');
        }

        console.log(`📤 [Envío Solicitado] Destinatario: ${jid} | Mensaje: "${text}"`);

        if (!client) {
            return res.status(503).json({ error: 'El cliente de WhatsApp Web no está inicializado.' });
        }

        const sentMsg = await client.sendMessage(jid, text);

        return res.status(200).json({
            key: {
                id: sentMsg.id.id,
                remoteJid: jid,
                fromMe: true
            },
            message: {
                conversation: text
            },
            status: 'SUCCESS'
        });

    } catch (err) {
        console.error('❌ Error enviando mensaje por WhatsApp Web:', err.message);
        return res.status(500).json({ error: err.message });
    }
});

// Endpoint para redescargar multimedia
app.all('/message/redownload/:msgId', async (req, res) => {
    try {
        const msgId = req.params.msgId;
        console.log(`🔄 [Re-descarga manual solicitada] Buscando mensaje ID: ${msgId}`);
        if (!client) {
            return res.status(503).json({ error: 'Cliente de WhatsApp no inicializado' });
        }
        let freshMsg = await client.getMessageById(msgId).catch(() => null);
        if (!freshMsg) {
            return res.status(404).json({ error: 'Mensaje no encontrado en la sesión' });
        }
        const media = await descargarMediaConReintento(freshMsg, 4);
        if (!media || !media.data) {
            return res.status(500).json({ error: 'Fallo al descargar multimedia' });
        }

        const effectiveMime = media.mimetype || 'image/jpeg';
        const dataUrl = `data:${effectiveMime};base64,${media.data}`;
        const conversationText = `[VOUCHER] ${dataUrl}`;

        const remoteJid = freshMsg.fromMe ? freshMsg.to : freshMsg.from;
        const finalJid = await resolverJidTelefono(remoteJid);

        const payload = {
            event: 'messages.upsert',
            instance: 'LocalBridge',
            data: {
                key: {
                    remoteJid: finalJid,
                    fromMe: freshMsg.fromMe,
                    id: freshMsg.id.id
                },
                pushName: freshMsg._data?.notifyName || 'Cliente WhatsApp',
                lid: remoteJid ? remoteJid.split('@')[0] : '',
                message: {
                    conversation: conversationText
                },
                messageType: 'image',
                messageId: freshMsg.id.id
            }
        };

        const resp = await axios.post(BACKEND_URL, payload, {
            headers: { 'Content-Type': 'application/json; charset=utf-8' }
        });

        return res.status(200).json({ success: true, mime: effectiveMime, size: media.data.length, backendResp: resp.data });
    } catch (e) {
        console.error('❌ Error en re-descarga manual:', e.message);
        return res.status(500).json({ error: e.message });
    }
});

app.listen(PORT, () => {
    console.log('=====================================================');
    console.log(`🌐 Servidor del puente escuchando en puerto ${PORT}`);
    console.log(`📡 URL destino para Webhooks: ${BACKEND_URL}`);
    console.log('=====================================================');
});

process.on('unhandledRejection', (reason) => {
    console.warn('⚠️ [Advertencia Global] Rejection no manejada:', reason?.message || reason);
});

process.on('uncaughtException', (err) => {
    console.warn('⚠️ [Advertencia Global] Excepción no capturada:', err?.message || err);
});

function inicializarCliente() {
    isReconnecting = false;
    currentStatus = 'CONNECTING';
    qrCodeBase64 = null;
    console.log('=====================================================');
    console.log('🤖 INICIALIZANDO CLIENTE WHATSAPP WEB...');
    console.log('=====================================================');

    client = new Client({
        authStrategy: new LocalAuth({
            dataPath: './.wwebjs_auth'
        }),
        authTimeoutMs: 120000,
        puppeteer: {
            headless: true,
            args: [
                '--no-sandbox',
                '--disable-setuid-sandbox',
                '--disable-dev-shm-usage',
                '--disable-accelerated-2d-canvas',
                '--no-first-run',
                '--no-zygote',
                '--disable-gpu',
                '--disable-extensions',
                '--disable-background-networking',
                '--disable-default-apps',
                '--disable-sync',
                '--js-flags=--max-old-space-size=512'
            ]
        }
    });

    client.on('loading_screen', (percent, message) => {
        currentStatus = 'CONNECTING';
        console.log(`⏳ [Cargando] ${percent}% - ${message}`);
    });

    client.on('qr', async (qr) => {
        currentStatus = 'QR_READY';
        console.log('=====================================================');
        console.log('👉 NUEVO CÓDIGO QR GENERADO:');
        qrcodeTerminal.generate(qr, { small: true });
        console.log('=====================================================');

        if (QRCode) {
            try {
                qrCodeBase64 = await QRCode.toDataURL(qr);
            } catch (errQr) {
                console.error('❌ Error convirtiendo QR a Base64:', errQr.message);
            }
        }
    });

    client.on('authenticated', () => {
        currentStatus = 'CONNECTED';
        qrCodeBase64 = null;
        if (!connectedAtTimestamp) {
            connectedAtTimestamp = new Date().toLocaleString('es-PE', { timeZone: 'America/Lima' });
        }
        console.log('🔑 [Autenticación] Sesión autenticada correctamente. Estado: CONNECTED');
    });

    client.on('ready', () => {
        currentStatus = 'CONNECTED';
        qrCodeBase64 = null;
        if (client.info && client.info.wid) {
            connectedPhoneNumber = client.info.wid.user;
            console.log(`📱 Conectado como: ${connectedPhoneNumber}`);
        }
        console.log('=====================================================');
        console.log('✅ ¡WhatsApp Web listo y conectado con éxito!');
        console.log(`📡 Reenviando mensajes hacia: ${BACKEND_URL}`);
        console.log('=====================================================');
    });

    client.on('change_state', (state) => {
        console.log(`🔄 [Estado] Cambio de estado de conexión: ${state}`);
        if (state === 'DISCONNECTED' || state === 'UNPAIRED') {
            currentStatus = 'DISCONNECTED';
            qrCodeBase64 = null;
        }
    });

    client.on('message_create', async (msg) => {
        try {
            if (currentStatus !== 'CONNECTED') {
                currentStatus = 'CONNECTED';
                qrCodeBase64 = null;
            }

            const rawMsgId = msg.id?.id || 'null';

            if (msg.id?.id && isDuplicateMsg(msg.id.id)) {
                return;
            }

            const remoteJid = msg.fromMe ? msg.to : msg.from;

            if (!remoteJid || (!remoteJid.endsWith('@c.us') && !remoteJid.endsWith('@lid'))) {
                return;
            }

            const finalJid = await resolverJidTelefono(remoteJid);
            console.log(`📥 [Mensaje Detectado] ID: ${msg.id.id} | De mí: ${msg.fromMe} | JID Destino: ${finalJid}`);

            let pushName = 'Cliente WhatsApp';
            try {
                if (msg._data && msg._data.notifyName) {
                    pushName = msg._data.notifyName;
                } else {
                    const contactPromise = client.getContactById(finalJid);
                    const timeoutPromise = new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout')), 2000));
                    const contact = await Promise.race([contactPromise, timeoutPromise]).catch(() => null);
                    if (contact) {
                        pushName = contact.pushname || contact.name || 'Cliente WhatsApp';
                    }
                }
            } catch (errContact) {}

            let conversationText = msg.body || '';

            if (msg.hasMedia || msg.type === 'image' || msg.type === 'document' || msg.type === 'audio' || msg.type === 'ptt') {
                try {
                    const media = await descargarMediaConReintento(msg, 4);
                    if (media && media.data) {
                        const mime = (media.mimetype || 'image/jpeg').toLowerCase();
                        const filename = (media.filename || '').toLowerCase();
                        const isImageMime = mime.startsWith('image/');
                        const isImageExt = filename.endsWith('.jpg') || filename.endsWith('.jpeg') || filename.endsWith('.png') || filename.endsWith('.webp');
                        const isImageOrVoucherDoc = isImageMime || msg.type === 'image' || isImageExt;

                        if (isImageOrVoucherDoc) {
                            const effectiveMime = isImageMime ? media.mimetype : 'image/jpeg';
                            const dataUrl = `data:${effectiveMime};base64,${media.data}`;
                            conversationText = `[VOUCHER] ${dataUrl}`;
                            if (msg.body && msg.body.trim()) {
                                conversationText += ` | ${msg.body}`;
                            }
                        } else if (mime.startsWith('audio/') || msg.type === 'audio' || msg.type === 'ptt') {
                            conversationText = '[Audio]';
                        } else {
                            conversationText = '[Archivo]';
                            if (media.filename) {
                                conversationText += ` | ${media.filename}`;
                            }
                        }
                    } else {
                        const rawId = msg.id?.id || 'desconocido';
                        const isPotentialVoucher = msg.type === 'image' || msg.type === 'document' || msg.hasMedia;
                        if (isPotentialVoucher && msg.type !== 'audio' && msg.type !== 'ptt') {
                            conversationText = `[VOUCHER_FALLIDO] | El cliente adjuntó un comprobante pero no pudo descargarse automáticamente | wamid:${rawId}`;
                            if (msg.body && msg.body.trim()) {
                                conversationText += ` | ${msg.body}`;
                            }
                        } else {
                            conversationText = '[Archivo]';
                        }
                    }
                } catch (err) {
                    conversationText = '[Archivo]';
                }
            }

            const payload = {
                event: 'messages.upsert',
                instance: 'LocalBridge',
                data: {
                    key: {
                        remoteJid: finalJid,
                        fromMe: msg.fromMe,
                        id: msg.id.id
                    },
                    pushName: pushName,
                    lid: remoteJid.split('@')[0],
                    message: {
                        conversation: conversationText
                    },
                    messageType: msg.type || 'conversation',
                    messageId: msg.id.id
                }
            };

            const logPayload = JSON.parse(JSON.stringify(payload));
            if (logPayload.data?.message?.conversation?.startsWith('[VOUCHER] data:')) {
                logPayload.data.message.conversation = logPayload.data.message.conversation.substring(0, 100) + '... [Base64 Truncado]';
            }
            console.log('📤 Enviando payload al backend:', JSON.stringify(logPayload, null, 2));

            axios.post(BACKEND_URL, payload, {
                headers: {
                    'Content-Type': 'application/json; charset=utf-8',
                    'Accept': 'application/json',
                    'Accept-Charset': 'utf-8',
                    'ngrok-skip-browser-warning': 'true'
                }
            })
            .then(response => {
                console.log(`🚀 Webhook enviado con éxito. Código HTTP: ${response.status}. Respuesta:`, response.data);
            })
            .catch(error => {
                console.error('❌ Error enviando webhook al backend:', error.message);
                if (error.response) {
                    console.error(`   Código HTTP de error: ${error.response.status}`);
                    console.error(`   Cuerpo del error:`, error.response.data);
                } else if (error.request) {
                    console.error(`   No se pudo recibir respuesta del backend en: ${BACKEND_URL}`);
                }
            });

        } catch (e) {
            console.error('❌ Error en message_create:', e);
        }
    });

    client.on('auth_failure', (msg) => {
        currentStatus = 'DISCONNECTED';
        qrCodeBase64 = null;
        connectedPhoneNumber = null;
        connectedAtTimestamp = null;
        console.error('❌ Fallo de autenticación en WhatsApp:', msg);
        limpiarCarpetaSesion();
        rebotarConexion();
    });

    client.on('disconnected', (reason) => {
        currentStatus = 'DISCONNECTED';
        qrCodeBase64 = null;
        connectedPhoneNumber = null;
        connectedAtTimestamp = null;
        console.warn('❌ Desconectado de WhatsApp. Motivo:', reason);

        // Si la sesión fue revocada en el teléfono o expiró, limpiar archivos para generar QR fresco
        if (reason === 'LOGOUT' || reason === 'UNPAIRED' || reason === 'CONFLICT') {
            console.log('🚪 Sesión revocada. Eliminando archivos residuales...');
            limpiarCarpetaSesion();
        }
        rebotarConexion();
    });

    client.initialize().catch(err => {
        console.error('❌ Error crítico al inicializar cliente de WhatsApp Web:', err?.message || err);
        rebotarConexion();
    });
}

function rebotarConexion() {
    if (isReconnecting) return;
    isReconnecting = true;
    console.log('🔄 Intentando reconexión en 5 segundos...');
    setTimeout(async () => {
        if (client) {
            try {
                await client.destroy().catch(() => {});
            } catch (e) {}
            client = null;
        }
        inicializarCliente();
    }, 5000);
}

// Iniciar cliente al arrancar el script
inicializarCliente();