const fs = require('fs');
const path = require('path');
const express = require('express');
const cors = require('cors');
const axios = require('axios');
const qrcodeTerminal = require('qrcode-terminal');
const QRCode = require('qrcode');
const pino = require('pino');
const {
    default: makeWASocket,
    useMultiFileAuthState,
    DisconnectReason,
    fetchLatestBaileysVersion,
    downloadMediaMessage,
    extractMessageContent
} = require('@whiskeysockets/baileys');

// ── 3. Configuración de logger silencioso/warn para evitar saturación de logs ──
const logger = pino({
    level: process.env.LOG_LEVEL || 'warn'
});

// ── Configuración de la URL de Spring Boot con fallback a Azure ─────────────
let rawBackendUrl = process.env.BACKEND_URL || 'https://puntoycoma-backend-cde2g6htdcewgafr.eastus2-01.azurewebsites.net/webhook';
rawBackendUrl = rawBackendUrl.trim();

// Si corre en Azure App Service y apunta a localhost, sustituir por URL pública de Azure
if (process.env.WEBSITE_SITE_NAME && (rawBackendUrl.includes('localhost') || rawBackendUrl.includes('127.0.0.1'))) {
    rawBackendUrl = 'https://puntoycoma-backend-cde2g6htdcewgafr.eastus2-01.azurewebsites.net/webhook';
}

if (!rawBackendUrl.endsWith('/webhook') && !rawBackendUrl.includes('/api/')) {
    rawBackendUrl = rawBackendUrl.replace(/\/+$/, '') + '/webhook';
}
const BACKEND_URL = rawBackendUrl;
const PORT = process.env.PORT || 3001;

// Carpeta de persistencia para credenciales de Baileys
const AUTH_DIR = path.join(__dirname, 'baileys_auth');

// ── Estado global en memoria para consulta del backend y frontend ────────────
let currentStatus = 'DISCONNECTED'; // 'DISCONNECTED', 'CONNECTING', 'QR_READY', 'CONNECTED'
let qrCodeBase64 = null;
let sock = null;
let isReconnecting = false;
let connectedPhoneNumber = null;
let connectedAtTimestamp = null;

// Caché de deduplicación en memoria para mensajes (Key: msgId, Value: timestamp)
const processedMsgIds = new Map();

// ── 1. Mapeos bidireccionales en memoria para Resolución LID <-> Teléfono ────
const lidToPhoneMap = new Map();
const phoneToLidMap = new Map();

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

// Limpiar carpeta de sesión de Baileys (y residuos legacy si existieran)
function limpiarCarpetaSesion() {
    try {
        if (fs.existsSync(AUTH_DIR)) {
            fs.rmSync(AUTH_DIR, { recursive: true, force: true });
            console.log(`🧹 [Limpieza] Carpeta de sesión Baileys '${AUTH_DIR}' eliminada con éxito.`);
        }
        const legacyAuth = path.join(__dirname, '.wwebjs_auth');
        if (fs.existsSync(legacyAuth)) {
            fs.rmSync(legacyAuth, { recursive: true, force: true });
            console.log('🧹 [Limpieza] Carpeta residual legacy .wwebjs_auth eliminada.');
        }
    } catch (err) {
        console.error('⚠️ Error al limpiar carpeta de sesión:', err.message);
    }
}

