package com.logistica.cotizacionenvio.provider;

import java.math.BigDecimal;
import java.time.Duration;

public record SimulatedProviderConfig(
        String providerName,
        Duration minLatency,
        Duration maxLatency,
        double failureRate,
        BigDecimal basePrice,
        BigDecimal pricePerKg,
        int estimatedDays
) {
}
