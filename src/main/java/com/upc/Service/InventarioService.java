package com.upc.Service;

import com.upc.Entity.Pedido;

/**
 * Contrato para el servicio de gestión de inventario y movimientos automáticos
 * vinculados a la evolución del estado de los pedidos.
 */
public interface InventarioService {

    /**
     * Procesa las actualizaciones de stock en los productos e inserta los movimientos
     * correspondientes en el historial de inventario basándose en la fuente de verdad
     * de movimientos reales y la transición de estado.
     *
     * @param pedido          Entidad Pedido.
     * @param estadoAnterior  Estado real del pedido antes de la modificación.
     * @param nuevoEstadoRaw  Nuevo estado solicitado ("VALIDADO", "RECHAZADO", "EN REVISION", "PENDIENTE").
     */
    void procesarTransicionEstadoPedido(Pedido pedido, String estadoAnterior, String nuevoEstadoRaw);

    /**
     * Devuelve el stock descontado de un pedido basándose en la fuente de verdad
     * de movimientos SALIDA del historial.
     */
    void devolverStockPorPedido(Pedido pedido);
}
