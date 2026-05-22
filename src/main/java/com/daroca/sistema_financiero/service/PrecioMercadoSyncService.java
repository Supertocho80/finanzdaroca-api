package com.daroca.sistema_financiero.service;

import com.daroca.sistema_financiero.entity.ActivoFinanciero;
import com.daroca.sistema_financiero.integration.YahooFinanceChartClient;
import com.daroca.sistema_financiero.integration.YahooFinanceChartClient.ChartQuote;
import com.daroca.sistema_financiero.repository.ActivoFinancieroRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PrecioMercadoSyncService {

    private final ActivoFinancieroRepository activoFinancieroRepository;
    private final YahooFinanceChartClient yahooFinanceChartClient;

    @Scheduled(initialDelay = 5000, fixedDelay = 60000)
    public void actualizarPreciosMercado() {
        List<ActivoFinanciero> activos = activoFinancieroRepository.findAll();

        for (ActivoFinanciero activo : activos) {
            try {
                Optional<ChartQuote> quoteOpt = yahooFinanceChartClient.fetchChartQuote(activo.getTicker());
                if (quoteOpt.isEmpty()) {
                    log.warn("Precio no disponible para el ticker: {}", activo.getTicker());
                    continue;
                }

                ChartQuote quote = quoteOpt.get();
                activo.setPrecioMercado(quote.price());
                if (quote.currency() != null) {
                    activo.setMoneda(quote.currency());
                }

                activoFinancieroRepository.save(activo);
                log.info("Precio actualizado para {}: {} {}", activo.getTicker(), quote.price(),
                        quote.currency() != null ? quote.currency() : "N/A");
            } catch (Exception e) {
                log.error("Error al actualizar precio para {}: {}", activo.getTicker(), e.getMessage());
            }
        }
    }
}