// ── Inicialización del Socket de Baileys ─────────────────────────────────────
async function inicializarCliente() {
    isReconnecting = false;
    currentStatus = 'CONNECTING';
    qrCodeBase64 = null;

    console.log('=====================================================');
    console.log('🤖 INICIALIZANDO CLIENTE WHATSAPP CON BAILEYS...');
    console.log('=====================================================');

    try {
        // Asegurar que la carpeta de credenciales existe
        if (!fs.existsSync(AUTH_DIR)) {
            fs.mkdirSync(AUTH_DIR, { recursive: true });
        }

        const { state, saveCreds } = await useMultiFileAuthState(AUTH_DIR);
        const { version } = await fetchLatestBaileysVersion().catch(() => ({
            version: [2, 3000, 1015901307]
        }));

        // ── 3. Configuración del socket para evitar Bad MAC y sincronización pesada ──
        sock = makeWASocket({
            version,
            auth: state,
            logger,
            printQRInTerminal: false,
            browser: ['PuntoyComa Bridge', 'Chrome', '1.0.0'],
            generateHighQualityLinkPreview: false,
            syncFullHistory: false, // Evita descargar chats antiguos que causan Bad MAC
            getMessage: async (key) => {
                // Permite a Baileys gestionar reintentos de cifrado sin lanzar Bad MAC
                return undefined;
            }
        });

        // Persistir cambios en las credenciales automáticamente
        sock.ev.on('creds.update', saveCreds);

        // ── 1. Monitorear eventos de contactos para resolución LID <-> Teléfono ──
        sock.ev.on('contacts.upsert', (contacts) => {
            if (!contacts || !Array.isArray(contacts)) return;
            for (const c of contacts) {
                if (c.id && c.lid) {
                    const phone = c.id.split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
                    const lid = c.lid.split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
                    if (phone && lid) {
                        lidToPhoneMap.set(lid, phone);
                        phoneToLidMap.set(phone, lid);
                    }
                }
            }
        });

        sock.ev.on('contacts.update', (contacts) => {
            if (!contacts || !Array.isArray(contacts)) return;
            for (const c of contacts) {
                if (c.id && c.lid) {
                    const phone = c.id.split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
                    const lid = c.lid.split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
                    if (phone && lid) {
                        lidToPhoneMap.set(lid, phone);
                        phoneToLidMap.set(phone, lid);
                    }
                }
            }
        });

        // Monitorear cambios de conexión y emisión de QR
        sock.ev.on('connection.update', async (update) => {
            const { connection, lastDisconnect, qr } = update;

            // 1. Emisión de nuevo código QR
            if (qr) {
                currentStatus = 'QR_READY';
                console.log('=====================================================');
                console.log('👉 NUEVO CÓDIGO QR GENERADO (Escanea con tu WhatsApp):');
                qrcodeTerminal.generate(qr, { small: true });
                console.log('=====================================================');

                try {
                    qrCodeBase64 = await QRCode.toDataURL(qr);
                } catch (errQr) {
                    console.error('❌ Error generando DataURL de código QR:', errQr.message);
                }
            }

            // 2. Estado conectando
            if (connection === 'connecting') {
                currentStatus = 'CONNECTING';
                console.log('⏳ [Baileys] Conectando con los servidores de WhatsApp...');
            }

            // 3. Conexión establecida con éxito
            if (connection === 'open') {
                currentStatus = 'CONNECTED';
                qrCodeBase64 = null;
                connectedAtTimestamp = new Date().toLocaleString('es-PE', { timeZone: 'America/Lima' });

                if (sock.user && sock.user.id) {
                    connectedPhoneNumber = sock.user.id.split(':')[0].split('@')[0];
                    console.log(`📱 Conectado como: ${connectedPhoneNumber}`);
                }

                console.log('=====================================================');
                console.log('✅ ¡WhatsApp Web listo y conectado con éxito (Baileys)!');
                console.log(`📡 Reenviando mensajes hacia: ${BACKEND_URL}`);
                console.log('=====================================================');
            }

            // 4. Desconexión o cierre
            if (connection === 'close') {
                currentStatus = 'DISCONNECTED';
                qrCodeBase64 = null;
                connectedPhoneNumber = null;
                connectedAtTimestamp = null;

                const statusCode = lastDisconnect?.error?.output?.statusCode || lastDisconnect?.error?.statusCode;
                const isLoggedOut = statusCode === DisconnectReason.loggedOut;

                console.warn(`❌ [Baileys] Conexión cerrada. Código: ${statusCode} | Motivo: ${lastDisconnect?.error?.message || 'Desconocido'}`);

                if (isLoggedOut) {
                    console.log('🚪 Sesión cerrada en el dispositivo móvil (loggedOut). Purgando credenciales de sesión...');
                    limpiarCarpetaSesion();
                    programarReconexion(1000);
                } else {
                    console.log('🔄 Desconexión transitoria. Reconectando en 3 segundos...');
                    programarReconexion(3000);
                }
            }
        });

        // ── Recepción y procesamiento de mensajes entrantes (messages.upsert) ──
        sock.ev.on('messages.upsert', async ({ messages, type }) => {
            if (!messages || !Array.isArray(messages)) return;

            for (const m of messages) {
                try {
                    // Ignorar mensajes sin contenido o stub
                    if (!m.message) continue;

                    const msgId = m.key?.id;
                    if (isDuplicateMsg(msgId)) {
                        continue;
                    }

                    const remoteJid = m.key?.remoteJid;
                    if (!remoteJid) continue;

                    // Ignorar grupos (@g.us) y estados/historias (status@broadcast)
                    if (remoteJid === 'status@broadcast' || remoteJid.endsWith('@g.us')) {
                        continue;
                    }

                    // ── 1. RESOLUCIÓN DE LID A NÚMERO TELEFÓNICO REAL Y NORMALIZACIÓN A @c.us ──
                    let finalJid = '';
                    let lid = '';

                    if (remoteJid.endsWith('@lid') || remoteJid.includes('@lid')) {
                        const rawLid = remoteJid.split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
                        lid = rawLid;

                        // Intentar resolver el teléfono real del participante
                        let resolvedPhone = null;
                        const participantCandidate = m.key?.participant || m.participant;
                        if (participantCandidate && (participantCandidate.endsWith('@s.whatsapp.net') || participantCandidate.endsWith('@c.us'))) {
                            resolvedPhone = participantCandidate.split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
                            lidToPhoneMap.set(rawLid, resolvedPhone);
                            phoneToLidMap.set(resolvedPhone, rawLid);
                            console.log(`✨ [Resolución LID] Mapeado vía participant: LID ${rawLid} -> Teléfono ${resolvedPhone}`);
                        } else if (lidToPhoneMap.has(rawLid)) {
                            resolvedPhone = lidToPhoneMap.get(rawLid);
                            console.log(`✨ [Resolución LID] Encontrado en caché de contactos: LID ${rawLid} -> Teléfono ${resolvedPhone}`);
                        }

                        if (resolvedPhone) {
                            finalJid = `${resolvedPhone}@c.us`;
                        } else {
                            // Coherencia: si aún no se tiene el teléfono, mantener estructura @c.us con el LID
                            finalJid = `${rawLid}@c.us`;
                            console.log(`ℹ️ [Resolución LID] Teléfono no resuelto para LID ${rawLid}, enviando ${finalJid}`);
                        }
                    } else if (remoteJid.endsWith('@s.whatsapp.net')) {
                        // Limpiar número y transformar @s.whatsapp.net a @c.us
                        const cleanNumber = remoteJid.split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
                        finalJid = `${cleanNumber}@c.us`;

                        // Asociar LID si lo conocemos
                        if (phoneToLidMap.has(cleanNumber)) {
                            lid = phoneToLidMap.get(cleanNumber);
                        } else if (m.key?.participant && m.key.participant.includes('@lid')) {
                            lid = m.key.participant.split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
                            lidToPhoneMap.set(lid, cleanNumber);
                            phoneToLidMap.set(cleanNumber, lid);
                        } else {
                            lid = cleanNumber;
                        }
                    } else {
                        // Formato directo @c.us u otro
                        const cleanDigits = remoteJid.split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
                        finalJid = `${cleanDigits}@c.us`;
                        lid = cleanDigits;
                    }

                    const fromMe = Boolean(m.key?.fromMe);
                    const pushName = m.pushName || (fromMe ? 'Administrador' : 'Cliente WhatsApp');

                    // Desenvolver contenido (ephemeral, viewOnce, etc.)
                    const content = extractMessageContent(m.message);
                    if (!content) continue;

                    let conversationText = '';
                    let messageType = 'conversation';

                    // 1. Mensaje de texto (plano o extendido)
                    if (content.conversation) {
                        conversationText = content.conversation;
                    } else if (content.extendedTextMessage?.text) {
                        conversationText = content.extendedTextMessage.text;
                    }

                    // 2. Mensajes multimedia (imágenes o comprobantes en formato documento)
                    const imageMsg = content.imageMessage;
                    const docMsg = content.documentMessage;
                    const docMime = (docMsg?.mimetype || '').toLowerCase();
                    const docFileName = (docMsg?.fileName || '').toLowerCase();
                    const isImageDoc = docMsg && (
                        docMime.startsWith('image/') ||
                        docFileName.endsWith('.jpg') ||
                        docFileName.endsWith('.jpeg') ||
                        docFileName.endsWith('.png') ||
                        docFileName.endsWith('.webp')
                    );

                    if (imageMsg || isImageDoc) {
                        messageType = 'image';
                        try {
                            console.log(`📥 [Media Baileys] Descargando imagen/comprobante en memoria para mensaje ${msgId}...`);
                            const buffer = await downloadMediaMessage(
                                m,
                                'buffer',
                                {},
                                { logger }
                            );

                            if (buffer && buffer.length > 0) {
                                const base64Data = buffer.toString('base64');
                                const effectiveMime = imageMsg?.mimetype || (docMime.startsWith('image/') ? docMime : 'image/jpeg');
                                const dataUrl = `data:${effectiveMime};base64,${base64Data}`;
                                conversationText = `[VOUCHER] ${dataUrl}`;

                                const caption = imageMsg?.caption || docMsg?.caption || '';
                                if (caption && caption.trim()) {
                                    conversationText += ` | ${caption.trim()}`;
                                }
                                console.log(`✅ [Media Baileys] Imagen procesada con éxito (${buffer.length} bytes)`);
                            } else {
                                conversationText = `[VOUCHER_FALLIDO] | El cliente adjuntó un comprobante pero no pudo descargarse automáticamente | wamid:${msgId}`;
                            }
                        } catch (errMedia) {
                            console.error(`❌ [Error Media Baileys] Fallo en downloadMediaMessage: ${errMedia.message}`);
                            conversationText = `[VOUCHER_FALLIDO] | El cliente adjuntó un comprobante pero no pudo descargarse automáticamente | wamid:${msgId}`;
                        }
                    } else if (content.audioMessage) {
                        messageType = 'audio';
                        conversationText = '[Audio]';
                    } else if (docMsg) {
                        messageType = 'document';
                        conversationText = '[Archivo]';
                        if (docMsg.fileName) {
                            conversationText += ` | ${docMsg.fileName}`;
                        }
                    }

                    // Si no hay texto ni multimedia reconocido, omitir
                    if (!conversationText || !conversationText.trim()) {
                        continue;
                    }

                    // ── 4. TIMESTAMP NUMÉRICO EN SEGUNDOS ──
                    const currentTimestampSeconds = Math.floor(Date.now() / 1000);

                    // Estructura idéntica esperada por Spring Boot Webhook
                    const payload = {
                        event: 'messages.upsert',
                        instance: 'LocalBridge',
                        date_time: new Date().toISOString(),
                        timestamp: currentTimestampSeconds,
                        data: {
                            key: {
                                remoteJid: finalJid,
                                fromMe: fromMe,
                                id: msgId
                            },
                            pushName: pushName,
                            lid: lid,
                            message: {
                                conversation: conversationText
                            },
                            messageType: messageType,
                            messageId: msgId,
                            messageTimestamp: currentTimestampSeconds
                        }
                    };

                    // Vista previa limpia en log truncando Base64
                    const logPreview = JSON.parse(JSON.stringify(payload));
                    if (logPreview.data?.message?.conversation?.startsWith('[VOUCHER] data:')) {
                        logPreview.data.message.conversation = logPreview.data.message.conversation.substring(0, 100) + '... [Base64 Truncado]';
                    }
                    console.log(`📤 [Baileys -> Spring Boot] Mensaje de ${finalJid} (LID: ${lid} | fromMe: ${fromMe}):`, JSON.stringify(logPreview, null, 2));

                    axios.post(BACKEND_URL, payload, {
                        headers: {
                            'Content-Type': 'application/json; charset=utf-8',
                            'Accept': 'application/json',
                            'Accept-Charset': 'utf-8',
                            'ngrok-skip-browser-warning': 'true'
                        },
                        timeout: 30000
                    })
                    .then(response => {
                        console.log(`🚀 [Spring Boot Webhook] Recibido con éxito (${response.status}):`, response.data);
                    })
                    .catch(error => {
                        console.error('❌ [Spring Boot Webhook Error]:', error.message);
                        if (error.response) {
                            console.error(`   HTTP ${error.response.status} - Data:`, error.response.data);
                        } else if (error.request) {
                            console.error(`   No se recibió respuesta de ${BACKEND_URL}`);
                        }
                    });

                } catch (errInner) {
                    console.error('❌ Error en procesamiento de mensaje individual:', errInner);
                }
            }
        });

    } catch (errInit) {
        console.error('❌ Error crítico al inicializar socket de Baileys:', errInit?.message || errInit);
        programarReconexion(5000);
    }
}

