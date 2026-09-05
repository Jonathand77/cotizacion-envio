package com.logistica.cotizacionenvio.service;

import com.logistica.cotizacionenvio.domain.ShippingQuoteRequest;
import com.logistica.cotizacionenvio.domain.ShippingQuoteResult;
import com.logistica.cotizacionenvio.repository.ShippingQuoteRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fachada idempotente sobre el orquestador. Por cada requestId se ejecuta
 * como maximo UNA orquestacion: si ya hay una en curso o ya completada
 * para ese requestId, toda llamada (concurrente o posterior) comparte el
 * mismo Mono cacheado en vez de volver a consultar proveedores. El
 * resultado tambien se persiste en el ShippingQuoteRepository para poder
 * recuperarlo por GET sin depender del payload original.
 *
 * El requestId es la unica clave de idempotencia: si llegara un payload
 * distinto bajo el mismo requestId, se ignora y se devuelve el resultado
 * ya calculado para ese id - se asume que un mismo requestId siempre
 * representa la misma solicitud logica (reintento del consumidor).
 *
 * El mapa de Monos cacheados no se depura (mismo trade-off ya asumido
 * para el repositorio en memoria: vive mientras viva el proceso).
 * Eliminar la entrada apenas termina la orquestacion abriria una ventana
 * de carrera donde una solicitud concurrente que llega justo despues del
 * borrado dispararia una segunda orquestacion innecesaria - se evita por
 * completo no borrando nunca.
 */
@Service
public class ShippingQuoteService {

    private final ShippingQuoteOrchestrator orchestrator;
    private final ShippingQuoteRepository repository;
    private final Map<String, Mono<ShippingQuoteResult>> quotesByRequestId = new ConcurrentHashMap<>();

    public ShippingQuoteService(ShippingQuoteOrchestrator orchestrator, ShippingQuoteRepository repository) {
        this.orchestrator = orchestrator;
        this.repository = repository;
    }

    public Mono<ShippingQuoteResult> quote(ShippingQuoteRequest request) {
        return quotesByRequestId.computeIfAbsent(request.requestId(), id -> orchestrator.orchestrate(request)
                .flatMap(repository::saveIfAbsent)
                .cache());
    }

    public Mono<ShippingQuoteResult> find(String requestId) {
        return repository.findByRequestId(requestId);
    }
}
