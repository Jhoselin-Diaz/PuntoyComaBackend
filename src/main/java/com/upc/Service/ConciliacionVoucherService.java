package com.upc.Service;

import com.upc.Entity.Pedido;
import com.upc.Repository.PedidoRepository;
import com.upc.Entity.PagoVoucher;
import com.upc.Repository.PagoVoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
import java.util.List;
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

    @Autowired
    private com.upc.Repository.ChatRepository chatRepository;

    @Autowired
    private PagoVoucherRepository pagoVoucherRepository;

    @Lazy
    @Autowired
    private OpenAiService openAiService;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key:}")
    private String supabaseKey;

    @Autowired
    private org.springframework.web.client.RestTemplate restTemplate;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.api-url}")
    private String apiUrl;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

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
    public void procesarYConciliarVoucher(String telefono, String contenidoVoucher) {
        procesarYConciliarVoucher(telefono, contenidoVoucher, null);
    }

    @Async
    public void procesarYConciliarVoucher(String telefono, String contenidoVoucher, String wamid) {
        if (telefono != null) {
            telefono = telefono.split("@")[0].replaceAll("[^0-9]", "");
        }
        final String cleanTelefono = telefono;
        log.info("[ConciliacionVoucherService] Recibido procesarYConciliarVoucher para teléfono: {} | wamid: {}", cleanTelefono, wamid);

        Object lock = com.upc.Util.SharedLockManager.getLock(cleanTelefono);
        synchronized (lock) {
            transactionTemplate.executeWithoutResult(status -> {
                log.info("[ConciliacionVoucherService] Iniciando conciliación asíncrona de voucher bajo lock para teléfono: {}", cleanTelefono);

                // 1. Buscar el pedido PENDIENTE o EN REVISION más reciente
                Pedido pedido = pedidoRepository.findTopByUsuarioTelefonoAndEstadoOrderByFechaDesc(cleanTelefono, "PENDIENTE");
                if (pedido == null) {
                    pedido = pedidoRepository.findTopByUsuarioTelefonoAndEstadoOrderByFechaDesc(cleanTelefono, "EN REVISION");
                }
                if (pedido == null) {
                    log.warn("[ConciliacionVoucherService] No se encontró ningún pedido PENDIENTE ni EN REVISION para el cliente: {}. Se aborta el OCR.", cleanTelefono);
                    return;
                }

                // Deduplicación por WAMID si ya fue procesado previamente
                if (wamid != null && !wamid.trim().isEmpty() && pagoVoucherRepository.existsByWamid(wamid.trim())) {
                    log.warn("[ConciliacionVoucherService] Deduplicación: Voucher con wamid {} ya fue registrado previamente en BD. Se ignora.", wamid);
                    return;
                }

                // 2. Extraer la imagen (ya sea base64 o URL) y guardarla como archivo temporal
                File tempFile = null;
                String voucherUrl = null;
                try {
                    String cleanContenidoVoucher = contenidoVoucher;
                    if (cleanContenidoVoucher.startsWith("[VOUCHER] ")) {
                        cleanContenidoVoucher = cleanContenidoVoucher.substring("[VOUCHER] ".length()).trim();
                    }
                    if (cleanContenidoVoucher.contains(" | ")) {
                        cleanContenidoVoucher = cleanContenidoVoucher.substring(0, cleanContenidoVoucher.indexOf(" | ")).trim();
                    }

                    byte[] imageBytes = null;
                    String contentType = "image/jpeg";
                    String extension = ".jpg";

                    if (cleanContenidoVoucher.startsWith("data:")) {
                        // Es un Base64 Data URL
                        int commaIndex = cleanContenidoVoucher.indexOf(",");
                        if (commaIndex != -1) {
                            String metadata = cleanContenidoVoucher.substring(0, commaIndex);
                            String base64Content = cleanContenidoVoucher.substring(commaIndex + 1);
                            imageBytes = Base64.getDecoder().decode(base64Content.trim());

                            if (metadata.contains("image/png")) {
                                contentType = "image/png";
                                extension = ".png";
                            } else if (metadata.contains("image/webp")) {
                                contentType = "image/webp";
                                extension = ".webp";
                            }
                        }
                    } else if (cleanContenidoVoucher.startsWith("http")) {
                        // Es una URL externa, descargarla
                        log.info("[ConciliacionVoucherService] Descargando imagen desde URL: {}", cleanContenidoVoucher);
                        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                        java.net.http.HttpRequest httpReq = java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create(cleanContenidoVoucher))
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

                    // 4. Procesar el texto con expresiones regulares y la imagen en Base64
                    String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                    DatosVoucher datos = procesarTexto(textoExtraido, base64Image, contentType);

                    // 5. Subir a Supabase Storage para tener una URL permanente y accesible
                    voucherUrl = subirVoucherASupabase(imageBytes, contentType, extension);
                    if (voucherUrl == null) {
                        voucherUrl = cleanContenidoVoucher.length() > 500 ? cleanContenidoVoucher.substring(0, 500) : cleanContenidoVoucher;
                    }

                    // Deduplicación por URL si ya existe en el pedido
                    if (voucherUrl != null && !voucherUrl.trim().isEmpty() && pagoVoucherRepository.existsByPedidoIdAndVoucherUrl(pedido.getId(), voucherUrl.trim())) {
                        log.warn("[ConciliacionVoucherService] Deduplicación: VoucherUrl ya fue registrado en el pedido ID {}. Se ignora.", pedido.getId());
                        return;
                    }

                    // 6. VERIFICAR AUTORIZACIÓN DE VOUCHERS ADICIONALES (FUENTE DE VERDAD BD: Pedido.estadoPago)
                    List<PagoVoucher> vouchersExistentes = pagoVoucherRepository.findByPedidoIdOrderByFechaPagoAsc(pedido.getId());
                    boolean tieneVouchersPrevios = (vouchersExistentes != null && !vouchersExistentes.isEmpty());

                    if (tieneVouchersPrevios) {
                        boolean estaAutorizadoMontoFaltante = "SOLICITAR_FALTANTE".equalsIgnoreCase(pedido.getEstadoPago());
                        if (!estaAutorizadoMontoFaltante) {
                            log.warn("[ConciliacionVoucherService] ABONO ADICIONAL NO AUTORIZADO: Pedido ID {} ya tiene un voucher en revisión y su estadoPago no es 'SOLICITAR_FALTANTE'. Se ignora el nuevo PagoVoucher.", pedido.getId());
                            
                            // Actualizar chat amigablemente para requerir acción del administrador
                            chatRepository.findByTelefonoCliente(cleanTelefono).ifPresent(chat -> {
                                String msgNoAutorizado = "¡Hola " + chat.getNombreCliente() + "! 😊 Hemos recibido tu comprobante adicional. Sin embargo, tu comprobante anterior aún se encuentra en revisión. Si necesitas abonar un saldo restante, por favor espera la confirmación de nuestro equipo. ¡Gracias!";
                                chat.setSugerenciaIa(msgNoAutorizado);
                                chatRepository.save(chat);
                            });
                            return;
                        }
                    }

                    // PERSISTIR ENTIDAD PagoVoucher INDIVIDUAL DE FORMA AUTORIZADA
                    PagoVoucher pv = new PagoVoucher();
                    pv.setPedido(pedido);
                    pv.setMonto(datos.monto != null ? datos.monto : 0.0);
                    pv.setReferencia(datos.referencia != null ? datos.referencia : "OCR-" + System.currentTimeMillis());
                    pv.setWamid(wamid != null ? wamid.trim() : null);
                    pv.setVoucherUrl(voucherUrl);
                    pv.setFechaPago(datos.fechaPago != null ? datos.fechaPago : LocalDateTime.now());
                    pv.setMetodoPago(datos.metodoPago != null ? datos.metodoPago : "YAPE");
                    pagoVoucherRepository.save(pv);
                    log.info("[ConciliacionVoucherService] PagoVoucher guardado con éxito. ID: {}, Monto: S/ {}, Pedido ID: {}", pv.getId(), pv.getMonto(), pedido.getId());

                    // 7. CÁLCULO DETERMINISTA DESDE BASE DE DATOS (SUMA REAL DE PAGO_VOUCHER)
                    Double acumuladoPagado = pagoVoucherRepository.obtenerMontoTotalPagadoPorPedido(pedido.getId());
                    if (acumuladoPagado == null) acumuladoPagado = 0.0;
                    double total = pedido.getTotal() != null ? pedido.getTotal() : 0.0;
                    double diferencia = total - acumuladoPagado;

                    pedido.setMontoDetectado(acumuladoPagado);
                    pedido.setMontoOcr(acumuladoPagado);
                    pedido.setDiferencia(diferencia);
                    
                    // Concatenar referencias para resumen
                    String refNueva = datos.referencia != null ? datos.referencia : "OCR-" + System.currentTimeMillis();
                    String refExistente = pedido.getReferenciaPago();
                    if (refExistente != null && !refExistente.isEmpty()) {
                        if (!refExistente.contains(refNueva)) {
                            pedido.setReferenciaPago(refExistente + ", " + refNueva);
                        }
                    } else {
                        pedido.setReferenciaPago(refNueva);
                    }

                    pedido.setMetodoPago(datos.metodoPago);
                    pedido.setFechaPago(datos.fechaPago != null ? datos.fechaPago : LocalDateTime.now());
                    
                    // Concatenar URLs de vouchers para resumen
                    String urlExistente = pedido.getVoucherUrl();
                    if (urlExistente != null && !urlExistente.isEmpty()) {
                        if (!urlExistente.contains(voucherUrl)) {
                            pedido.setVoucherUrl(urlExistente + ", " + voucherUrl);
                        }
                    } else {
                        pedido.setVoucherUrl(voucherUrl);
                    }

                    pedido.setBancoEmisor(datos.metodoPago != null && datos.metodoPago.startsWith("Transferencia") ? datos.metodoPago.replace("Transferencia ", "") : datos.metodoPago);
                    if (Math.abs(diferencia) < 0.01 || diferencia <= 0.0) {
                        pedido.setEstado("PENDIENTE");
                        pedido.setEstadoPago("PAGO_COMPLETO");
                        pedido.setDiferencia(0.0);
                        if (diferencia < -0.01) {
                            pedido.setResultadoConciliacion("SOBREPAGO: El monto acumulado (S/ " + String.format("%.2f", acumuladoPagado) + ") supera el total (S/ " + String.format("%.2f", total) + ").");
                        } else {
                            pedido.setResultadoConciliacion("COMPLETAMENTE PAGADO: El monto acumulado coincide con el total.");
                        }
                    } else {
                        pedido.setEstado("PENDIENTE");
                        pedido.setEstadoPago("PAGO_PARCIAL");
                        pedido.setResultadoConciliacion("PAGO PARCIAL: Acumulado S/ " + String.format("%.2f", acumuladoPagado) + ", Saldo pendiente S/ " + String.format("%.2f", diferencia));
                    }

                    pedidoRepository.save(pedido);
                    log.info("[ConciliacionVoucherService] Pedido ID {} conciliado determinísticamente en BD. Acumulado: S/ {}, Saldo: S/ {}, Estado: {}, EstadoPago: {}", 
                            pedido.getId(), acumuladoPagado, pedido.getDiferencia(), pedido.getEstado(), pedido.getEstadoPago());

                    // 8. ACTUALIZAR CHAT Y REGENERAR SUGERENCIA IA CON DATOS REALES DE BD
                    final double finalDiferencia = diferencia;
                    final Double finalAcumulado = acumuladoPagado;
                    final Long finalPedidoId = pedido.getId();
                    chatRepository.findByTelefonoCliente(cleanTelefono).ifPresent(chat -> {
                        chat.setPedidoReferenciadoId(finalPedidoId);
                        chat.setFasePedido("CONCILIACION");
                        chat.setPedidoIdentificado(true);
                        chat.setDireccionDetectada(true);
                        chat.setDatosCompletos(true);
                        if (Math.abs(finalDiferencia) < 0.01 || finalDiferencia <= 0.0) {
                            chat.setContextoInmediato(null);
                            chat.setSugerenciaIa("¡Muchas gracias por enviarnos tu comprobante! 🎉 Ya lo tenemos registrado y estamos finalizando la validación de tu pedido para proceder con la entrega.");
                        } else {
                            chat.setSugerenciaIa("¡Hola " + chat.getNombreCliente() + "! 😊 Hemos registrado tu abono de S/." + String.format("%.2f", finalAcumulado) + ". Queda un saldo pendiente de S/." + String.format("%.2f", finalDiferencia) + ". Por favor, envíanos el saldo restante para completar tu pedido.");
                        }
                        chatRepository.save(chat);
                        log.info("[ConciliacionVoucherService] Chat actualizado a CONCILIACION para el teléfono: {}", cleanTelefono);

                        // REGENERAR SUGERENCIA IA DESPUÉS DE LA PERSISTENCIA DEFINITIVA EN BD
                        try {
                            openAiService.generarSoloSugerenciaIA(chat.getId(), cleanTelefono, "[VOUCHER]");
                        } catch (Exception ex) {
                            log.error("[ConciliacionVoucherService] Error al regenerar sugerencia IA tras voucher: {}", ex.getMessage());
                        }
                    });

                } catch (Exception e) {
                    log.error("[ConciliacionVoucherService] Error en el proceso de conciliación del voucher: {}", e.getMessage(), e);
                } finally {
                    if (tempFile != null && tempFile.exists()) {
                        boolean deleted = tempFile.delete();
                        log.debug("[ConciliacionVoucherService] Archivo temporal eliminado: {}", deleted);
                    }
                }
            });
        }
    }

    public DatosVoucher procesarTexto(String textoCrudo, String base64Image, String contentType) {
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

        // Fallback a OpenAI si no se detectó monto o es menor/igual a 0.0
        if (datos.monto == null || datos.monto <= 0.0) {
            log.info("[ConciliacionVoucherService] Monto no detectado o inválido (S/. 0.00). Aplicando fallback de OpenAI multimodal...");
            DatosVoucher datosIA = extraerDatosConIA(textoCrudo, base64Image, contentType);
            if (datosIA != null) {
                if (datosIA.monto != null && datosIA.monto > 0.0) {
                    datos.monto = datosIA.monto;
                }
                if (datosIA.referencia != null) {
                    datos.referencia = datosIA.referencia;
                }
                if (datosIA.fechaPago != null) {
                    datos.fechaPago = datosIA.fechaPago;
                }
                if (datosIA.metodoPago != null) {
                    datos.metodoPago = datosIA.metodoPago;
                }
            }
        }

        log.info("[ConciliacionVoucherService] Análisis de OCR completado. Resultados: {}", datos);
        return datos;
    }

    private Double extraerMonto(String texto) {
        log.info("[OCR Regex] Buscando monto en el texto del voucher...");
        
        // Patrón ultra flexible (soporta errores comunes de OCR sobre 'S/'):
        Pattern p1 = Pattern.compile("(?i)(?:[s5$§]/\\.?|monto|pagado|total|importe)[:\\s]*([\\d\\s\\.,]+)");
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

    private DatosVoucher extraerDatosConIA(String textoCrudo, String base64Image, String contentType) {
        log.info("[ConciliacionVoucherService] Llamando a OpenAI multimodal para extraer datos visualmente del comprobante...");
        try {
            if (apiKey == null || apiKey.trim().isEmpty() || (apiKey.startsWith("sk-proj-xpb4m") && apiKey.length() < 30)) {
                log.warn("[ConciliacionVoucherService] OpenAI API Key no configurada o incompleta. Saltando fallback de IA.");
                return null;
            }

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            com.fasterxml.jackson.databind.node.ObjectNode requestNode = objectMapper.createObjectNode();
            requestNode.put("model", "gpt-4o-mini");

            com.fasterxml.jackson.databind.node.ArrayNode messages = objectMapper.createArrayNode();

            com.fasterxml.jackson.databind.node.ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", "Eres un asistente experto en contabilidad y pasarelas de pago (Yape, Plin, BCP). " +
                    "Tu tarea es analizar la imagen del comprobante de pago provista y devolver la información clave en formato JSON. " +
                    "Debes devolver un JSON con la siguiente estructura:\n" +
                    "{\n" +
                    "  \"monto\": 150.00,\n" +
                    "  \"referencia\": \"12345678\",\n" +
                    "  \"fechaPago\": \"2026-06-29T13:32:00\",\n" +
                    "  \"metodoPago\": \"YAPE\"\n" +
                    "}\n" +
                    "Si no encuentras algún campo, devuélvelo como null o un string vacío. Analiza visualmente la imagen para encontrar el monto exacto, la referencia y la fecha/hora.");
            messages.add(systemMessage);

            // Mensaje del usuario multimodal (texto + imagen)
            com.fasterxml.jackson.databind.node.ArrayNode contentArray = objectMapper.createArrayNode();
            
            com.fasterxml.jackson.databind.node.ObjectNode textNode = objectMapper.createObjectNode();
            textNode.put("type", "text");
            textNode.put("text", "Analiza la imagen adjunta para extraer la información. Texto obtenido por OCR como referencia:\n\n" + textoCrudo);
            contentArray.add(textNode);

            com.fasterxml.jackson.databind.node.ObjectNode imageNode = objectMapper.createObjectNode();
            imageNode.put("type", "image_url");
            
            com.fasterxml.jackson.databind.node.ObjectNode imageUrlDetail = objectMapper.createObjectNode();
            imageUrlDetail.put("url", "data:" + contentType + ";base64," + base64Image);
            imageNode.set("image_url", imageUrlDetail);
            contentArray.add(imageNode);

            com.fasterxml.jackson.databind.node.ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.set("content", contentArray);
            messages.add(userMessage);

            requestNode.set("messages", messages);
            requestNode.put("temperature", 0.1);

            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(objectMapper.writeValueAsString(requestNode), headers);
            String responseStr = restTemplate.postForObject(apiUrl, entity, String.class);

            if (responseStr != null) {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(responseStr);
                String content = root.path("choices").get(0).path("message").path("content").asText("");
                
                // Limpiar si el modelo devuelve markdown
                if (content.contains("```json")) {
                    content = content.substring(content.indexOf("```json") + 7);
                    if (content.contains("```")) {
                        content = content.substring(0, content.indexOf("```"));
                    }
                } else if (content.contains("```")) {
                    content = content.substring(content.indexOf("```") + 3);
                    if (content.contains("```")) {
                        content = content.substring(0, content.indexOf("```"));
                    }
                }
                
                com.fasterxml.jackson.databind.JsonNode resultJson = objectMapper.readTree(content.trim());
                DatosVoucher datos = new DatosVoucher();
                datos.monto = resultJson.path("monto").asDouble(0.0);
                if (datos.monto <= 0.0) {
                    datos.monto = null;
                }
                datos.referencia = resultJson.path("referencia").asText(null);
                if (datos.referencia != null && datos.referencia.trim().isEmpty()) {
                    datos.referencia = null;
                }
                String fpStr = resultJson.path("fechaPago").asText(null);
                if (fpStr != null && !fpStr.trim().isEmpty()) {
                    try {
                        datos.fechaPago = LocalDateTime.parse(fpStr);
                    } catch (Exception e) {
                        log.warn("[ConciliacionVoucherService] No se pudo parsear fechaPago de la IA: {}", fpStr);
                    }
                }
                datos.metodoPago = resultJson.path("metodoPago").asText("YAPE");
                log.info("[ConciliacionVoucherService] Datos extraídos con OpenAI multimodal: {}", datos);
                return datos;
            }
        } catch (Exception e) {
            log.error("[ConciliacionVoucherService] Error llamando a OpenAI multimodal: {}", e.getMessage(), e);
        }
        return null;
    }
}
