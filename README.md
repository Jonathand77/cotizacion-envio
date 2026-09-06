# **📦 Cotización de Envíos - Servicio Backend Reactivo**

---

## 🛠️ Stack tecnológico y Arquitectura

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?logo=springboot&logoColor=white)
![Spring WebFlux](https://img.shields.io/badge/Spring%20WebFlux-Reactive-6DB33F?logo=spring&logoColor=white)
![Project Reactor](https://img.shields.io/badge/Project%20Reactor-Reactive%20Streams-blue)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?logo=apachemaven&logoColor=white)
![JUnit5](https://img.shields.io/badge/JUnit%205-Tests-25A162?logo=junit5&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-Layered%20(Domain%20%2F%20Provider%20%2F%20Repository%20%2F%20Service%20%2F%20Web)-blue)
![GitHub repo size](https://img.shields.io/github/repo-size/Jonathand77/cotizacion-envio)
![GitHub last commit](https://img.shields.io/github/last-commit/Jonathand77/cotizacion-envio)
![Languages](https://img.shields.io/github/languages/count/Jonathand77/cotizacion-envio)

## 👤 Autor

| 👨‍💻 Nombre | 📧 Correo | 🏫 Link directo al repositorio | 🐙 Usuario GitHub |
|---|---|---|---|
| **Jonathan David Fernández Vargas** | jonathanfdez62@gmail.com | [LinkRepositorio](https://github.com/Jonathand77/cotizacion-envio) | [jonathand77](https://github.com/jonathand77) |

**Servicio backend en Java + Spring WebFlux que recibe solicitudes de cotización de envío, consulta dos proveedores simulados en paralelo y selecciona la mejor alternativa válida disponible, de forma idempotente ante reintentos.**

> 📄 Documentación complementaria: [`ARQUITECTURA.md`](./ARQUITECTURA.md) (explicación técnica
> por paquete), [`DECISIONES_TECNICAS.md`](./DECISIONES_TECNICAS.md) (contexto, alternativas y
> trade-offs de las decisiones más relevantes) y [`USO_IA.md`](./USO_IA.md) (uso de
> herramientas de IA en esta entrega).

---

## 1. 🔍 Introducción

Una empresa de logística necesita comparar cotizaciones de distintos proveedores de envío y
elegir la mejor alternativa para despachar un pedido. Los proveedores responden con tiempos
variables y pueden fallar de forma independiente, y una misma solicitud puede llegar más de
una vez por reintentos del sistema que consume este servicio.

Esta solución resuelve eso con una arquitectura por capas sobre **Spring WebFlux** (reactivo,
no bloqueante): **dominio** (modelo y validación), **proveedores** (simulados en memoria,
sin dependencias externas), **repositorio** (estado en memoria por `requestId`),
**orquestación** (consulta paralela, timeout, selección) y **web** (endpoints REST +
manejo de errores). Los dos proveedores se consultan **en paralelo**, con tolerancia a fallos
individuales y timeouts, y el resultado de cada `requestId` queda protegido para que
reintentos no dupliquen trabajo ni produzcan respuestas incompatibles.

### Requisitos funcionales cubiertos

1. **Crear una solicitud de cotización** (`POST /api/v1/shipping-quotes`): valida la entrada,
   consulta 2 proveedores en paralelo, tolera que uno falle o exceda el tiempo esperado, y
   selecciona la alternativa válida de menor costo (regla de desempate documentada).
2. **Evitar reprocesamiento inconsistente**: reenviar el mismo `requestId` no produce
   resultados incompatibles ni repite trabajo innecesario.
3. **Consultar una solicitud** (`GET /api/v1/shipping-quotes/{requestId}`): recupera el
   resultado conocido de una solicitud ya procesada.

## 2. ⚙️ Requisitos Previos

Antes de comenzar, asegúrate de contar con:
- Git
- [JDK 21](https://adoptium.net/) (no se necesita tener Maven instalado — ver más abajo)
- Un cliente HTTP para probar la API (`curl`, Postman, Insomnia, etc.)
- Un editor de código como Visual Studio Code (opcional)

> No es necesario instalar Maven: el proyecto incluye el **Maven Wrapper** (`mvnw` /
> `mvnw.cmd`), que descarga automáticamente la versión correcta la primera vez que se usa.

## 📦 Estructura del Proyecto

```
cotizacion-envio/
├──  RAÍZ
│   ├── .gitignore
│   ├── .git/
│   ├── .mvn/wrapper/maven-wrapper.properties
│   ├── mvnw / mvnw.cmd
│   ├── pom.xml
│   ├── README.md
│   ├── ARQUITECTURA.md
│   ├── DECISIONES_TECNICAS.md
│   ├── USO_IA.md
│   └── src/
│       ├── main/
│       │   ├── java/com/logistica/cotizacionenvio/
│       │   │   ├── CotizacionEnvioApplication.java
│       │   │   ├── domain/
│       │   │   │   ├── ShippingQuoteRequest.java
│       │   │   │   ├── ProviderQuote.java
│       │   │   │   ├── ProviderOutcome.java
│       │   │   │   ├── QuoteStatus.java
│       │   │   │   └── ShippingQuoteResult.java
│       │   │   ├── provider/
│       │   │   │   ├── ProviderClient.java
│       │   │   │   ├── SimulatedProviderClient.java
│       │   │   │   ├── SimulatedProviderConfig.java
│       │   │   │   ├── ProviderClientsConfiguration.java
│       │   │   │   └── ProviderUnavailableException.java
│       │   │   ├── repository/
│       │   │   │   ├── ShippingQuoteRepository.java
│       │   │   │   └── InMemoryShippingQuoteRepository.java
│       │   │   ├── service/
│       │   │   │   ├── ShippingQuoteOrchestrator.java
│       │   │   │   └── ShippingQuoteService.java
│       │   │   └── web/
│       │   │       ├── ShippingQuoteController.java
│       │   │       ├── ShippingQuoteResponse.java
│       │   │       ├── ApiError.java
│       │   │       └── ShippingQuoteExceptionHandler.java
│       │   └── resources/
│       │       └── application.yml
│       └── test/java/com/logistica/cotizacionenvio/
│           ├── domain/ShippingQuoteRequestTest.java
│           ├── provider/SimulatedProviderClientTest.java
│           ├── repository/InMemoryShippingQuoteRepositoryTest.java
│           ├── service/ShippingQuoteOrchestratorTest.java
│           ├── service/ShippingQuoteServiceTest.java
│           ├── web/ShippingQuoteControllerTest.java
│           ├── acceptance/ShippingQuoteAcceptanceTest.java
│           ├── acceptance/ShippingQuoteProviderFailureAcceptanceTest.java
│           └── support/FakeProviderClient.java
```

---

## 3. 🖥️ Guía Paso a Paso para Levantar el Proyecto

### 3.1 Clonar el repositorio

```bash
git clone https://github.com/Jonathand77/cotizacion-envio.git
cd cotizacion-envio
```

### 3.2 Compilar y ejecutar

**⚠️ Nota:** no se necesita ninguna base de datos ni servicio externo — los dos proveedores
están simulados dentro del propio proceso.

```bash
# Linux/macOS/Git Bash
./mvnw spring-boot:run

# Windows (cmd/PowerShell)
mvnw.cmd spring-boot:run
```

El servicio arranca por defecto en `http://localhost:8080`. Para confirmarlo:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### 3.3 Probar los endpoints

```bash
# Crear una cotización
curl -X POST http://localhost:8080/api/v1/shipping-quotes \
  -H "Content-Type: application/json" \
  -d '{"requestId":"REQ-1001","origin":"BOG","destination":"MDE","weightKg":12.5}'

# Consultar el resultado
curl http://localhost:8080/api/v1/shipping-quotes/REQ-1001
```

Repetir el mismo `POST` con el mismo `requestId` devuelve exactamente el mismo resultado
(mismo `quoteId`), sin volver a consultar a los proveedores — ver `ARQUITECTURA.md`, sección
"Idempotencia".

### 3.4 Correr la suite de pruebas

```bash
./mvnw test
```

---

## 4. 🧩 Modelo de Dominio y Reglas de Negocio

### 4.1 Entrada de la solicitud (`ShippingQuoteRequest`)

| Campo | Tipo | Restricción |
|---|---|---|
| `requestId` | String | Obligatorio |
| `origin` | String | Obligatorio, distinto de `destination` |
| `destination` | String | Obligatorio, distinto de `origin` |
| `weightKg` | Double | Obligatorio, mayor que 0 |

### 4.2 Reglas de comportamiento

| Regla | Dónde se cubre |
|---|---|
| `weightKg` > 0 | `@Positive` en `ShippingQuoteRequest` |
| `origin`/`destination` obligatorios y **distintos** entre sí | `@NotBlank` + `@AssertTrue` cruzado (`isOriginDifferentFromDestination`) |
| Solicitudes con `requestId` distinto se procesan de forma concurrente | Arquitectura reactiva (WebFlux/Netty no bloqueante); la idempotencia solo serializa por clave (`requestId`), nunca de forma global |
| Si al menos un proveedor responde válido a tiempo, esa cotización se usa | El orquestador selecciona entre las respuestas exitosas, sin exigir que respondan todas |
| Si ninguno responde válido, error controlado sin filtrar detalles internos | La respuesta pública para `FAILED` usa un mensaje genérico; el detalle crudo de cada proveedor se queda en logs internos |
| Configuración que varía entre ambientes no acoplada al código | Todo lo variable (latencias, tasas de fallo, precios, timeout) vive en `application.yml`, inyectado vía `@Value` |
| Logs sin secretos ni información sensible | No hay credenciales ni datos personales en el dominio (origin/destination/weight no son PII) |

### 4.3 Buenas prácticas y arquitectura

- **Separación de responsabilidades**: `domain` (modelo + validación), `provider` (puerto +
  simulación), `repository` (puerto + persistencia en memoria), `service` (orquestación +
  idempotencia), `web` (controller + manejo de errores) — cada capa depende de interfaces,
  no de implementaciones concretas.
- **Reactivo de punta a punta**: consulta paralela a proveedores con `Flux`/`flatMap`, sin
  bloquear hilos ni gestionar `ExecutorService` manualmente.
- **Idempotencia real**: `ConcurrentHashMap#computeIfAbsent` garantiza como máximo una
  orquestación por `requestId`, verificado con pruebas de concurrencia real (30-50 hilos).
- **Errores controlados**: un único contrato de error (`ApiError{message}`) para toda la
  API, sin exponer stack traces, nombres de clases internas ni mensajes crudos de excepciones.
- **Configuración externalizada**: latencias, tasas de fallo, precios y timeouts viven en
  `application.yml`, nunca hardcodeados en el código.

## 5. 🚀 API REST - Endpoints

| Método | Endpoint | Descripción | Códigos de respuesta |
|---|---|---|---|
| `POST` | `/api/v1/shipping-quotes` | Crea/procesa una solicitud de cotización (idempotente por `requestId`) | `201` (creada, `COMPLETED` o `FAILED`), `400` (validación o cuerpo mal formado) |
| `GET` | `/api/v1/shipping-quotes/{requestId}` | Recupera el resultado conocido de una solicitud | `200`, `404` (no existe) |
| `GET` | `/actuator/health` | Verifica que el servicio está disponible | `200` |

Ejemplo de respuesta (`COMPLETED`):

```json
{
  "requestId": "REQ-1001",
  "status": "COMPLETED",
  "selected": { "provider": "PROVIDER_B", "quoteId": "PROVIDER_B-4994", "price": 18000, "estimatedDays": 3 },
  "message": null
}
```

Ejemplo de respuesta (`FAILED`, ningún proveedor entregó cotización válida):

```json
{
  "requestId": "REQ-2002",
  "status": "FAILED",
  "selected": null,
  "message": "No fue posible obtener una cotizacion valida de los proveedores disponibles en el tiempo esperado."
}
```

Detalle completo del contrato (por qué `FAILED` también responde `201`, qué se omite
deliberadamente de la respuesta pública, y el mapeo completo de errores) en
[`ARQUITECTURA.md`](./ARQUITECTURA.md).

## 6. 🧪 Pruebas y Validaciones

31 tests automatizados, construidos de forma incremental junto con cada componente:

| Capa | Archivo | Qué cubre |
|---|---|---|
| Dominio | `ShippingQuoteRequestTest` | Validaciones de entrada |
| Proveedores | `SimulatedProviderClientTest` | Cálculo de precio, simulación de fallo (tiempo virtual) |
| Repositorio | `InMemoryShippingQuoteRepositoryTest` | Atomicidad ante 50 hilos concurrentes |
| Orquestación | `ShippingQuoteOrchestratorTest` | Selección, desempate (3 niveles), tolerancia a fallo/timeout |
| Idempotencia | `ShippingQuoteServiceTest` | Sin duplicar trabajo, 30 hilos concurrentes con el mismo `requestId` |
| Web | `ShippingQuoteControllerTest` | Contrato HTTP, validación, manejo de errores sin fugas |
| Aceptación | `ShippingQuoteAcceptanceTest` / `...ProviderFailureAcceptanceTest` | Flujo completo (crear + consultar) contra la app real, incluyendo un proveedor caído |

Pruebas de performance quedaron fuera de alcance por tiempo (opcionales según el enunciado) —
ver `ARQUITECTURA.md` para qué se priorizó en su lugar y el impacto concreto de no tenerlas.

```bash
# Toda la suite (31 tests)
./mvnw test

# Solo las pruebas de aceptación (contra la app real, sin mocks)
./mvnw -Dtest=ShippingQuoteAcceptanceTest,ShippingQuoteProviderFailureAcceptanceTest test
```

**Para reproducir el resultado de las pruebas de aceptación**: son deterministas a pesar de
correr contra proveedores "reales" porque la tasa de fallo de cada proveedor se fija por
`@SpringBootTest(properties = {...})` en cada clase de prueba (0% o 100%, según el escenario)
en vez de depender de la aleatoriedad por defecto de `application.yml` — se puede correr el
comando de arriba las veces que se quiera y el resultado será el mismo siempre (solo cambia
cuánto tarda, por la latencia simulada real). Detalle en `ARQUITECTURA.md`, sección "Pruebas
de aceptación".

## 7. 📊 Observabilidad y Operación

- **Logs correlacionados por `requestId`**: cada paso de la orquestación (consulta a
  proveedores, resultado de cada uno, selección final) y de la idempotencia (solicitud nueva
  vs. repetida) se registra con el `requestId` como primer campo.
- **Registro del resultado de cada proveedor**, sin información sensible (no hay
  credenciales ni datos personales en el dominio).
- **Verificación de disponibilidad**: `GET /actuator/health`.

Evidencia real (log capturado en ejecución):

```
[REQ-OBS-1] solicitud nueva: iniciando orquestacion
[REQ-OBS-1] consultando 2 proveedor(es) en paralelo: [PROVIDER_A, PROVIDER_B]
[REQ-OBS-1] PROVIDER_A respondio con cotizacion valida: quoteId=PROVIDER_A-4902 price=18750 estimatedDays=2
[REQ-OBS-1] PROVIDER_B no entrego una cotizacion valida: Proveedor no disponible: PROVIDER_B
[REQ-OBS-1] cotizacion seleccionada: proveedor=PROVIDER_A price=18750 estimatedDays=2
[REQ-OBS-1] solicitud repetida: se reutiliza el resultado ya calculado/en curso, sin reconsultar proveedores
```

Detalle completo en [`ARQUITECTURA.md`](./ARQUITECTURA.md), sección "Observabilidad".

## 8. 📚 Decisiones técnicas y uso de IA

- [`DECISIONES_TECNICAS.md`](./DECISIONES_TECNICAS.md): las 7 decisiones más relevantes
  (persistencia en memoria vs. SQL, simulación de proveedores, orquestación reactiva,
  resiliencia, regla de desempate, idempotencia, representación de errores en la API), cada
  una con contexto, alternativas, trade-offs, validación y condición de cambio.
- [`USO_IA.md`](./USO_IA.md): para qué se usaron herramientas de IA durante el desarrollo,
  qué se incorporó, cómo se validó, y qué se corrigió/adaptó/descartó — incluyendo dos bugs
  reales encontrados y corregidos (una condición de carrera en la idempotencia, y dos fugas
  de información en el manejo de errores).

---
## **Fin de la guía y manual de uso.**
---
