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

// ── Configuración de logger silencioso/warnings para Baileys ────────────────
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

        sock = makeWASocket({
            version,
            auth: state,
            logger,
            printQRInTerminal: false, // Manejado manualmente con qrcode-terminal y qrcode (Base64)
            browser: ['PuntoyComa Bridge', 'Chrome', '1.0.0'],
            generateHighQualityLinkPreview: false,
            syncFullHistory: false
        });

        // Persistir cambios en las credenciales automáticamente
        sock.ev.on('creds.update', saveCreds);

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

                    // Normalizar el JID a @c.us para mantener 100% de compatibilidad con Spring Boot y BD
                    let cleanNumber = remoteJid.split('@')[0];
                    if (cleanNumber.includes(':')) {
                        cleanNumber = cleanNumber.split(':')[0];
                    }
                    cleanNumber = cleanNumber.replace(/[^0-9]/g, '');
                    const finalJid = `${cleanNumber}@c.us`;

                    const fromMe = Boolean(m.key?.fromMe);
                    const pushName = m.pushName || (fromMe ? 'Administrador' : 'Cliente WhatsApp');
                    const lid = remoteJid.split('@')[0];

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

                    // Estructura idéntica esperada por Spring Boot Webhook
                    const payload = {
                        event: 'messages.upsert',
                        instance: 'LocalBridge',
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
                            messageId: msgId
                        }
                    };

                    // Vista previa limpia en log truncando Base64
                    const logPreview = JSON.parse(JSON.stringify(payload));
                    if (logPreview.data?.message?.conversation?.startsWith('[VOUCHER] data:')) {
                        logPreview.data.message.conversation = logPreview.data.message.conversation.substring(0, 100) + '... [Base64 Truncado]';
                    }
                    console.log(`📤 [Baileys -> Spring Boot] Enviando mensaje de ${finalJid} (fromMe: ${fromMe}):`, JSON.stringify(logPreview, null, 2));

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

// 4. Endpoint /message/sendText/:instance y /message/sendText (Envío de mensajes salientes desde Spring Boot)
const enviarMensajeTexto = async (req, res) => {
    try {
        const { number, textMessage, text: directText } = req.body;
        const text = textMessage?.text || directText || '';

        if (!number || !text) {
            return res.status(400).json({ error: 'Número y texto son requeridos' });
        }

        // Formatear JID para Baileys: {numero}@s.whatsapp.net
        let jid = String(number).trim();
        if (jid.endsWith('@s.whatsapp.net')) {
            // Ya formateado
        } else if (jid.endsWith('@c.us')) {
            jid = jid.replace('@c.us', '@s.whatsapp.net');
        } else {
            const cleanDigits = jid.split('@')[0].replace(/[^0-9]/g, '');
            jid = `${cleanDigits}@s.whatsapp.net`;
        }

        console.log(`📤 [Envío Solicitado] Destinatario: ${jid} | Mensaje: "${text}"`);

        if (!sock || currentStatus !== 'CONNECTED') {
            return res.status(503).json({ error: 'El cliente de WhatsApp Web no está inicializado o conectado.' });
        }

        const sentMsg = await sock.sendMessage(jid, { text });
        const msgId = sentMsg?.key?.id || `baileys-${Date.now()}`;

        return res.status(200).json({
            key: {
                id: msgId,
                remoteJid: jid,
                fromMe: true
            },
            message: {
                conversation: text
            },
            status: 'SUCCESS'
        });

    } catch (err) {
        console.error('❌ Error enviando mensaje por WhatsApp Baileys:', err.message);
        return res.status(500).json({ error: err.message });
    }
};

app.post('/message/sendText/:instance', enviarMensajeTexto);
app.post('/message/sendText', enviarMensajeTexto);

// 5. Diagnóstico de salud y métricas del puente
app.get('/health', (req, res) => {
    return res.status(200).json({
        engine: 'Baileys',
        status: currentStatus,
        connected: currentStatus === 'CONNECTED',
        phoneNumber: connectedPhoneNumber,
        uptime: Math.round(process.uptime()),
        memoryUsage: process.memoryUsage()
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