package com.logistica.cotizacionenvio.domain;

import java.time.Instant;
import java.util.List;

public record ShippingQuoteResult(
        String requestId,
        QuoteStatus status,
        ProviderQuote selected,
        List<ProviderOutcome> providerOutcomes,
        Instant createdAt
) {
}