// Programador de reconexión con debounce
function programarReconexion(delayMs = 3000) {
    if (isReconnecting) return;
    isReconnecting = true;
    console.log(`🔄 Reconectando en ${Math.round(delayMs / 1000)}s...`);

    setTimeout(async () => {
        if (sock) {
            try {
                sock.ev.removeAllListeners();
                sock.end(undefined);
            } catch (e) {}
            sock = null;
        }
        inicializarCliente();
    }, delayMs);
}

// ── Servidor Express y Endpoints REST ───────────────────────────────────────
const app = express();
app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Función controladora común para /status y /api/whatsapp/estado
const obtenerEstadoConexion = (req, res) => {
    const isConn = currentStatus === 'CONNECTED';
    const finalPhone = (sock && sock.user && sock.user.id)
        ? sock.user.id.split(':')[0].split('@')[0]
        : (connectedPhoneNumber || '51969306604');

    return res.status(200).json({
        status: currentStatus,
        conectado: isConn,
        phoneNumber: isConn ? finalPhone : null,
        connectedAt: isConn ? (connectedAtTimestamp || new Date().toLocaleString('es-PE', { timeZone: 'America/Lima' })) : null,
        qrCodeBase64: qrCodeBase64,
        qr: qrCodeBase64
    });
};

// 1. Endpoint /status (Consultado por Spring Boot WhatsappServiceImpl)
app.get('/status', obtenerEstadoConexion);

