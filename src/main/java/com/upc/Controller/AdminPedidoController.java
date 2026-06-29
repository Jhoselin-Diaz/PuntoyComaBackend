package com.upc.Controller;

import com.upc.Entity.Pedido;
import com.upc.Entity.PedidoDetalle;
import com.upc.Repository.PedidoRepository;
import com.upc.Repository.PedidoDetalleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/pedidos")
@CrossOrigin(origins = "http://localhost:4200")
public class AdminPedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoDetalleRepository pedidoDetalleRepository;

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

    @DeleteMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> eliminarPedido(@PathVariable Long id) {
        // Eliminar detalles vinculados primero
        pedidoDetalleRepository.deleteByPedidoId(id);
        pedidoRepository.deleteById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    private List<Map<String, Object>> mapPedidosToResponse(List<Pedido> pedidos) {
        List<Map<String, Object>> response = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

        for (Pedido p : pedidos) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getNumeroOrden() != null ? p.getNumeroOrden() : "#ORD-00" + p.getId());
            map.put("dbId", p.getId());
            map.put("cliente", p.getNombreCompleto() != null ? p.getNombreCompleto() : 
                     (p.getUsuario() != null ? p.getUsuario().getNombre() : "Cliente WhatsApp"));
            map.put("telefono", p.getTelefonoContacto() != null ? p.getTelefonoContacto() : 
                     (p.getUsuario() != null ? p.getUsuario().getTelefono() : ""));
            map.put("direccion", p.getDireccionEntrega() != null ? p.getDireccionEntrega() : "");
            map.put("direccionEnvio", p.getDireccionEntrega() != null ? p.getDireccionEntrega() : "");
            map.put("direccionEntrega", p.getDireccionEntrega() != null ? p.getDireccionEntrega() : "");
            System.out.println("Direccion enviada al frontend: " + p.getDireccionEntrega());
            
            // Variantes o productos
            String productoResumen = p.getVariantesEspecificas() != null ? p.getVariantesEspecificas() : "";
            if (productoResumen.isEmpty()) {
                List<PedidoDetalle> dets = pedidoDetalleRepository.findByPedidoId(p.getId());
                if (dets != null && !dets.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < dets.size(); i++) {
                        PedidoDetalle d = dets.get(i);
                        sb.append(d.getProducto().getNombre()).append(" x").append(d.getCantidad());
                        if (i < dets.size() - 1) sb.append(", ");
                    }
                    productoResumen = sb.toString();
                } else {
                    productoResumen = "Pedido en validación";
                }
            }
            map.put("producto", productoResumen);
            
            // Validar estado para que coincida con el tipado del front
            String est = p.getEstado();
            if (est == null) est = "Pendiente";
            if ("PENDIENTE".equalsIgnoreCase(est)) est = "Pendiente";
            else if ("VALIDADO".equalsIgnoreCase(est)) est = "Validado";
            else if ("RECHAZADO".equalsIgnoreCase(est)) est = "Rechazado";
            else if ("EN REVISION".equalsIgnoreCase(est) || "EN_REVISION".equalsIgnoreCase(est)) est = "En revision";
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
                    pd.put("nombre", d.getProducto().getNombre());
                    pd.put("cantidad", d.getCantidad());
                    pd.put("precio", d.getPrecioUnitario());
                    prodDetalles.add(pd);
                }
            }
            map.put("productosDetalle", prodDetalles);

            response.add(map);
        }
        return response;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Pedido> actualizarPedido(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return pedidoRepository.findById(id).map(pedido -> {
            if (body.containsKey("estado")) {
                String est = (String) body.get("estado");
                if ("Validado".equalsIgnoreCase(est)) pedido.setEstado("VALIDADO");
                else if ("Rechazado".equalsIgnoreCase(est)) pedido.setEstado("RECHAZADO");
                else if ("Pendiente".equalsIgnoreCase(est)) pedido.setEstado("PENDIENTE");
                else if ("En revision".equalsIgnoreCase(est) || "En revisión".equalsIgnoreCase(est)) pedido.setEstado("EN REVISION");
            }
            if (body.containsKey("validacion")) {
                pedido.setResultadoConciliacion((String) body.get("validacion"));
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
            if (pedido.getTotal() != null && pedido.getMontoDetectado() != null) {
                pedido.setDiferencia(pedido.getTotal() - pedido.getMontoDetectado());
            }
            
            Pedido guardado = pedidoRepository.save(pedido);
            return new ResponseEntity<>(guardado, HttpStatus.OK);
        }).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @Autowired
    private com.upc.Service.OpenAiService openAiService;

    @PostMapping("/{pedidoId}/solicitar-monto-faltante")
    public ResponseEntity<Map<String, String>> solicitarMontoFaltante(@PathVariable Long pedidoId) {
        String mensaje = openAiService.generarSugerenciaMontoFaltante(pedidoId);
        Map<String, String> response = new HashMap<>();
        response.put("mensaje", mensaje);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
