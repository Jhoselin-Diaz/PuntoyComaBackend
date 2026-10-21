const { Client, LocalAuth } = require('whatsapp-web.js');
const qrcodeTerminal = require('qrcode-terminal');
const axios = require('axios');
const express = require('express');
const cors = require('cors'); // <-- 1. IMPORTAR CORS

let QRCode = null;
try {
    QRCode = require('qrcode');
} catch (e) {
    console.warn('⚠️ Módulo "qrcode" no disponible. Ejecuta npm install en puente-whatsapp para generar Base64 de QR.');
}

// 2. Variables dinámicas para Azure y entorno local
const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080/webhook';
const PORT = process.env.PORT || 3001;

// Estado global en memoria para consulta del backend/frontend
let currentStatus = 'DISCONNECTED'; // Estados: 'DISCONNECTED', 'CONNECTING', 'QR_READY', 'CONNECTED'
let qrCodeBase64 = null;
let client = null;
let isReconnecting = false;

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

            // Forzar resolución de mediaData en WhatsApp Web
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

            // En WhatsApp PC / Web, Meta CDN retorna Content-Type: application/octet-stream.
            // Para evitar InvalidMediaFileType, intentar con type: 'document' primero, y fallback a target.type.
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

    const msgIdStr = msg.id?._serialized || msg.id?.id || 'desconocido';

    console.log('=====================================================');
    console.log(`🔍 [Diagnóstico Media] Inspección del objeto msg (ID: ${msgIdStr}):`);
    console.log(`   - msg.id:`, JSON.stringify(msg.id));
    console.log(`   - msg.type: ${msg.type}`);
    console.log(`   - msg.hasMedia: ${msg.hasMedia}`);
    console.log(`   - msg.mimetype: ${msg.mimetype || msg._data?.mimetype || '(no definido)'}`);
    console.log(`   - Estado Conexión Puente: currentStatus=${currentStatus}, clientReady=${client && currentStatus === 'CONNECTED'}`);
    if (msg._data) {
        console.log(`   - msg._data (Campos de Media):`, {
            mimetype: msg._data.mimetype,
            size: msg._data.size,
            directPath: msg._data.directPath,
            mediaKey: msg._data.mediaKey ? '[PRESENTE]' : '[AUSENTE]',
            encFilehash: msg._data.encFilehash,
            deprecatedMms3Url: msg._data.deprecatedMms3Url
        });
    }
    console.log('=====================================================');

    // Pausa inicial de 2.0 segundos antes del primer intento de descarga
    // Permite que Puppeteer y el cliente Web sincronicen el archivo y descifren tokens con el CDN de WhatsApp
    if (msg.hasMedia || msg.type === 'image' || msg.type === 'document') {
        console.log(`⏳ [Media Pre-descarga] Pausa inicial de 2.0s para sincronización de tokens/CDN de WhatsApp...`);
        await new Promise(resolve => setTimeout(resolve, 2000));
    }

    for (let intento = 1; intento <= maxIntentos; intento++) {
        try {
            console.log(`⏳ [Media] Intentando descargar multimedia para mensaje ID ${msgIdStr} (Intento ${intento}/${maxIntentos})...`);
            
            if (!client || currentStatus !== 'CONNECTED') {
                console.warn(`⚠️ [Media] Advertencia: El cliente de WhatsApp Web no está en estado CONNECTED (Estado actual: ${currentStatus}).`);
            }

            // Re-obtener el mensaje actualizado directamente de la sesión de WhatsApp Web en cada intento
            let targetMsg = msg;
            try {
                if (client && typeof client.getMessageById === 'function') {
                    const lookupId = msg.id?._serialized || msg.id?.id || msg.id;
                    const freshMsg = await client.getMessageById(lookupId).catch(() => null);
                    if (freshMsg) {
                        asegurarIdSerializado(freshMsg);
                        const stage = freshMsg._data?.mediaData?.mediaStage || 'no definido';
                        console.log(`🔄 [Media] Mensaje fresco obtenido de la sesión (mediaStage: ${stage}, _serialized: ${freshMsg.id?._serialized}).`);
                        targetMsg = freshMsg;
                    }
                }
            } catch (errLookup) {
                console.warn(`⚠️ [Media] No se pudo re-obtener freshMsg por ID, usando instancia actual:`, errLookup.message);
            }
            asegurarIdSerializado(targetMsg);

            let media = null;
            try {
                media = await targetMsg.downloadMedia();
            } catch (errDownload) {
                console.warn(`⚠️ [Media] targetMsg.downloadMedia() falló (${errDownload?.message || errDownload}). Ejecutando bypass directo vía WAWebDownloadManager...`);
            }

            // Si downloadMedia devolvió nulo o lanzó error, ejecutar bypass con Puppeteer
            if (!media || !media.data) {
                try {
                    const directResult = await descargarDirectoConPuppeteer(client, targetMsg);
                    if (directResult && directResult.data) {
                        console.log(`✨ [Media Bypass Exitoso] Descarga directa completada. Mime: ${directResult.mimetype}, Tamaño Base64: ${directResult.data.length} caracteres.`);
                        media = directResult;
                    } else if (directResult && directResult.error) {
                        console.warn(`⚠️ [Media Bypass Advertencia]`, directResult.error);
                    }
                } catch (errBypass) {
                    console.error(`❌ [Media Bypass Error]`, errBypass.message);
                }
            }

            if (media && media.data) {
                console.log(`✅ [Media] Multimedia descargada con éxito en el intento ${intento}. Mime: ${media.mimetype}, Tamaño Data Base64: ${media.data.length} caracteres.`);
                return media;
            } else if (!media) {
                console.warn(`⚠️ [Media] No se obtuvieron datos en el intento ${intento} para el mensaje ID ${msgIdStr}`);
            } else {
                console.warn(`⚠️ [Media] Objeto media sin propiedad data en el intento ${intento} para el mensaje ID ${msgIdStr}`);
            }
        } catch (errLoop) {
            console.error(`❌ [Media Error Bucle] Falló intento ${intento}: ${errLoop.message}`);
        }

        if (intento < maxIntentos) {
            console.log(`⏱️ Esperando 3000ms adicionales antes del intento ${intento + 1}...`);
            await new Promise(resolve => setTimeout(resolve, 3000));
        }
    }

    console.error(`💥 [Media Error Final] Fallaron todos los ${maxIntentos} intentos de descarga para el mensaje ID ${msgIdStr}.`);
    return null;
}

