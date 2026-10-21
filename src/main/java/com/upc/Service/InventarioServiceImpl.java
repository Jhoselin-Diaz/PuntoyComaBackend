package com.upc.Service;

import com.upc.Entity.InventarioMovimiento;
import com.upc.Entity.Pedido;
import com.upc.Entity.PedidoDetalle;
import com.upc.Entity.Producto;
import com.upc.Repository.InventarioMovimientoRepository;
import com.upc.Repository.PedidoDetalleRepository;
import com.upc.Repository.PedidoRepository;
import com.upc.Repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.upc.Util.StockInsuficienteException;

@Service
public class InventarioServiceImpl implements InventarioService {

    private static final Logger log = LoggerFactory.getLogger(InventarioServiceImpl.class);

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PedidoDetalleRepository pedidoDetalleRepository;

    @Autowired
    private InventarioMovimientoRepository inventarioMovimientoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Override
    @Transactional
    public void procesarTransicionEstadoPedido(Pedido pedido, String estadoAnteriorRaw, String nuevoEstadoRaw) {
        if (pedido == null || nuevoEstadoRaw == null) {
            return;
        }

        String estadoAnterior = normalizarEstado(estadoAnteriorRaw != null ? estadoAnteriorRaw : pedido.getEstado());
        String nuevoEstado = normalizarEstado(nuevoEstadoRaw);
        boolean yaDescontado = Boolean.TRUE.equals(pedido.getStockDescontado());

        log.info("[InventarioService] Procesando pedido ID: {} ({}) | Estado anterior: {} | Nuevo estado: {} | Stock descontado previa: {}",
                pedido.getId(), pedido.getNumeroOrden(), estadoAnterior, nuevoEstado, yaDescontado);

        // TRANSICIÓN 1: Hacia VALIDADO (y aún no descontado) -> DESCONTAR STOCK
        if ("VALIDADO".equalsIgnoreCase(nuevoEstado)) {
            if (!yaDescontado) {
                descontarStockPorPedido(pedido);
            } else {
                log.info("[InventarioService] Idempotencia: El pedido ID {} ya tiene el stock descontado. Se ignora solicitud duplicada de descuento.", pedido.getId());
            }
        }
        // TRANSICIÓN 2: Hacia RECHAZADO (y con stock descontado previa) -> DEVOLVER STOCK
        else if ("RECHAZADO".equalsIgnoreCase(nuevoEstado)) {
            if (yaDescontado) {
                devolverStockPorPedido(pedido);
            } else {
                log.info("[InventarioService] Idempotencia: El pedido ID {} nunca tuvo stock descontado. No se realiza devolución.", pedido.getId());
            }
        }
        // TRANSICIÓN 3: Hacia EN REVISION o PENDIENTE -> MANTENER ESTADO Y NO TOCAR STOCK
        else {
            log.info("[InventarioService] Transición a '{}' no modifica stock para el pedido ID {}. Se mantiene stockDescontado={}.",
                    nuevoEstado, pedido.getId(), yaDescontado);
        }
    }

