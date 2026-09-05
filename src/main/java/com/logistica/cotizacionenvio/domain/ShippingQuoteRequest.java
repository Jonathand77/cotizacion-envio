package com.logistica.cotizacionenvio.domain;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ShippingQuoteRequest(

        @NotBlank(message = "requestId es obligatorio")
        String requestId,

        @NotBlank(message = "origin es obligatorio")
        String origin,

        @NotBlank(message = "destination es obligatorio")
        String destination,

        @NotNull(message = "weightKg es obligatorio")
        @Positive(message = "weightKg debe ser mayor que 0")
        Double weightKg
) {

    @AssertTrue(message = "origin y destination no pueden ser iguales")
    public boolean isOriginDifferentFromDestination() {
        return origin == null || destination == null || !origin.equalsIgnoreCase(destination);
    }
}
