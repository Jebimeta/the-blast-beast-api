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
| Tests | JUnit 5, AssertJ, Mockito |
| Contenedores | Docker multi-stage |

## Arquitectura

**Contrato primero.** `src/main/resources/openapi/openapi.yaml` es la fuente de verdad: el
plugin de OpenAPI genera las interfaces `*ApiDelegate` y los modelos de `domain.vo`.
Los controladores solo implementan el delegate.

```
controller/   -> implementa *ApiDelegate, orquesta servicios
service/      -> reglas de negocio y transacciones
repository/   -> entidades JPA, Spring Data y búsqueda full-text nativa
mapper/       -> MapStruct: entidad -> VO
exception/    -> BusinessException + ErrorCode + ProblemDetail (RFC 9457)
properties/   -> @ConfigurationProperties tipadas y validadas
```

## API pública (M1)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/articles` | Listado paginado con `category`, `tag`, `band`, `q`, `sort`, `page`, `size` |
| GET | `/api/v1/articles/featured` | Cinco noticias destacadas |
| GET | `/api/v1/articles/{slug}` | Detalle; `404` si no existe o es borrador, `410` si está archivado |
| GET | `/api/v1/categories` | Categorías con número de artículos publicados |
| GET | `/api/v1/bands/{slug}` | Banda con sus noticias publicadas |
| GET | `/api/v1/search?q=` | Búsqueda full-text en español (mínimo 2 caracteres) |
| GET | `/api/v1/ping` | Estado del servicio |

`sort` acepta `latest` (por defecto), `oldest` y `relevance`. Los parámetros de filtro se
pueden combinar. El listado devuelve `content`, `page`, `size`, `totalElements`,
`totalPages`, `first` y `last`.

La búsqueda usa `plainto_tsquery('spanish', ...)` sobre la columna generada
`articles.search_vector`, con índice GIN. No hay Elasticsearch.

## Arranque local

Requisitos: JDK 21, Maven 3.9+, PostgreSQL 17.

Los valores por defecto de `application.yml` apuntan a `jdbc:postgresql://localhost:5432/blastbeast`
con usuario y contraseña `blastbeast`.

```bash
# Solo esquema
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Esquema + contenido de ejemplo (8 noticias, 4 categorías, bandas y etiquetas)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

El perfil `dev` aplica `src/main/resources/db/dev/R__seed.sql`; `local` además activa el log
SQL. Ningún perfil de producción carga datos de ejemplo.

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

Diez tests unitarios con Mockito, sin base de datos: nueve de `ArticleService` (paginación,
valores por defecto, destacados, detalle, 404/410, búsqueda) y uno de `PingController`.
Los tests de integración con Testcontainers llegan en M4.

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
| `SITE_BASE_URL` | sí | Origen público del sitio (CORS) |
| `SPRING_PROFILES_ACTIVE` | no | `dev` carga el seed; sin perfil no carga datos |
| `JWT_SECRET` | M2 | Clave HS256 (mínimo 32 bytes) |
| `STORAGE_*` | M2 | Object storage de imágenes |

## Documentación

- [Auditoría de los repos de referencia](./docs/00-auditoria-repos.md)
- [Arquitectura, modelo de datos, API y roadmap](./docs/01-arquitectura.md)
- [M1 — Lectura pública: endpoints, seed y SEO](./docs/02-lectura-publica.md)

## Roadmap

- [x] **M0** Fundaciones: contrato, Flyway, seguridad base, ProblemDetail, Docker, CI
- [x] **M1** Lectura pública: artículos, categorías, bandas, búsqueda full-text, seed
- [ ] **M2** Redacción: JWT con refresh en cookie HttpOnly, CRUD, medios, revisiones
- [ ] **M3** SEO: sitemap, RSS, prerender
- [ ] **M4** Calidad: Testcontainers, cobertura, e2e
- [ ] **M5** Producción: VPS, TLS, backups, monitorización

## Licencia

MIT
