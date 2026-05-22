package com.daroca.sistema_financiero.service;

import com.daroca.sistema_financiero.dto.CarteraResponseDto;
import com.daroca.sistema_financiero.dto.InformePatrimonioDto;
import com.daroca.sistema_financiero.dto.PosicionCarteraDto;
import com.daroca.sistema_financiero.dto.PosicionEfectivoDto;
import com.daroca.sistema_financiero.entity.ActivoFinanciero;
import com.daroca.sistema_financiero.entity.Cliente;
import com.daroca.sistema_financiero.entity.Rol;
import com.daroca.sistema_financiero.entity.TipoOperacion;
import com.daroca.sistema_financiero.entity.Transaccion;
import com.daroca.sistema_financiero.integration.YahooFinanceChartClient;
import com.daroca.sistema_financiero.repository.ClienteRepository;
import com.daroca.sistema_financiero.repository.TransaccionRepository;
import com.daroca.sistema_financiero.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final TransaccionRepository transaccionRepository;
    private final YahooFinanceChartClient yahooFinanceChartClient;

    public List<Cliente> listarTodos() {
        return clienteRepository.findAll();
    }

    public Cliente obtenerPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id: " + id));
    }

    public List<Cliente> obtenerClientesPorUsuario(Long usuarioId, Rol rol) {
        if (rol == Rol.ADMIN) {
            return clienteRepository.findAll();
        }
        return clienteRepository.findByAsesorId(usuarioId);
    }

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal DIEZ_POR_CIENTO = new BigDecimal("0.10");
    private static final BigDecimal TIPO_INTERES_ANUAL = new BigDecimal("0.02");
    private static final BigDecimal DIAS_ANUAL = new BigDecimal("365");

    private static final Set<String> TICKERS_RENTA_FIJA_ULTRA_ESTABLES = Set.of(
            "BND", "AGG", "TLT", "IEF", "SHY", "GOVT", "VGIT", "VCSH", "BSV", "LQD",
            "IEAC", "VGOV", "IGSB", "MUB", "TIP", "BIL", "SGOV");

    public InformePatrimonioDto calcularPatrimonio(Long clienteId, String divisaDestino) {
        CarteraResponseDto cartera = obtenerCarteraConsolidada(clienteId, divisaDestino);
        return new InformePatrimonioDto(
                cartera.clienteId(),
                cartera.monedaDestino(),
                cartera.capitalTotalDepositado(),
                cartera.patrimonioNetoTotal(),
                cartera.beneficioGlobalNeto(),
                cartera.rentabilidadGlobalPorcentaje(),
                cartera.comisionExito());
    }

    public CarteraResponseDto obtenerCarteraConsolidada(Long clienteId, String divisaDestino) {
        Cliente cliente = obtenerPorId(clienteId);
        String divisa = divisaDestino.toUpperCase();
        List<Transaccion> transacciones = transaccionRepository.findByClienteIdOrderByFechaAsc(clienteId);

        Map<String, PosicionActivo> posicionesActivosMap = new HashMap<>();
        Map<String, BigDecimal> saldosEfectivo = new HashMap<>();
        Map<String, BigDecimal> capitalDepositadoPorDivisa = new HashMap<>();
        Map<String, LocalDateTime> ultimaFechaPorDivisa = new HashMap<>();
        Map<String, BigDecimal> interesesPorDivisa = new HashMap<>();
        Map<String, BigDecimal> tiposCambioCache = new HashMap<>();

        for (Transaccion transaccion : transacciones) {
            TipoOperacion tipo = transaccion.getTipoOperacion();

            switch (tipo) {
                case DEPOSITO -> procesarDeposito(transaccion, saldosEfectivo, capitalDepositadoPorDivisa, ultimaFechaPorDivisa);
                case RETIRO -> procesarRetiro(transaccion, saldosEfectivo, capitalDepositadoPorDivisa, ultimaFechaPorDivisa);
                case COMPRA -> procesarCompra(transaccion, posicionesActivosMap, saldosEfectivo, ultimaFechaPorDivisa);
                case VENTA -> procesarVenta(transaccion, posicionesActivosMap, saldosEfectivo, ultimaFechaPorDivisa);
            }
        }

        aplicarInteresesCuentaRemunerada(saldosEfectivo, ultimaFechaPorDivisa, interesesPorDivisa);

        List<PosicionEfectivoDto> posicionesEfectivo = new ArrayList<>();
        BigDecimal saldoEfectivoTotal = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entrada : saldosEfectivo.entrySet()) {
            String moneda = entrada.getKey();
            BigDecimal intereses = redondear(interesesPorDivisa.getOrDefault(moneda, BigDecimal.ZERO));
            BigDecimal saldoTotalOriginal = redondear(entrada.getValue());
            BigDecimal saldoOriginal = redondear(saldoTotalOriginal.subtract(intereses));
            BigDecimal saldoConvertido = redondear(convertirADivisaDestino(
                    saldoTotalOriginal, moneda, divisa, tiposCambioCache));

            saldoEfectivoTotal = saldoEfectivoTotal.add(saldoConvertido);

            if (saldoTotalOriginal.compareTo(BigDecimal.ZERO) != 0) {
                posicionesEfectivo.add(new PosicionEfectivoDto(
                        moneda, saldoOriginal, intereses, saldoTotalOriginal, saldoConvertido));
            }
        }

        List<PosicionCarteraDto> posicionesActivos = new ArrayList<>();
        BigDecimal valoracionTotalActivos = BigDecimal.ZERO;

        for (PosicionActivo posicion : posicionesActivosMap.values()) {
            if (posicion.cantidad <= 0) {
                continue;
            }

            ActivoFinanciero activo = posicion.activo;
            String monedaOriginal = obtenerMonedaActivo(activo);

            BigDecimal precioMedioOriginal = posicion.costeTotal.divide(
                    BigDecimal.valueOf(posicion.cantidad), 10, RoundingMode.DOWN);
            BigDecimal precioMedioCompra = redondear(convertirADivisaDestino(
                    precioMedioOriginal, monedaOriginal, divisa, tiposCambioCache));
            BigDecimal precioMercadoActual = redondear(convertirADivisaDestino(
                    BigDecimal.valueOf(activo.getPrecioMercado()), monedaOriginal, divisa, tiposCambioCache));

            BigDecimal cantidad = BigDecimal.valueOf(posicion.cantidad);
            BigDecimal valoracionTotal = redondear(precioMercadoActual.multiply(cantidad));
            BigDecimal costeMedioInvertido = redondear(precioMedioCompra.multiply(cantidad));

            valoracionTotalActivos = valoracionTotalActivos.add(valoracionTotal);

            BigDecimal beneficioPosicion = valoracionTotal.subtract(costeMedioInvertido);
            BigDecimal rentabilidadPosicion = calcularRentabilidad(beneficioPosicion, costeMedioInvertido);
            boolean alertaMifid = evaluarAlertaMifid(cliente.getPerfilRiesgo(), activo.getTicker());

            posicionesActivos.add(new PosicionCarteraDto(
                    activo.getTicker(),
                    activo.getNombre(),
                    posicion.cantidad,
                    precioMedioCompra,
                    precioMercadoActual,
                    valoracionTotal,
                    monedaOriginal,
                    rentabilidadPosicion,
                    alertaMifid));
        }

        BigDecimal capitalTotalDepositado = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entrada : capitalDepositadoPorDivisa.entrySet()) {
            capitalTotalDepositado = capitalTotalDepositado.add(convertirADivisaDestino(
                    entrada.getValue(), entrada.getKey(), divisa, tiposCambioCache));
        }

        valoracionTotalActivos = redondear(valoracionTotalActivos);
        saldoEfectivoTotal = redondear(saldoEfectivoTotal);
        capitalTotalDepositado = redondear(capitalTotalDepositado);

        BigDecimal patrimonioNetoTotal = redondear(saldoEfectivoTotal.add(valoracionTotalActivos));
        BigDecimal beneficioGlobalNeto = redondear(patrimonioNetoTotal.subtract(capitalTotalDepositado));
        BigDecimal rentabilidadGlobalPorcentaje = calcularRentabilidad(beneficioGlobalNeto, capitalTotalDepositado);
        BigDecimal comisionExito = calcularComisionExito(beneficioGlobalNeto);

        return new CarteraResponseDto(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getPerfilRiesgo(),
                divisa,
                capitalTotalDepositado,
                saldoEfectivoTotal,
                valoracionTotalActivos,
                patrimonioNetoTotal,
                beneficioGlobalNeto,
                rentabilidadGlobalPorcentaje,
                comisionExito,
                posicionesActivos,
                posicionesEfectivo);
    }

    private void procesarDeposito(
            Transaccion transaccion,
            Map<String, BigDecimal> saldosEfectivo,
            Map<String, BigDecimal> capitalDepositadoPorDivisa,
            Map<String, LocalDateTime> ultimaFechaPorDivisa) {
        String moneda = obtenerMonedaTransaccion(transaccion);
        BigDecimal monto = BigDecimal.valueOf(transaccion.getPrecioEjecucion());
        saldosEfectivo.merge(moneda, monto, BigDecimal::add);
        capitalDepositadoPorDivisa.merge(moneda, monto, BigDecimal::add);
        ultimaFechaPorDivisa.put(moneda, transaccion.getFecha());
    }

    private void procesarRetiro(
            Transaccion transaccion,
            Map<String, BigDecimal> saldosEfectivo,
            Map<String, BigDecimal> capitalDepositadoPorDivisa,
            Map<String, LocalDateTime> ultimaFechaPorDivisa) {
        String moneda = obtenerMonedaTransaccion(transaccion);
        BigDecimal monto = BigDecimal.valueOf(transaccion.getPrecioEjecucion());
        saldosEfectivo.merge(moneda, monto.negate(), BigDecimal::add);
        capitalDepositadoPorDivisa.merge(moneda, monto.negate(), BigDecimal::add);
        ultimaFechaPorDivisa.put(moneda, transaccion.getFecha());
    }

    private void procesarCompra(
            Transaccion transaccion,
            Map<String, PosicionActivo> posicionesActivosMap,
            Map<String, BigDecimal> saldosEfectivo,
            Map<String, LocalDateTime> ultimaFechaPorDivisa) {
        ActivoFinanciero activo = transaccion.getActivoFinanciero();
        String moneda = obtenerMonedaActivo(activo);
        int cantidad = transaccion.getCantidad();
        BigDecimal coste = BigDecimal.valueOf(cantidad).multiply(BigDecimal.valueOf(transaccion.getPrecioEjecucion()));

        saldosEfectivo.merge(moneda, coste.negate(), BigDecimal::add);
        ultimaFechaPorDivisa.put(moneda, transaccion.getFecha());

        PosicionActivo posicion = posicionesActivosMap.computeIfAbsent(
                activo.getTicker(), t -> new PosicionActivo(activo));
        posicion.cantidad += cantidad;
        posicion.costeTotal = posicion.costeTotal.add(coste);
    }

    private void procesarVenta(
            Transaccion transaccion,
            Map<String, PosicionActivo> posicionesActivosMap,
            Map<String, BigDecimal> saldosEfectivo,
            Map<String, LocalDateTime> ultimaFechaPorDivisa) {
        ActivoFinanciero activo = transaccion.getActivoFinanciero();
        String moneda = obtenerMonedaActivo(activo);
        int cantidad = transaccion.getCantidad();
        BigDecimal precioVenta = BigDecimal.valueOf(transaccion.getPrecioEjecucion());
        BigDecimal ingresos = BigDecimal.valueOf(cantidad).multiply(precioVenta);

        saldosEfectivo.merge(moneda, ingresos, BigDecimal::add);
        ultimaFechaPorDivisa.put(moneda, transaccion.getFecha());

        PosicionActivo posicion = posicionesActivosMap.get(activo.getTicker());
        if (posicion == null || posicion.cantidad < cantidad) {
            throw new RuntimeException("Venta inválida: posición insuficiente en " + activo.getTicker());
        }

        BigDecimal precioMedio = posicion.costeTotal.divide(
                BigDecimal.valueOf(posicion.cantidad), 10, RoundingMode.DOWN);
        BigDecimal costeVendido = precioMedio.multiply(BigDecimal.valueOf(cantidad));

        posicion.cantidad -= cantidad;
        posicion.costeTotal = posicion.costeTotal.subtract(costeVendido);
    }

    private void aplicarInteresesCuentaRemunerada(
            Map<String, BigDecimal> saldosEfectivo,
            Map<String, LocalDateTime> ultimaFechaPorDivisa,
            Map<String, BigDecimal> interesesPorDivisa) {
        LocalDateTime ahora = LocalDateTime.now();

        for (Map.Entry<String, BigDecimal> entrada : saldosEfectivo.entrySet()) {
            String moneda = entrada.getKey();
            BigDecimal saldo = entrada.getValue();

            if (saldo.compareTo(BigDecimal.ZERO) <= 0 || !ultimaFechaPorDivisa.containsKey(moneda)) {
                interesesPorDivisa.put(moneda, BigDecimal.ZERO);
                continue;
            }

            long dias = ChronoUnit.DAYS.between(ultimaFechaPorDivisa.get(moneda).toLocalDate(), ahora.toLocalDate());
            if (dias <= 0) {
                interesesPorDivisa.put(moneda, BigDecimal.ZERO);
                continue;
            }

            BigDecimal interes = saldo
                    .multiply(TIPO_INTERES_ANUAL)
                    .multiply(BigDecimal.valueOf(dias))
                    .divide(DIAS_ANUAL, 10, RoundingMode.DOWN);

            interesesPorDivisa.put(moneda, redondear(interes));
            saldosEfectivo.put(moneda, saldo.add(interes));
        }
    }

    private String obtenerMonedaTransaccion(Transaccion transaccion) {
        if (transaccion.getMoneda() != null && !transaccion.getMoneda().isBlank()) {
            return transaccion.getMoneda().toUpperCase();
        }
        return "EUR";
    }

    private String obtenerMonedaActivo(ActivoFinanciero activo) {
        if (activo.getMoneda() != null && !activo.getMoneda().isBlank()) {
            return activo.getMoneda().toUpperCase();
        }
        return "EUR";
    }

    private boolean evaluarAlertaMifid(String perfilRiesgo, String ticker) {
        if (perfilRiesgo == null || !"CONSERVADOR".equalsIgnoreCase(perfilRiesgo)) {
            return false;
        }
        return !esActivoUltraEstable(ticker);
    }

    private boolean esActivoUltraEstable(String ticker) {
        return TICKERS_RENTA_FIJA_ULTRA_ESTABLES.contains(ticker.toUpperCase());
    }

    private static class PosicionActivo {
        private final ActivoFinanciero activo;
        private int cantidad;
        private BigDecimal costeTotal = BigDecimal.ZERO;

        private PosicionActivo(ActivoFinanciero activo) {
            this.activo = activo;
        }
    }

    private BigDecimal calcularRentabilidad(BigDecimal beneficioNeto, BigDecimal flujoCajaNeto) {
        if (flujoCajaNeto.compareTo(BigDecimal.ZERO) == 0) {
            return redondear(BigDecimal.ZERO);
        }
        return redondear(beneficioNeto.divide(flujoCajaNeto, 10, RoundingMode.DOWN).multiply(CIEN));
    }

    private BigDecimal calcularComisionExito(BigDecimal beneficioNeto) {
        if (beneficioNeto.compareTo(BigDecimal.ZERO) > 0) {
            return redondear(beneficioNeto.multiply(DIEZ_POR_CIENTO));
        }
        return redondear(BigDecimal.ZERO);
    }

    private BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.DOWN);
    }

    private BigDecimal convertirADivisaDestino(
            BigDecimal valor,
            String monedaOrigen,
            String divisaDestino,
            Map<String, BigDecimal> tiposCambioCache) {
        if (monedaOrigen == null) {
            monedaOrigen = "EUR";
        }

        if (monedaOrigen.equalsIgnoreCase(divisaDestino)) {
            return valor;
        }

        String moneda = monedaOrigen.toUpperCase();
        String cacheKey = moneda + divisaDestino;
        BigDecimal tipoCambio = tiposCambioCache.computeIfAbsent(
                cacheKey, k -> obtenerTipoCambio(moneda, divisaDestino));
        return valor.multiply(tipoCambio);
    }

    private BigDecimal obtenerTipoCambio(String monedaOrigen, String divisaDestino) {
        String parDivisa = monedaOrigen + divisaDestino + "=X";
        return yahooFinanceChartClient.fetchRegularMarketPrice(parDivisa)
                .map(BigDecimal::valueOf)
                .orElseThrow(() -> new RuntimeException(
                        "No se pudo obtener el tipo de cambio para " + monedaOrigen + " hacia " + divisaDestino));
    }

    public Cliente crear(Cliente cliente) {
        if (cliente.getAsesor() == null || cliente.getAsesor().getId() == null) {
            throw new RuntimeException("El cliente debe tener un asesor asignado");
        }
        cliente.setAsesor(usuarioRepository.findById(cliente.getAsesor().getId())
                .orElseThrow(() -> new RuntimeException("Asesor no encontrado")));
        return clienteRepository.save(cliente);
    }

    public Cliente actualizar(Long id, Cliente clienteActualizado) {
        Cliente cliente = obtenerPorId(id);
        cliente.setNombre(clienteActualizado.getNombre());
        cliente.setEmail(clienteActualizado.getEmail());
        cliente.setPerfilRiesgo(clienteActualizado.getPerfilRiesgo());
        if (clienteActualizado.getAsesor() != null && clienteActualizado.getAsesor().getId() != null) {
            cliente.setAsesor(usuarioRepository.findById(clienteActualizado.getAsesor().getId())
                    .orElseThrow(() -> new RuntimeException("Asesor no encontrado")));
        }
        return clienteRepository.save(cliente);
    }

    public void eliminar(Long id) {
        if (!clienteRepository.existsById(id)) {
            throw new RuntimeException("Cliente no encontrado con id: " + id);
        }
        clienteRepository.deleteById(id);
    }
}
