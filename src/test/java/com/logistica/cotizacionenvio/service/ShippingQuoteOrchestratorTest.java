package com.logistica.cotizacionenvio.service;

import com.logistica.cotizacionenvio.domain.ProviderQuote;
import com.logistica.cotizacionenvio.domain.QuoteStatus;
import com.logistica.cotizacionenvio.domain.ShippingQuoteRequest;
import com.logistica.cotizacionenvio.provider.ProviderClient;
import com.logistica.cotizacionenvio.provider.ProviderUnavailableException;
import com.logistica.cotizacionenvio.support.FakeProviderClient;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingQuoteOrchestratorTest {

    private static final ShippingQuoteRequest REQUEST = new ShippingQuoteRequest("REQ-1", "BOG", "MDE", 10.0);

    @Test
    void seleccionaLaCotizacionDeMenorPrecioCuandoAmbosProveedoresResponden() {
        var barata = new ProviderQuote("PROVIDER_A", "A-1", BigDecimal.valueOf(15000), 2);
        var cara = new ProviderQuote("PROVIDER_B", "B-1", BigDecimal.valueOf(18000), 1);
        var orchestrator = orchestratorWith(
                FakeProviderClient.returning("PROVIDER_A", barata),
                FakeProviderClient.returning("PROVIDER_B", cara));

        StepVerifier.create(orchestrator.orchestrate(REQUEST))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(QuoteStatus.COMPLETED);
                    assertThat(result.selected()).isEqualTo(barata);
                    assertThat(result.providerOutcomes()).hasSize(2);
                })
                .verifyComplete();
    }

    @Test
    void antePrecioEmpatadoDesempataPorMenorTiempoEstimado() {
        var lenta = new ProviderQuote("PROVIDER_A", "A-1", BigDecimal.valueOf(15000), 4);
        var rapida = new ProviderQuote("PROVIDER_B", "B-1", BigDecimal.valueOf(15000), 2);
        var orchestrator = orchestratorWith(
                FakeProviderClient.returning("PROVIDER_A", lenta),
                FakeProviderClient.returning("PROVIDER_B", rapida));

        StepVerifier.create(orchestrator.orchestrate(REQUEST))
                .assertNext(result -> assertThat(result.selected()).isEqualTo(rapida))
                .verifyComplete();
    }

    @Test
    void antePrecioYDiasEmpatadosDesempataPorNombreDeProveedor() {
        var b = new ProviderQuote("PROVIDER_B", "B-1", BigDecimal.valueOf(15000), 2);
        var a = new ProviderQuote("PROVIDER_A", "A-1", BigDecimal.valueOf(15000), 2);
        var orchestrator = orchestratorWith(
                FakeProviderClient.returning("PROVIDER_B", b),
                FakeProviderClient.returning("PROVIDER_A", a));

        StepVerifier.create(orchestrator.orchestrate(REQUEST))
                .assertNext(result -> assertThat(result.selected()).isEqualTo(a))
                .verifyComplete();
    }

    @Test
    void toleraQueUnProveedorFalleYUsaElOtro() {
        var buena = new ProviderQuote("PROVIDER_B", "B-1", BigDecimal.valueOf(17000), 3);
        var orchestrator = orchestratorWith(
                FakeProviderClient.failingWith("PROVIDER_A", new ProviderUnavailableException("PROVIDER_A")),
                FakeProviderClient.returning("PROVIDER_B", buena));

        StepVerifier.create(orchestrator.orchestrate(REQUEST))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(QuoteStatus.COMPLETED);
                    assertThat(result.selected()).isEqualTo(buena);
                    assertThat(result.providerOutcomes()).anySatisfy(outcome -> {
                        assertThat(outcome.provider()).isEqualTo("PROVIDER_A");
                        assertThat(outcome.success()).isFalse();
                        assertThat(outcome.failureReason()).contains("PROVIDER_A");
                    });
                })
                .verifyComplete();
    }

    @Test
    void marcaLaSolicitudComoFallidaCuandoAmbosProveedoresFallan() {
        var orchestrator = orchestratorWith(
                FakeProviderClient.failingWith("PROVIDER_A", new ProviderUnavailableException("PROVIDER_A")),
                FakeProviderClient.failingWith("PROVIDER_B", new ProviderUnavailableException("PROVIDER_B")));

        StepVerifier.create(orchestrator.orchestrate(REQUEST))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(QuoteStatus.FAILED);
                    assertThat(result.selected()).isNull();
                    assertThat(result.providerOutcomes()).allSatisfy(o -> assertThat(o.success()).isFalse());
                })
                .verifyComplete();
    }

    @Test
    void tratamosUnProveedorLentoComoFalloPorTimeout() {
        var orchestrator = new ShippingQuoteOrchestrator(
                List.of(
                        FakeProviderClient.delayedBy("PROVIDER_A", Duration.ofSeconds(10),
                                new ProviderQuote("PROVIDER_A", "A-1", BigDecimal.valueOf(15000), 2)),
                        FakeProviderClient.returning("PROVIDER_B",
                                new ProviderQuote("PROVIDER_B", "B-1", BigDecimal.valueOf(17000), 3))),
                1000L);

        StepVerifier.withVirtualTime(() -> orchestrator.orchestrate(REQUEST))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(10))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(QuoteStatus.COMPLETED);
                    assertThat(result.selected().provider()).isEqualTo("PROVIDER_B");
                    assertThat(result.providerOutcomes()).anySatisfy(outcome -> {
                        assertThat(outcome.provider()).isEqualTo("PROVIDER_A");
                        assertThat(outcome.success()).isFalse();
                        assertThat(outcome.failureReason()).contains("timeout");
                    });
                })
                .verifyComplete();
    }

    private static ShippingQuoteOrchestrator orchestratorWith(ProviderClient... clients) {
        return new ShippingQuoteOrchestrator(List.of(clients), 1500L);
    }
}
