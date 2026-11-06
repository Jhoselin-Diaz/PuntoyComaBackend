package com.upc.Controller;

import com.upc.Entity.Pedido;
import com.upc.Entity.PedidoDetalle;
import com.upc.Repository.PedidoDetalleRepository;
import com.upc.Repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/rastreo")
@CrossOrigin(origins = {"http://localhost:4200", "https://puntoycoma-store.netlify.app", "https://puntoycoma-store.netlify.app/"})
public class PublicRastreoController {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoDetalleRepository pedidoDetalleRepository;

    @GetMapping("/{telefono}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> consultarRastreoPublico(@PathVariable String telefono) {
        if (telefono == null || telefono.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        String cleanTel = telefono.replaceAll("[^0-9]", "");
        if (cleanTel.length() < 7) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        // Normalización y variantes de teléfono con y sin prefijo 51
        String tel51 = cleanTel.startsWith("51") ? cleanTel : (cleanTel.length() == 9 ? "51" + cleanTel : cleanTel);
        String tel9 = (cleanTel.startsWith("51") && cleanTel.length() == 11) ? cleanTel.substring(2) : cleanTel;

        LocalDateTime ahora = LocalDateTime.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

        List<Pedido> pedidos = pedidoRepository.findAll().stream()
                .filter(p -> {
                    if (p == null) return false;

                    // 1. Verificación de coincidencia telefónica
                    String pTel1 = (p.getUsuario() != null && p.getUsuario().getTelefono() != null)
                            ? p.getUsuario().getTelefono().replaceAll("[^0-9]", "") : "";
                    String pTel2 = p.getTelefonoContacto() != null
                            ? p.getTelefonoContacto().replaceAll("[^0-9]", "") : "";

                    boolean coincide = (!cleanTel.isEmpty() && (pTel1.equals(cleanTel) || pTel2.equals(cleanTel)))
                            || (!tel51.isEmpty() && (pTel1.equals(tel51) || pTel2.equals(tel51)))
                            || (!tel9.isEmpty() && (pTel1.equals(tel9) || pTel2.equals(tel9)));

                    if (!coincide) return false;

                    // 2. Filtro de estado: "EN_CAMINO" o tener agencia y guía asignadas
                    String est = (p.getEstado() != null ? p.getEstado().trim().toUpperCase() : "");
                    boolean esEnCamino = "EN_CAMINO".equals(est) || "EN CAMINO".equals(est);
                    boolean tieneGuiaYAgencia = (p.getAgenciaEnvio() != null && !p.getAgenciaEnvio().trim().isEmpty())
                            && (p.getNumeroGuia() != null && !p.getNumeroGuia().trim().isEmpty());

                    if (!esEnCamino && !tieneGuiaYAgencia) return false;

                    // Si ya está ENTREGADO o CANCELADO/RECHAZADO, no debe figurar en rastreo activo
                    if ("ENTREGADO".equals(est) || "RECHAZADO".equals(est) || "CANCELADO".equals(est)) {
                        return false;
                    }

                    // 3. Filtro de vigencia: fechaExpiracionRastreo posterior a ahora (o nula)
                    if (p.getFechaExpiracionRastreo() != null && p.getFechaExpiracionRastreo().isBefore(ahora)) {
                        return false;
                    }

                    return true;
                })
                .sorted((p1, p2) -> {
                    // Más recientes primero
                    if (p1.getFecha() != null && p2.getFecha() != null) {
                        return p2.getFecha().compareTo(p1.getFecha());
                    }
                    if (p1.getId() != null && p2.getId() != null) {
                        return p2.getId().compareTo(p1.getId());
                    }
                    return 0;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> respuesta = new ArrayList<>();
        for (Pedido p : pedidos) {
            Map<String, Object> item = new HashMap<>();
            String idOrden = p.getNumeroOrden() != null ? p.getNumeroOrden() : "#ORD-00" + p.getId();
            item.put("id", idOrden);
            item.put("numeroOrden", idOrden);
            item.put("dbId", p.getId());
            item.put("estado", "En camino");
            item.put("estadoRaw", p.getEstado());
            item.put("agenciaEnvio", p.getAgenciaEnvio() != null ? p.getAgenciaEnvio() : "SHALOM");
            item.put("agencia", p.getAgenciaEnvio() != null ? p.getAgenciaEnvio() : "SHALOM");
            item.put("numeroGuia", p.getNumeroGuia() != null ? p.getNumeroGuia() : "");
            item.put("guia", p.getNumeroGuia() != null ? p.getNumeroGuia() : "");
            item.put("numeroSeguimiento", p.getNumeroGuia() != null ? p.getNumeroGuia() : "");
            item.put("fechaDespacho", p.getFechaDespacho() != null ? p.getFechaDespacho().toString() : "");
            item.put("fechaExpiracionRastreo", p.getFechaExpiracionRastreo() != null ? p.getFechaExpiracionRastreo().toString() : "");
            item.put("total", "S/ " + String.format("%.2f", p.getTotal() != null ? p.getTotal() : 0.0));

            String clienteNom = "Cliente";
            if (p.getNombreCompleto() != null && !p.getNombreCompleto().trim().isEmpty()) {
                clienteNom = p.getNombreCompleto();
            } else if (p.getUsuario() != null && p.getUsuario().getNombre() != null) {
                clienteNom = p.getUsuario().getNombre();
            }
            item.put("nombreCompleto", clienteNom);
            item.put("cliente", clienteNom);

            String telContacto = "";
            if (p.getTelefonoContacto() != null && !p.getTelefonoContacto().trim().isEmpty()) {
                telContacto = p.getTelefonoContacto();
            } else if (p.getUsuario() != null && p.getUsuario().getTelefono() != null) {
                telContacto = p.getUsuario().getTelefono();
            }
            item.put("telefono", telContacto);

            item.put("direccion", p.getDireccionEntrega() != null ? p.getDireccionEntrega() : "");
            item.put("direccionEnvio", p.getDireccionEntrega() != null ? p.getDireccionEntrega() : "");

            if (p.getFecha() != null) {
                item.put("fecha", p.getFecha().format(dateFormatter));
                item.put("hora", p.getFecha().format(timeFormatter).toUpperCase());
            } else {
                item.put("fecha", "Reciente");
                item.put("hora", "");
            }

            // Resumen de productos
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
                    productoResumen = "Productos seleccionados";
                }
            }
            item.put("producto", productoResumen);
            item.put("productos", productoResumen);

            respuesta.add(item);
        }

        return ResponseEntity.ok(respuesta);
    }
}

