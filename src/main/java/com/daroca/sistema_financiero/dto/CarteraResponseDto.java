package com.daroca.sistema_financiero.dto;

import java.math.BigDecimal;
import java.util.List;

public record CarteraResponseDto(
        Long clienteId,
        String nombreCliente,
        String perfilRiesgo,
        String monedaDestino,
        BigDecimal capitalTotalDepositado,
        BigDecimal saldoEfectivoTotal,
        BigDecimal valoracionTotalActivos,
        BigDecimal patrimonioNetoTotal,
        BigDecimal beneficioGlobalNeto,
        BigDecimal rentabilidadGlobalPorcentaje,
        BigDecimal comisionExito,
        List<PosicionCarteraDto> posicionesActivos,
        List<PosicionEfectivoDto> posicionesEfectivo) {
}
