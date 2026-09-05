package com.logistica.cotizacionenvio.domain;

import java.math.BigDecimal;

public record ProviderQuote(
        String provider,
        String quoteId,
        BigDecimal price,
        int estimatedDays
) {
}
