package com.logistica.cotizacionenvio.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Registra los proveedores simulados como beans de ProviderClient. Al ser
 * beans separados, el orquestador (Paso 5) los recibe inyectados como
 * List<ProviderClient> sin conocer cuantos ni cuales hay.
 */
@Configuration
public class ProviderClientsConfiguration {

    @Bean
    public ProviderClient providerAClient(
            @Value("${providers.provider-a.min-latency-ms:200}") long minLatencyMs,
            @Value("${providers.provider-a.max-latency-ms:800}") long maxLatencyMs,
            @Value("${providers.provider-a.failure-rate:0.1}") double failureRate,
            @Value("${providers.provider-a.base-price:15000}") long basePrice,
            @Value("${providers.provider-a.price-per-kg:300}") long pricePerKg,
            @Value("${providers.provider-a.estimated-days:2}") int estimatedDays) {
        return new SimulatedProviderClient(new SimulatedProviderConfig(
                "PROVIDER_A",
                Duration.ofMillis(minLatencyMs),
                Duration.ofMillis(maxLatencyMs),
                failureRate,
                BigDecimal.valueOf(basePrice),
                BigDecimal.valueOf(pricePerKg),
                estimatedDays));
    }

    @Bean
    public ProviderClient providerBClient(
            @Value("${providers.provider-b.min-latency-ms:300}") long minLatencyMs,
            @Value("${providers.provider-b.max-latency-ms:1200}") long maxLatencyMs,
            @Value("${providers.provider-b.failure-rate:0.2}") double failureRate,
            @Value("${providers.provider-b.base-price:14000}") long basePrice,
            @Value("${providers.provider-b.price-per-kg:320}") long pricePerKg,
            @Value("${providers.provider-b.estimated-days:3}") int estimatedDays) {
        return new SimulatedProviderClient(new SimulatedProviderConfig(
                "PROVIDER_B",
                Duration.ofMillis(minLatencyMs),
                Duration.ofMillis(maxLatencyMs),
                failureRate,
                BigDecimal.valueOf(basePrice),
                BigDecimal.valueOf(pricePerKg),
                estimatedDays));
    }
}
