# The Blast Beast — API

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![OpenAPI](https://img.shields.io/badge/OpenAPI-3.0-6BA539?logo=openapiinitiative&logoColor=white)

API REST del portal de noticias de heavy metal **The Blast Beast**.

## Stack

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Validation, Security, Actuator) |
| Base de datos | PostgreSQL + Flyway |
| Contrato | OpenAPI 3.0 con `delegatePattern` |
| Mapeo | MapStruct |
| Tests | JUnit 5, AssertJ, Testcontainers |
| Contenedores | Docker multi-stage |

## Arquitectura

**Contrato primero.** `src/main/resources/openapi/openapi.yaml` es la fuente de verdad: el
plugin de OpenAPI genera las interfaces `*ApiDelegate` y los modelos de `domain.vo`.
Los controladores solo implementan el delegate.

```
controller/   -> implementa *ApiDelegate, orquesta servicios, mapea con MapStruct
service/      -> reglas de negocio y transacciones
repository/   -> entidades JPA + Spring Data
exception/    -> BusinessException + ErrorCode + ProblemDetail (RFC 9457)
properties/   -> @ConfigurationProperties tipadas y validadas
```

## Arranque local

Requisitos: JDK 21, Maven 3.9+, PostgreSQL 17.

Los valores por defecto de `application.yml` apuntan a `jdbc:postgresql://localhost:5432/blastbeast`
con usuario y contraseña `blastbeast`, así que en local basta con:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Para usar otra base de datos, exporta las variables antes de arrancar (Spring Boot no lee
`.env` por sí solo; el archivo sirve como plantilla y para `docker run --env-file`):

```bash
export DB_URL=jdbc:postgresql://localhost:5432/blastbeast
export DB_USER=blastbeast
export DB_PASSWORD=blastbeast
```

- API: http://localhost:8080/api/v1/ping
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Flyway aplica `src/main/resources/db/migration/V1__baseline.sql` en el primer arranque:
11 tablas de dominio, `flyway_schema_history`, extensión `pg_trgm` y búsqueda full-text en
español sobre `articles.search_vector`. No hay `ddl-auto: update`.

## Tests

```bash
./mvnw verify
```

El test actual (`PingControllerTest`) es un `@WebMvcTest` y no necesita base de datos. Los
tests de integración de M4 usarán Testcontainers y requerirán Docker en marcha.

## Docker

```bash
docker build -t the-blast-beast-api .
docker run --rm -p 8080:8080 --env-file .env the-blast-beast-api
```

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DB_URL` | sí | JDBC de PostgreSQL |
| `DB_USER` | sí | Usuario de base de datos |
| `DB_PASSWORD` | sí | Contraseña |
| `SITE_BASE_URL` | sí | Origen permitido por CORS |
| `JWT_SECRET` | M2 | Clave HS256 (mínimo 32 bytes) |
| `STORAGE_*` | M2 | Object storage de imágenes |

## Roadmap

- [x] **M0** Fundaciones: contrato, Flyway, seguridad base, ProblemDetail, Docker, CI
- [ ] **M1** Lectura pública: artículos, categorías, bandas, búsqueda full-text
- [ ] **M2** Redacción: JWT con refresh en cookie HttpOnly, CRUD, medios, revisiones
- [ ] **M3** SEO: sitemap, RSS, prerender
- [ ] **M4** Calidad: Testcontainers, cobertura, e2e
- [ ] **M5** Producción: VPS, TLS, backups, monitorización

## Licencia

MIT
