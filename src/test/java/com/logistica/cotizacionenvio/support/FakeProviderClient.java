package com.logistica.cotizacionenvio.support;

import com.logistica.cotizacionenvio.domain.ProviderQuote;
import com.logistica.cotizacionenvio.domain.ShippingQuoteRequest;
import com.logistica.cotizacionenvio.provider.ProviderClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Test double de ProviderClient con comportamiento determinista, para no
 * depender de la aleatoriedad de SimulatedProviderClient en los tests de
 * orquestacion.
 */
public class FakeProviderClient implements ProviderClient {

    private final String providerName;
    private final Function<ShippingQuoteRequest, Mono<ProviderQuote>> behavior;

    private FakeProviderClient(String providerName, Function<ShippingQuoteRequest, Mono<ProviderQuote>> behavior) {
        this.providerName = providerName;
        this.behavior = behavior;
    }

    public static FakeProviderClient returning(String providerName, ProviderQuote quote) {
        return new FakeProviderClient(providerName, request -> Mono.just(quote));
    }

    public static FakeProviderClient failingWith(String providerName, Throwable error) {
        return new FakeProviderClient(providerName, request -> Mono.error(error));
    }

    public static FakeProviderClient delayedBy(String providerName, Duration delay, ProviderQuote quote) {
        return new FakeProviderClient(providerName, request -> Mono.just(quote).delayElement(delay));
    }

    /** Cuenta cuantas veces se invoca requestQuote - util para verificar que no se duplica trabajo. */
    public static FakeProviderClient counting(String providerName, ProviderQuote quote, AtomicInteger invocationCount) {
        return new FakeProviderClient(providerName, request -> {
            invocationCount.incrementAndGet();
            return Mono.just(quote);
        });
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public Mono<ProviderQuote> requestQuote(ShippingQuoteRequest request) {
        return behavior.apply(request);
    }
}
