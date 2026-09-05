package com.logistica.cotizacionenvio.domain;

/**
 * Resultado de consultar a un proveedor puntual: exitoso con su cotizacion,
 * o fallido con el motivo (timeout, error, etc). Se conserva junto al
 * resultado final para poder diagnosticar por que no se eligio un proveedor.
 */
public record ProviderOutcome(
        String provider,
        boolean success,
        ProviderQuote quote,
        String failureReason
) {

    public static ProviderOutcome success(ProviderQuote quote) {
        return new ProviderOutcome(quote.provider(), true, quote, null);
    }

    public static ProviderOutcome failure(String provider, String failureReason) {
        return new ProviderOutcome(provider, false, null, failureReason);
    }
}