// Inicializar Express para recibir comandos de envío de Spring Boot y responder estado
const app = express();
app.use(express.json());

// Endpoint para consultar el estado actual de WhatsApp Web y obtener el QR si existe
app.get('/status', (req, res) => {
    return res.status(200).json({
        status: currentStatus,
        qrCodeBase64: qrCodeBase64
    });
});

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

                if (!target) {
                    return { found: false, searchId };
                }

                const info = {
                    found: true,
                    id: target.id?._serialized || String(target.id),
                    type: target.type,
                    mimetype: target.mimetype,
                    size: target.size,
                    directPath: target.directPath,
                    hasMediaKey: !!target.mediaKey,
                    mediaKeyLength: target.mediaKey ? target.mediaKey.length : 0,
                    encFilehash: target.encFilehash,
                    filehash: target.filehash,
                    mediaStage: target.mediaData?.mediaStage,
                    mediaDataKeys: target.mediaData ? Object.keys(target.mediaData) : []
                };

                // Test downloadMedia
                let nativeDlResult = null;
                try {
                    if (typeof target.downloadMedia === 'function') {
                        const r = await target.downloadMedia({ downloadEvenIfExpensive: true, rmrReason: 1 });
                        nativeDlResult = { success: true, ret: typeof r };
                    }
                } catch (eNative) {
                    nativeDlResult = {
                        success: false,
                        name: eNative?.name,
                        message: eNative?.message,
                        stack: eNative?.stack,
                        string: String(eNative),
                        props: Object.keys(eNative || {})
                    };
                }
                info.nativeDl = nativeDlResult;
                info.mediaStageAfterNative = target.mediaData?.mediaStage;

                // Test downloadAndMaybeDecrypt with type: 'image' vs type: 'document'
                let decryptResult = null;
                try {
                    const downloadManager = window.require('WAWebDownloadManager')?.downloadManager;
                    if (downloadManager && typeof downloadManager.downloadAndMaybeDecrypt === 'function') {
                        const mockQpl = {
                            addAnnotations: () => mockQpl,
                            addPoint: () => mockQpl
                        };
                        // Try with type: 'document' if image fails
                        let decryptedMedia = null;
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
                            decryptResult = { docError: eDoc.message };
                        }

                        if (decryptedMedia) {
                            const data = await window.WWebJS.arrayBufferToBase64Async(decryptedMedia);
                            decryptResult = {
                                successWithDocType: true,
                                byteLength: decryptedMedia.byteLength,
                                base64Len: data ? data.length : 0,
                                sample: data ? data.substring(0, 30) : null
                            };
                        }
                    }
                } catch (eDec) {
                    decryptResult = { error: eDec.message };
                }
                info.decryptResult = decryptResult;

                // Inspect mediaData values
                const mdVals = {};
                if (target.mediaData) {
                    for (const k of Object.keys(target.mediaData)) {
                        const v = target.mediaData[k];
                        if (v === null || v === undefined) {
                            mdVals[k] = String(v);
                        } else if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') {
                            mdVals[k] = v;
                        } else {
                            mdVals[k] = `[${typeof v}: ${v.constructor?.name}]`;
                        }
                    }
                }
                info.mediaDataValues = mdVals;

                // Check __x_mediaBlob
                const mb = target.mediaData?.__x_mediaBlob || target.mediaData?.mediaBlob;
                if (mb) {
                    try {
                        let raw = mb;
                        if (typeof mb.blob === 'function') raw = await mb.blob();
                        else if (mb._blob) raw = mb._blob;
                        if (raw && typeof raw.arrayBuffer === 'function') {
                            const buf = await raw.arrayBuffer();
                            const b64 = await window.WWebJS.arrayBufferToBase64Async(buf);
                            info.extractedFromMb = { len: b64 ? b64.length : 0, sample: b64 ? b64.substring(0, 30) : null };
                        }
                    } catch (eMb) {
                        info.extractedFromMbError = eMb.message;
                    }
                }

                return info;
            } catch (err) {
                return { fatalEvalError: err.message, stack: err.stack };
            }
        }, id);
        return res.json(result);
    } catch (e) {
        return res.status(500).json({ error: e.message });
    }
});

