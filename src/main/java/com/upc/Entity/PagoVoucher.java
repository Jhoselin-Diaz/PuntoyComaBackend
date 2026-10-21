package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedidos_vouchers_pagos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagoVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Pedido pedido;

    private Double monto;
    private String referencia;

    @Column(name = "wamid")
    private String wamid;

    @Column(name = "voucher_url", columnDefinition = "TEXT")
    private String voucherUrl;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Column(name = "metodo_pago")
    private String metodoPago;
}
