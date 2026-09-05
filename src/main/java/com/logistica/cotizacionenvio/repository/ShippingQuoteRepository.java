package com.logistica.cotizacionenvio.repository;

import com.logistica.cotizacionenvio.domain.ShippingQuoteResult;
import reactor.core.publisher.Mono;

public interface ShippingQuoteRepository {

    Mono<ShippingQuoteResult> findByRequestId(String requestId);

    /**
     * Guarda el resultado solo si aun no existe uno para ese requestId.
     * Si ya existe (por ejemplo, por un reintento concurrente del mismo
     * requestId), se descarta el nuevo y se devuelve el que ya estaba
     * guardado - primero en escribir, gana.
     */
    Mono<ShippingQuoteResult> saveIfAbsent(ShippingQuoteResult result);
}
