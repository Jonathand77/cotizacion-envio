# Arquitectura

Explicación de cómo está construido el servicio, paquete por paquete, en el orden en que se
fue implementando.
Para el razonamiento detrás de cada decisión (alternativas consideradas, trade-offs), ver
[`DECISIONES_TECNICAS.md`](./DECISIONES_TECNICAS.md) — aquí solo se enlaza donde aplica, para
no repetir el mismo contenido dos veces.

## Modelo de dominio

Paquete `com.logistica.cotizacionenvio.domain`:

- `ShippingQuoteRequest`: entrada de la solicitud (`requestId`, `origin`, `destination`,
  `weightKg`), validada con Bean Validation antes de procesar. `weightKg` debe ser mayor
  que cero, y `origin`/`destination` son obligatorios y no pueden ser iguales entre sí
  (validación cruzada vía `@AssertTrue`, sin distinguir mayúsculas/minúsculas).
- `ProviderQuote`: cotización devuelta por un proveedor (`provider`, `quoteId`, `price`,
  `estimatedDays`).
- `ProviderOutcome`: resultado de consultar a un proveedor puntual — éxito con su cotización,
  o fallo con el motivo (timeout, error). Permite diagnosticar por qué no se usó un proveedor.
- `QuoteStatus`: estado final de la operación (`COMPLETED` si hubo al menos una cotización
  válida, `FAILED` si ambos proveedores fallaron).
- `ShippingQuoteResult`: resultado agregado que se guarda y se devuelve — `requestId`,
  `status`, cotización `selected`, y el detalle `providerOutcomes` de ambos proveedores.

## Proveedores simulados

Paquete `com.logistica.cotizacionenvio.provider`:

- `ProviderClient`: puerto (interfaz) que expone `requestQuote(request)` — la orquestación
  (Paso 5) dependerá solo de esta interfaz, no de la implementación concreta.
- `SimulatedProviderClient`: implementación que simula la respuesta de un proveedor real
  con **latencia variable** (`Mono.delay` entre un mínimo y un máximo configurables) y una
  **probabilidad de fallo** configurable (`ProviderUnavailableException`), calculando el
  precio como `basePrice + pricePerKg * weightKg`.
- `ProviderClientsConfiguration`: registra `PROVIDER_A` y `PROVIDER_B` como dos beans
  `ProviderClient` con perfiles distintos, leídos de `application.yml` (sección `providers`).
  Al ser beans separados del mismo tipo, el orquestador podrá inyectar `List<ProviderClient>`
  sin conocer cuántos proveedores hay ni sus detalles.

