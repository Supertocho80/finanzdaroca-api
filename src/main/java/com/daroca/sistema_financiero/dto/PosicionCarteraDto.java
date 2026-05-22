package com.daroca.sistema_financiero.dto;

import java.math.BigDecimal;

public record PosicionCarteraDto(
        String ticker,
        String nombreActivo,
        Integer cantidad,
        BigDecimal precioMedioCompra,
        BigDecimal precioMercadoActual,
        BigDecimal valoracionTotal,
        String monedaOriginal,
        BigDecimal rentabilidadPorcentaje,
        boolean alertaMifid) {
}
