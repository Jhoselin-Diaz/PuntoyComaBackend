# Etapa 1: Compilación del JAR
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Entorno de ejecución de producción
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Instalar dependencias nativas para Tesseract OCR y el idioma español
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-spa \
    && rm -rf /var/lib/apt/lists/*

# Copiar el JAR desde la etapa de compilación
COPY --from=build /app/target/*.jar app.jar

# Exponer el puerto del backend (8080 por defecto)
EXPOSE 8080

# Configurar la variable de entorno para que Tess4J apunte al tessdata de Linux
ENV TESSERACT_DATAPATH=/usr/share/tesseract-ocr/5/tessdata

# Ejecutar el backend
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
