package com.logistica.cotizacionenvio.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Escenario de fallo relevante, de punta a punta contra la app real: un
 * proveedor cae (tasa de fallo 100%) y el otro responde con normalidad.
 * Es la caracteristica de resiliencia central de toda la solucion, por
 * eso se prueba tambien a nivel de aceptacion y no solo unitariamente
 * (ver ShippingQuoteOrchestratorTest.toleraQueUnProveedorFalleYUsaElOtro
 * para la version aislada con dobles de prueba).
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
                "providers.provider-a.failure-rate=1",
                "providers.provider-b.failure-rate=0"
        }
)
@AutoConfigureWebTestClient
class ShippingQuoteProviderFailureAcceptanceTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void toleraQueUnProveedorFalleYCompletaConElOtroEnElFlujoReal() {
        webTestClient.post()
                .uri("/api/v1/shipping-quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"requestId":"ACC-FAIL-1","origin":"BOG","destination":"MDE","weightKg":8}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.requestId").isEqualTo("ACC-FAIL-1")
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.selected.provider").isEqualTo("PROVIDER_B");

        webTestClient.get()
                .uri("/api/v1/shipping-quotes/ACC-FAIL-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.selected.provider").isEqualTo("PROVIDER_B");
    }
}
