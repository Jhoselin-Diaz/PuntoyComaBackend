package com.upc.Controller;

import com.upc.Entity.Pedido;
import com.upc.Entity.PedidoDetalle;
import com.upc.Repository.PedidoRepository;
import com.upc.Repository.PedidoDetalleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.upc.Util.StockInsuficienteException;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/pedidos")
@CrossOrigin(origins = {"http://localhost:4200", "https://puntoycoma-store.netlify.app", "https://puntoycoma-store.netlify.app/"})
public class AdminPedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoDetalleRepository pedidoDetalleRepository;

    @Autowired
    private com.upc.Repository.PagoVoucherRepository pagoVoucherRepository;

    @Autowired
    private com.upc.Repository.InventarioMovimientoRepository inventarioMovimientoRepository;

    @Autowired
    private com.upc.Service.DashboardService dashboardService;

    @GetMapping("/mas-vendidos")
    public ResponseEntity<List<com.upc.DTO.ProductoVigilanciaDTO>> obtenerMasVendidos() {
        return ResponseEntity.ok(dashboardService.obtenerMasVendidos());
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> obtenerTodosLosPedidos() {
        List<Pedido> pedidos = pedidoRepository.findAll();
        return new ResponseEntity<>(mapPedidosToResponse(pedidos), HttpStatus.OK);
    }

    @GetMapping("/cliente/{telefono}")
    public ResponseEntity<List<Map<String, Object>>> obtenerPedidosDeCliente(@PathVariable String telefono) {
        List<Pedido> pedidos = pedidoRepository.findByUsuarioTelefono(telefono);
        return new ResponseEntity<>(mapPedidosToResponse(pedidos), HttpStatus.OK);
    }

    private java.util.Optional<Pedido> buscarPedidoPorIdFlexible(String idStr) {
        if (idStr == null || idStr.trim().isEmpty()) {
            return java.util.Optional.empty();
        }
        String cleanId = idStr.trim();

        // 1. Intentar por ID numérico directo
        try {
            Long numericId = Long.parseLong(cleanId);
            java.util.Optional<Pedido> porId = pedidoRepository.findById(numericId);
            if (porId.isPresent()) return porId;
        } catch (NumberFormatException ignored) {}

        // 2. Intentar por numeroOrden exacto (ej. "WSP-1791376377853")
        java.util.Optional<Pedido> porOrden = pedidoRepository.findByNumeroOrden(cleanId);
        if (porOrden.isPresent()) return porOrden;

        // 3. Si tiene prefijo #ORD-00, ORD-00, #, o WSP-, limpiar y buscar por ID numérico si aplica
        String sinPrefijo = cleanId.replaceAll("^(#ORD-0*|ORD-0*|#|WSP-)", "").trim();
        if (!sinPrefijo.isEmpty()) {
            try {
                Long numericId = Long.parseLong(sinPrefijo);
                java.util.Optional<Pedido> porId = pedidoRepository.findById(numericId);
                if (porId.isPresent()) return porId;
            } catch (NumberFormatException ignored) {}
        }

        // 4. Búsqueda por wamid en PagoVoucher
        try {
            List<com.upc.Entity.PagoVoucher> vouchers = pagoVoucherRepository.findAll();
            for (com.upc.Entity.PagoVoucher pv : vouchers) {
                if (pv.getWamid() != null && (cleanId.equalsIgnoreCase(pv.getWamid()) || pv.getWamid().contains(cleanId)) && pv.getPedido() != null) {
                    return java.util.Optional.of(pv.getPedido());
                }
            }
        } catch (Exception ignored) {}

        // 5. Búsqueda por coincidencia en numeroOrden, referenciaPago, voucherUrl o id
        List<Pedido> todos = pedidoRepository.findAll();
        for (Pedido p : todos) {
            if (cleanId.equalsIgnoreCase(p.getNumeroOrden()) ||
                (p.getNumeroOrden() != null && (cleanId.contains(p.getNumeroOrden()) || p.getNumeroOrden().contains(cleanId))) ||
                (p.getReferenciaPago() != null && (p.getReferenciaPago().contains(cleanId) || cleanId.contains(p.getReferenciaPago()))) ||
                (p.getVoucherUrl() != null && p.getVoucherUrl().contains(cleanId)) ||
                (p.getId() != null && cleanId.equals(String.valueOf(p.getId())))) {
                return java.util.Optional.of(p);
            }
        }

        return java.util.Optional.empty();
    }

    private String getClienteKey(Pedido p) {
        if (p.getUsuario() != null && p.getUsuario().getId() != null) {
            return "U_" + p.getUsuario().getId();
        }
        if (p.getTelefonoContacto() != null && !p.getTelefonoContacto().trim().isEmpty()) {
            return "T_" + p.getTelefonoContacto().replaceAll("[^\\d]", "");
        }
        if (p.getUsuario() != null && p.getUsuario().getTelefono() != null && !p.getUsuario().getTelefono().trim().isEmpty()) {
            return "T_" + p.getUsuario().getTelefono().replaceAll("[^\\d]", "");
        }
        if (p.getNombreCompleto() != null && !p.getNombreCompleto().trim().isEmpty()) {
            return "N_" + p.getNombreCompleto().trim().toLowerCase();
        }
        return "P_" + p.getId();
    }

    @DeleteMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> eliminarPedido(@PathVariable String id) {
        return buscarPedidoPorIdFlexible(id).map(pedido -> {
            Long pedidoId = pedido.getId();
            if (Boolean.TRUE.equals(pedido.getStockDescontado())) {
                inventarioService.devolverStockPorPedido(pedido);
            }
            String telefonoCliente = null;
            if (pedido.getUsuario() != null && pedido.getUsuario().getTelefono() != null) {
                telefonoCliente = pedido.getUsuario().getTelefono();
            } else if (pedido.getTelefonoContacto() != null) {
                telefonoCliente = pedido.getTelefonoContacto();
            }
            if (telefonoCliente != null) {
                String telFinal = telefonoCliente;
                chatRepository.findByTelefonoCliente(telFinal).ifPresent(chat -> {
                    chat.setContextoInmediato(null);
                    chat.setFasePedido("INICIAL");
                    chat.setPedidoReferenciadoId(null);
                    chat.setPedidoIdentificado(false);
                    chat.setDireccionDetectada(false);
                    chat.setDatosCompletos(false);
                    chat.setSugerenciaIa(null);
                    chat.setFechaCorte(java.time.LocalDateTime.now(java.time.ZoneId.of("America/Lima")));
                    chatRepository.save(chat);
                    System.out.println("[AUDITORÍA RESETEADO] Chat de " + telFinal + " reseteado automáticamente a INICIAL con fechaCorte tras eliminar el pedido ID: " + pedidoId);
                });
            }

            // 1. Desvincular movimientos de inventario asociados para evitar conflictos de clave foránea
            List<com.upc.Entity.InventarioMovimiento> movs = inventarioMovimientoRepository.findByPedidoId(pedidoId);
            if (movs != null && !movs.isEmpty()) {
                for (com.upc.Entity.InventarioMovimiento m : movs) {
                    m.setPedido(null);
                }
                inventarioMovimientoRepository.saveAll(movs);
            }

            // 2. Eliminar comprobantes/vouchers de pago vinculados al pedido
            pagoVoucherRepository.deleteByPedidoId(pedidoId);

            // 3. Eliminar detalles de productos del pedido
            pedidoDetalleRepository.deleteByPedidoId(pedidoId);

            // 4. Eliminar el pedido
            pedidoRepository.deleteById(pedidoId);
            return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
        }).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    private List<Map<String, Object>> mapPedidosToResponse(List<Pedido> pedidos) {
        List<Map<String, Object>> response = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

        // Agrupar pedidos no validados por cliente para determinar "1er Pendiente" vs "Pendiente"
        Map<String, List<Pedido>> pendientesPorCliente = new HashMap<>();
        for (Pedido p : pedidos) {
            String est = p.getEstado();
            boolean esValidado = "VALIDADO".equalsIgnoreCase(est);
            boolean esRechazado = "RECHAZADO".equalsIgnoreCase(est);
            boolean esEnRevision = "EN REVISION".equalsIgnoreCase(est) || "EN_REVISION".equalsIgnoreCase(est) || "EN REVISIÓN".equalsIgnoreCase(est);
            if (!esValidado && !esRechazado && !esEnRevision) {
                String key = getClienteKey(p);
                pendientesPorCliente.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
            }
        }
        // Ordenar cronológicamente (más antiguo / primero registrado primero)
        for (List<Pedido> lista : pendientesPorCliente.values()) {
            lista.sort((p1, p2) -> {
                if (p1.getFecha() != null && p2.getFecha() != null) {
                    int cmp = p1.getFecha().compareTo(p2.getFecha());
                    if (cmp != 0) return cmp;
                }
                if (p1.getId() != null && p2.getId() != null) {
                    return p1.getId().compareTo(p2.getId());
                }
                return 0;
            });
        }

        for (Pedido p : pedidos) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getNumeroOrden() != null ? p.getNumeroOrden() : "#ORD-00" + p.getId());
            map.put("dbId", p.getId());
            map.put("cliente", p.getNombreCompleto() != null ? p.getNombreCompleto() : 
                     (p.getUsuario() != null ? p.getUsuario().getNombre() : "Cliente WhatsApp"));
            map.put("dni", p.getDni() != null ? p.getDni() : "");
            map.put("telefono", p.getTelefonoContacto() != null ? p.getTelefonoContacto() : 
                     (p.getUsuario() != null ? p.getUsuario().getTelefono() : ""));
            map.put("direccion", p.getDireccionEntrega() != null ? p.getDireccionEntrega() : "");
            map.put("direccionEnvio", p.getDireccionEntrega() != null ? p.getDireccionEntrega() : "");
            map.put("direccionEntrega", p.getDireccionEntrega() != null ? p.getDireccionEntrega() : "");
            
            // Variantes o productos
            String productoResumen = p.getVariantesEspecificas() != null ? p.getVariantesEspecificas() : "";
            if (productoResumen.isEmpty()) {
                List<PedidoDetalle> dets = pedidoDetalleRepository.findByPedidoId(p.getId());
                if (dets != null && !dets.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < dets.size(); i++) {
                        PedidoDetalle d = dets.get(i);
                        String nomProd = d.getProducto() != null ? d.getProducto().getNombre() : "Producto";
                        int cantProd = d.getCantidad() != null ? d.getCantidad() : 1;
                        sb.append(nomProd).append(" x").append(cantProd);
                        if (i < dets.size() - 1) sb.append(", ");
                    }
                    productoResumen = sb.toString();
                } else {
                    productoResumen = "Pedido en validación";
                }
            }
            map.put("producto", productoResumen);
            
            // Estados asistenciales con Human-in-the-loop:
            // "Validado", "1er Pendiente", "Pendiente", "En revision", "Rechazado"
            String estRaw = p.getEstado();
            String est;
            if ("VALIDADO".equalsIgnoreCase(estRaw)) {
                est = "Validado";
            } else if ("RECHAZADO".equalsIgnoreCase(estRaw)) {
                est = "Rechazado";
            } else if ("EN REVISION".equalsIgnoreCase(estRaw) || "EN_REVISION".equalsIgnoreCase(estRaw) || "EN REVISIÓN".equalsIgnoreCase(estRaw)) {
                est = "En revision";
            } else {
                List<Pedido> pends = pendientesPorCliente.get(getClienteKey(p));
                int idx = (pends != null) ? pends.indexOf(p) : 0;
                if (idx == 0) {
                    est = "1er Pendiente";
                } else {
                    est = "Pendiente";
                }
            }
            map.put("estado", est);
            
            map.put("validacion", p.getResultadoConciliacion() != null ? p.getResultadoConciliacion() : "Esperando validación");
            
            // Fechas formateadas
            if (p.getFecha() != null) {
                map.put("fecha", p.getFecha().format(dateFormatter));
                map.put("hora", p.getFecha().format(timeFormatter).toUpperCase());
            } else {
                map.put("fecha", "Hoy");
                map.put("hora", "Justo ahora");
            }
            
            String fechaPagoStr = p.getFechaPago() != null ? p.getFechaPago().toString() : (p.getFecha() != null ? p.getFecha().toString() : null);
            map.put("fechaPago", fechaPagoStr);
            map.put("fecha_pago", fechaPagoStr);
            
            map.put("total", "S/ " + String.format("%.2f", p.getTotal() != null ? p.getTotal() : 0.0));
            map.put("montoOcr", p.getMontoOcr());
            map.put("montoDetectado", p.getMontoOcr());
            map.put("monto_ocr", p.getMontoOcr());
            map.put("diferencia", p.getDiferencia() != null ? "S/ " + String.format("%.2f", p.getDiferencia()) : "S/ 0.00");
            map.put("metodoPago", p.getMetodoPago() != null ? p.getMetodoPago() : "Yape");
            map.put("referenciaPago", p.getReferenciaPago() != null ? p.getReferenciaPago() : "WSP-" + p.getId());
            map.put("voucherUrl", p.getVoucherUrl() != null ? p.getVoucherUrl() : "");
            
            // Productos detalle array
            List<Map<String, Object>> prodDetalles = new ArrayList<>();
            List<PedidoDetalle> dets = pedidoDetalleRepository.findByPedidoId(p.getId());
            if (dets != null) {
                for (PedidoDetalle d : dets) {
                    Map<String, Object> pd = new HashMap<>();
                    pd.put("nombre", d.getProducto() != null ? d.getProducto().getNombre() : "Producto");
                    pd.put("cantidad", d.getCantidad() != null ? d.getCantidad() : 1);
                    double pu = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : (d.getProducto() != null && d.getProducto().getPrecio() != null ? d.getProducto().getPrecio() : 0.0);
                    pd.put("precio", pu);
                    prodDetalles.add(pd);
                }
            }
            map.put("productosDetalle", prodDetalles);

            response.add(map);
        }
        return response;
    }

    @Autowired
    private com.upc.Service.InventarioService inventarioService;

    @Autowired
    private com.upc.Repository.ChatRepository chatRepository;

    @PutMapping("/{id}/validar")
    public ResponseEntity<Map<String, Object>> validarPedidoEndpoint(@PathVariable String id) {
        System.out.println("[ADMIN PEDIDO] Petición recibida para validar pedido con ID: " + id);
        return buscarPedidoPorIdFlexible(id).map(pedido -> {
            String estadoAnterior = pedido.getEstado();
            String nuevoEst = "VALIDADO";

            try {
                inventarioService.procesarTransicionEstadoPedido(pedido, estadoAnterior, nuevoEst);
            } catch (StockInsuficienteException sie) {
                System.err.println("[ADMIN PEDIDO] Stock insuficiente al validar pedido: " + sie.getMessage());
                Map<String, Object> err = new HashMap<>();
                err.put("error", "STOCK_INSUFICIENTE");
                err.put("message", sie.getMessage());
                return new ResponseEntity<>(err, HttpStatus.BAD_REQUEST);
            }

            pedido.setEstado("VALIDADO");
            pedido.setResultadoConciliacion("Pago verificado (Manual)");
            pedido.setEstadoPago("PAGO_COMPLETO");
            pedido.setDiferencia(0.0);

            Pedido guardado = pedidoRepository.save(pedido);
            System.out.println("[ADMIN PEDIDO] Pedido ID " + guardado.getId() + " (" + guardado.getNumeroOrden() + ") VALIDADO en BD.");

            // Actualizar reactivamente el chat y sugerencia IA del cliente
            String telefonoClienteVal = (guardado.getUsuario() != null && guardado.getUsuario().getTelefono() != null)
                    ? guardado.getUsuario().getTelefono()
                    : guardado.getTelefonoContacto();
            if (telefonoClienteVal != null && !telefonoClienteVal.trim().isEmpty()) {
                String cleanTel = telefonoClienteVal.replaceAll("[^0-9]", "");
                String tel51 = cleanTel.startsWith("51") ? cleanTel : (cleanTel.length() == 9 ? "51" + cleanTel : cleanTel);
                java.util.Optional<com.upc.Entity.Chat> chatOpt = chatRepository.findByTelefonoCliente(tel51);
                if (chatOpt.isEmpty()) {
                    chatOpt = chatRepository.findByTelefonoCliente(cleanTel);
                }
                chatOpt.ifPresent(chat -> {
                    chat.setContextoInmediato(null);
                    chat.setPedidoReferenciadoId(null);
                    String nuevaSugerencia = "¡Tu pago ha sido validado con éxito! 🎉 Estamos preparando tu pedido para despacho. Te enviaremos el número de seguimiento en cuanto esté en camino. ¡Muchas gracias por tu compra! ❤️";
                    chat.setFasePedido("INICIAL");
                    chat.setPedidoIdentificado(false);
                    chat.setDireccionDetectada(false);
                    chat.setDatosCompletos(false);
                    chat.setSugerenciaIa(nuevaSugerencia);
                    chatRepository.save(chat);
                    System.out.println("[AUDITORÍA REACCIÓN] Chat de " + tel51 + " actualizado reactivamente tras validación manual. pedidoReferenciadoId limpiado.");
                });
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("mensaje", "Pedido validado correctamente");
            resp.put("id", guardado.getNumeroOrden() != null ? guardado.getNumeroOrden() : "#ORD-00" + guardado.getId());
            resp.put("dbId", guardado.getId());
            resp.put("estado", "Validado");
            resp.put("validacion", guardado.getResultadoConciliacion());

            return new ResponseEntity<>(resp, HttpStatus.OK);
        }).orElseGet(() -> {
            System.err.println("[ADMIN PEDIDO] No se encontró el pedido a validar: " + id);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Pedido no encontrado con ID: " + id);
            return new ResponseEntity<>(err, HttpStatus.NOT_FOUND);
        });
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarPedido(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return buscarPedidoPorIdFlexible(id).map(pedido -> {
            if (body.containsKey("estado")) {
                String est = (String) body.get("estado");
                String nuevoEst = null;
                if ("Validado".equalsIgnoreCase(est) || "VALIDADO".equalsIgnoreCase(est)) nuevoEst = "VALIDADO";
                else if ("Rechazado".equalsIgnoreCase(est) || "RECHAZADO".equalsIgnoreCase(est)) nuevoEst = "RECHAZADO";
                else if ("Pendiente".equalsIgnoreCase(est) || "PENDIENTE".equalsIgnoreCase(est) || "1er Pendiente".equalsIgnoreCase(est) || "2do Pendiente".equalsIgnoreCase(est)) nuevoEst = "PENDIENTE";
                else if ("En revision".equalsIgnoreCase(est) || "En revisión".equalsIgnoreCase(est) || "EN_REVISION".equalsIgnoreCase(est) || "EN REVISION".equalsIgnoreCase(est)) nuevoEst = "EN REVISION";

                if (nuevoEst != null) {
                    String estadoAnterior = pedido.getEstado();
                    try {
                        inventarioService.procesarTransicionEstadoPedido(pedido, estadoAnterior, nuevoEst);
                    } catch (StockInsuficienteException sie) {
                        System.err.println("[ADMIN PEDIDO] Stock insuficiente al actualizar pedido: " + sie.getMessage());
                        Map<String, Object> err = new HashMap<>();
                        err.put("error", "STOCK_INSUFICIENTE");
                        err.put("message", sie.getMessage());
                        return new ResponseEntity<Map<String, Object>>(err, HttpStatus.BAD_REQUEST);
                    }
                    pedido.setEstado(nuevoEst);
                    if ("VALIDADO".equals(nuevoEst)) {
                        pedido.setValidacion("Pago verificado (Manual)");
                        pedido.setEstadoPago("PAGO_COMPLETO");
                        pedido.setDiferencia(0.0);
                    }
                }
            }
            if (body.containsKey("validacion")) {
                if (!"VALIDADO".equalsIgnoreCase(pedido.getEstado())) {
                    pedido.setValidacion((String) body.get("validacion"));
                }
            }
            if (body.containsKey("montoDetectado")) {
                Object md = body.get("montoDetectado");
                if (md != null) {
                    if (md instanceof Number) {
                        pedido.setMontoDetectado(((Number) md).doubleValue());
                    } else {
                        try {
                            String s = md.toString().replaceAll("[^\\d.]", "");
                            pedido.setMontoDetectado(Double.parseDouble(s));
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (body.containsKey("metodoPago")) {
                pedido.setMetodoPago((String) body.get("metodoPago"));
            }
            if (body.containsKey("referenciaPago")) {
                pedido.setReferenciaPago((String) body.get("referenciaPago"));
            }
            if (body.containsKey("direccion")) {
                pedido.setDireccionEntrega((String) body.get("direccion"));
            }
            if (body.containsKey("direccionEnvio")) {
                pedido.setDireccionEntrega((String) body.get("direccionEnvio"));
            }
            if (body.containsKey("direccionEntrega")) {
                pedido.setDireccionEntrega((String) body.get("direccionEntrega"));
            }
            
            // Recalcular diferencia
            if ("VALIDADO".equalsIgnoreCase(pedido.getEstado())) {
                pedido.setDiferencia(0.0);
            } else if (pedido.getTotal() != null && pedido.getMontoDetectado() != null) {
                pedido.setDiferencia(pedido.getTotal() - pedido.getMontoDetectado());
            }
            
            Pedido guardado = pedidoRepository.save(pedido);

            // Cuando el administrador actualiza el estado del pedido, actualizamos reactivamente la sugerencia de la IA en el Chat!
            String telefonoCliente = guardado.getUsuario() != null && guardado.getUsuario().getTelefono() != null
                    ? guardado.getUsuario().getTelefono()
                    : guardado.getTelefonoContacto();
            if (telefonoCliente != null && !telefonoCliente.trim().isEmpty()) {
                String cleanTel = telefonoCliente.replaceAll("[^0-9]", "");
                String tel51 = cleanTel.startsWith("51") ? cleanTel : (cleanTel.length() == 9 ? "51" + cleanTel : cleanTel);
                java.util.Optional<com.upc.Entity.Chat> chatOpt = chatRepository.findByTelefonoCliente(tel51);
                if (chatOpt.isEmpty()) {
                    chatOpt = chatRepository.findByTelefonoCliente(cleanTel);
                }
                chatOpt.ifPresent(chat -> {
                    chat.setContextoInmediato(null);
                    String nuevaSugerencia = "Hola " + chat.getNombreCliente() + ", ";
                    if ("VALIDADO".equalsIgnoreCase(guardado.getEstado())) {
                        nuevaSugerencia = "¡Tu pago ha sido validado con éxito! 🎉 Estamos preparando tu pedido para despacho. Te enviaremos el número de seguimiento en cuanto esté en camino. ¡Muchas gracias por tu compra! ❤️";
                        // CORRECCIÓN: Al VALIDAR, el pedido queda CERRADO. Se resetea el estado comercial del Chat
                        // y se limpia pedidoReferenciadoId para no arrastrar la referencia al pedido cerrado.
                        chat.setFasePedido("INICIAL");
                        chat.setPedidoIdentificado(false);
                        chat.setDireccionDetectada(false);
                        chat.setDatosCompletos(false);
                        chat.setPedidoReferenciadoId(null);
                    } else if ("RECHAZADO".equalsIgnoreCase(guardado.getEstado())) {
                        nuevaSugerencia = "Hola " + chat.getNombreCliente() + ", lamentablemente hemos detectado una discrepancia en tu comprobante de pago: " + (guardado.getResultadoConciliacion() != null ? guardado.getResultadoConciliacion() : "Los datos no coinciden.") + ". Por favor, envíanos el comprobante correcto o comunícate con nosotros para solucionarlo. 😊";
                        // CORRECCIÓN: Al RECHAZAR, se resetea el contexto comercial y se limpia pedidoReferenciadoId.
                        chat.setFasePedido("INICIAL");
                        chat.setPedidoIdentificado(false);
                        chat.setDireccionDetectada(false);
                        chat.setDatosCompletos(false);
                        chat.setPedidoReferenciadoId(null);
                    } else if ("EN REVISION".equalsIgnoreCase(guardado.getEstado())) {
                        nuevaSugerencia = "Hola " + chat.getNombreCliente() + ", tu pedido se encuentra en revisión. En breve un asesor te atenderá para confirmar los detalles. ¡Gracias por tu paciencia! 😊";
                        // CORRECCIÓN: Al pasar a EN REVISION también limpiamos la referencia para evitar contaminación.
                        chat.setPedidoReferenciadoId(null);
                    } else if ("PENDIENTE".equalsIgnoreCase(guardado.getEstado())) {
                        nuevaSugerencia = "Hola " + chat.getNombreCliente() + ", hemos recibido tu comprobante. Nuestro equipo está validando los datos en el sistema y te confirmaremos en unos minutos. ¡Gracias por tu paciencia! 😊";
                    }
                    chat.setSugerenciaIa(nuevaSugerencia);
                    chatRepository.save(chat);
                    System.out.println("[AUDITORÍA REACCION] Chat de " + telefonoCliente + " actualizado reactivamente tras cambio de estado del pedido a: " + guardado.getEstado());
                });
            }

            return new ResponseEntity<>(guardado, HttpStatus.OK);
        }).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @Autowired
    private com.upc.Service.OpenAiService openAiService;

    @PostMapping("/{pedidoId}/solicitar-monto-faltante")
    public ResponseEntity<Map<String, String>> solicitarMontoFaltante(@PathVariable Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado con ID: " + pedidoId));
        
        // PERSISTIR LA AUTORIZACIÓN FINANCIERA DIRECTAMENTE EN LA ENTIDAD PEDIDO (FUENTE DE VERDAD BD)
        pedido.setEstadoPago("SOLICITAR_FALTANTE");
        pedidoRepository.save(pedido);
        System.out.println("[AUDITORÍA AUTORIZACIÓN] Pedido ID " + pedidoId + " actualizado a estadoPago = SOLICITAR_FALTANTE en BD.");

        String mensaje = openAiService.generarSugerenciaMontoFaltante(pedidoId);
        Map<String, String> response = new HashMap<>();
        response.put("mensaje", mensaje);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @ExceptionHandler(com.upc.Util.StockInsuficienteException.class)
    public ResponseEntity<Map<String, Object>> handleStockInsuficiente(com.upc.Util.StockInsuficienteException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "STOCK_INSUFICIENTE");
        response.put("message", ex.getMessage());
        response.put("mensaje", ex.getMessage());
        response.put("detalles", ex.getDetalles());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}
