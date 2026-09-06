package com.logistica.cotizacionenvio.domain;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingQuoteRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    // Una solicitud con todos los campos correctos no genera violaciones de validacion
    @Test
    void aceptaUnaSolicitudValida() {
        var request = new ShippingQuoteRequest("REQ-1001", "BOG", "MDE", 12.5);

        assertThat(validator.validate(request)).isEmpty();
    }

    // requestId en blanco es rechazado por @NotBlank
    @Test
    void rechazaRequestIdEnBlanco() {
        var request = new ShippingQuoteRequest(" ", "BOG", "MDE", 12.5);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    // origin/destination en blanco son rechazados por @NotBlank
    @Test
    void rechazaOrigenODestinoEnBlanco() {
        var request = new ShippingQuoteRequest("REQ-1001", "", "MDE", 12.5);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    // weightKg nulo o menor/igual a cero es rechazado por @NotNull/@Positive
    @Test
    void rechazaPesoNuloOMenorOIgualACero() {
        assertThat(validator.validate(new ShippingQuoteRequest("REQ-1001", "BOG", "MDE", 0.0)))
                .isNotEmpty();
        assertThat(validator.validate(new ShippingQuoteRequest("REQ-1001", "BOG", "MDE", null)))
                .isNotEmpty();
    }

    // origin igual a destination es rechazado por la validacion cruzada @AssertTrue
    @Test
    void rechazaOrigenIgualADestino() {
        assertThat(validator.validate(new ShippingQuoteRequest("REQ-1001", "BOG", "BOG", 12.5)))
                .isNotEmpty();
    }
}
