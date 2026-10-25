# Imagen base ultraligera de Node.js Alpine (sin Chromium / Puppeteer)
FROM node:20-alpine

WORKDIR /app

# Instalar dependencias de producción
COPY package*.json ./
RUN npm ci --only=production

# Copiar el código fuente
COPY . .

# Exponer el puerto del puente (3001)
EXPOSE 3001

# Definir volumen persistente para la sesión de Baileys
VOLUME ["/app/baileys_auth"]

# Iniciar el puente de WhatsApp con Baileys
CMD ["npm", "start"]
