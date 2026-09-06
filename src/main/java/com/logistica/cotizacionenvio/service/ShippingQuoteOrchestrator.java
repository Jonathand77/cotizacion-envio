package com.logistica.cotizacionenvio.service;

import com.logistica.cotizacionenvio.domain.ProviderOutcome;
import com.logistica.cotizacionenvio.domain.ProviderQuote;
import com.logistica.cotizacionenvio.domain.QuoteStatus;
import com.logistica.cotizacionenvio.domain.ShippingQuoteRequest;
import com.logistica.cotizacionenvio.domain.ShippingQuoteResult;
import com.logistica.cotizacionenvio.provider.ProviderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Consulta a todos los proveedores disponibles en paralelo (no en
 * secuencia), tolera que alguno falle o exceda el tiempo esperado, y
 * selecciona la mejor alternativa valida.
 *
 * Regla desempate: menor precio; si hay empate, menor tiempo
 * estimado de entrega (mas valor por el mismo costo, que es el objetivo
 * del servicio); si aun persiste el empate, por nombre de proveedor, para
 * que la seleccion sea deterministica y reproducible ante los mismos
 * insumos.
 */
@Service
public class ShippingQuoteOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ShippingQuoteOrchestrator.class);

    private static final Comparator<ProviderQuote> BEST_QUOTE_ORDER = Comparator
            .comparing(ProviderQuote::price)
            .thenComparing(ProviderQuote::estimatedDays)
            .thenComparing(ProviderQuote::provider);

    private final List<ProviderClient> providerClients;
    private final Duration providerTimeout;

    public ShippingQuoteOrchestrator(
            List<ProviderClient> providerClients,
            @Value("${orchestration.provider-timeout-ms:1500}") long providerTimeoutMs) {
        this.providerClients = providerClients;
        this.providerTimeout = Duration.ofMillis(providerTimeoutMs);
    }

    public Mono<ShippingQuoteResult> orchestrate(ShippingQuoteRequest request) {
        log.info("[{}] consultando {} proveedor(es) en paralelo: {}",
                request.requestId(), providerClients.size(), providerNames());
        return Flux.fromIterable(providerClients)
                .flatMap(client -> outcomeFor(client, request))
                .collectList()
                .map(outcomes -> buildResult(request.requestId(), outcomes));
    }

    private String providerNames() {
        return providerClients.stream().map(ProviderClient::providerName).toList().toString();
    }

    private Mono<ProviderOutcome> outcomeFor(ProviderClient client, ShippingQuoteRequest request) {
        return client.requestQuote(request)
                .timeout(providerTimeout)
                .map(ProviderOutcome::success)
                .doOnNext(outcome -> log.info("[{}] {} respondio con cotizacion valida: quoteId={} price={} estimatedDays={}",
                        request.requestId(), outcome.provider(), outcome.quote().quoteId(),
                        outcome.quote().price(), outcome.quote().estimatedDays()))
                .onErrorResume(error -> {
                    String reason = reasonFor(error);
                    log.warn("[{}] {} no entrego una cotizacion valida: {}",
                            request.requestId(), client.providerName(), reason);
                    return Mono.just(ProviderOutcome.failure(client.providerName(), reason));
                });
    }

    private String reasonFor(Throwable error) {
        if (error instanceof TimeoutException) {
            return "timeout tras " + providerTimeout.toMillis() + "ms";
        }
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }

    private ShippingQuoteResult buildResult(String requestId, List<ProviderOutcome> outcomes) {
        ProviderQuote selected = outcomes.stream()
                .filter(ProviderOutcome::success)
                .map(ProviderOutcome::quote)
                .min(BEST_QUOTE_ORDER)
                .orElse(null);

        QuoteStatus status = selected != null ? QuoteStatus.COMPLETED : QuoteStatus.FAILED;

        if (selected != null) {
            log.info("[{}] cotizacion seleccionada: proveedor={} price={} estimatedDays={}",
                    requestId, selected.provider(), selected.price(), selected.estimatedDays());
        } else {
            log.warn("[{}] ningun proveedor entrego una cotizacion valida, status=FAILED", requestId);
        }

        return new ShippingQuoteResult(requestId, status, selected, outcomes, Instant.now());
    }
}
