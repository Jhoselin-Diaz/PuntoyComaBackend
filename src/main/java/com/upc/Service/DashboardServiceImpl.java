package com.upc.Service;

import com.upc.DTO.DashboardVigilanciaDTO;
import com.upc.DTO.ProductoVigilanciaDTO;
import com.upc.Entity.Producto;
import com.upc.Repository.PedidoDetalleRepository;
import com.upc.Repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final PedidoDetalleRepository pedidoDetalleRepository;
    private final ProductoRepository productoRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductoVigilanciaDTO> obtenerMasVendidos() {
        List<Object[]> rawList = pedidoDetalleRepository.findTopProductosMasVendidosValidados();
        List<ProductoVigilanciaDTO> masVendidos = new ArrayList<>();

        for (Object[] row : rawList) {
            Long prodId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String nombre = row[1] != null ? (String) row[1] : "Producto";
            String sku = row[2] != null ? (String) row[2] : (prodId != null ? "PC-" + prodId : "-");
            Integer stock = row[3] != null ? ((Number) row[3]).intValue() : 0;
            Integer totalVentas = row[4] != null ? ((Number) row[4]).intValue() : 0;

            masVendidos.add(ProductoVigilanciaDTO.builder()
                    .id(prodId)
                    .nombre(nombre)
                    .sku(sku)
                    .ventas(totalVentas)
                    .stock(stock)
                    .disponible(stock)
                    .tipo("venta")
                    .build());
        }

        // Asegurar ordenamiento estricto por total de ventas validadas descendente
        masVendidos.sort((a, b) -> Integer.compare(b.getVentas(), a.getVentas()));
        return masVendidos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoVigilanciaDTO> obtenerAgotandose() {
        Map<Long, Integer> ventasMap = obtenerVentasPorProductoId();
        List<Producto> productos = productoRepository.findAll();

        return productos.stream()
                .filter(p -> p.getStock() != null && p.getStock() > 0 && p.getStock() <= 5)
                .sorted(Comparator.comparingInt(Producto::getStock))
                .map(p -> {
                    String sku = p.getCodigoSku();
                    if (sku == null || sku.trim().isEmpty()) {
                        sku = "PC-" + p.getId();
                    }
                    int ventas = ventasMap.getOrDefault(p.getId(), 0);
                    return ProductoVigilanciaDTO.builder()
                            .id(p.getId())
                            .nombre(p.getNombre())
                            .sku(sku)
                            .stock(p.getStock())
                            .disponible(p.getStock())
                            .ventas(ventas)
                            .tipo("alerta")
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoVigilanciaDTO> obtenerSinExistencias() {
        Map<Long, Integer> ventasMap = obtenerVentasPorProductoId();
        List<Producto> productos = productoRepository.findAll();

        return productos.stream()
                .filter(p -> p.getStock() == null || p.getStock() <= 0)
                .sorted(Comparator.comparing(Producto::getNombre, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(p -> {
                    String sku = p.getCodigoSku();
                    if (sku == null || sku.trim().isEmpty()) {
                        sku = "PC-" + p.getId();
                    }
                    int ventas = ventasMap.getOrDefault(p.getId(), 0);
                    return ProductoVigilanciaDTO.builder()
                            .id(p.getId())
                            .nombre(p.getNombre())
                            .sku(sku)
                            .stock(0)
                            .disponible(0)
                            .ventas(ventas)
                            .tipo("agotado")
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardVigilanciaDTO obtenerVigilanciaStock() {
        List<ProductoVigilanciaDTO> masVendidos = obtenerMasVendidos();
        List<ProductoVigilanciaDTO> agotandose = obtenerAgotandose();
        List<ProductoVigilanciaDTO> sinExistencias = obtenerSinExistencias();

        List<ProductoVigilanciaDTO> destacados = new ArrayList<>();
        // Agregar hasta 3 más vendidos
        destacados.addAll(masVendidos.stream().limit(3).collect(Collectors.toList()));
        // Agregar hasta 3 que se están agotando
        destacados.addAll(agotandose.stream().limit(3).collect(Collectors.toList()));
        // Agregar hasta 3 sin existencias
        destacados.addAll(sinExistencias.stream().limit(3).collect(Collectors.toList()));

        return DashboardVigilanciaDTO.builder()
                .masVendidos(masVendidos)
                .agotandose(agotandose)
                .sinExistencias(sinExistencias)
                .destacados(destacados)
                .build();
    }

    private Map<Long, Integer> obtenerVentasPorProductoId() {
        List<Object[]> rawList = pedidoDetalleRepository.findTopProductosMasVendidosValidados();
        Map<Long, Integer> map = new HashMap<>();
        for (Object[] row : rawList) {
            if (row[0] != null && row[4] != null) {
                map.put(((Number) row[0]).longValue(), ((Number) row[4]).intValue());
            }
        }
        return map;
    }
}
