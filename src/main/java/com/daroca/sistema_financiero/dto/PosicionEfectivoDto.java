package com.daroca.sistema_financiero.dto;

import java.math.BigDecimal;

public record PosicionEfectivoDto(
        String moneda,
        BigDecimal saldoOriginal,
        BigDecimal interesesDevengados,
        BigDecimal saldoTotalOriginal,
        BigDecimal saldoEnMonedaDestino) {
}
