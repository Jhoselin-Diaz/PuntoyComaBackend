package com.upc.Service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@Service
public class OcrServiceImpl implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrServiceImpl.class);

    @Value("${tesseract.datapath}")
    private String datapath;

    @Override
    public String extraerTextoDeImagen(File imagen) {
        log.info("[OCR Service] Iniciando procesamiento de imagen: {}", imagen.getAbsolutePath());
        try {
            // 1. Leer imagen original
            BufferedImage imgOriginal = ImageIO.read(imagen);
            if (imgOriginal == null) {
                log.warn("[OCR Service] No se pudo leer la imagen (ImageIO devolvió null).");
                return "";
            }

            // 2. Preprocesamiento para optimizar OCR
            BufferedImage imgProcesada = preprocesarImagen(imgOriginal);

            // 3. Configurar Tesseract
            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath(datapath);
            tesseract.setLanguage("spa"); // idioma español estrictamente

            // 4. Ejecutar OCR
            log.info("[OCR Service] Ejecutando OCR en imagen preprocesada...");
            String textoExtraido = tesseract.doOCR(imgProcesada);
            log.info("[OCR Service] OCR completado con éxito.");
            return textoExtraido;

        } catch (IOException e) {
            log.error("[OCR Service] Error de E/S leyendo la imagen: {}", e.getMessage(), e);
            throw new RuntimeException("Error de lectura de imagen para OCR", e);
        } catch (TesseractException e) {
            log.error("[OCR Service] Error de Tesseract al procesar la imagen: {}", e.getMessage(), e);
            throw new RuntimeException("Error de Tesseract OCR", e);
        }
    }

    private BufferedImage preprocesarImagen(BufferedImage imgOriginal) {
        if (imgOriginal == null) return null;

        // Escalar la imagen a 2x para aumentar la resolución (DPI virtual)
        int targetWidth = imgOriginal.getWidth() * 2;
        int targetHeight = imgOriginal.getHeight() * 2;

        // Usar escalado bicúbico de alta calidad en escala de grises (TYPE_BYTE_GRAY)
        BufferedImage gray = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_GRAY);
        java.awt.Graphics2D g2d = gray.createGraphics();
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(imgOriginal, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        // Aplicar binarización por umbral (blanco y negro puro) para limpiar fondos
        BufferedImage binarized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int rgb = gray.getRGB(x, y);
                int grayValue = rgb & 0xFF;
                // Umbral simple de 140 (los vouchers suelen tener fondos muy claros o muy oscuros en las letras)
                int binarizedValue = (grayValue < 140) ? 0 : 255;
                int newRgb = (binarizedValue << 16) | (binarizedValue << 8) | binarizedValue;
                binarized.setRGB(x, y, newRgb);
            }
        }

        return binarized;
    }
}
