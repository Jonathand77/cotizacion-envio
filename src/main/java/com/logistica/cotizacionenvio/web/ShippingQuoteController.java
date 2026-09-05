package com.logistica.cotizacionenvio.web;

import com.logistica.cotizacionenvio.domain.ShippingQuoteRequest;
import com.logistica.cotizacionenvio.service.ShippingQuoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/shipping-quotes")
public class ShippingQuoteController {

    private final ShippingQuoteService service;

    public ShippingQuoteController(ShippingQuoteService service) {
        this.service = service;
    }

    @PostMapping
    public Mono<ResponseEntity<ShippingQuoteResponse>> create(@Valid @RequestBody ShippingQuoteRequest request) {
        return service.quote(request)
                .map(result -> ResponseEntity
                        .created(URI.create("/api/v1/shipping-quotes/" + result.requestId()))
                        .body(ShippingQuoteResponse.from(result)));
    }

    @GetMapping("/{requestId}")
    public Mono<ResponseEntity<Object>> find(@PathVariable String requestId) {
        return service.find(requestId)
                .map(result -> ResponseEntity.ok().<Object>body(ShippingQuoteResponse.from(result)))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .<Object>body(new ApiError("No existe una solicitud con requestId: " + requestId)));
    }
}
