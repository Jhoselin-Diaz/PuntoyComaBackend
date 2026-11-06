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
@RequestMapping({"/api/admin/pedidos", "/api/pedidos"})
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
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<com.upc.DTO.ProductoVigilanciaDTO>> obtenerMasVendidos() {
        return ResponseEntity.ok(dashboardService.obtenerMasVendidos());
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> obtenerTodosLosPedidos() {
        try {
            List<Pedido> pedidos = pedidoRepository.findAll();
            return new ResponseEntity<>(mapPedidosToResponse(pedidos), HttpStatus.OK);
        } catch (Exception e) {
            System.err.println("[AdminPedidoController] Error al obtener pedidos: " + e.getMessage());
            e.printStackTrace();
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
        }
    }

    @GetMapping("/cliente/{telefono}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> obtenerPedidosDeCliente(@PathVariable String telefono) {
        try {
            String cleanTel = telefono != null ? telefono.replaceAll("[^0-9]", "") : "";
            String tel51 = cleanTel.startsWith("51") ? cleanTel : (cleanTel.length() == 9 ? "51" + cleanTel : cleanTel);
            String tel9 = cleanTel.startsWith("51") && cleanTel.length() == 11 ? cleanTel.substring(2) : cleanTel;

            List<Pedido> pedidos = pedidoRepository.findAll().stream()
                .filter(p -> {
                    String pTel1 = (p.getUsuario() != null && p.getUsuario().getTelefono() != null) ? p.getUsuario().getTelefono().replaceAll("[^0-9]", "") : "";
                    String pTel2 = p.getTelefonoContacto() != null ? p.getTelefonoContacto().replaceAll("[^0-9]", "") : "";
                    return (!cleanTel.isEmpty() && (pTel1.equals(cleanTel) || pTel2.equals(cleanTel)))
                        || (!tel51.isEmpty() && (pTel1.equals(tel51) || pTel2.equals(tel51)))
                        || (!tel9.isEmpty() && (pTel1.equals(tel9) || pTel2.equals(tel9)));
                })
                .collect(java.util.stream.Collectors.toList());

            return new ResponseEntity<>(mapPedidosToResponse(pedidos), HttpStatus.OK);
        } catch (Exception e) {
            System.err.println("[AdminPedidoController] Error al obtener pedidos de cliente " + telefono + ": " + e.getMessage());
            e.printStackTrace();
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
        }
    }

    @GetMapping("/rastreo/{telefono}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> consultarRastreoPublico(@PathVariable String telefono) {
        try {
            ResponseEntity<List<Map<String, Object>>> todosResp = obtenerPedidosDeCliente(telefono);
            List<Map<String, Object>> todos = todosResp != null ? todosResp.getBody() : null;
            if (todos == null || todos.isEmpty()) {
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }

            java.time.LocalDateTime ahora = java.time.LocalDateTime.now();

            List<Map<String, Object>> activos = todos.stream().filter(p -> {
                String est = (String) p.get("estado");
                String estRaw = (String) p.get("estadoRaw");
                boolean esEnCamino = "En camino".equalsIgnoreCase(est) || "EN_CAMINO".equalsIgnoreCase(est) || "EN_CAMINO".equalsIgnoreCase(estRaw);
                String guia = (String) p.get("numeroGuia");
                if (guia == null || guia.trim().isEmpty()) {
                    guia = (String) p.get("guia");
                }
                String agencia = (String) p.get("agenciaEnvio");
                if (agencia == null || agencia.trim().isEmpty()) {
                    agencia = (String) p.get("agencia");
                }
                boolean tieneGuiaYAgencia = (agencia != null && !agencia.trim().isEmpty()) && (guia != null && !guia.trim().isEmpty());

                if (!esEnCamino && !tieneGuiaYAgencia) return false;
                if ("Entregado".equalsIgnoreCase(est) || "ENTREGADO".equalsIgnoreCase(est) || "ENTREGADO".equalsIgnoreCase(estRaw)) return false;
                if ("Rechazado".equalsIgnoreCase(est) || "RECHAZADO".equalsIgnoreCase(est) || "Cancelado".equalsIgnoreCase(est)) return false;

                Object expObj = p.get("fechaExpiracionRastreo");
                if (expObj != null) {
                    try {
                        String expStr = expObj.toString().trim();
                        if (!expStr.isEmpty()) {
                            java.time.LocalDateTime expDate = java.time.LocalDateTime.parse(expStr);
                            if (expDate.isBefore(ahora)) {
                                return false;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                return true;
            }).collect(java.util.stream.Collectors.toList());

            return ResponseEntity.ok(activos);
        } catch (Exception e) {
            System.err.println("[AdminPedidoController] Error en consultarRastreoPublico: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
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
        if (p == null) return "UNKNOWN";
        try {
            if (p.getUsuario() != null && p.getUsuario().getId() != null) {
                return "U_" + p.getUsuario().getId();
            }
        } catch (Exception ignored) {}
        if (p.getTelefonoContacto() != null && !p.getTelefonoContacto().trim().isEmpty()) {
            return "T_" + p.getTelefonoContacto().replaceAll("[^\\d]", "");
        }
        try {
            if (p.getUsuario() != null && p.getUsuario().getTelefono() != null && !p.getUsuario().getTelefono().trim().isEmpty()) {
                return "T_" + p.getUsuario().getTelefono().replaceAll("[^\\d]", "");
            }
        } catch (Exception ignored) {}
        if (p.getNombreCompleto() != null && !p.getNombreCompleto().trim().isEmpty()) {
            return "N_" + p.getNombreCompleto().trim().toLowerCase();
        }
        return "P_" + (p.getId() != null ? p.getId() : "0");
    }

    private java.time.LocalDateTime parseFlexibleDateTime(Object val) {
        if (val == null) return null;
        String str = val.toString().trim();
        if (str.isEmpty()) return null;
        try {
            return java.time.LocalDateTime.parse(str);
        } catch (Exception e1) {
            try {
                return java.time.Instant.parse(str).atZone(java.time.ZoneId.of("America/Lima")).toLocalDateTime();
            } catch (Exception e2) {
                try {
                    return java.time.OffsetDateTime.parse(str).atZoneSameInstant(java.time.ZoneId.of("America/Lima")).toLocalDateTime();
                } catch (Exception e3) {
                    return null;
                }
            }
        }
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
            if (telefonoCliente != null && !telefonoCliente.trim().isEmpty()) {
                String cleanTel = telefonoCliente.replaceAll("[^0-9]", "");
                String tel51 = cleanTel.startsWith("51") ? cleanTel : (cleanTel.length() == 9 ? "51" + cleanTel : cleanTel);
                java.util.Optional<com.upc.Entity.Chat> chatOpt = chatRepository.findByTelefonoCliente(tel51);
                if (chatOpt.isEmpty()) {
                    chatOpt = chatRepository.findByTelefonoCliente(cleanTel);
                }
                chatOpt.ifPresent(chat -> {
                    chat.setContextoInmediato(null);
                    chat.setFasePedido("INICIAL");
                    chat.setPedidoReferenciadoId(null);
                    chat.setPedidoIdentificado(false);
                    chat.setDireccionDetectada(false);
                    chat.setDatosCompletos(false);
                    chat.setSugerenciaIa(null);
                    chat.setFechaCorte(java.time.LocalDateTime.now(java.time.ZoneId.of("America/Lima")));
                    chatRepository.save(chat);

                    // Registro de aviso de sistema en el hilo del chat del cliente (CP051 / HU0026)
                    try {
                        com.upc.Entity.Mensaje avisoSistema = new com.upc.Entity.Mensaje();
                        avisoSistema.setChat(chat);
                        avisoSistema.setContenido("[Sistema]: Pedido pendiente eliminado. Conversación restablecida al estado INICIAL.");
                        avisoSistema.setRemitente("ADMINISTRADOR");
                        avisoSistema.setFechaEnvio(java.time.LocalDateTime.now(java.time.ZoneId.of("America/Lima")));
                        mensajeRepository.save(avisoSistema);
                    } catch (Exception e) {
                        System.err.println("No se pudo registrar aviso de sistema en el chat: " + e.getMessage());
                    }

                    System.out.println("[AUDITORÍA RESETEADO] Chat de " + tel51 + " reseteado automáticamente a INICIAL con fechaCorte y aviso tras eliminar el pedido ID: " + pedidoId);
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
            if (p == null) continue;
            String est = p.getEstado();
            boolean esValidado = "VALIDADO".equalsIgnoreCase(est);
            boolean esEnCamino = "EN_CAMINO".equalsIgnoreCase(est) || "EN CAMINO".equalsIgnoreCase(est);
            boolean esEntregado = "ENTREGADO".equalsIgnoreCase(est);
            boolean esRechazado = "RECHAZADO".equalsIgnoreCase(est);
            boolean esEnRevision = "EN REVISION".equalsIgnoreCase(est) || "EN_REVISION".equalsIgnoreCase(est) || "EN REVISIÓN".equalsIgnoreCase(est);
            if (!esValidado && !esEnCamino && !esEntregado && !esRechazado && !esEnRevision) {
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
            if (p == null) continue;
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getNumeroOrden() != null ? p.getNumeroOrden() : "#ORD-00" + p.getId());
            map.put("dbId", p.getId());
            String clienteNom = "Cliente WhatsApp";
            try {
                if (p.getNombreCompleto() != null && !p.getNombreCompleto().trim().isEmpty()) {
                    clienteNom = p.getNombreCompleto();
                } else if (p.getUsuario() != null && p.getUsuario().getNombre() != null) {
                    clienteNom = p.getUsuario().getNombre();
                }
            } catch (Exception ignored) {}
            map.put("cliente", clienteNom);

            map.put("dni", p.getDni() != null ? p.getDni() : "");

            String telContacto = "";
            try {
                if (p.getTelefonoContacto() != null && !p.getTelefonoContacto().trim().isEmpty()) {
                    telContacto = p.getTelefonoContacto();
                } else if (p.getUsuario() != null && p.getUsuario().getTelefono() != null) {
                    telContacto = p.getUsuario().getTelefono();
                }
            } catch (Exception ignored) {}
            map.put("telefono", telContacto);
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
            } else if ("EN_CAMINO".equalsIgnoreCase(estRaw) || "EN CAMINO".equalsIgnoreCase(estRaw)) {
                est = "En camino";
            } else if ("ENTREGADO".equalsIgnoreCase(estRaw)) {
                est = "Entregado";
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
            map.put("agencia", p.getAgenciaEnvio() != null ? p.getAgenciaEnvio() : "");
            map.put("agenciaEnvio", p.getAgenciaEnvio() != null ? p.getAgenciaEnvio() : "");
            map.put("numeroGuia", p.getNumeroGuia() != null ? p.getNumeroGuia() : "");
            map.put("guia", p.getNumeroGuia() != null ? p.getNumeroGuia() : "");
            map.put("fechaDespacho", p.getFechaDespacho() != null ? p.getFechaDespacho().toString() : "");
            map.put("fechaExpiracionRastreo", p.getFechaExpiracionRastreo() != null ? p.getFechaExpiracionRastreo().toString() : "");
            
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

    @Autowired
    private com.upc.Repository.MensajeRepository mensajeRepository;

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
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> actualizarPedido(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return buscarPedidoPorIdFlexible(id).map(pedido -> {
            if (body.containsKey("estado")) {
                String est = (String) body.get("estado");
                String nuevoEst = null;
                if ("Validado".equalsIgnoreCase(est) || "VALIDADO".equalsIgnoreCase(est)) nuevoEst = "VALIDADO";
                else if ("En camino".equalsIgnoreCase(est) || "EN_CAMINO".equalsIgnoreCase(est) || "EN CAMINO".equalsIgnoreCase(est)) nuevoEst = "EN_CAMINO";
                else if ("Entregado".equalsIgnoreCase(est) || "ENTREGADO".equalsIgnoreCase(est)) nuevoEst = "ENTREGADO";
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
            if (body.containsKey("agencia") || body.containsKey("agenciaEnvio")) {
                String ag = (String) (body.containsKey("agencia") ? body.get("agencia") : body.get("agenciaEnvio"));
                pedido.setAgenciaEnvio(ag);
            }
            if (body.containsKey("numeroGuia") || body.containsKey("guia")) {
                String ng = (String) (body.containsKey("numeroGuia") ? body.get("numeroGuia") : body.get("guia"));
                pedido.setNumeroGuia(ng);
            }
            if (body.containsKey("fechaDespacho")) {
                java.time.LocalDateTime fd = parseFlexibleDateTime(body.get("fechaDespacho"));
                pedido.setFechaDespacho(fd != null ? fd : java.time.LocalDateTime.now(java.time.ZoneId.of("America/Lima")));
            }
            if (body.containsKey("fechaExpiracionRastreo")) {
                java.time.LocalDateTime fe = parseFlexibleDateTime(body.get("fechaExpiracionRastreo"));
                if (fe != null) {
                    pedido.setFechaExpiracionRastreo(fe);
                } else {
                    pedido.setFechaExpiracionRastreo(java.time.LocalDateTime.now(java.time.ZoneId.of("America/Lima")).plusDays(5));
                }
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
                    } else if ("EN_CAMINO".equalsIgnoreCase(guardado.getEstado()) || "EN CAMINO".equalsIgnoreCase(guardado.getEstado())) {
                        String tracking = (guardado.getNumeroGuia() != null && !guardado.getNumeroGuia().trim().isEmpty() && !"DIRECTO".equalsIgnoreCase(guardado.getNumeroGuia()))
                                ? (" Tu código de seguimiento es: " + guardado.getNumeroGuia() + " (" + (guardado.getAgenciaEnvio() != null ? guardado.getAgenciaEnvio() : "Envío") + ").")
                                : (" Tu entrega está siendo coordinada por " + (guardado.getAgenciaEnvio() != null ? guardado.getAgenciaEnvio() : "nuestro motorizado") + ".");
                        nuevaSugerencia = "¡Hola " + chat.getNombreCliente() + "! Tu pedido ya se encuentra en camino. 🚚" + tracking + " Puedes consultar el estado en nuestra sección de rastreo en la web. ¡Muchas gracias por tu preferencia! ❤️";
                    } else if ("ENTREGADO".equalsIgnoreCase(guardado.getEstado())) {
                        nuevaSugerencia = "¡Hola " + chat.getNombreCliente() + "! Tu pedido figura como entregado. Esperamos que disfrutes tus productos. ¡Fue un placer atenderte! ✨";
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

            Map<String, Object> resp = mapPedidosToResponse(java.util.Collections.singletonList(guardado)).get(0);
            resp.put("success", true);
            resp.put("mensaje", "Pedido actualizado correctamente");
            resp.put("estado", guardado.getEstado());
            resp.put("agenciaEnvio", guardado.getAgenciaEnvio() != null ? guardado.getAgenciaEnvio() : "");
            resp.put("numeroGuia", guardado.getNumeroGuia() != null ? guardado.getNumeroGuia() : "");
            resp.put("fechaDespacho", guardado.getFechaDespacho() != null ? guardado.getFechaDespacho().toString() : "");
            resp.put("fechaExpiracionRastreo", guardado.getFechaExpiracionRastreo() != null ? guardado.getFechaExpiracionRastreo().toString() : "");
            return new ResponseEntity<>(resp, HttpStatus.OK);
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
