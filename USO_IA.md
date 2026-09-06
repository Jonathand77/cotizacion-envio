# Uso de Inteligencia Artificial

Este documento describe el uso de IA (Claude Code, de Anthropic) durante el desarrollo de
esta prueba técnica. No se incluyó información confidencial, datos reales ni credenciales en
ningún prompt, código o commit — todos los datos (`origin`, `destination`, `weightKg`,
precios, etc.) son ficticios y generados para pruebas.

Se usó en cuatro frentes concretos:

## 1. Configuración y estructura esqueleto del proyecto

**Para qué**: montar rápido la base del proyecto (Maven, Maven Wrapper, estructura de
paquetes por responsabilidad — `domain`, `provider`, `repository`, `service`, `web` —,
`application.yml` inicial) sin tener que resolver a mano la configuración repetitiva de un
proyecto Spring Boot WebFlux desde cero.

**Qué incorporé**: el `pom.xml` con las dependencias necesarias (webflux, validation,
actuator), el wrapper (`mvnw`/`mvnw.cmd`), y la estructura de paquetes que se usó durante
todo el desarrollo.

**Cómo lo validé**: ejecutando `./mvnw clean package` y `./mvnw spring-boot:run`, y
confirmando que el servicio arranca y `/actuator/health` responde `UP` antes de seguir
construyendo sobre esa base.

## 2. Toma de decisiones entre distintas ideas

**Para qué**: en varios puntos tenía más de una idea de cómo resolver algo y usé la IA como
guia para comparar alternativas antes de decidir — por ejemplo: operadores nativos de Reactor vs.
Resilience4j para tolerar fallos de proveedores, y qué código HTTP debía devolver el caso en
que ningún proveedor entrega una cotización válida.

**Qué incorporé**: en cada caso, la alternativa que quedó mejor justificada frente al alcance
real de la prueba (operadores de Reactor en vez de Resilience4j, `201`
con `status: FAILED` en vez de un código `5xx`). El razonamiento completo de cada decisión
quedó documentado en `DECISIONES_TECNICAS.md`.

**Qué descarté**: SQL/R2DBC como mecanismo de persistencia, Resilience4j como librería de
resiliencia, y un código `5xx` para el caso de "ningún proveedor válido" — las tres por
añadir complejidad que el enunciado no pedía para el alcance de esta prueba.

## 3. Panorama de cómo implementar los tests

**Para qué**: no tenía tan claro de entrada cómo probar bien con
concurrencia real (llamadas paralelas a proveedores, idempotencia ante solicitudes
simultáneas), así que usé la IA para entender qué técnicas aplicaban — `StepVerifier` con
tiempo virtual para no esperar latencias reales en los tests, dobles de prueba
(`FakeProviderClient`) para tener proveedores deterministas, `@WebFluxTest` + `@MockBean`
para probar la capa web aislada, y pruebas de concurrencia real con `ExecutorService` +
`CountDownLatch`.

**Cómo lo validé**: corriendo la suite completa después de cada paso, y en particular
repitiendo el test de concurrencia de idempotencia 10 veces seguidas para descartar que un
resultado en verde fuera casualidad.

**Qué corregí**: esa repetición destapó un bug real — la primera versión de
`ShippingQuoteService` limpiaba el mapa de "orquestación en vuelo" apenas terminaba, lo que
abría una ventana de carrera con un proveedor muy rápido y podía duplicar trabajo
innecesariamente. Lo corregí no depurando nunca esa entrada (detalle completo en
`ARQUITECTURA.md`, sección "Idempotencia"). También, al revisar la suite completa, recorté 4 tests
que no trazaban a ningún requisito explícito del enunciado o eran redundantes con un test de
una capa inferior, dejando la suite enfocada en lo que sí importa probar (más adelante se
sumaron 2 pruebas de aceptación de punta a punta, para un total de 31).

## 4. Redacción de la documentación y comentarios del código

**Para qué**: mejorar la claridad de lo que ya había decidido y construido — tanto la
redacción de `README.md`, `ARQUITECTURA.md` y `DECISIONES_TECNICAS.md` (explicar decisiones
de diseño de forma ordenada, con sus alternativas descartadas) como los comentarios
puntuales en el código, en las clases donde había una razón no obvia detrás de una línea (por
ejemplo, por qué `ShippingQuoteService` no depura nunca su mapa de idempotencia, por qué el
orquestador usa `flatMap` y no `concatMap`, o la regla de desempate en
`ShippingQuoteOrchestrator`).

**Qué incorporé**: una plantilla y la redacción final de las secciones de `README.md`,
`ARQUITECTURA.md` y `DECISIONES_TECNICAS.md`, y los comentarios javadoc en las clases con
lógica no evidente, siempre explicando el *porqué* de una decisión, no el *qué* (que ya se lee
del propio código).

**Qué corregí/descarté**: se evitaron a propósito comentarios que solo repetían lo que el
código ya dice con nombres claros (por ejemplo, no hay comentarios tipo "esto valida que el
peso sea mayor a cero" sobre una anotación `@Positive` que ya lo dice sola) — se dejaron
únicamente los que explican un porqué que el código por sí solo no comunica.
