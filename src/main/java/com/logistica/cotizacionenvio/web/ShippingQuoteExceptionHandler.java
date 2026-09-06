package com.logistica.cotizacionenvio.web;

import com.logistica.cotizacionenvio.domain.ShippingQuoteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/**
 * Maneja los errores de la capa web con un unico contrato de respuesta
 * (ApiError). Ninguna excepcion cruda ni stack trace llega al cliente: el
 * detalle real de cualquier fallo inesperado se registra en el log del
 * servidor, no en la respuesta HTTP.
 *
 * ResponseStatusException se maneja aparte de la excepcion generica para
 * preservar el codigo HTTP que Spring ya calculo correctamente (ej. 400
 * por JSON mal formado, 415 por Content-Type no soportado) en vez de
 * degradarlo a un 500 generico. Se usa la frase estandar del codigo HTTP
 * como mensaje en vez de ex.getReason(), que en algunos casos (ej. 415)
 * incluye el nombre completo de la clase interna de dominio.
 */
@RestControllerAdvice
public class ShippingQuoteExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ShippingQuoteExceptionHandler.class);

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiError> handleValidation(WebExchangeBindException ex) {
        String message = ex.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.info("[{}] solicitud rechazada por validacion: {}", requestIdFrom(ex), message);
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    private String requestIdFrom(WebExchangeBindException ex) {
        return ex.getBindingResult().getTarget() instanceof ShippingQuoteRequest request
                ? request.requestId()
                : "desconocido";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        String message = HttpStatus.valueOf(ex.getStatusCode().value()).getReasonPhrase();
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Error inesperado procesando una solicitud de cotizacion", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Ocurrio un error inesperado. Intenta nuevamente mas tarde."));
    }
}
