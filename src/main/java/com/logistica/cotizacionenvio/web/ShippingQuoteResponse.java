package com.logistica.cotizacionenvio.web;

import com.logistica.cotizacionenvio.domain.ProviderQuote;
import com.logistica.cotizacionenvio.domain.QuoteStatus;
import com.logistica.cotizacionenvio.domain.ShippingQuoteResult;

/**
 * Representacion publica de un resultado de cotizacion. Omite
 * deliberadamente ProviderOutcome/failureReason (el detalle interno de
 * por que fallo cada proveedor) para no filtrar detalles de
 * implementacion al cliente; ese detalle queda solo en logs.
 */
public record ShippingQuoteResponse(
        String requestId,
        QuoteStatus status,
        ProviderQuote selected,
        String message
) {

    private static final String NO_VALID_QUOTE_MESSAGE =
            "No fue posible obtener una cotizacion valida de los proveedores disponibles en el tiempo esperado.";

    public static ShippingQuoteResponse from(ShippingQuoteResult result) {
        String message = result.status() == QuoteStatus.FAILED ? NO_VALID_QUOTE_MESSAGE : null;
        return new ShippingQuoteResponse(result.requestId(), result.status(), result.selected(), message);
    }
}
