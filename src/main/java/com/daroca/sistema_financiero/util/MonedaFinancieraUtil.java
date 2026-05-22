package com.daroca.sistema_financiero.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MonedaFinancieraUtil {

    private static final String MONEDA_PENCES_LONDRES = "GBp";
    private static final String MONEDA_LIBRAS = "GBP";
    private static final BigDecimal CIEN = new BigDecimal("100");

    private MonedaFinancieraUtil() {
    }

    public static String normalizarMoneda(String moneda) {
        if (moneda == null || moneda.isBlank()) {
            return "EUR";
        }
        if (MONEDA_PENCES_LONDRES.equals(moneda)) {
            return MONEDA_LIBRAS;
        }
        return moneda.toUpperCase();
    }

    public static double normalizarPrecio(double precio, String moneda) {
        if (MONEDA_PENCES_LONDRES.equals(moneda)) {
            return precio / 100.0;
        }
        return precio;
    }

    public static BigDecimal normalizarPrecio(BigDecimal precio, String moneda) {
        if (MONEDA_PENCES_LONDRES.equals(moneda)) {
            return precio.divide(CIEN, 10, RoundingMode.DOWN);
        }
        return precio;
    }
}
