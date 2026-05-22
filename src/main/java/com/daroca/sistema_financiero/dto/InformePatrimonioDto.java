package com.daroca.sistema_financiero.dto;

import java.math.BigDecimal;

public record InformePatrimonioDto(
        Long clienteId,
        String moneda,
        BigDecimal flujoCajaNeto,
        BigDecimal patrimonioActual,
        BigDecimal beneficioNeto,
        BigDecimal rentabilidadPorcentaje,
        BigDecimal comisionExito) {
}
