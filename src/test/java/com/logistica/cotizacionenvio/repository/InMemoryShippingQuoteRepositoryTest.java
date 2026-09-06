package com.logistica.cotizacionenvio.repository;

import com.logistica.cotizacionenvio.domain.ProviderQuote;
import com.logistica.cotizacionenvio.domain.QuoteStatus;
import com.logistica.cotizacionenvio.domain.ShippingQuoteResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryShippingQuoteRepositoryTest {

    private final InMemoryShippingQuoteRepository repository = new InMemoryShippingQuoteRepository();

    // Consultar un requestId nunca guardado devuelve un Mono vacio, no un error
    @Test
    void devuelveVacioCuandoNoExisteLaSolicitud() {
        assertThat(repository.findByRequestId("REQ-DESCONOCIDO").blockOptional()).isEmpty();
    }

    // Un resultado guardado se puede recuperar despues por su requestId
    @Test
    void guardaYRecuperaUnResultado() {
        var result = resultFor("REQ-1", "A-1");

        repository.saveIfAbsent(result).block();

        assertThat(repository.findByRequestId("REQ-1").block()).isEqualTo(result);
    }

    // saveIfAbsent descarta el segundo valor y devuelve el primero ya guardado (primero en escribir, gana)
    @Test
    void saveIfAbsentNoSobrescribeUnResultadoYaGuardado() {
        var primero = resultFor("REQ-1", "A-1");
        var segundo = resultFor("REQ-1", "B-2");

        var guardadoPrimero = repository.saveIfAbsent(primero).block();
        var guardadoSegundo = repository.saveIfAbsent(segundo).block();

        assertThat(guardadoPrimero).isEqualTo(primero);
        assertThat(guardadoSegundo).isEqualTo(primero);
        assertThat(repository.findByRequestId("REQ-1").block()).isEqualTo(primero);
    }

    // Con 50 hilos escribiendo concurrentemente el mismo requestId, solo un resultado prevalece
    @Test
    void esAtomicoAnteEscriturasConcurrentesConElMismoRequestId() throws InterruptedException {
        int hilos = 50;
        ExecutorService executor = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch partida = new CountDownLatch(1);
        Set<ShippingQuoteResult> ganadores = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < hilos; i++) {
            ShippingQuoteResult candidato = resultFor("REQ-CONCURRENTE", "QUOTE-" + i);
            executor.submit(() -> {
                listos.countDown();
                awaitUninterruptibly(partida);
                ganadores.add(repository.saveIfAbsent(candidato).block());
            });
        }

        listos.await();
        partida.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(ganadores).hasSize(1);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ShippingQuoteResult resultFor(String requestId, String quoteId) {
        var quote = new ProviderQuote("PROVIDER_A", quoteId, BigDecimal.valueOf(1000), 2);
        return new ShippingQuoteResult(requestId, QuoteStatus.COMPLETED, quote, List.of(), Instant.now());
    }
}
