package com.logistica.cotizacionenvio.web;

/** Forma unica de error publico para toda la API: nunca expone stack traces ni excepciones crudas. */
public record ApiError(String message) {
}
