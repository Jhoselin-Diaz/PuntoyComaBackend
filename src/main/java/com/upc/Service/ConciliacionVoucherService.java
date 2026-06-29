package com.upc.Service;

import com.upc.Entity.Pedido;
import com.upc.Repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConciliacionVoucherService {

    private static final Logger log = LoggerFactory.getLogger(ConciliacionVoucherService.class);

    @Autowired
    private OcrService ocrService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key:}")
    private String supabaseKey;

    public static class DatosVoucher {
        public Double monto;
        public String referencia;
        public LocalDateTime fechaPago;
        public String metodoPago;

        @Override
        public String toString() {
            return "DatosVoucher{monto=" + monto + ", referencia='" + referencia + "', fechaPago=" + fechaPago + ", metodoPago='" + metodoPago + "'}";
        }
    }

    @Async
    @Transactional
    public void procesarYConciliarVoucher(String telefono, String contenidoVoucher) {
        log.info("[ConciliacionVoucherService] Iniciando conciliación asíncrona de voucher para teléfono: {}", telefono);

        // 1. Buscar el pedido PENDIENTE más reciente
        Pedido pedido = pedidoRepository.findTopByUsuarioTelefonoAndEstadoOrderByFechaDesc(telefono, "PENDIENTE");
        if (pedido == null) {
            log.warn("[ConciliacionVoucherService] No se encontró ningún pedido PENDIENTE para el cliente: {}. Se aborta el OCR.", telefono);
            return;
        }

        // 2. Extraer la imagen (ya sea base64 o URL) y guardarla como archivo temporal
        File tempFile = null;
        String voucherUrl = null;
        try {
            if (contenidoVoucher.startsWith("[VOUCHER] ")) {
                contenidoVoucher = contenidoVoucher.substring("[VOUCHER] ".length()).trim();
            }

            byte[] imageBytes = null;
            String contentType = "image/jpeg";
            String extension = ".jpg";

            if (contenidoVoucher.startsWith("data:")) {
                // Es un Base64 Data URL
                int commaIndex = contenidoVoucher.indexOf(",");
                if (commaIndex != -1) {
                    String metadata = contenidoVoucher.substring(0, commaIndex);
                    String base64Content = contenidoVoucher.substring(commaIndex + 1);
                    imageBytes = Base64.getDecoder().decode(base64Content.trim());

                    if (metadata.contains("image/png")) {
                        contentType = "image/png";
                        extension = ".png";
                    } else if (metadata.contains("image/webp")) {
                        contentType = "image/webp";
                        extension = ".webp";
                    }
                }
            } else if (contenidoVoucher.startsWith("http")) {
                // Es una URL externa, descargarla
                log.info("[ConciliacionVoucherService] Descargando imagen desde URL: {}", contenidoVoucher);
                java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest httpReq = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(contenidoVoucher))
                        .GET()
                        .build();
                java.net.http.HttpResponse<byte[]> httpRes = httpClient.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                if (httpRes.statusCode() == 200) {
                    imageBytes = httpRes.body();
                    var headers = httpRes.headers();
                    var contentTypeOpt = headers.firstValue("Content-Type");
                    if (contentTypeOpt.isPresent()) {
                        contentType = contentTypeOpt.get();
                        if (contentType.contains("png")) extension = ".png";
                        else if (contentType.contains("webp")) extension = ".webp";
                    }
                } else {
                    log.error("[ConciliacionVoucherService] Error descargando imagen. Código HTTP: {}", httpRes.statusCode());
                }
            }

            if (imageBytes == null) {
                log.error("[ConciliacionVoucherService] No se pudieron obtener los bytes de la imagen del voucher.");
                return;
            }

            // Guardar en archivo temporal
            tempFile = File.createTempFile("voucher_", extension);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(imageBytes);
            }

            // 3. Ejecutar OCR
            String textoExtraido = ocrService.extraerTextoDeImagen(tempFile);
            if (textoExtraido == null || textoExtraido.trim().isEmpty()) {
                log.warn("[ConciliacionVoucherService] OCR no extrajo texto de la imagen.");
                return;
            }

            // 4. Procesar el texto con expresiones regulares
            DatosVoucher datos = procesarTexto(textoExtraido);

            // 5. Subir a Supabase Storage para tener una URL permanente y accesible
            voucherUrl = subirVoucherASupabase(imageBytes, contentType, extension);
            if (voucherUrl == null) {
                // Fallback a guardar la URL original o base64 truncada si falla la subida
                voucherUrl = contenidoVoucher.length() > 500 ? contenidoVoucher.substring(0, 500) : contenidoVoucher;
            }

            // 6. Actualizar el Pedido
            double total = pedido.getTotal() != null ? pedido.getTotal() : 0.0;
            double detectado = datos.monto != null ? datos.monto : 0.0;
            double diferencia = total - detectado;

            pedido.setMontoDetectado(detectado);
            pedido.setMontoOcr(detectado); // Sincronizar ambas variantes de variables
            pedido.setDiferencia(diferencia);
            pedido.setReferenciaPago(datos.referencia != null ? datos.referencia : "OCR-" + System.currentTimeMillis());
            pedido.setMetodoPago(datos.metodoPago);
            pedido.setFechaPago(datos.fechaPago);
            pedido.setVoucherUrl(voucherUrl);
            pedido.setBancoEmisor(datos.metodoPago.startsWith("Transferencia") ? datos.metodoPago.replace("Transferencia ", "") : datos.metodoPago);

            if (Math.abs(diferencia) < 0.01) {
                pedido.setEstado("VALIDADO");
                pedido.setResultadoConciliacion("Pago verificado automáticamente. El monto coincide.");
            } else {
                pedido.setEstado("RECHAZADO");
                pedido.setResultadoConciliacion("El monto detectado (S/ " + String.format("%.2f", detectado) + ") no coincide con el total (S/ " + String.format("%.2f", total) + ").");
            }

            pedidoRepository.save(pedido);
            log.info("[ConciliacionVoucherService] Pedido ID {} conciliado exitosamente con estado {}", pedido.getId(), pedido.getEstado());

        } catch (Exception e) {
            log.error("[ConciliacionVoucherService] Error en el proceso de conciliación del voucher: {}", e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                log.debug("[ConciliacionVoucherService] Archivo temporal eliminado: {}", deleted);
            }
        }
    }

    public DatosVoucher procesarTexto(String textoCrudo) {
        log.info("[ConciliacionVoucherService] Iniciando análisis de texto OCR...");
        DatosVoucher datos = new DatosVoucher();

        // 1. Extraer Monto
        datos.monto = extraerMonto(textoCrudo);

        // 2. Extraer Referencia/Operación
        datos.referencia = extraerReferencia(textoCrudo);

        // 3. Extraer Fecha/Hora
        datos.fechaPago = extraerFechaHora(textoCrudo);

        // 4. Detectar Método de Pago
        datos.metodoPago = detectarMetodoPago(textoCrudo);

        log.info("[ConciliacionVoucherService] Análisis de OCR completado. Resultados: {}", datos);
        return datos;
    }

    private Double extraerMonto(String texto) {
        log.info("[OCR Regex] Buscando monto en el texto del voucher...");
        
        // Patrón ultra flexible:
        Pattern p1 = Pattern.compile("(?i)(?:s/\\.?|monto|pagado|total)[:\\s]*([\\d\\s\\.,]+)");
        Matcher m1 = p1.matcher(texto);
        
        while (m1.find()) {
            String textoMonto = m1.group(1);
            if (textoMonto == null) continue;
            
            // Asegurar limpieza absoluta de caracteres antes del parseo
            String textoLimpio = textoMonto.replaceAll("[^0-9.,]", "");
            if (textoLimpio.contains(",") && textoLimpio.contains(".")) {
                textoLimpio = textoLimpio.replace(",", ""); // Quitar miles
            } else if (textoLimpio.contains(",") && !textoLimpio.contains(".")) {
                textoLimpio = textoLimpio.replace(",", "."); // Cambiar coma decimal por punto
            }
            
            try {
                Double montoFinal = Double.parseDouble(textoLimpio.trim());
                log.info("[OCR Regex] Monto detectado con éxito: {} (de la cadena limpia: {})", montoFinal, textoLimpio);
                return montoFinal;
            } catch (Exception ex) {
                System.err.println("[FALLO CRÍTICO OCR] No se pudo parsear el texto limpido '" + textoLimpio + "' a Double. Forzando log de depuración.");
                ex.printStackTrace();
            }
        }
        
        // Fallback: Cualquier número decimal razonable
        Pattern pFallback = Pattern.compile("(\\d+\\s*\\.\\s*\\d{2})");
        Matcher mFallback = pFallback.matcher(texto);
        if (mFallback.find()) {
            String val = mFallback.group(1);
            String textoLimpio = val.replaceAll("[^0-9.,]", "");
            if (textoLimpio.contains(",") && textoLimpio.contains(".")) {
                textoLimpio = textoLimpio.replace(",", "");
            } else if (textoLimpio.contains(",") && !textoLimpio.contains(".")) {
                textoLimpio = textoLimpio.replace(",", ".");
            }
            try {
                Double montoFinal = Double.parseDouble(textoLimpio.trim());
                log.info("[OCR Regex] Monto detectado vía Fallback: {}", montoFinal);
                return montoFinal;
            } catch (Exception ex) {
                System.err.println("[FALLO CRÍTICO OCR Fallback] No se pudo parsear '" + textoLimpio + "' a Double.");
            }
        }
        
        return null;
    }

    private String extraerReferencia(String texto) {
        // Patrones para número de operación/referencia
        // BCP/Yape/Plin suelen decir "Operación: 123456", "Nro. Operación 123456", "Ref. 123456", "Nro: 123456"
        Pattern p1 = Pattern.compile("(?i)(?:nro\\.?\\s*operaci[oó]n|nro\\.?\\s*de\\s*operaci[oó]n|operaci[oó]n|ref(?:erencia)?|nro|c[oó]digo|transacci[oó]n)[:\\s#-]+([a-zA-Z0-9]{4,20})");
        Matcher m1 = p1.matcher(texto);
        if (m1.find()) {
            return m1.group(1).trim();
        }

        // Si no se encuentra con palabras clave, buscar un número largo (de 6 a 12 dígitos) que podría ser la operación
        Pattern p2 = Pattern.compile("\\b(\\d{6,12})\\b");
        Matcher m2 = p2.matcher(texto);
        if (m2.find()) {
            return m2.group(1);
        }

        return null;
    }

    private LocalDateTime extraerFechaHora(String texto) {
        LocalDate fecha = null;
        LocalTime hora = null;

        // Limpiar el texto para que no haya saltos de línea molestos en medio de la fecha/hora
        String textoLimpio = texto.replaceAll("\\s+", " ");

        // 1. Patrón específico para Yape: e.g., "27 de jun. 2026 | 10:21 a. m."
        Pattern pYapeDate = Pattern.compile("(?i)(\\d{1,2})\\s+de\\s+([a-zA-Záéíóúñ.]{3,10})\\s+(\\d{4})\\s*\\|\\s*(?:[^\\n\\d]*?)(\\d{1,2}):(\\d{2})\\s*(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?)?");
        Matcher mYapeDate = pYapeDate.matcher(textoLimpio);
        if (mYapeDate.find()) {
            try {
                int dia = Integer.parseInt(mYapeDate.group(1));
                String mesStr = mYapeDate.group(2).toLowerCase().replace(".", "").trim();
                int anio = Integer.parseInt(mYapeDate.group(3));
                int h = Integer.parseInt(mYapeDate.group(4));
                int m = Integer.parseInt(mYapeDate.group(5));
                String ampm = mYapeDate.group(6);

                if (ampm != null) {
                    ampm = ampm.toLowerCase().replace(".", "").replaceAll("\\s+", "");
                    if (ampm.contains("pm") && h < 12) {
                        h += 12;
                    } else if (ampm.contains("am") && h == 12) {
                        h = 0;
                    }
                }

                int mes = obtenerMesDeNombre(mesStr);
                if (mes > 0) {
                    fecha = LocalDate.of(anio, mes, dia);
                    hora = LocalTime.of(h, m, 0);
                    log.info("[OCR Date] Fecha y hora de Yape detectadas con éxito: {} - {}", fecha, hora);
                    return LocalDateTime.of(fecha, hora);
                }
            } catch (Exception e) {
                log.error("[OCR Date] Error parseando patrón específico de Yape: " + mYapeDate.group(0), e);
            }
        }

        // 2. Buscar fecha en formato DD/MM/YYYY o DD-MM-YYYY
        Pattern pFecha1 = Pattern.compile("(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})");
        Matcher mFecha1 = pFecha1.matcher(textoLimpio);
        if (mFecha1.find()) {
            try {
                int dia = Integer.parseInt(mFecha1.group(1));
                int mes = Integer.parseInt(mFecha1.group(2));
                int anio = Integer.parseInt(mFecha1.group(3));
                if (anio < 100) anio += 2000; // si es 26, asumir 2026
                fecha = LocalDate.of(anio, mes, dia);
            } catch (Exception ignored) {}
        }

        // 3. Buscar fecha escrita completa: e.g., "29 de junio de 2026" o "29 Jun 2026"
        if (fecha == null) {
            Pattern pFecha2 = Pattern.compile("(?i)(\\d{1,2})\\s+(?:de\\s+)?([a-zA-Záéíóúñ]{3,10})\\.?\\s*(?:de\\s+)?(\\d{4})");
            Matcher mFecha2 = pFecha2.matcher(textoLimpio);
            if (mFecha2.find()) {
                try {
                    int dia = Integer.parseInt(mFecha2.group(1));
                    String mesStr = mFecha2.group(2).toLowerCase();
                    int anio = Integer.parseInt(mFecha2.group(3));
                    int mes = obtenerMesDeNombre(mesStr);
                    if (mes > 0) {
                        fecha = LocalDate.of(anio, mes, dia);
                    }
                } catch (Exception ignored) {}
            }
        }

        // 4. Buscar fecha escrita sin año: e.g., "29 Jun" o "29 de junio" o "29 de jun."
        if (fecha == null) {
            Pattern pFecha3 = Pattern.compile("(?i)(\\d{1,2})\\s+(?:de\\s+)?([a-zA-Záéíóúñ]{3,10})\\.?");
            Matcher mFecha3 = pFecha3.matcher(textoLimpio);
            if (mFecha3.find()) {
                try {
                    int dia = Integer.parseInt(mFecha3.group(1));
                    String mesStr = mFecha3.group(2).toLowerCase();
                    int mes = obtenerMesDeNombre(mesStr);
                    if (mes > 0) {
                        int anio = LocalDate.now(java.time.ZoneId.of("America/Lima")).getYear();
                        fecha = LocalDate.of(anio, mes, dia);
                    }
                } catch (Exception ignored) {}
            }
        }

        // 5. Buscar hora: e.g., "11:34:25 am" o "11:34 pm" o "23:34"
        Pattern pHora = Pattern.compile("(?i)(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?");
        Matcher mHora = pHora.matcher(textoLimpio);
        if (mHora.find()) {
            try {
                int h = Integer.parseInt(mHora.group(1));
                int m = Integer.parseInt(mHora.group(2));
                int s = mHora.group(3) != null ? Integer.parseInt(mHora.group(3)) : 0;
                String ampm = mHora.group(4);

                if (ampm != null) {
                    ampm = ampm.toLowerCase().replace(".", "");
                    if (ampm.contains("pm") && h < 12) {
                        h += 12;
                    } else if (ampm.contains("am") && h == 12) {
                        h = 0;
                    }
                }
                hora = LocalTime.of(h, m, s);
            } catch (Exception ignored) {}
        }

        // Consolidar sin usar LocalDateTime.now() como asignación directa
        if (fecha != null && hora != null) {
            return LocalDateTime.of(fecha, hora);
        } else if (fecha != null) {
            return LocalDateTime.of(fecha, LocalTime.of(12, 0, 0));
        }

        return null;
    }

    private int obtenerMesDeNombre(String mesStr) {
        if (mesStr.contains("ene")) return 1;
        if (mesStr.contains("feb")) return 2;
        if (mesStr.contains("mar")) return 3;
        if (mesStr.contains("abr")) return 4;
        if (mesStr.contains("may")) return 5;
        if (mesStr.contains("jun")) return 6;
        if (mesStr.contains("jul")) return 7;
        if (mesStr.contains("ago")) return 8;
        if (mesStr.contains("sep") || mesStr.contains("set")) return 9;
        if (mesStr.contains("oct")) return 10;
        if (mesStr.contains("nov")) return 11;
        if (mesStr.contains("dic")) return 12;
        return 0;
    }

    private String detectarMetodoPago(String texto) {
        String textoLower = texto.toLowerCase();
        if (textoLower.contains("yape")) {
            return "Yape";
        }
        if (textoLower.contains("plin")) {
            return "Plin";
        }
        if (textoLower.contains("bcp") || textoLower.contains("banco de credito") || textoLower.contains("credito")) {
            return "Transferencia BCP";
        }
        if (textoLower.contains("bbva") || textoLower.contains("continental")) {
            return "Transferencia BBVA";
        }
        if (textoLower.contains("interbank")) {
            return "Transferencia Interbank";
        }
        return "Yape"; // valor por defecto habitual
    }

    private String subirVoucherASupabase(byte[] bytes, String contentType, String extension) {
        try {
            String uniqueName = "voucher-" + UUID.randomUUID().toString() + extension;
            String uploadUrl = supabaseUrl + "/storage/v1/object/productos/" + uniqueName;

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(uploadUrl))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Content-Type", contentType)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Respuesta fallida de Supabase Storage (código {}): {}", response.statusCode(), response.body());
                return null;
            }

            return supabaseUrl + "/storage/v1/object/public/productos/" + uniqueName;
        } catch (Exception e) {
            log.error("Error subiendo imagen de voucher a Supabase Storage: {}", e.getMessage(), e);
            return null;
        }
    }
}
