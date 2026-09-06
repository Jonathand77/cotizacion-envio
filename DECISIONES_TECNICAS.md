# Decisiones técnicas

Consolida las decisiones más relevantes tomadas durante el desarrollo. `ARQUITECTURA.md` ya
documenta el detalle técnico de cada componente; este documento reúne el razonamiento detrás
de las decisiones que más impactan el diseño, en un solo lugar.

## 1. Almacenamiento del estado de las solicitudes: memoria vs. SQL

- **Contexto**: el servicio debe conservar el resultado de cada `requestId` para soportar
  idempotencia y el `GET` de consulta.
- **Alternativas**: (A) `ConcurrentHashMap` en memoria. (B) R2DBC + H2/Postgres.
- **Decisión**: (A). El valor a demostrar en esta prueba es la orquestación reactiva, el
  manejo de fallos y la idempotencia — no el modelado de datos. La garantía que se exige
  (mismo `requestId` sin resultados incompatibles) es un problema de concurrencia dentro de
  un proceso, no de consistencia distribuida.
- **Trade-offs/riesgos**: el estado se pierde al reiniciar el proceso; no escala a múltiples
  instancias (cada réplica tendría su propio mapa).
- **Cómo lo validé**: test con 50 hilos escribiendo concurrentemente el mismo `requestId`
  (`InMemoryShippingQuoteRepositoryTest`) — solo uno prevalece.
- **Qué me haría cambiarla**: si el servicio necesitara sobrevivir a reinicios, correr en
  múltiples instancias detrás de un balanceador, o requerir historial más allá de la vida del
  proceso. El store está detrás de una interfaz (`ShippingQuoteRepository`) para que ese
  cambio sea localizado.

## 2. Simulación de proveedores: in-process vs. servidor mock

- **Contexto**: el enunciado exige no depender de servicios externos reales, con una
  simulación local y reproducible.
- **Alternativas**: (A) Clase en memoria con `Mono.delay` + probabilidad de fallo
  configurable. (B) Servidor HTTP mock (WireMock, otro microservicio).
- **Decisión**: (A). Cumple el requisito con cero infraestructura — nada que levantar, ningún
  puerto de red, cualquiera clona el repo y corre los tests sin nada adicional.
- **Trade-offs/riesgos**: no ejercita comportamiento real de un cliente HTTP (timeouts de
  red, pools de conexión) — pero eso no es lo que esta prueba evalúa.
- **Cómo lo validé**: `SimulatedProviderClientTest` con `StepVerifier.withVirtualTime`, y
  smoke tests manuales contra la app real.
- **Qué me haría cambiarla**: si el objetivo fuera evaluar el manejo de un cliente HTTP real
  (reintentos de red, deserialización, resiliencia de transporte) en vez de la orquestación y
  selección de negocio.

## 3. Consulta paralela a proveedores: reactivo nativo vs. hilos/`CompletableFuture`

- **Contexto**: no se debe convertir el procesamiento en una secuencia innecesaria; el stack
  obligatorio es Spring WebFlux.
- **Alternativas**: (A) `Flux.fromIterable(...).flatMap(...)` (Reactor nativo). (B)
  `CompletableFuture.supplyAsync(...).thenCombine(...)` sobre un `ExecutorService`.
- **Decisión**: (A). `flatMap` suscribe a todos los proveedores de forma concurrente de
  manera nativa, sin gestionar hilos a mano ni arriesgar bloquear el event loop de Netty.
- **Trade-offs/riesgos**: exige conocer los operadores de Reactor; mezclar código bloqueante
  en este stack sería un error fácil de cometer sin ese conocimiento.
- **Cómo lo validé**: `ShippingQuoteOrchestratorTest`, incluyendo el caso de timeout con
  `StepVerifier.withVirtualTime` (sin depender de esperar tiempo real en los tests).
- **Qué me haría cambiarla**: si el proyecto no usara WebFlux (ej. Spring MVC clásico),
  `CompletableFuture` sería la opción natural para ese stack.

## 4. Tolerancia a fallos de proveedor: operadores de Reactor vs. Resilience4j

- **Contexto**: se debe tolerar que un proveedor falle o exceda el tiempo esperado, para una
  solicitud individual.
- **Alternativas**: (A) `.timeout()` + `.onErrorResume()` de Reactor. (B) Resilience4j
  (circuit breaker, retry).
