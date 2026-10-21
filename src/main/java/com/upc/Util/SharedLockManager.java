package com.upc.Util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de cerrojos compartidos para evitar condiciones de carrera concurrentes
 * al procesar transacciones asíncronas de un mismo cliente en distintos servicios.
 */
public class SharedLockManager {
    private static final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public static Object getLock(String key) {
        if (key == null) {
            return new Object();
        }
        return locks.computeIfAbsent(key, k -> new Object());
    }
}
