# Imagen base liviana de Node.js
FROM node:20-slim

# Instalar Chromium y dependencias del sistema necesarias para ejecutar Puppeteer headless en Linux
RUN apt-get update && apt-get install -y \
    chromium \
    fonts-ipafont-gothic \
    fonts-wqy-zenhei \
    fonts-thai-tlwg \
    fonts-kacst \
    fonts-freefont-ttf \
    libxss1 \
    --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

# Configurar Puppeteer para omitir la descarga del binario de Chromium embebido y usar el del sistema
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium

WORKDIR /app

# Instalar dependencias de producción
COPY package*.json ./
RUN npm ci --only=production

# Copiar el código fuente
COPY . .

# Exponer el puerto del puente (3001)
EXPOSE 3001

# Definir volumen persistente para la sesión para que no se pierda al reiniciar
VOLUME ["/app/.wwebjs_auth"]

# Iniciar el puente de WhatsApp Web
CMD ["npm", "start"]