- **Decisión**: (A). Resuelve exactamente el requisito sin dependencias nuevas ni
  configuración adicional que el enunciado no pide.
- **Trade-offs/riesgos**: no hay circuit breaker — el servicio seguiría intentando llamar a
  un proveedor consistentemente caído en cada solicitud nueva, sin "aprender" de fallos
  pasados.
- **Cómo lo validé**: `ShippingQuoteOrchestratorTest` (timeout, un proveedor falla, ambos
  fallan).
- **Qué me haría cambiarla**: si el patrón de fallos en producción mostrara valor real en
  dejar de llamar a un proveedor tras N fallos consecutivos — ahí Resilience4j sí aportaría
  algo que Reactor solo no resuelve.

## 5. Regla de selección y desempate de cotizaciones

- **Contexto**: elegir la alternativa válida de menor costo, y documentar una regla de
  desempate coherente con el objetivo del servicio.
- **Alternativas consideradas para el desempate**: aleatorio, el proveedor que respondió
  primero, un proveedor fijo preferido, menor tiempo estimado de entrega.
- **Decisión**: precio → menor tiempo estimado de entrega (a igual costo, más valor) →
  nombre de proveedor en orden alfabético (desempate final, puramente determinista).
- **Trade-offs/riesgos**: el último nivel no tiene significado de negocio — existe solo para
  garantizar que la selección sea reproducible ante los mismos insumos.
- **Cómo lo validé**: 3 tests en `ShippingQuoteOrchestratorTest`, uno por cada nivel de
  desempate.
- **Qué me haría cambiarla**: si se agregara un dato de negocio adicional a la cotización
  (ej. confiabilidad histórica del proveedor) que debiera pesar antes que el nombre.

## 6. Mecanismo de idempotencia

- **Contexto**: un mismo `requestId` puede llegar más de una vez por reintentos; no debe
  duplicar trabajo ni producir resultados incompatibles.
- **Alternativas**: (A) Chequear el repositorio primero y, si está vacío, registrar la
  orquestación en un mapa de "en vuelo" que se depura al terminar. (B) Un único mapa
  `requestId → Mono` cacheado que nunca se depura.
- **Decisión**: (B), tras encontrar una condición de carrera real en (A) durante el
  desarrollo (ver detalle en `ARQUITECTURA.md`, sección "Idempotencia").
- **Trade-offs/riesgos**: el mapa crece indefinidamente, igual que el repositorio en memoria
  — mismo trade-off ya aceptado, no uno nuevo.
- **Cómo lo validé**: test de 30 hilos concurrentes con el mismo `requestId`, corrido 10
  veces seguidas sin fallos tras la corrección (antes de corregir, fallaba ~4 de cada 5
  corridas).
- **Qué me haría cambiarla**: si la vida útil del proceso fuera muy larga y el volumen de
  `requestId` distintos fuera masivo, valdría la pena un mecanismo de expiración (TTL) en vez
  de retención indefinida.

## 7. Representación de "ningún proveedor válido" en la API

- **Contexto**: si ningún proveedor entrega una cotización válida, se debe responder con un
  error controlado, sin filtrar detalles internos de implementación.
- **Alternativas para el código HTTP**: (A) `201 Created` con `status: "FAILED"` en el
  cuerpo. (B) Un código `5xx` (502/503).
- **Decisión**: (A). El servicio procesó correctamente la solicitud (validó, orquestó,
  esperó lo que tenía que esperar); el resultado de negocio es "no hay alternativa
  disponible", no una falla técnica de la propia API. Un `5xx` sugeriría incorrectamente que
  el cliente debe reintentar.
- **Trade-offs/riesgos**: un consumidor que solo mire el código HTTP (sin leer el cuerpo) no
  distinguiría a simple vista éxito de "sin alternativa" — por diseño, ese estado vive en el
  campo `status` del cuerpo, no en el código HTTP.
- **Cómo lo validé**: `ShippingQuoteControllerTest`; además, esta zona del código tuvo 2
  fugas de información reales encontradas y corregidas probando manualmente con `curl`
  durante el Paso 8 (ver `ARQUITECTURA.md`, sección "Manejo de errores y resiliencia").
- **Qué me haría cambiarla**: si un consumidor automatizado necesitara distinguir por código
  HTTP (no por cuerpo) entre "procesado sin alternativa" y otros estados, valdría la pena
  revisar esta convención con ese consumidor.
