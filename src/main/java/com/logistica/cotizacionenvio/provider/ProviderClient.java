package com.logistica.cotizacionenvio.provider;

import com.logistica.cotizacionenvio.domain.ProviderQuote;
import com.logistica.cotizacionenvio.domain.ShippingQuoteRequest;
import reactor.core.publisher.Mono;

public interface ProviderClient {

    String providerName();

    Mono<ProviderQuote> requestQuote(ShippingQuoteRequest request);
}