    private void descontarStockPorPedido(Pedido pedido) {
        List<PedidoDetalle> detalles = pedidoDetalleRepository.findByPedidoId(pedido.getId());
        if (detalles == null || detalles.isEmpty()) {
            log.warn("[InventarioService] El pedido ID: {} no tiene detalles de productos asociados. No se modifica stock.", pedido.getId());
            return;
        }

        // 1. FASE DE AUDITORÍA Y PRE-VALIDACIÓN DE STOCK (Atómico)
        List<Map<String, Object>> productosSinStock = new ArrayList<>();
        StringBuilder errorMsgBuilder = new StringBuilder();

        for (PedidoDetalle detalle : detalles) {
            Producto producto = detalle.getProducto();
            if (producto == null) continue;
            int cantidadRequerida = detalle.getCantidad() != null ? detalle.getCantidad() : 0;
            if (cantidadRequerida <= 0) continue;

            int stockDisponible = producto.getStock() != null ? producto.getStock() : 0;
            if (stockDisponible < cantidadRequerida) {
                Map<String, Object> item = new HashMap<>();
                item.put("producto", producto.getNombre());
                item.put("productoId", producto.getId());
                item.put("stockDisponible", stockDisponible);
                item.put("cantidadRequerida", cantidadRequerida);
                productosSinStock.add(item);

                if (errorMsgBuilder.length() > 0) errorMsgBuilder.append("; ");
                errorMsgBuilder.append(String.format("'%s' (ID: %d): Stock disp. %d, Req. %d",
                        producto.getNombre(), producto.getId(), stockDisponible, cantidadRequerida));
            }
        }

        if (!productosSinStock.isEmpty()) {
            Map<String, Object> primer = productosSinStock.get(0);
            String nombreProd = (String) primer.get("producto");
            int stockDisp = (int) primer.get("stockDisponible");
            String msg;
            if (productosSinStock.size() == 1) {
                msg = String.format("No se puede validar el pedido: el producto '%s' no tiene stock suficiente (Stock disponible: %d).", nombreProd, stockDisp);
            } else {
                msg = String.format("No se puede validar el pedido: los productos solicitados no tienen stock suficiente (%s).", errorMsgBuilder.toString());
            }
            log.error("[InventarioService] {}", msg);
            throw new StockInsuficienteException(msg, productosSinStock);
        }

        // 2. FASE DE EJECUCIÓN Y REGISTRO DE MOVIMIENTOS EN HISTORIAL DE INVENTARIO
        for (PedidoDetalle detalle : detalles) {
            Producto producto = detalle.getProducto();
            if (producto == null) continue;
            int cantidadRequerida = detalle.getCantidad() != null ? detalle.getCantidad() : 0;
            if (cantidadRequerida <= 0) continue;

            int stockPrevio = producto.getStock() != null ? producto.getStock() : 0;
            int nuevoStock = stockPrevio - cantidadRequerida;
            producto.setStock(nuevoStock);
            productoRepository.save(producto);

            // Registrar movimiento de inventario (SALIDA) asociado al pedido
            InventarioMovimiento movimiento = new InventarioMovimiento();
            movimiento.setProducto(producto);
            movimiento.setPedido(pedido);
            movimiento.setCantidad(-cantidadRequerida);
            movimiento.setTipoMovimiento("SALIDA");
            movimiento.setProveedor("Sistema Pedidos");
            movimiento.setFecha(LocalDateTime.now());
            movimiento.setNotas("Salida por pedido validado #" + (pedido.getNumeroOrden() != null ? pedido.getNumeroOrden() : pedido.getId()));
            inventarioMovimientoRepository.save(movimiento);

            log.info("[InventarioService] Stock descontado para producto '{}' (ID: {}): {} -> {}. Movimiento SALIDA (Pedido ID: {}) registrado.",
                    producto.getNombre(), producto.getId(), stockPrevio, nuevoStock, pedido.getId());
        }

        // Marcar el pedido como descontado para asegurar idempotencia
        pedido.setStockDescontado(true);
        pedidoRepository.save(pedido);
    }

