package com.logistica.cotizacionenvio.service;

import com.logistica.cotizacionenvio.domain.ProviderQuote;
import com.logistica.cotizacionenvio.domain.ShippingQuoteRequest;
import com.logistica.cotizacionenvio.domain.ShippingQuoteResult;
import com.logistica.cotizacionenvio.repository.InMemoryShippingQuoteRepository;
import com.logistica.cotizacionenvio.support.FakeProviderClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingQuoteServiceTest {

    @Test
    void unaSegundaSolicitudConElMismoRequestIdNoConsultaProveedoresDeNuevo() {
        var counter = new AtomicInteger();
        var quote = new ProviderQuote("PROVIDER_A", "A-1", BigDecimal.valueOf(15000), 2);
        var service = serviceWith(counter, quote);
        var request = new ShippingQuoteRequest("REQ-1", "BOG", "MDE", 10.0);

        var primero = service.quote(request).block();
        var segundo = service.quote(request).block();

        assertThat(primero).isEqualTo(segundo);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void solicitudesConRequestIdDistintoSeProcesanIndependientemente() {
        var counter = new AtomicInteger();
        var quote = new ProviderQuote("PROVIDER_A", "A-1", BigDecimal.valueOf(15000), 2);
        var service = serviceWith(counter, quote);

        service.quote(new ShippingQuoteRequest("REQ-1", "BOG", "MDE", 10.0)).block();
        service.quote(new ShippingQuoteRequest("REQ-2", "BOG", "MDE", 10.0)).block();

        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void solicitudesConcurrentesConElMismoRequestIdSoloDisparanUnaOrquestacion() throws InterruptedException {
        var counter = new AtomicInteger();
        var quote = new ProviderQuote("PROVIDER_A", "A-1", BigDecimal.valueOf(15000), 2);
        var service = serviceWith(counter, quote);
        var request = new ShippingQuoteRequest("REQ-CONCURRENTE", "BOG", "MDE", 10.0);

        int hilos = 30;
        ExecutorService executor = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch partida = new CountDownLatch(1);
        Set<ShippingQuoteResult> resultados = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < hilos; i++) {
            executor.submit(() -> {
                listos.countDown();
                awaitUninterruptibly(partida);
                resultados.add(service.quote(request).block());
            });
        }

        listos.await();
        partida.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(resultados).hasSize(1);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void findDevuelveElResultadoGuardadoParaConsultaPosterior() {
        var quote = new ProviderQuote("PROVIDER_A", "A-1", BigDecimal.valueOf(15000), 2);
        var service = serviceWith(new AtomicInteger(), quote);
        var request = new ShippingQuoteRequest("REQ-1", "BOG", "MDE", 10.0);

        var guardado = service.quote(request).block();
        var encontrado = service.find("REQ-1").block();

        assertThat(encontrado).isEqualTo(guardado);
    }

    @Test
    void findDevuelveVacioParaUnRequestIdDesconocido() {
        var service = serviceWith(new AtomicInteger(), new ProviderQuote("PROVIDER_A", "A-1", BigDecimal.ONE, 1));

        assertThat(service.find("NO-EXISTE").blockOptional()).isEmpty();
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ShippingQuoteService serviceWith(AtomicInteger counter, ProviderQuote quote) {
        var orchestrator = new ShippingQuoteOrchestrator(
                List.of(FakeProviderClient.counting("PROVIDER_A", quote, counter)), 1500L);
        return new ShippingQuoteService(orchestrator, new InMemoryShippingQuoteRepository());
    }
}
