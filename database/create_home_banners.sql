-- Script SQL para la tabla `home_banners` en Supabase/PostgreSQL

CREATE TABLE IF NOT EXISTS home_banners (
    id SERIAL PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    subtitulo TEXT,
    texto_boton VARCHAR(100),
    link_boton VARCHAR(255),
    imagen_url TEXT,
    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    visible BOOLEAN DEFAULT TRUE
);

-- Insertar un banner por defecto (seed) si la tabla está vacía
INSERT INTO home_banners (titulo, subtitulo, texto_boton, link_boton, imagen_url, visible)
SELECT 
    'Elegantes Tazas & Vasos Aesthetic', 
    'Dale un toque premium a tus mañanas con nuestra colección exclusiva elaborada por artesanos.', 
    'Explorar Colección', 
    '/productos', 
    'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?q=80&w=1920&auto=format&fit=crop', 
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM home_banners);
