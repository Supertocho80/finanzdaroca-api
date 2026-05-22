package com.daroca.sistema_financiero.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import com.daroca.sistema_financiero.util.MonedaFinancieraUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@Slf4j
public class YahooFinanceChartClient {

    public record ChartQuote(double price, String currency) {}

    private static final String CHART_BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Lazy
    @Autowired
    private YahooFinanceChartClient self;

    public Optional<Double> fetchRegularMarketPrice(String symbol) {
        return self.fetchChartQuote(symbol).map(ChartQuote::price);
    }

    public Optional<Double> fetchTipoCambio(String monedaOrigen, String divisaDestino) {
        if (monedaOrigen == null || monedaOrigen.isBlank() || divisaDestino == null || divisaDestino.isBlank()) {
            log.warn("Par de divisas inválido para tipo de cambio: {} -> {}", monedaOrigen, divisaDestino);
            return Optional.empty();
        }
        if (monedaOrigen.equalsIgnoreCase(divisaDestino)) {
            return Optional.of(1.0);
        }
        return fetchRegularMarketPrice(monedaOrigen.toUpperCase() + divisaDestino.toUpperCase() + "=X");
    }

    @Cacheable("cotizaciones")
    public Optional<ChartQuote> fetchChartQuote(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            log.warn("Ticker inválido para consulta Yahoo Finance");
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CHART_BASE_URL + symbol))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Yahoo Finance respondió HTTP {} para el símbolo {}", response.statusCode(), symbol);
                return Optional.empty();
            }

            JsonNode result = jsonMapper.readTree(response.body())
                    .path("chart")
                    .path("result");

            if (!result.isArray() || result.isEmpty()) {
                return Optional.empty();
            }

            JsonNode meta = result.get(0).path("meta");
            JsonNode precioNode = meta.path("regularMarketPrice");
            if (precioNode.isMissingNode() || precioNode.isNull()) {
                return Optional.empty();
            }

            String currency = null;
            JsonNode monedaNode = meta.path("currency");
            if (!monedaNode.isMissingNode() && !monedaNode.isNull()) {
                currency = monedaNode.asText();
            }

            double precio = precioNode.asDouble();
            double precioNormalizado = MonedaFinancieraUtil.normalizarPrecio(precio, currency);
            String monedaNormalizada = MonedaFinancieraUtil.normalizarMoneda(currency);

            return Optional.of(new ChartQuote(precioNormalizado, monedaNormalizada));
        } catch (Exception e) {
            log.warn("Error al consultar Yahoo Finance para {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }
}
