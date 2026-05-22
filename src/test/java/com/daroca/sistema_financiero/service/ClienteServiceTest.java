package com.daroca.sistema_financiero.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.daroca.sistema_financiero.dto.CarteraResponseDto;
import com.daroca.sistema_financiero.entity.ActivoFinanciero;
import com.daroca.sistema_financiero.entity.Cliente;
import com.daroca.sistema_financiero.entity.TipoOperacion;
import com.daroca.sistema_financiero.entity.Transaccion;
import com.daroca.sistema_financiero.integration.YahooFinanceChartClient;
import com.daroca.sistema_financiero.repository.ClienteRepository;
import com.daroca.sistema_financiero.repository.TransaccionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    private static final Long CLIENTE_ID = 1L;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private YahooFinanceChartClient yahooFinanceChartClient;

    @InjectMocks
    private ClienteService clienteService;

    private Cliente clienteModerado;
    private Cliente clienteConservador;

    @BeforeEach
    void setUp() {
        clienteModerado = Cliente.builder()
                .id(CLIENTE_ID)
                .nombre("Cliente Moderado")
                .email("moderado@test.com")
                .perfilRiesgo("MODERADO")
                .build();

        clienteConservador = Cliente.builder()
                .id(CLIENTE_ID)
                .nombre("Cliente Conservador")
                .email("conservador@test.com")
                .perfilRiesgo("CONSERVADOR")
                .build();
    }

    @Test
    void debeCalcularPatrimonioCorrectamenteConEfectivoYAcciones() {
        when(clienteRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteModerado));

        ActivoFinanciero activoEur = ActivoFinanciero.builder()
                .id(10L)
                .ticker("IBE.MC")
                .nombre("Iberdrola")
                .precioMercado(100.0)
                .moneda("EUR")
                .build();

        LocalDateTime ahora = LocalDateTime.now();

        Transaccion deposito = Transaccion.builder()
                .id(1L)
                .tipoOperacion(TipoOperacion.DEPOSITO)
                .moneda("EUR")
                .precioEjecucion(5000.0)
                .cantidad(1)
                .fecha(ahora)
                .build();

        Transaccion compra = Transaccion.builder()
                .id(2L)
                .tipoOperacion(TipoOperacion.COMPRA)
                .activoFinanciero(activoEur)
                .cantidad(10)
                .precioEjecucion(100.0)
                .fecha(ahora)
                .build();

        when(transaccionRepository.findByClienteIdOrderByFechaAsc(CLIENTE_ID))
                .thenReturn(List.of(deposito, compra));

        CarteraResponseDto cartera = clienteService.obtenerCarteraConsolidada(CLIENTE_ID, "EUR");

        assertThat(cartera.capitalTotalDepositado()).isEqualByComparingTo(bd("5000.00"));
        assertThat(cartera.saldoEfectivoTotal()).isEqualByComparingTo(bd("4000.00"));
        assertThat(cartera.patrimonioNetoTotal()).isEqualByComparingTo(bd("5000.00"));
        assertThat(cartera.posicionesActivos()).hasSize(1);
        assertThat(cartera.posicionesActivos().getFirst().valoracionTotal())
                .isEqualByComparingTo(bd("1000.00"));
    }

    @Test
    void debeDispararAlertaMifidParaClienteConservadorConAccionesVolatiles() {
        when(clienteRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteConservador));

        ActivoFinanciero aapl = ActivoFinanciero.builder()
                .id(20L)
                .ticker("AAPL")
                .nombre("Apple Inc.")
                .precioMercado(150.0)
                .moneda("USD")
                .build();

        Transaccion compra = Transaccion.builder()
                .id(1L)
                .tipoOperacion(TipoOperacion.COMPRA)
                .activoFinanciero(aapl)
                .cantidad(5)
                .precioEjecucion(140.0)
                .fecha(LocalDateTime.now())
                .build();

        when(transaccionRepository.findByClienteIdOrderByFechaAsc(CLIENTE_ID))
                .thenReturn(List.of(compra));
        when(yahooFinanceChartClient.fetchTipoCambio(eq("USD"), eq("EUR")))
                .thenReturn(Optional.of(0.90));

        CarteraResponseDto cartera = clienteService.obtenerCarteraConsolidada(CLIENTE_ID, "EUR");

        assertThat(cartera.posicionesActivos()).hasSize(1);
        assertThat(cartera.posicionesActivos().getFirst().ticker()).isEqualTo("AAPL");
        assertThat(cartera.posicionesActivos().getFirst().alertaMifid()).isTrue();
    }

    @Test
    void debeAplicarConversionDeDivisaCorrectamenteParaActivosExtranjeros() {
        when(clienteRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteModerado));

        ActivoFinanciero activoUsd = ActivoFinanciero.builder()
                .id(30L)
                .ticker("AAPL")
                .nombre("Apple Inc.")
                .precioMercado(100.0)
                .moneda("USD")
                .build();

        LocalDateTime ahora = LocalDateTime.now();

        Transaccion deposito = Transaccion.builder()
                .id(1L)
                .tipoOperacion(TipoOperacion.DEPOSITO)
                .moneda("EUR")
                .precioEjecucion(1000.0)
                .cantidad(1)
                .fecha(ahora)
                .build();

        Transaccion compra = Transaccion.builder()
                .id(2L)
                .tipoOperacion(TipoOperacion.COMPRA)
                .activoFinanciero(activoUsd)
                .cantidad(10)
                .precioEjecucion(100.0)
                .fecha(ahora)
                .build();

        when(transaccionRepository.findByClienteIdOrderByFechaAsc(CLIENTE_ID))
                .thenReturn(List.of(deposito, compra));
        when(yahooFinanceChartClient.fetchTipoCambio(eq("USD"), eq("EUR")))
                .thenReturn(Optional.of(0.90));

        CarteraResponseDto cartera = clienteService.obtenerCarteraConsolidada(CLIENTE_ID, "EUR");

        BigDecimal valoracionEsperadaEur = bd("1000.00").multiply(bd("0.90"));

        assertThat(cartera.posicionesActivos()).hasSize(1);
        assertThat(cartera.posicionesActivos().getFirst().valoracionTotal())
                .isEqualByComparingTo(valoracionEsperadaEur);
        assertThat(cartera.posicionesActivos().getFirst().precioMercadoActual())
                .isEqualByComparingTo(bd("90.00"));
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }
}
