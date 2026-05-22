package com.daroca.sistema_financiero.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "activos_financieros")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivoFinanciero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private Double precioMercado;
}
