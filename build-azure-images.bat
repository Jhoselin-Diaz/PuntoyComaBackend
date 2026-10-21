@echo off
:: Script de Automatización de Compilación de Imágenes Docker para Azure

echo =================================================================
echo  COMPILANDO IMAGENES DOCKER PARA DESPLIEGUE EN AZURE CONTAINER REGISTRY
echo =================================================================
echo.

set ACR_NAME=miacrpuntoycoma
set /p ACR_NAME="Introduce el nombre de tu Azure Container Registry (ACR) [Default: miacrpuntoycoma]: "

echo.
echo [1/3] Compilando imagen del Backend (Spring Boot + Tesseract OCR)...
docker build -t %ACR_NAME%.azurecr.io/puntoycomabackend:latest -f Dockerfile .

echo.
echo [2/3] Compilando imagen del Puente de WhatsApp (Node.js + Puppeteer + Chromium)...
cd puente-whatsapp
docker build -t %ACR_NAME%.azurecr.io/whatsapp-bridge:latest -f Dockerfile .
cd ..

echo.
echo [3/3] Resumen de comandos para subir a Azure (Azure Container Registry):
echo.
echo  Paso 1: Autenticarse en Azure:
echo     az acr login --name %ACR_NAME%
echo.
echo  Paso 2: Subir imagenes compiladas:
echo     docker push %ACR_NAME%.azurecr.io/puntoycomabackend:latest
echo     docker push %ACR_NAME%.azurecr.io/whatsapp-bridge:latest
echo.
echo =================================================================
echo  Proceso de compilación local finalizado.
echo =================================================================
pause
