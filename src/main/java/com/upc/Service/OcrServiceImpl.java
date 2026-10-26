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

        } catch (Throwable t) {
            log.warn("[OCR Service] Tesseract no disponible o falló en el entorno nativo ({}). Se retornará vacío para delegar a OpenAI Vision.", t.getMessage());
            return "";
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

        // Calcular histograma para Otsu
        int[] histogram = new int[256];
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int rgb = gray.getRGB(x, y);
                int grayValue = rgb & 0xFF;
                histogram[grayValue]++;
            }
        }

        // Algoritmo de Otsu para encontrar el umbral óptimo
        int totalPixels = targetWidth * targetHeight;
        float sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }

        float sumB = 0;
        int wB = 0;
        int wF = 0;

        float varMax = 0;
        int threshold = 128; // fallback default

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            wF = totalPixels - wB;
            if (wF == 0) break;

            sumB += (float) (t * histogram[t]);
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;

            float varBetween = (float) wB * (float) wF * (mB - mF) * (mB - mF);

            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = t;
            }
        }

        log.info("[OCR Preprocessing] Otsu threshold calculado: {}", threshold);

        // Binarizar la imagen con el umbral calculado
        // Además, detectamos si el fondo es oscuro y las letras claras (como blanco sobre fondo morado)
        // Para esto, contamos cuántos píxeles están sobre y bajo el umbral.
        int pixelsAbove = 0;
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int rgb = gray.getRGB(x, y);
                int grayValue = rgb & 0xFF;
                if (grayValue > threshold) {
                    pixelsAbove++;
                }
            }
        }

        boolean invert = pixelsAbove < (totalPixels / 2); // invertimos si la mayoría de los píxeles son oscuros (fondo morado)
        log.info("[OCR Preprocessing] Invertir colores detectado: {}", invert);

        BufferedImage binarized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int rgb = gray.getRGB(x, y);
                int grayValue = rgb & 0xFF;

                boolean isAbove = grayValue >= threshold;
                int binarizedValue;
                if (invert) {
                    binarizedValue = isAbove ? 0 : 255; // invertido
                } else {
                    binarizedValue = isAbove ? 255 : 0; // normal
                }

                int newRgb = (binarizedValue << 16) | (binarizedValue << 8) | binarizedValue;
                binarized.setRGB(x, y, newRgb);
            }
        }

        return binarized;
    }
}
