package com.logistica.cotizacionenvio.repository;

import com.logistica.cotizacionenvio.domain.ShippingQuoteResult;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacena el estado de las solicitudes en memoria, dentro del propio
 * proceso (ver README, seccion "Decisiones de diseno", para la
 * justificacion de por que no se usa una base de datos externa).
 * computeIfAbsent es atomico por clave en ConcurrentHashMap, lo cual
 * garantiza que ante escrituras concurrentes con el mismo requestId
 * solo una prevalezca.
 */
@Repository
public class InMemoryShippingQuoteRepository implements ShippingQuoteRepository {

    private final Map<String, ShippingQuoteResult> store = new ConcurrentHashMap<>();

    @Override
    public Mono<ShippingQuoteResult> findByRequestId(String requestId) {
        return Mono.justOrEmpty(store.get(requestId));
    }

    @Override
    public Mono<ShippingQuoteResult> saveIfAbsent(ShippingQuoteResult result) {
        return Mono.fromSupplier(() -> store.computeIfAbsent(result.requestId(), id -> result));
    }
}
