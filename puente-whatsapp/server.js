const { Client, LocalAuth } = require('whatsapp-web.js');
const qrcode = require('qrcode-terminal');
const axios = require('axios');
const express = require('express');

const BACKEND_URL = 'http://localhost:8080/webhook';

// Inicializar Express para recibir comandos de envío de Spring Boot
const app = express();
app.use(express.json());

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

const PORT = 3001;
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

let client = null;
let isReconnecting = false;

function inicializarCliente() {
    isReconnecting = false;
    console.log('=====================================================');
    console.log('🤖 INICIALIZANDO CLIENTE WHATSAPP WEB...');
    console.log('=====================================================');

    client = new Client({
        authStrategy: new LocalAuth(),
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
        console.log(`⏳ [Cargando] ${percent}% - ${message}`);
    });

    client.on('qr', (qr) => {
        console.log('=====================================================');
        console.log('👉 NUEVO CÓDIGO QR GENERADO. Escanéalo con tu celular:');
        qrcode.generate(qr, { small: true });
        console.log('=====================================================');
    });

    client.on('authenticated', () => {
        console.log('🔑 [Autenticación] Sesión autenticada correctamente.');
    });

    client.on('ready', () => {
        console.log('=====================================================');
        console.log('✅ ¡WhatsApp Web listo y conectado con éxito!');
        console.log(`📡 Enviando webhooks a: ${BACKEND_URL}`);
        console.log('=====================================================');
    });

    client.on('change_state', (state) => {
        console.log(`🔄 [Estado] Cambio de estado de conexión: ${state}`);
        if (state === 'DISCONNECTED' || state === 'UNPAIRED') {
            console.warn('⚠️ Conexión perdida con el dispositivo remoto');
        }
    });

    // Capturar todos los mensajes creados (tanto entrantes como salientes)
    client.on('message_create', async (msg) => {
        try {
            // LOG ABSOLUTO E INMEDIATO: Captura todos los eventos creados antes de filtrar
            console.log(`📥 [Mensaje Creado/Detectado] ID: ${msg.id?.id || 'null'} | from: ${msg.from} | to: ${msg.to} | De mí: ${msg.fromMe} | Cuerpo: "${msg.body || '(sin texto)'}"`);

            // Identificar el socio del chat (el número del cliente en chats individuales)
            const remoteJid = msg.fromMe ? msg.to : msg.from;

            // Filtrar y omitir chats grupales o estados (permitir @c.us y @lid)
            if (!remoteJid || (!remoteJid.endsWith('@c.us') && !remoteJid.endsWith('@lid'))) {
                console.log(`ℹ️ [Filtro] Mensaje ignorado por no pertenecer a un chat individual (@c.us o @lid). JID: ${remoteJid}`);
                return;
            }

            // Traducir el JID si es de tipo @lid para enviarlo como número telefónico real (@c.us)
            let finalJid = remoteJid;
            if (remoteJid.endsWith('@lid')) {
                try {
                    // Método 1: getContactLidAndPhone (directo de la librería)
                    if (typeof client.getContactLidAndPhone === 'function') {
                        const resolvedArray = await client.getContactLidAndPhone([remoteJid]).catch(() => null);
                        if (resolvedArray && resolvedArray.length > 0 && resolvedArray[0].pn) {
                            finalJid = resolvedArray[0].pn;
                            console.log(`✨ [Resolución LID] Traducido ${remoteJid} -> ${finalJid} (Método 1)`);
                        }
                    }
                    // Método 2: getContactById (fallback usando contacto)
                    if (finalJid.endsWith('@lid')) {
                        const contact = await client.getContactById(remoteJid).catch(() => null);
                        if (contact && contact.number) {
                            finalJid = contact.number + '@c.us';
                            console.log(`✨ [Resolución LID] Traducido ${remoteJid} -> ${finalJid} (Método 2)`);
                        }
                    }
                } catch (errResolve) {
                    console.warn('⚠️ No se pudo resolver el LID a número telefónico:', errResolve.message);
                }
            }

            // LOG CON EL JID FINAL RESOLVIDO: Esto se ejecuta antes de cualquier operación asíncrona que pueda colgarse
            console.log(`📥 [Mensaje Detectado] ID: ${msg.id.id} | De mí: ${msg.fromMe} | JID Destino: ${finalJid}`);

            // Obtener el nombre del perfil de manera no bloqueante.
            // A veces msg.getContact() se cuelga indefinidamente si Puppeteer tiene demoras de red.
            let pushName = 'Cliente WhatsApp';
            try {
                if (msg._data && msg._data.notifyName) {
                    pushName = msg._data.notifyName;
                } else {
                    // Intentamos con timeout de 2 segundos para no colgar el flujo
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

            if (msg.hasMedia) {
                try {
                    const media = await msg.downloadMedia();
                    if (media && media.data) {
                        const dataUrl = `data:${media.mimetype};base64,${media.data}`;
                        
                        if (media.mimetype.startsWith('image/')) {
                            // Opción B para Vouchers: [VOUCHER] base64_image_url | caption
                            conversationText = `[VOUCHER] ${dataUrl}`;
                            if (msg.body && msg.body.trim()) {
                                conversationText += ` | ${msg.body}`;
                            }
                        } else if (media.mimetype.startsWith('audio/')) {
                            conversationText = '[Audio]';
                        } else {
                            conversationText = '[Archivo]';
                            if (media.filename) {
                                conversationText += ` | ${media.filename}`;
                            }
                        }
                    } else {
                        conversationText = '[Archivo]';
                    }
                } catch (err) {
                    console.error('⚠️ Error descargando multimedia:', err.message);
                    conversationText = '[Archivo]';
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
                    messageType: 'conversation'
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
        console.error('❌ Fallo de autenticación en WhatsApp:', msg);
        rebotarConexion();
    });

    // Control de desconexión
    client.on('disconnected', (reason) => {
        console.log('🔌 Cliente desconectado. Razón:', reason);
        console.warn('⚠️ Conexión perdida con el dispositivo remoto');
        rebotarConexion();
    });

    client.initialize().catch(err => {
        console.error('❌ Error al inicializar cliente:', err.message);
        rebotarConexion();
    });
}

function rebotarConexion() {
    if (isReconnecting) return;
    isReconnecting = true;
    console.log('🔄 Intentando restablecer la conexión en 10 segundos...');

    try {
        if (client) {
            client.removeAllListeners();
            client.destroy().catch(() => {});
        }
    } catch (e) {
        console.warn('⚠️ No se pudo destruir el cliente de forma limpia:', e.message);
    }

    setTimeout(() => {
        inicializarCliente();
    }, 10000);
}

// Arrancar por primera vez
inicializarCliente();