Por qué simulación in-process y no un servidor mock: ver
[`DECISIONES_TECNICAS.md` #2](./DECISIONES_TECNICAS.md#2-simulación-de-proveedores-in-process-vs-servidor-mock).

## Almacenamiento de estado

Paquete `com.logistica.cotizacionenvio.repository`:

- `ShippingQuoteRepository`: puerto con `findByRequestId` (para el `GET`) y `saveIfAbsent`
  (guarda solo si el `requestId` no existe aún — si ya existe, descarta el nuevo valor y
  devuelve el que ya estaba guardado).
- `InMemoryShippingQuoteRepository`: implementación con `ConcurrentHashMap`, usando
  `computeIfAbsent` para que `saveIfAbsent` sea **atómico por `requestId`** — verificado
  con un test de 50 hilos escribiendo concurrentemente el mismo `requestId`: solo uno
  prevalece.

Por qué memoria en vez de SQL: ver
[`DECISIONES_TECNICAS.md` #1](./DECISIONES_TECNICAS.md#1-almacenamiento-del-estado-de-las-solicitudes-memoria-vs-sql).

## Orquestación y selección

`com.logistica.cotizacionenvio.service.ShippingQuoteOrchestrator`:

- Consulta a **todos** los `ProviderClient` inyectados **en paralelo**, usando
  `Flux.fromIterable(providerClients).flatMap(...)` — `flatMap` suscribe a todos los
  proveedores de forma concurrente (a diferencia de `concatMap`, que sí sería secuencial).
- Cada proveedor tiene un `timeout` configurable (`orchestration.provider-timeout-ms`,
  1500 ms por defecto) y un `onErrorResume` que convierte **cualquier** fallo (timeout,
  `ProviderUnavailableException`, o cualquier otra excepción) en un `ProviderOutcome` de
  fallo — un proveedor caído nunca tumba la solicitud completa.
- Selecciona la cotización ganadora entre las exitosas.

Por qué composición reactiva nativa en vez de hilos/`CompletableFuture`, y por qué
`.timeout()`/`.onErrorResume()` de Reactor en vez de Resilience4j: ver
[`DECISIONES_TECNICAS.md` #3](./DECISIONES_TECNICAS.md#3-consulta-paralela-a-proveedores-reactivo-nativo-vs-hiloscompletablefuture)
y [#4](./DECISIONES_TECNICAS.md#4-tolerancia-a-fallos-de-proveedor-operadores-de-reactor-vs-resilience4j).

### Regla de selección y desempate

1. **Menor precio.**
2. Si hay empate en precio: **menor tiempo estimado de entrega** — a igual costo, más
   valor para el cliente de logística es entregar más rápido.
3. Si aun así persiste el empate (mismo precio y mismos días): **nombre de proveedor**
   (orden alfabético) — un desempate final puramente determinista, para que la selección
   sea siempre reproducible ante los mismos insumos (importante también de cara a la
   idempotencia del Paso 6).

Alternativas de desempate descartadas y por qué: ver
[`DECISIONES_TECNICAS.md` #5](./DECISIONES_TECNICAS.md#5-regla-de-selección-y-desempate-de-cotizaciones).

Si ningún proveedor responde con éxito, el resultado queda en estado `FAILED` con
`selected = null`, conservando igualmente el detalle de por qué falló cada proveedor
(`providerOutcomes`).

## Idempotencia

`com.logistica.cotizacionenvio.service.ShippingQuoteService`:

- Por cada `requestId` se ejecuta **como máximo una orquestación**. Se usa
  `ConcurrentHashMap#computeIfAbsent` sobre un mapa `requestId → Mono<ShippingQuoteResult>`
  cacheado (`.cache()`): toda llamada — ya sea un reintento posterior o una solicitud
  concurrente con el mismo `requestId` — recibe el mismo `Mono`, sin volver a consultar
  proveedores.
- El resultado también se persiste en `ShippingQuoteRepository` (Paso 4), para que el
  `GET /api/v1/shipping-quotes/{requestId}` (Paso 7) pueda recuperarlo sin depender del
  payload original de la solicitud.
- El `requestId` es la **única** clave de idempotencia: si llegara un payload distinto bajo
  el mismo `requestId`, se ignora y se devuelve el resultado ya calculado — se asume que un
  mismo `requestId` siempre representa la misma solicitud lógica (reintento del consumidor).

**Bug encontrado y corregido durante el desarrollo**: la primera versión comprobaba primero
el repositorio y, si estaba vacío, registraba la orquestación en un mapa de "en vuelo" que
se auto-eliminaba al terminar (`doFinally`). Con un proveedor que responde muy rápido (sin
latencia), la orquestación podía completarse y auto-eliminarse del mapa **antes** de que un
hilo concurrente llegara a registrarse, disparando una segunda orquestación innecesaria (el
resultado final seguía siendo consistente gracias a `saveIfAbsent`, pero sí se duplicaba
trabajo). Se detectó corriendo el test de concurrencia varias veces seguidas — falló ~4 de
cada 5 corridas. La solución final no depura nunca la entrada del mapa, eliminando la ventana
de carrera por completo en vez de solo reducirla. Verificado con 10 corridas consecutivas del
test de concurrencia sin fallos. Detalle de la decisión: ver
[`DECISIONES_TECNICAS.md` #6](./DECISIONES_TECNICAS.md#6-mecanismo-de-idempotencia).

## Endpoints REST

Paquete `com.logistica.cotizacionenvio.web`:

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/shipping-quotes` | Crea/procesa una solicitud de cotización |
| `GET` | `/api/v1/shipping-quotes/{requestId}` | Recupera el resultado conocido de una solicitud |

**`POST /api/v1/shipping-quotes`** — valida el cuerpo (`@Valid`), delega en
`ShippingQuoteService.quote(...)` (Paso 6, idempotente) y responde:

- **`201 Created`** con `Location: /api/v1/shipping-quotes/{requestId}` y el cuerpo, tanto si
  el resultado es `COMPLETED` (con `selected`) como si es `FAILED` (con `message` genérico).
  Por qué `FAILED` también es `201` y no un `5xx`: ver
  [`DECISIONES_TECNICAS.md` #7](./DECISIONES_TECNICAS.md#7-representación-de-ningún-proveedor-válido-en-la-api).
- **`400 Bad Request`** si la validación falla (`weightKg` ≤ 0, `origin`/`destination` vacíos
  o iguales), con un cuerpo `{"message": "..."}` que junta los mensajes de validación por
  campo — **nunca** una excepción cruda ni un stack trace.

**`GET /api/v1/shipping-quotes/{requestId}`** — delega en `ShippingQuoteService.find(...)`:

- **`200 OK`** con el mismo cuerpo que el POST, si el `requestId` es conocido.
- **`404 Not Found`** con `{"message": "No existe una solicitud con requestId: ..."}` si no lo es.

`ShippingQuoteResponse` omite deliberadamente `providerOutcomes`/`failureReason` (el detalle
crudo de por qué falló cada proveedor) — eso es información operativa interna para
diagnóstico (logs, Paso 9), no algo que deba viajar en la respuesta HTTP pública.

## Manejo de errores y resiliencia

`ShippingQuoteExceptionHandler` cierra el mapeo de errores con un contrato único
(`ApiError{message}`) para toda la API:

| Situación | Excepción | HTTP | Mensaje al cliente |
|---|---|---|---|
| Validación fallida (`weightKg` ≤ 0, `origin`/`destination` vacíos o iguales) | `WebExchangeBindException` | 400 | Mensajes de campo (los que nosotros definimos, seguros de exponer) |
| Cuerpo JSON mal formado, `Content-Type` no soportado, etc. | `ResponseStatusException` (y subtipos) | El que Spring ya calculó (400, 415, …) | Frase estándar del código HTTP — **no** `ex.getReason()` crudo |
| `requestId` desconocido en `GET` | — (manejado directo en el controller) | 404 | Mensaje con el `requestId` buscado |
| Cualquier excepción no anticipada | `Exception` (catch-all) | 500 | Mensaje genérico fijo; el detalle real se registra con `log.error(...)` en el servidor |

**Dos bugs de fuga de información encontrados y corregidos mientras se construía esto**
(ambos detectados probando manualmente los endpoints con `curl`, no solo con tests):

1. El primer catch-all (`@ExceptionHandler(Exception.class)`) capturaba **todo**, incluyendo
   excepciones que Spring ya clasifica con un código HTTP correcto (ej.
   `UnsupportedMediaTypeStatusException` → 415), y las degradaba a un `500` genérico. Se
   corrigió agregando un handler específico para `ResponseStatusException` que preserva el
   código HTTP original.
2. Ese mismo handler, al usar `ex.getReason()`, exponía el nombre completo de la clase interna
   de dominio en el mensaje (`"...not supported for bodyType=com.logistica.cotizacionenvio.domain.ShippingQuoteRequest"`).
   Se corrigió usando la frase estándar del código HTTP (`HttpStatus.getReasonPhrase()`) en vez
   del mensaje crudo de Spring.

Ambos casos están cubiertos por tests que verifican explícitamente que el mensaje de error
**no** contiene el paquete interno (`com.logistica.cotizacionenvio`) ni el nombre/mensaje de la
excepción original.

**Defensa adicional en `application.yml`**: `server.error.include-message/include-stacktrace: never`,
para que ni siquiera el manejador de error por defecto de Spring exponga detalles si algo
ocurriera fuera del alcance de `ShippingQuoteExceptionHandler`.

## Pruebas de aceptación

Paquete de test `com.logistica.cotizacionenvio.acceptance` — a diferencia de todos los tests
anteriores (unitarios, o de la capa web con `@MockBean`), estas corren contra la aplicación
**real** completa: contexto de Spring real, `SimulatedProviderClient` real (no mocks),
`InMemoryShippingQuoteRepository` real.

- `ShippingQuoteAcceptanceTest`: flujo principal — `POST` crea una cotización, `GET`
  posterior recupera el mismo resultado.
- `ShippingQuoteProviderFailureAcceptanceTest`: el escenario de fallo más relevante de toda
  la solución — un proveedor cae (100% de tasa de fallo) y el otro responde con normalidad;
  se verifica que la respuesta sea igualmente `COMPLETED` con la cotización del proveedor
  sano, de punta a punta.

**Cómo se logra que sean reproducibles** a pesar de correr contra proveedores "reales": las
tasas de fallo de `PROVIDER_A`/`PROVIDER_B` se sobrescriben por prueba vía
`@SpringBootTest(properties = {...})` (0% y 100% según el escenario), en vez de depender de
la aleatoriedad configurada por defecto en `application.yml`. La latencia sigue siendo
variable (real), pero eso solo afecta cuánto tarda la prueba, no si pasa o falla — verificado
corriendo ambas pruebas 3 veces seguidas sin fallos.

**Qué se dejó fuera y por qué**: no se implementaron pruebas de performance (el enunciado las
marca como opcionales, "si el tiempo lo permite"). El tiempo se priorizó en las pruebas que sí
se hicieron, porque esas fueron las que **realmente encontraron problemas reales en el
código**: el test de concurrencia de idempotencia destapó una condición de carrera real (ver
sección "Idempotencia" más abajo), y las pruebas de manejo de errores destaparon 2 fugas de
información real (ver sección "Manejo de errores y resiliencia"). Profundizar en performance
sin haber cerrado esos hallazgos habría sido invertir tiempo en algo opcional mientras
quedaban vulnerabilidades reales sin corregir en lo obligatorio.

**Impacto de no tenerlas**: no hay evidencia medida de cómo se comporta el servicio ante
muchas solicitudes concurrentes con `requestId` **distintos** (throughput, latencia p95/p99
bajo carga). Esto **no** compromete la correctitud funcional ya verificada — eso lo cubren
los tests de concurrencia existentes (`InMemoryShippingQuoteRepositoryTest`,
`ShippingQuoteServiceTest`) — pero sí sería relevante antes de exponer el servicio a tráfico
real de producción. Completarlo después implicaría agregar un test que dispare N requests
concurrentes con `requestId` distintos contra la app real (vía `WebTestClient` o una
herramienta como Gatling/k6) y reporte tiempos observados, sin tocar código de negocio.

## Observabilidad

El enunciado pide que se pueda diagnosticar una solicitud **sin inspeccionar el código**.
Se cubre con 3 mecanismos:

**1. Logs correlacionados por `requestId`** — `ShippingQuoteOrchestrator` y
`ShippingQuoteService` incluyen el `requestId` como primer campo de cada línea de log
(`[REQ-1234] ...`). No se usa MDC/`ThreadLocal`: en WebFlux la ejecución puede saltar entre
distintos hilos (schedulers de Reactor) durante una misma solicitud, y el MDC clásico no
sobrevive esos saltos sin configuración adicional (propagación de contexto de Reactor). Pasar
el `requestId` explícitamente como parámetro es más simple y funciona sin importar en qué
hilo se ejecute cada paso — se ve directamente en la evidencia de abajo (cada línea corre en
un hilo distinto: `ctor-http-nio-3`, `parallel-2`, `parallel-4`...).

**2. Registro del resultado de cada proveedor** — cada `ProviderOutcome` se loguea en el
momento en que se resuelve: éxito con `provider`, `quoteId`, `price`, `estimatedDays`; fallo
con el motivo (`timeout tras Xms`, o el mensaje de `ProviderUnavailableException`). Nada de
esto es información sensible — no hay credenciales, tokens, ni datos personales en el dominio
de esta solución (origen/destino/peso son datos operativos, no PII).

**3. Verificación de disponibilidad** — `GET /actuator/health` (expuesto desde el Paso 1),
responde `{"status":"UP"}` sin necesidad de tocar ningún endpoint de negocio.

### Evidencia (log real, capturado corriendo la app y disparando 2 solicitudes con el mismo `requestId`)

```
[REQ-OBS-1] solicitud nueva: iniciando orquestacion
[REQ-OBS-1] consultando 2 proveedor(es) en paralelo: [PROVIDER_A, PROVIDER_B]
[REQ-OBS-1] PROVIDER_A respondio con cotizacion valida: quoteId=PROVIDER_A-4902 price=18750 estimatedDays=2
[REQ-OBS-1] PROVIDER_B no entrego una cotizacion valida: Proveedor no disponible: PROVIDER_B
[REQ-OBS-1] cotizacion seleccionada: proveedor=PROVIDER_A price=18750 estimatedDays=2
[REQ-OBS-1] solicitud repetida: se reutiliza el resultado ya calculado/en curso, sin reconsultar proveedores
```

Con solo estas líneas, sin abrir el código, se puede reconstruir toda la historia de la
solicitud: qué proveedores se consultaron, cuál falló y por qué, cuál se eligió, y que un
segundo intento con el mismo `requestId` no volvió a golpear a los proveedores.

**Fuera de alcance por tiempo**: logging estructurado en JSON (ej. `logstash-logback-encoder`)
sería la evolución natural para un entorno productivo real con agregadores de logs (ELK,
Datadog, etc.), pero no aporta valor adicional para el alcance de esta prueba frente a logs de
texto plano bien formados.
