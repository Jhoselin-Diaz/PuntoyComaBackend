package com.upc.Controller;

import com.upc.Entity.Pedido;
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
@CrossOrigin(origins = "*")
public class PublicRastreoController {

    @Autowired
    private PedidoRepository pedidoRepository;

    @GetMapping("/{telefono}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> consultarRastreoPublico(@PathVariable String telefono) {
        try {
            if (telefono == null || telefono.trim().isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            String cleanTel = telefono.replaceAll("[^0-9]", "");
            if (cleanTel.length() < 7) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            String tel51 = cleanTel.startsWith("51") ? cleanTel : (cleanTel.length() == 9 ? "51" + cleanTel : cleanTel);
            String tel9 = cleanTel.startsWith("51") && cleanTel.length() == 11 ? cleanTel.substring(2) : cleanTel;

            LocalDateTime ahora = LocalDateTime.now();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

            List<Pedido> pedidos = pedidoRepository.findAll().stream()
                .filter(p -> {
                    if (p == null) return false;
                    String pTel1 = (p.getUsuario() != null && p.getUsuario().getTelefono() != null) ? p.getUsuario().getTelefono().replaceAll("[^0-9]", "") : "";
                    String pTel2 = p.getTelefonoContacto() != null ? p.getTelefonoContacto().replaceAll("[^0-9]", "") : "";
                    return (!cleanTel.isEmpty() && (pTel1.equals(cleanTel) || pTel2.equals(cleanTel)))
                        || (!tel51.isEmpty() && (pTel1.equals(tel51) || pTel2.equals(tel51)))
                        || (!tel9.isEmpty() && (pTel1.equals(tel9) || pTel2.equals(tel9)));
                })
                .filter(p -> {
                    String est = (p.getEstado() != null ? p.getEstado().trim().toUpperCase() : "");
                    boolean esEnCamino = "EN_CAMINO".equals(est) || "EN CAMINO".equals(est);
                    String guia = p.getNumeroGuia();
                    String agencia = p.getAgenciaEnvio();
                    boolean tieneGuiaYAgencia = (agencia != null && !agencia.trim().isEmpty()) && (guia != null && !guia.trim().isEmpty());

                    if (!esEnCamino && !tieneGuiaYAgencia) return false;
                    if ("ENTREGADO".equals(est) || "RECHAZADO".equals(est) || "CANCELADO".equals(est)) return false;

                    if (p.getFechaExpiracionRastreo() != null && p.getFechaExpiracionRastreo().isBefore(ahora)) {
                        return false;
                    }
                    return true;
                })
                .sorted((p1, p2) -> {
                    if (p1.getFecha() != null && p2.getFecha() != null) return p2.getFecha().compareTo(p1.getFecha());
                    if (p1.getId() != null && p2.getId() != null) return p2.getId().compareTo(p1.getId());
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
                item.put("total", "S/ " + String.format(Locale.US, "%.2f", p.getTotal() != null ? p.getTotal() : 0.0));

                String clienteNom = "Cliente";
                if (p.getNombreCompleto() != null && !p.getNombreCompleto().trim().isEmpty()) {
                    clienteNom = p.getNombreCompleto();
                } else if (p.getUsuario() != null && p.getUsuario().getNombre() != null) {
                    clienteNom = p.getUsuario().getNombre();
                }
                item.put("cliente", clienteNom);
                item.put("nombreCompleto", clienteNom);

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

                String prod = p.getVariantesEspecificas() != null ? p.getVariantesEspecificas() : "Productos seleccionados";
                item.put("producto", prod);
                item.put("productos", prod);

                respuesta.add(item);
            }

            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            System.err.println("[PublicRastreoController] Error en consultarRastreoPublico: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
}
