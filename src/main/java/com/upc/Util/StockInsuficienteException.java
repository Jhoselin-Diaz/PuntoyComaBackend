package com.upc.Util;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Excepción lanzada cuando el stock disponible en inventario no cubre
 * la cantidad solicitada para uno o más productos de un pedido.
 * Al heredar de RuntimeException, garantiza el rollback transaccional en Spring.
 */
public class StockInsuficienteException extends RuntimeException {

    private final List<Map<String, Object>> detalles;

    public StockInsuficienteException(String message, List<Map<String, Object>> detalles) {
        super(message);
        this.detalles = detalles != null ? detalles : Collections.emptyList();
    }

    public List<Map<String, Object>> getDetalles() {
        return detalles;
    }
}
