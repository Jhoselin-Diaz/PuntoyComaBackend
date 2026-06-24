package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedidos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_orden")
    private String numeroOrden;

    private LocalDateTime fecha;
    private Double total;
    private String estado;

    @Column(name = "metodo_pago")
    private String metodoPago;

    @Column(name = "referencia_pago")
    private String referenciaPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // OCR audit and conciliation fields
    @Column(name = "monto_detectado")
    private Double montoDetectado;

    private Double diferencia;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Column(name = "voucher_url")
    private String voucherUrl;

    @Column(name = "banco_emisor")
    private String bancoEmisor;

    @Column(name = "resultado_conciliacion")
    private String resultadoConciliacion;
}
