package com.logistica.cotizacionenvio.provider;

import com.logistica.cotizacionenvio.domain.ProviderQuote;
import com.logistica.cotizacionenvio.domain.ShippingQuoteRequest;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simula la respuesta de un proveedor real aplicando una latencia variable y
 * una probabilidad de fallo, ambas configurables. No depende de ningun
 * servicio externo: todo ocurre en memoria dentro del propio proceso.
 */
public class SimulatedProviderClient implements ProviderClient {

    private final SimulatedProviderConfig config;

    public SimulatedProviderClient(SimulatedProviderConfig config) {
        this.config = config;
    }

    @Override
    public String providerName() {
        return config.providerName();
    }

    @Override
    public Mono<ProviderQuote> requestQuote(ShippingQuoteRequest request) {
        return Mono.defer(() -> Mono.delay(randomLatency()).then(Mono.defer(() -> resolveOutcome(request))));
    }

    private Duration randomLatency() {
        long minMillis = config.minLatency().toMillis();
        long spanMillis = config.maxLatency().toMillis() - minMillis;
        long extraMillis = spanMillis <= 0 ? 0 : ThreadLocalRandom.current().nextLong(spanMillis);
        return Duration.ofMillis(minMillis + extraMillis);
    }

    private Mono<ProviderQuote> resolveOutcome(ShippingQuoteRequest request) {
        if (ThreadLocalRandom.current().nextDouble() < config.failureRate()) {
            return Mono.error(new ProviderUnavailableException(config.providerName()));
        }
        BigDecimal price = config.basePrice()
                .add(config.pricePerKg().multiply(BigDecimal.valueOf(request.weightKg())))
                .setScale(0, RoundingMode.HALF_UP);
        String quoteId = config.providerName() + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
        return Mono.just(new ProviderQuote(config.providerName(), quoteId, price, config.estimatedDays()));
    }
}