// 2. Endpoint /api/whatsapp/estado (Consultado por Frontend Angular)
app.get('/api/whatsapp/estado', obtenerEstadoConexion);

// 3. Endpoint /reset (Cierra sesión, purga credenciales y genera nuevo QR en 1 segundo)
app.get('/reset', async (req, res) => {
    console.log('♻️ [Reset Manual] Purgando sesión y regenerando QR...');
    try {
        if (sock) {
            sock.ev.removeAllListeners();
            await sock.logout().catch(() => {});
            sock.end(undefined);
            sock = null;
        }
    } catch (e) {
        console.warn('⚠️ Excepción al cerrar sesión durante reset:', e.message);
    }

    limpiarCarpetaSesion();
    currentStatus = 'DISCONNECTED';
    qrCodeBase64 = null;
    connectedPhoneNumber = null;
    connectedAtTimestamp = null;

    setTimeout(() => {
        inicializarCliente();
    }, 1000);

    return res.status(200).json({
        status: 'RESETTING',
        message: 'Sesión eliminada con éxito. Generando nuevo código QR en breve...'
    });
});

// ── 2. CORRECCIÓN DEL ENDPOINT DE SALIDA /message/sendText/:instance ─────────
const enviarMensajeTexto = async (req, res) => {
    try {
        const { number, jid: rawJid, textMessage, text: directText } = req.body;
        const rawTarget = (number || rawJid || '').toString().trim();
        const text = textMessage?.text || directText || '';

        if (!rawTarget || !text) {
            console.warn('⚠️ [Envío Rechazado] Parámetros incompletos en /message/sendText:', req.body);
            return res.status(400).json({ error: 'Número/JID y texto son requeridos' });
        }

        // Determinar jidDestino compatible con Baileys
        let jidDestino = '';

        if (rawTarget.includes('@')) {
            const [localPart, domainPart] = rawTarget.split('@');
            const cleanDigits = localPart.split(':')[0].replace(/[^0-9]/g, '');

            if (domainPart === 'c.us') {
                // Si viene con @c.us: cambiar a @lid si tiene 15+ dígitos, sino a @s.whatsapp.net
                jidDestino = (cleanDigits.length >= 15) ? `${cleanDigits}@lid` : `${cleanDigits}@s.whatsapp.net`;
            } else if (domainPart === 'lid') {
                jidDestino = `${cleanDigits}@lid`;
            } else if (domainPart === 's.whatsapp.net') {
                jidDestino = (cleanDigits.length >= 15) ? `${cleanDigits}@lid` : `${cleanDigits}@s.whatsapp.net`;
            } else {
                jidDestino = (cleanDigits.length >= 15) ? `${cleanDigits}@lid` : `${cleanDigits}@s.whatsapp.net`;
            }
        } else {
            // Viene solo con dígitos numéricos
            const cleanDigits = rawTarget.replace(/[^0-9]/g, '');
            if (cleanDigits.length >= 15) {
                // Es un LID
                jidDestino = `${cleanDigits}@lid`;
            } else {
                // Es un número telefónico estándar (ej. 51933526011)
                jidDestino = `${cleanDigits}@s.whatsapp.net`;
            }
        }

        console.log(`📤 [Envío Solicitado] Destino Original: "${rawTarget}" -> JID Destino: "${jidDestino}" | Mensaje: "${text}"`);

        if (!sock || currentStatus !== 'CONNECTED') {
            console.error(`❌ [Error de Envío] El socket de WhatsApp no está conectado (Estado actual: ${currentStatus})`);
            return res.status(503).json({ error: 'El cliente de WhatsApp Web no está inicializado o conectado.' });
        }

        // Envío efectivo vía Baileys
        const sentMsg = await sock.sendMessage(jidDestino, { text });
        const msgId = sentMsg?.key?.id || `baileys-${Date.now()}`;
        console.log(`✅ [Envío Exitoso] Mensaje entregado a WhatsApp | Destino: ${jidDestino} | ID: ${msgId}`);

        return res.status(200).json({
            key: {
                id: msgId,
                remoteJid: jidDestino,
                fromMe: true
            },
            message: {
                conversation: text
            },
            status: 'SUCCESS'
        });

    } catch (err) {
        console.error('❌ [Error Detallado en /message/sendText]:', err);
        return res.status(500).json({
            error: err.message || 'Error desconocido al enviar mensaje',
            details: String(err)
        });
    }
};

app.post('/message/sendText/:instance', enviarMensajeTexto);
app.post('/message/sendText', enviarMensajeTexto);

// Diagnóstico de salud y métricas del puente
app.get('/health', (req, res) => {
    return res.status(200).json({
        engine: 'Baileys',
        status: currentStatus,
        connected: currentStatus === 'CONNECTED',
        phoneNumber: connectedPhoneNumber,
        uptime: Math.round(process.uptime()),
        memoryUsage: process.memoryUsage(),
        lidsCached: lidToPhoneMap.size
    });
});

// Iniciar servidor HTTP
app.listen(PORT, () => {
    console.log('=====================================================');
    console.log(`🌐 Servidor del puente Baileys escuchando en puerto ${PORT}`);
    console.log(`📡 URL destino para Webhooks Spring Boot: ${BACKEND_URL}`);
    console.log('=====================================================');
});

// Manejo de excepciones globales del proceso
process.on('unhandledRejection', (reason) => {
    console.warn('⚠️ [Advertencia Global] Rejection no manejada:', reason?.message || reason);
});

process.on('uncaughtException', (err) => {
    console.warn('⚠️ [Advertencia Global] Excepción no capturada:', err?.message || err);
});

// Iniciar cliente Baileys al arrancar
inicializarCliente();