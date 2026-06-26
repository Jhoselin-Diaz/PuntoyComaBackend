-- Script SQL para las tablas de Contacto en Supabase/PostgreSQL

CREATE TABLE IF NOT EXISTS contacto_bloques (
    id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    icon VARCHAR(50) NOT NULL,
    btn_text VARCHAR(255),
    btn_link TEXT,
    visible BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS contacto_cierre (
    id SERIAL PRIMARY KEY,
    btn_text VARCHAR(255) NOT NULL,
    number VARCHAR(50),
    message TEXT,
    visible BOOLEAN DEFAULT TRUE
);

-- Insertar datos por defecto (seed)
INSERT INTO contacto_bloques (id, title, description, icon, btn_text, btn_link, visible) VALUES
('block-wa', 'WhatsApp', 'Escríbenos y te respondemos lo antes posible.', 'whatsapp', 'Escríbenos por WhatsApp', 'https://wa.me/51999999999', TRUE),
('block-ig', 'Instagram', 'Síguenos y descubre nuestras novedades.', 'instagram', '@Brandname', 'https://instagram.com/', TRUE),
('block-support', 'Atención al Cliente', 'Estamos para ayudarte en lo que necesites.', 'support', 'Lun - Sáb: 9:00 am - 6:00 pm', '', TRUE),
('block-email', 'Email', 'Escríbenos y te responderemos lo antes posible.', 'email', 'hola@brandname.com', 'mailto:hola@brandname.com', TRUE),
('block-info', 'Información', 'Resolvemos tus dudas sobre productos, pedidos, envíos y más.', 'info', '', '', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO contacto_cierre (id, btn_text, number, message, visible) VALUES
(1, '¿Dudas sobre tu pedido?', '51999999999', 'Hola, tengo una consulta sobre un pedido.', TRUE)
ON CONFLICT (id) DO NOTHING;
