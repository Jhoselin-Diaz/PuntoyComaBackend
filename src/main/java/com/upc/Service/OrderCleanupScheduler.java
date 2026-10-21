package com.upc.Service;

import com.upc.Entity.Pedido;
import com.upc.Repository.PedidoDetalleRepository;
import com.upc.Repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderCleanupScheduler.class);

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoDetalleRepository pedidoDetalleRepository;

    // Se ejecuta a las 2:00 AM todos los días: "0 0 2 * * ?"
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void limpiarPedidosExpirados() {
        log.info("[Scheduler] Iniciando limpieza automática de pedidos PENDIENTES con más de 24 horas...");
        try {
            LocalDateTime limite = LocalDateTime.now().minusHours(24);
            List<Pedido> expirados = pedidoRepository.findByEstadoAndFechaBefore("PENDIENTE", limite);
            
            if (expirados != null && !expirados.isEmpty()) {
                log.info("[Scheduler] Se encontraron {} pedidos expirados para eliminar.", expirados.size());
                for (Pedido pedido : expirados) {
                    log.info("[Scheduler] Eliminando pedido ID: {}, Orden: {}, Fecha: {}", 
                             pedido.getId(), pedido.getNumeroOrden(), pedido.getFecha());
                    // 1. Eliminar los detalles de este pedido
                    pedidoDetalleRepository.deleteByPedidoId(pedido.getId());
                    // 2. Eliminar el pedido
                    pedidoRepository.delete(pedido);
                }
                log.info("[Scheduler] Limpieza completada con éxito.");
            } else {
                log.info("[Scheduler] No se encontraron pedidos PENDIENTES expirados.");
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error durante la limpieza de pedidos expirados: {}", e.getMessage(), e);
        }
    }
}