    @Override
    @Transactional
    public void devolverStockPorPedido(Pedido pedido) {
        if (pedido == null) return;

        // FUENTE DE VERDAD: Buscar movimientos de SALIDA asociados directamente al pedido
        List<InventarioMovimiento> salidasHist = inventarioMovimientoRepository.findByPedidoIdAndTipoMovimiento(pedido.getId(), "SALIDA");
        List<InventarioMovimiento> entradasHist = inventarioMovimientoRepository.findByPedidoIdAndTipoMovimiento(pedido.getId(), "ENTRADA");

        // Si ya existen ENTRADAS registradas que igualan las SALIDAS, ya fue devuelto.
        int totalSalidas = salidasHist.stream().mapToInt(m -> Math.abs(m.getCantidad() != null ? m.getCantidad() : 0)).sum();
        int totalEntradas = entradasHist.stream().mapToInt(m -> Math.abs(m.getCantidad() != null ? m.getCantidad() : 0)).sum();

        if (totalSalidas > 0 && totalEntradas >= totalSalidas) {
            log.info("[InventarioService] Devolución idempotente: El pedido ID {} ya cuenta con movimientos de ENTRADA por la totalidad del stock devuelto (Salidas: {}, Entradas: {}).",
                    pedido.getId(), totalSalidas, totalEntradas);
            pedido.setStockDescontado(false);
            pedidoRepository.save(pedido);
            return;
        }

        if (!salidasHist.isEmpty()) {
            // Devolución basada en los movimientos de SALIDA reales del pedido
            Map<Long, Integer> netosADevolver = new HashMap<>();
            for (InventarioMovimiento salida : salidasHist) {
                if (salida.getProducto() == null) continue;
                Long prodId = salida.getProducto().getId();
                int cantSalida = Math.abs(salida.getCantidad() != null ? salida.getCantidad() : 0);
                netosADevolver.put(prodId, netosADevolver.getOrDefault(prodId, 0) + cantSalida);
            }

            // Restar entradas previas para este producto si las hubiera
            for (InventarioMovimiento entrada : entradasHist) {
                if (entrada.getProducto() == null) continue;
                Long prodId = entrada.getProducto().getId();
                int cantEntrada = Math.abs(entrada.getCantidad() != null ? entrada.getCantidad() : 0);
                int actual = netosADevolver.getOrDefault(prodId, 0);
                netosADevolver.put(prodId, Math.max(0, actual - cantEntrada));
            }

            for (Map.Entry<Long, Integer> entry : netosADevolver.entrySet()) {
                Long prodId = entry.getKey();
                int cantidadDevolver = entry.getValue();
                if (cantidadDevolver <= 0) continue;

                Producto producto = productoRepository.findById(prodId).orElse(null);
                if (producto == null) continue;

                int stockPrevio = producto.getStock() != null ? producto.getStock() : 0;
                int nuevoStock = stockPrevio + cantidadDevolver;
                producto.setStock(nuevoStock);
                productoRepository.save(producto);

                // Registrar movimiento de inventario (ENTRADA)
                InventarioMovimiento movimiento = new InventarioMovimiento();
                movimiento.setProducto(producto);
                movimiento.setPedido(pedido);
                movimiento.setCantidad(cantidadDevolver);
                movimiento.setTipoMovimiento("ENTRADA");
                movimiento.setProveedor("Sistema Pedidos");
                movimiento.setFecha(LocalDateTime.now());
                movimiento.setNotas("Devolución por pedido rechazado/cancelado #" + (pedido.getNumeroOrden() != null ? pedido.getNumeroOrden() : pedido.getId()));
                inventarioMovimientoRepository.save(movimiento);

                log.info("[InventarioService] Stock devuelto (vía SALIDA real) para producto '{}' (ID: {}): {} -> {}. ENTRADA registrada.",
                        producto.getNombre(), producto.getId(), stockPrevio, nuevoStock);
            }
        } else {
            // FALLBACK CONTROLADO: Si NO existen registros SALIDA en la BD pero el pedido estaba marcado como descontado
            if (Boolean.TRUE.equals(pedido.getStockDescontado())) {
                log.warn("[InventarioService] Fallback: No se encontraron movimientos SALIDA para pedido ID: {}. Se intenta devolución con PedidoDetalle.", pedido.getId());
                List<PedidoDetalle> detalles = pedidoDetalleRepository.findByPedidoId(pedido.getId());
                if (detalles != null && !detalles.isEmpty()) {
                    for (PedidoDetalle detalle : detalles) {
                        Producto producto = detalle.getProducto();
                        if (producto == null) continue;
                        int cantidadDevolver = detalle.getCantidad() != null ? detalle.getCantidad() : 0;
                        if (cantidadDevolver <= 0) continue;

                        int stockPrevio = producto.getStock() != null ? producto.getStock() : 0;
                        int nuevoStock = stockPrevio + cantidadDevolver;
                        producto.setStock(nuevoStock);
                        productoRepository.save(producto);

                        InventarioMovimiento movimiento = new InventarioMovimiento();
                        movimiento.setProducto(producto);
                        movimiento.setPedido(pedido);
                        movimiento.setCantidad(cantidadDevolver);
                        movimiento.setTipoMovimiento("ENTRADA");
                        movimiento.setProveedor("Sistema Pedidos");
                        movimiento.setFecha(LocalDateTime.now());
                        movimiento.setNotas("Devolución fallback por pedido #" + (pedido.getNumeroOrden() != null ? pedido.getNumeroOrden() : pedido.getId()));
                        inventarioMovimientoRepository.save(movimiento);

                        log.info("[InventarioService] Stock devuelto (Fallback) para producto '{}' (ID: {}): {} -> {}.",
                                producto.getNombre(), producto.getId(), stockPrevio, nuevoStock);
                    }
                }
            } else {
                log.info("[InventarioService] Sin movimientos SALIDA ni flag stockDescontado activado para pedido ID {}. No se modifica stock.", pedido.getId());
            }
        }

        // Restablecer flag a false
        pedido.setStockDescontado(false);
        pedidoRepository.save(pedido);
    }

    private String normalizarEstado(String estado) {
        if (estado == null) return "PENDIENTE";
        String norm = estado.trim().toUpperCase();
        if ("EN REVISION".equals(norm) || "EN_REVISION".equals(norm) || "EN REVISIÓN".equals(norm)) {
            return "EN REVISION";
        }
        return norm;
    }
}
