package com.logistica.cotizacionenvio.provider;

import com.logistica.cotizacionenvio.domain.ShippingQuoteRequest;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedProviderClientTest {

    private static final ShippingQuoteRequest REQUEST =
            new ShippingQuoteRequest("REQ-1001", "BOG", "MDE", 10.0);

    // Sin fallo simulado, devuelve una cotizacion con el precio calculado (base + tarifa*peso)
    @Test
    void devuelveCotizacionCalculadaCuandoNoHayFallo() {
        var config = new SimulatedProviderConfig(
                "PROVIDER_TEST", Duration.ofMillis(100), Duration.ofMillis(100),
                0.0, BigDecimal.valueOf(1000), BigDecimal.valueOf(100), 2);
        var client = new SimulatedProviderClient(config);

        StepVerifier.withVirtualTime(() -> client.requestQuote(REQUEST))
                .expectSubscription()
                .thenAwait(Duration.ofMillis(100))
                .assertNext(quote -> {
                    assertThat(quote.provider()).isEqualTo("PROVIDER_TEST");
                    assertThat(quote.price()).isEqualByComparingTo(BigDecimal.valueOf(2000)); // 1000 + 100*10
                    assertThat(quote.estimatedDays()).isEqualTo(2);
                    assertThat(quote.quoteId()).startsWith("PROVIDER_TEST-");
                })
                .verifyComplete();
    }

    // Con 100% de tasa de fallo, siempre emite ProviderUnavailableException
    @Test
    void fallaConProviderUnavailableCuandoLaTasaDeFalloEsCienPorCiento() {
        var config = new SimulatedProviderConfig(
                "PROVIDER_TEST", Duration.ZERO, Duration.ZERO,
                1.0, BigDecimal.TEN, BigDecimal.ONE, 1);
        var client = new SimulatedProviderClient(config);

        StepVerifier.withVirtualTime(() -> client.requestQuote(REQUEST))
                .expectSubscription()
                .thenAwait(Duration.ofMillis(10))
                .expectErrorMatches(error -> error instanceof ProviderUnavailableException e
                        && e.getProvider().equals("PROVIDER_TEST"))
                .verify();
    }
}
