package com.logistica.cotizacionenvio.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Prueba de aceptacion del flujo principal (crear + consultar una
 * cotizacion) contra la aplicacion real completa: contexto de Spring
 * real, ProviderClient reales (SimulatedProviderClient), repositorio
 * real - nada mockeado.
 *
 * Las tasas de fallo de ambos proveedores se fuerzan a 0 via propiedades
 * para que el resultado sea determinista y la prueba reproducible, sin
 * depender de la aleatoriedad por defecto configurada en
 * application.yml.
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
                "providers.provider-a.failure-rate=0",
                "providers.provider-b.failure-rate=0"
        }
)
@AutoConfigureWebTestClient
class ShippingQuoteAcceptanceTest {

    @Autowired
    private WebTestClient webTestClient;

    // Flujo principal de punta a punta: POST crea la cotizacion y el GET posterior recupera el mismo resultado
    @Test
    void creaYLuegoConsultaUnaCotizacionCuandoAmbosProveedoresResponden() {
        webTestClient.post()
                .uri("/api/v1/shipping-quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"requestId":"ACC-HAPPY-1","origin":"BOG","destination":"MDE","weightKg":12.5}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/api/v1/shipping-quotes/ACC-HAPPY-1")
                .expectBody()
                .jsonPath("$.requestId").isEqualTo("ACC-HAPPY-1")
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.selected.provider").exists()
                .jsonPath("$.selected.price").exists()
                .jsonPath("$.message").doesNotExist();

        webTestClient.get()
                .uri("/api/v1/shipping-quotes/ACC-HAPPY-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.requestId").isEqualTo("ACC-HAPPY-1")
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.selected.provider").exists();
    }
}