// Endpoint de salida compatible con la firma de Evolution API que usa Spring Boot
app.post('/message/sendText/:instance', async (req, res) => {
    try {
        const { number, textMessage } = req.body;
        const text = textMessage ? textMessage.text : '';

        if (!number || !text) {
            return res.status(400).json({ error: 'Número y texto son requeridos' });
        }

        // Formatear el JID dinámicamente según sea LID (15 dígitos) o cuenta estándar (@c.us)
        let jid = number;
        if (!jid.includes('@')) {
            const numClean = number.replace(/[^0-9]/g, '');
            if (numClean.length >= 15) {
                jid = numClean + '@lid';
            } else {
                jid = numClean + '@c.us';
            }
        } else {
            const splitJid = jid.split('@');
            const numClean = splitJid[0].replace(/[^0-9]/g, '');
            if (splitJid[1] === 'lid' || numClean.length >= 15) {
                jid = numClean + '@lid';
            } else {
                jid = numClean + '@c.us';
            }
        }

        console.log(`📤 [Envío Solicitado] Destinatario: ${jid} | Mensaje: "${text}"`);

        if (!client) {
            return res.status(503).json({ error: 'El cliente de WhatsApp Web no está inicializado.' });
        }

        // Enviar mensaje
        const sentMsg = await client.sendMessage(jid, text);

        // Retornar formato de respuesta que Spring Boot espera
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

// Endpoint de soporte para redescargar media de un mensaje por ID y reenviar webhook a Spring Boot
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
    console.log(`🌐 Servidor del puente escuchando en http://localhost:${PORT}`);
    console.log('=====================================================');
});

// Manejadores globales para evitar caídas abruptas del proceso por Puppeteer o micro-cortes
process.on('unhandledRejection', (reason, promise) => {
    console.warn('⚠️ [Advertencia Global] Rejection no manejada detectada en el puente (no se detendrá el proceso):', reason);
});

process.on('uncaughtException', (err, origin) => {
    console.warn('⚠️ [Advertencia Global] Excepción no capturada detectada en el puente (no se detendrá el proceso):', err.message || err);
});

function inicializarCliente() {
    isReconnecting = false;
    currentStatus = 'CONNECTING';
    qrCodeBase64 = null;
    console.log('=====================================================');
    console.log('🤖 INICIALIZANDO CLIENTE WHATSAPP WEB...');
    console.log('=====================================================');

    client = new Client({
        authStrategy: new LocalAuth(),
        webVersionCache: {
            type: 'remote',
            remotePath: 'https://raw.githubusercontent.com/wwebjs/web-dapi-cached/main/latest.html'
        },
        puppeteer: {
            headless: true,
            args: [
                '--no-sandbox', 
                '--disable-setuid-sandbox',
                '--disable-dev-shm-usage',
                '--disable-accelerated-2d-canvas',
                '--no-first-run',
                '--no-zygote',
                '--disable-gpu'
            ]
        }
    });

    // Eventos de estado para diagnóstico visible en la terminal
    client.on('loading_screen', (percent, message) => {
        currentStatus = 'CONNECTING';
        console.log(`⏳ [Cargando] ${percent}% - ${message}`);
    });

    client.on('qr', async (qr) => {
        currentStatus = 'QR_READY';
        console.log('=====================================================');
        console.log('👉 NUEVO CÓDIGO QR GENERADO. Escanéalo con tu celular:');
        qrcodeTerminal.generate(qr, { small: true });
        console.log('=====================================================');

        // Convertir QR a formato Base64 (data:image/png;base64,...)
        if (QRCode) {
            try {
                qrCodeBase64 = await QRCode.toDataURL(qr);
            } catch (errQr) {
                console.error('❌ Error convirtiendo QR a Base64:', errQr.message);
            }
        }
    });

    client.on('authenticated', () => {
        currentStatus = 'CONNECTING';
        qrCodeBase64 = null;
        console.log('🔑 [Autenticación] Sesión autenticada correctamente.');
    });

    client.on('ready', () => {
        currentStatus = 'CONNECTED';
        qrCodeBase64 = null;
        console.log('=====================================================');
        console.log('✅ ¡WhatsApp Web listo y conectado con éxito!');
        console.log(`📡 Enviando webhooks a: ${BACKEND_URL}`);
        console.log('=====================================================');
    });

    client.on('change_state', (state) => {
        console.log(`🔄 [Estado] Cambio de estado de conexión: ${state}`);
        if (state === 'DISCONNECTED' || state === 'UNPAIRED') {
            currentStatus = 'DISCONNECTED';
            qrCodeBase64 = null;
            console.warn('⚠️ Conexión perdida con el dispositivo remoto');
        }
    });

    // Capturar todos los mensajes creados (tanto entrantes como salientes)
    client.on('message_create', async (msg) => {
        try {
            const rawMsgId = msg.id?.id || 'null';
            // LOG ABSOLUTO E INMEDIATO: Captura todos los eventos creados antes de filtrar
            console.log(`📥 [Mensaje Creado/Detectado] ID: ${rawMsgId} | from: ${msg.from} | to: ${msg.to} | De mí: ${msg.fromMe} | Cuerpo: "${msg.body || '(sin texto)'}"`);

            // Deduplicación temprana en Node por ID de mensaje de WhatsApp
            if (msg.id?.id && isDuplicateMsg(msg.id.id)) {
                console.log(`ℹ️ [Filtro Node] Mensaje con ID ${msg.id.id} ya procesado recientemente. Omitiendo webhook duplicado.`);
                return;
            }

            // Identificar el socio del chat (el número del cliente en chats individuales)
            const remoteJid = msg.fromMe ? msg.to : msg.from;

            // Filtrar y omitir chats grupales o estados (permitir @c.us y @lid)
            if (!remoteJid || (!remoteJid.endsWith('@c.us') && !remoteJid.endsWith('@lid'))) {
                console.log(`ℹ️ [Filtro] Mensaje ignorado por no pertenecer a un chat individual (@c.us o @lid). JID: ${remoteJid}`);
                return;
            }

            // Traducir el JID si es de tipo @lid para enviarlo como número telefónico real (@c.us)
            const finalJid = await resolverJidTelefono(remoteJid);

            // LOG CON EL JID FINAL RESOLVIDO
            console.log(`📥 [Mensaje Detectado] ID: ${msg.id.id} | De mí: ${msg.fromMe} | JID Destino: ${finalJid}`);

            // Obtener el nombre del perfil de manera no bloqueante.
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
            } catch (errContact) {
                console.warn('⚠️ No se pudo recuperar el nombre del contacto, usando por defecto:', errContact.message);
            }

            // Estructurar el texto de la conversación de forma robusta y simplificada
            let conversationText = msg.body || '';

            if (msg.hasMedia || msg.type === 'image' || msg.type === 'document' || msg.type === 'audio' || msg.type === 'ptt') {
                try {
                    const media = await descargarMediaConReintento(msg, 4);
                    if (media && media.data) {
                        const mime = (media.mimetype || 'image/jpeg').toLowerCase();
                        const filename = (media.filename || '').toLowerCase();
                        
                        // Soporte para imágenes enviadas como foto directa o como documento (.jpg, .jpeg, .png, .webp)
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
                        // Fallback cuando la descarga no pudo obtener los bytes
                        const rawId = msg.id?.id || 'desconocido';
                        const isPotentialVoucher = msg.type === 'image' || msg.type === 'document' || msg.hasMedia;
                        if (isPotentialVoucher && msg.type !== 'audio' && msg.type !== 'ptt') {
                            console.warn(`⚠️ [Media Fallback] Descarga fallida de comprobante/imagen para mensaje ID ${rawId}. Generando [VOUCHER_FALLIDO].`);
                            conversationText = `[VOUCHER_FALLIDO] | El cliente adjuntó un comprobante pero no pudo descargarse automáticamente | wamid:${rawId}`;
                            if (msg.body && msg.body.trim()) {
                                conversationText += ` | ${msg.body}`;
                            }
                        } else {
                            conversationText = '[Archivo]';
                        }
                    }
                } catch (err) {
                    console.error('⚠️ Error procesando multimedia:', err.message);
                    const rawId = msg.id?.id || 'desconocido';
                    const isPotentialVoucher = msg.type === 'image' || msg.type === 'document' || msg.hasMedia;
                    if (isPotentialVoucher && msg.type !== 'audio' && msg.type !== 'ptt') {
                        conversationText = `[VOUCHER_FALLIDO] | El cliente adjuntó un comprobante pero no pudo descargarse automáticamente | wamid:${rawId}`;
                    } else {
                        conversationText = '[Archivo]';
                    }
                }
            }

            // Estructurar el payload simulando la estructura del webhook de Evolution API
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
                    lid: remoteJid.split('@')[0], // Pasar el LID original para unificar e impedir duplicaciones
                    message: {
                        conversation: conversationText
                    },
                    messageType: msg.type || 'conversation',
                    messageId: msg.id.id
                }
            };

            // Log del payload abreviado para no inundar el terminal
            const logPayload = JSON.parse(JSON.stringify(payload));
            if (logPayload.data && logPayload.data.message && logPayload.data.message.conversation) {
                if (logPayload.data.message.conversation.startsWith('[VOUCHER] data:')) {
                    logPayload.data.message.conversation = logPayload.data.message.conversation.substring(0, 100) + '... [Base64 Truncado]';
                }
            }
            console.log('📤 Enviando payload al backend:', JSON.stringify(logPayload, null, 2));

            // Enviar POST a Spring Boot forzando codificación UTF-8
            axios.post(BACKEND_URL, payload, {
                headers: {
                    'Content-Type': 'application/json; charset=utf-8',
                    'Accept': 'application/json',
                    'Accept-Charset': 'utf-8',
                    'ngrok-skip-browser-warning': 'true'
                }
            })
            .then(response => {
                console.log(`🚀 Webhook enviado con éxito. Respuesta del backend: ${response.data}`);
            })
            .catch(error => {
                console.error('❌ Error enviando webhook al backend:', error.message);
                if (error.response) {
                    console.error(`   Código HTTP de error: ${error.response.status}`);
                    console.error(`   Cuerpo del error:`, error.response.data);
                } else if (error.request) {
                    console.error(`   No se pudo recibir respuesta del backend local. ¿El servidor Spring Boot está corriendo en el puerto 8080?`);
                }
            });

        } catch (e) {
            console.error('❌ Error en message_create:', e);
        }
    });

    // Control de fallas de autenticación
    client.on('auth_failure', (msg) => {
        currentStatus = 'DISCONNECTED';
        qrCodeBase64 = null;
        console.error('❌ Fallo de autenticación en WhatsApp:', msg);
        rebotarConexion();
    });

    // Control de desconexión
    client.on('disconnected', (reason) => {
        currentStatus = 'DISCONNECTED';
        qrCodeBase64 = null;
        console.warn('❌ Desconectado de WhatsApp:', reason);
        rebotarConexion();
    });

    client.initialize().catch(err => {
        console.error('❌ Error crítico al inicializar cliente de WhatsApp Web:', err);
        rebotarConexion();
    });
}

function rebotarConexion() {
    if (isReconnecting) return;
    isReconnecting = true;
    console.log('🔄 Intentando reconexión en 5 segundos...');
    setTimeout(() => {
        if (client) {
            try {
                client.destroy().catch(() => {});
            } catch (e) {}
        }
        inicializarCliente();
    }, 5000);
}

// Iniciar automáticamente el cliente al arrancar el script
inicializarCliente();
