package com.daroca.sistema_financiero.service;

import com.daroca.sistema_financiero.entity.ActivoFinanciero;
import com.daroca.sistema_financiero.repository.ActivoFinancieroRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PrecioMercadoSyncService {

    private static final String CHART_BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ActivoFinancieroRepository activoFinancieroRepository;

    @Scheduled(initialDelay = 5000, fixedDelay = 60000)
    public void actualizarPreciosMercado() {
        List<ActivoFinanciero> activos = activoFinancieroRepository.findAll();

        for (ActivoFinanciero activo : activos) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(CHART_BASE_URL + activo.getTicker()))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.error("Error HTTP {} al actualizar precio para {}", response.statusCode(), activo.getTicker());
                    continue;
                }

                JsonNode result = OBJECT_MAPPER.readTree(response.body())
                        .path("chart")
                        .path("result");

                if (!result.isArray() || result.isEmpty()) {
                    log.warn("Respuesta sin datos de cotización para el ticker: {}", activo.getTicker());
                    continue;
                }

                JsonNode precioNode = result.get(0).path("meta").path("regularMarketPrice");
                if (precioNode.isMissingNode() || precioNode.isNull()) {
                    log.warn("Precio no disponible en la respuesta para el ticker: {}", activo.getTicker());
                    continue;
                }

                Double precio = precioNode.asDouble();
                activo.setPrecioMercado(precio);
                activoFinancieroRepository.save(activo);
                log.info("Precio actualizado para {}: {} €", activo.getTicker(), precio);
            } catch (Exception e) {
                log.error("Error al actualizar precio para {}: {}", activo.getTicker(), e.getMessage());
            }
        }
    }
}
