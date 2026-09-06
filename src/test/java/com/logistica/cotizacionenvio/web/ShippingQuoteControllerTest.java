package com.logistica.cotizacionenvio.web;

import com.logistica.cotizacionenvio.domain.ProviderQuote;
import com.logistica.cotizacionenvio.domain.QuoteStatus;
import com.logistica.cotizacionenvio.domain.ShippingQuoteResult;
import com.logistica.cotizacionenvio.service.ShippingQuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Slice de la capa web unicamente: ShippingQuoteService se simula con
 * @MockBean para no depender de los proveedores reales (aleatorios) ni
 * repetir aqui las pruebas de orquestacion/idempotencia, ya cubiertas en
 * ShippingQuoteOrchestratorTest y ShippingQuoteServiceTest.
 */
@WebFluxTest(ShippingQuoteController.class)
class ShippingQuoteControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ShippingQuoteService service;

    // POST exitoso: responde 201 + Location, con la cotizacion seleccionada en el cuerpo
    @Test
    void creaUnaCotizacionExitosaYRespondeConLaAlternativaSeleccionada() {
        var quote = new ProviderQuote("PROVIDER_A", "A-1", BigDecimal.valueOf(15000), 2);
        var result = new ShippingQuoteResult("REQ-1001", QuoteStatus.COMPLETED, quote, List.of(), Instant.now());
        when(service.quote(any())).thenReturn(Mono.just(result));

        webTestClient.post()
                .uri("/api/v1/shipping-quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"requestId":"REQ-1001","origin":"BOG","destination":"MDE","weightKg":12.5}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/api/v1/shipping-quotes/REQ-1001")
                .expectBody()
                .jsonPath("$.requestId").isEqualTo("REQ-1001")
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.selected.provider").isEqualTo("PROVIDER_A")
                .jsonPath("$.message").doesNotExist();
    }

    // Cuando el resultado es FAILED, responde 201 igual, con mensaje generico y sin "selected"
    @Test
    void respondeConMensajeControladoCuandoNingunProveedorDioCotizacionValida() {
        var result = new ShippingQuoteResult("REQ-1002", QuoteStatus.FAILED, null, List.of(), Instant.now());
        when(service.quote(any())).thenReturn(Mono.just(result));

        webTestClient.post()
                .uri("/api/v1/shipping-quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"requestId":"REQ-1002","origin":"BOG","destination":"MDE","weightKg":12.5}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FAILED")
                .jsonPath("$.selected").doesNotExist()
                .jsonPath("$.message").isNotEmpty();
    }

    // Una solicitud invalida responde 400 con un ApiError limpio, no la excepcion de validacion cruda
    @Test
    void rechazaUnaSolicitudInvalidaConCuerpoDeErrorLimpio() {
        webTestClient.post()
                .uri("/api/v1/shipping-quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"requestId":"REQ-1003","origin":"BOG","destination":"BOG","weightKg":-1}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isNotEmpty();
    }

    // GET de un requestId conocido responde 200 con el resultado guardado
    @Test
    void devuelveLaCotizacionExistenteAlConsultarPorRequestId() {
        var quote = new ProviderQuote("PROVIDER_B", "B-1", BigDecimal.valueOf(17000), 3);
        var result = new ShippingQuoteResult("REQ-2001", QuoteStatus.COMPLETED, quote, List.of(), Instant.now());
        when(service.find(eq("REQ-2001"))).thenReturn(Mono.just(result));

        webTestClient.get()
                .uri("/api/v1/shipping-quotes/REQ-2001")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.requestId").isEqualTo("REQ-2001")
                .jsonPath("$.selected.price").isEqualTo(17000);
    }

    // GET de un requestId desconocido responde 404 con un mensaje claro
    @Test
    void devuelve404CuandoElRequestIdNoExiste() {
        when(service.find(eq("NO-EXISTE"))).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/v1/shipping-quotes/NO-EXISTE")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isNotEmpty();
    }

    // Content-Type no soportado preserva el 415 real de Spring, sin exponer el paquete interno
    @Test
    void rechazaUnContentTypeNoSoportadoPreservandoEl415() {
        webTestClient.post()
                .uri("/api/v1/shipping-quotes")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("{\"requestId\":\"REQ-X\",\"origin\":\"BOG\",\"destination\":\"MDE\",\"weightKg\":1}")
                .exchange()
                .expectStatus().value(status -> org.assertj.core.api.Assertions.assertThat(status).isEqualTo(415))
                .expectBody()
                .jsonPath("$.message").value(
                        (String message) -> org.assertj.core.api.Assertions.assertThat(message)
                                .isNotEmpty()
                                .doesNotContain("com.logistica.cotizacionenvio"),
                        String.class);
    }

    // Una excepcion no anticipada responde 500 generico, sin exponer su mensaje ni su tipo
    @Test
    void unaExcepcionInesperadaResponde500SinFiltrarElDetalleInterno() {
        when(service.quote(any())).thenReturn(
                Mono.error(new IllegalStateException("conexion a base de datos secreta://user:pass@host caida")));

        webTestClient.post()
                .uri("/api/v1/shipping-quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"requestId":"REQ-1004","origin":"BOG","destination":"MDE","weightKg":12.5}
                        """)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.message").value(
                        (String message) -> {
                            org.assertj.core.api.Assertions.assertThat(message)
                                    .doesNotContain("secreta")
                                    .doesNotContain("IllegalStateException");
                        },
                        String.class);
    }
}
