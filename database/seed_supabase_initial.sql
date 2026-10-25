-- ====================================================================
-- SCRIPT DE INICIALIZACIÓN DE DATOS (SEED) - SUPABASE (PuntoyComa2)
-- Base de Datos: postgres
-- Generado por: Antigravity AI / Arquitectura Punto y Coma
-- ====================================================================

-- 1. ADMINISTRADORES (admins) - Panel de Control Netlify
-- Passwords por defecto: 'admin123' (Hash BCrypt compatible con Spring Security)
INSERT INTO admins (nombre_completo, correo, password, rol, creado_en)
VALUES 
    ('Administrador', 'admin@puntoycoma.com', '$2a$10$KNlwjcD0YVHrWIqP0KwUKeURCngoceDIb77lemB2gzBndS95w.67S', 'ADMIN', NOW()),
    ('Jhoselin Díaz', 'jhoselin@puntoycoma.com', '$2a$10$KNlwjcD0YVHrWIqP0KwUKeURCngoceDIb77lemB2gzBndS95w.67S', 'ADMIN', NOW()),
    ('Jhoselin Díaz', 'xvantx2@gmail.com', '$2a$10$KNlwjcD0YVHrWIqP0KwUKeURCngoceDIb77lemB2gzBndS95w.67S', 'ADMIN', NOW())
ON CONFLICT (correo) DO UPDATE 
SET nombre_completo = EXCLUDED.nombre_completo,
    password = EXCLUDED.password;

-- 2. USUARIOS DE SISTEMA (usuarios) - Auditoría y Chats
INSERT INTO usuarios (nombre, email, password, rol, activo, habilitado, intentos_fallidos, cuenta_bloqueada)
VALUES 
    ('Admin Principal', 'admin@puntoycoma.com', '$2a$10$KNlwjcD0YVHrWIqP0KwUKeURCngoceDIb77lemB2gzBndS95w.67S', 'ADMIN', TRUE, TRUE, 0, FALSE),
    ('Jhoselin Díaz', 'jhoselin@puntoycoma.com', '$2a$10$KNlwjcD0YVHrWIqP0KwUKeURCngoceDIb77lemB2gzBndS95w.67S', 'ADMIN', TRUE, TRUE, 0, FALSE)
ON CONFLICT (email) DO NOTHING;

-- 3. CATÁLOGO INICIAL DE PRODUCTOS (productos)
INSERT INTO productos (
    nombre, descripcion, precio, stock, categoria, codigo_sku,
    image_url, imagen_principal, capacidad, material,
    activo, visible, destacado, nuevo, vistas_contador,
    apto_microondas, apto_lavavajillas, grado_alimentario
)
SELECT 
    'Taza Nube',
    'Taza aesthetic en forma de nube. Elaborada con cerámica de alta calidad y acabado suave al tacto. Ideal para café o té con estilo.',
    38.00, 25, 'Tazas', 'PC-001',
    'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?q=80&w=800&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?q=80&w=800&auto=format&fit=crop',
    '350 ml', 'Cerámica de alta temperatura',
    TRUE, TRUE, TRUE, TRUE, 0,
    TRUE, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM productos WHERE nombre = 'Taza Nube');

INSERT INTO productos (
    nombre, descripcion, precio, stock, categoria, codigo_sku,
    image_url, imagen_principal, capacidad, material,
    activo, visible, destacado, nuevo, vistas_contador,
    apto_temperaturas, grado_alimentario
)
SELECT 
    'Vaso con Sorbete',
    'Vaso aesthetic con tapa de bambú natural y sorbete de vidrio reutilizable. Ideal para café frío, té helado, matcha o jugos frescos.',
    38.00, 20, 'Vasos', 'PC-002',
    'https://images.unsplash.com/photo-1577937927133-66ef06acdf18?q=80&w=800&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1577937927133-66ef06acdf18?q=80&w=800&auto=format&fit=crop',
    '450 ml', 'Vidrio borosilicato y bambú',
    TRUE, TRUE, TRUE, FALSE, 0,
    TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM productos WHERE nombre = 'Vaso con Sorbete');

INSERT INTO productos (
    nombre, descripcion, precio, stock, categoria, codigo_sku,
    image_url, imagen_principal, capacidad, material,
    activo, visible, destacado, nuevo, vistas_contador,
    apto_microondas, grado_alimentario
)
SELECT 
    'Taza Artesanal de Arcilla',
    'Taza moldeada a mano por artesanos peruanos. Acabado rústico contemporáneo con textura terrosa y esmaltado interior brillante.',
    42.00, 15, 'Tazas', 'PC-003',
    'https://images.unsplash.com/photo-1517256064527-09c73fc73e38?q=80&w=800&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1517256064527-09c73fc73e38?q=80&w=800&auto=format&fit=crop',
    '300 ml', 'Arcilla natural horneada',
    TRUE, TRUE, TRUE, TRUE, 0,
    TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM productos WHERE nombre = 'Taza Artesanal de Arcilla');

INSERT INTO productos (
    nombre, descripcion, precio, stock, categoria, codigo_sku,
    image_url, imagen_principal, capacidad, material,
    activo, visible, destacado, nuevo, vistas_contador,
    apto_microondas, apto_lavavajillas, grado_alimentario
)
SELECT 
    'Taza Rosa Aesthetic',
    'Taza de cerámica minimalista en tonalidad rosa pastel mate con asa ergonómica oversize para un agarre cómodo y fotogénico.',
    35.00, 18, 'Tazas', 'PC-004',
    'https://images.unsplash.com/photo-1536939459926-301728717817?q=80&w=800&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1536939459926-301728717817?q=80&w=800&auto=format&fit=crop',
    '320 ml', 'Cerámica esmaltada',
    TRUE, TRUE, FALSE, FALSE, 0,
    TRUE, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM productos WHERE nombre = 'Taza Rosa Aesthetic');

INSERT INTO productos (
    nombre, descripcion, precio, stock, categoria, codigo_sku,
    image_url, imagen_principal, capacidad, material,
    activo, visible, destacado, nuevo, vistas_contador,
    apto_temperaturas, grado_alimentario
)
SELECT 
    'Vaso Térmico Minimalista',
    'Vaso térmico doble capa con aislamiento al vacío. Mantiene tus bebidas frías por 12 horas o calientes por 6 horas.',
    45.00, 12, 'Vasos', 'PC-005',
    'https://images.unsplash.com/photo-1570784332176-fdd73da66f03?q=80&w=800&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1570784332176-fdd73da66f03?q=80&w=800&auto=format&fit=crop',
    '500 ml', 'Acero inoxidable 304',
    TRUE, TRUE, TRUE, TRUE, 0,
    TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM productos WHERE nombre = 'Vaso Térmico Minimalista');

-- 4. BANNER PRINCIPAL HOME (home_banners)
INSERT INTO home_banners (titulo, subtitulo, texto_boton, link_boton, imagen_url, visible, actualizado_en)
SELECT 
    'Elegantes Tazas & Vasos Aesthetic', 
    'Dale un toque premium a tus mañanas con nuestra colección exclusiva elaborada por artesanos.', 
    'Explorar Colección', 
    '/productos', 
    'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?q=80&w=1920&auto=format&fit=crop', 
    TRUE,
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM home_banners);

-- 5. BLOQUES DE CONTACTO (contacto_bloques y contacto_cierre)
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
