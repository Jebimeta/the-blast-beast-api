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
| Tests | JUnit 5, AssertJ, Mockito + Testcontainers (PostgreSQL 17) |
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

## API de redacción (M2)

Autenticación JWT stateless con refresco en cookie `HttpOnly`:

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/v1/auth/login` | Email + contraseña; access token en el body y refresco en cookie |
| POST | `/api/v1/auth/refresh` | Rota el refresco y emite un access token nuevo |
| POST | `/api/v1/auth/logout` | Revoca el refresco y borra la cookie |
| GET | `/api/v1/auth/me` | Usuario autenticado |

El panel usa `/api/v1/admin/**` (artículos, revisiones, taxonomía y medios) y
`/api/v1/admin/markdown/preview`. Todo exige `Authorization: Bearer` y rol `ADMIN` o
`EDITOR`.

- El access token dura 15 minutos y vive solo en memoria del navegador.
- El refresh token es opaco, se guarda hasheado y rota en cada uso. La reutilización de un
  token revocado invalida toda su familia.
- Las imágenes se guardan en disco tras `StorageService` y se generan variantes WebP
  `thumb` (480 px) y `hero` (1600 px). La API pública expone `cover.variants` y el
  frontend elige la adecuada.
- Markdown: commonmark + tablas GFM + autolinks, saneado con la lista blanca de OWASP.
- Cada actualización guarda una revisión restaurable.

Detalle completo en [M2 — Redacción](./docs/03-redaccion.md).

## SEO técnico y sindicación (M3)

Recursos XML públicos en la raíz del dominio, servidos por la API y publicados por Caddy:

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/sitemap.xml` | URLs indexables con `lastmod` e imagen `hero` |
| GET | `/robots.txt` | Rastrea el portal, bloquea `/admin` y `/api/` |
| GET | `/feed.xml` | RSS 2.0 global, últimos 30 artículos |
| GET | `/categorias/{slug}/feed.xml` | RSS 2.0 de una categoría |

- El sitemap incluye portada, archivo, categorías, bandas y artículos `PUBLISHED`; las
  URLs son absolutas a partir de `SITE_BASE_URL`.
- Los feeds llevan el HTML completo y saneado en `content:encoded`, autor, categoría y la
  variante `hero` en `media:content`.
- Sitemap y feeds responden con `ETag` y `304 Not Modified`; `robots.txt` se genera con el
  origen público real.
- Caddy enruta estas rutas a la API antes del catch-all del frontend.

Detalle completo en [M3 — SEO técnico](./docs/04-seo.md).

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

Credenciales del seed de desarrollo:

```
redaccion@theblastbeast.com / Redaccion2026!
```

En producción, si la tabla de usuarios está vacía, la API crea un ADMIN con
`ADMIN_EMAIL` y `ADMIN_PASSWORD`.

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

**Tests unitarios** (Surefire, sin base de datos): 29 tests con JUnit 5, AssertJ y Mockito
sobre servicios, mappers y controladores.

**Tests de integración** (Failsafe, PostgreSQL real): 41 tests en `src/test/java/com/theblastbeast/it`
que arrancan un contenedor `postgres:17-alpine` con Testcontainers, aplican Flyway y prueban
la API completa vía HTTP: lectura pública, autenticación y rotación de refrescos, CRUD
editorial con revisiones, taxonomía, medios con variantes WebP, y sitemap/robots/feeds.

**Cobertura** (JaCoCo): el build falla si la cobertura de líneas de
`com.theblastbeast.service` baja del 70 %. El informe se genera en `target/site/jacoco/`.
Cobertura actual: **93,6 % de líneas** y 65,5 % de ramas.

> Los tests de integración necesitan Docker en marcha. En CI, `ubuntu-latest` ya lo incluye.
> Un único contenedor `postgres:17-alpine` se reutiliza durante toda la ejecución; entre
> tests se truncan las tablas, no se recrea el esquema.

## Docker

```bash
docker build -t the-blast-beast-api .
docker run --rm -p 8080:8080 --env-file .env \
  -v blastbeast-media:/app/data/media the-blast-beast-api
```

La imagen final usa `eclipse-temurin:21-jre-jammy` (glibc): `webp-imageio` necesita libc
nativa para generar las variantes WebP. En Alpine caerían a PNG. Los medios se persisten en
`/app/data/media`.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DB_URL` | sí | JDBC de PostgreSQL |
| `DB_USER` | sí | Usuario de base de datos |
| `DB_PASSWORD` | sí | Contraseña |
| `SITE_BASE_URL` | sí | Origen público del sitio (CORS, canonical) |
| `SPRING_PROFILES_ACTIVE` | no | `dev` carga el seed; sin perfil no carga datos |
| `JWT_SECRET` | sí en producción | Clave HS256 (mínimo 32 bytes) |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | no | Duraciones ISO-8601 (`PT15M`, `P30D`) |
| `AUTH_COOKIE_SECURE` | no | `true` con HTTPS para la cookie de refresco |
| `AUTH_COOKIE_DOMAIN` | no | Dominio de la cookie (vacío = mismo host) |
| `STORAGE_LOCAL_ROOT` | no | Directorio de medios (`./data/media` por defecto) |
| `STORAGE_PUBLIC_BASE_URL` | sí | URL pública de medios (`https://dominio/media`) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | no | Bootstrap del primer ADMIN si no hay usuarios |

## Documentación

- [Auditoría de los repos de referencia](./docs/00-auditoria-repos.md)
- [Arquitectura, modelo de datos, API y roadmap](./docs/01-arquitectura.md)
- [M1 — Lectura pública: endpoints, seed y SEO](./docs/02-lectura-publica.md)
- [M2 — Redacción: auth, CRUD, medios, revisiones y panel](./docs/03-redaccion.md)
- [M3 — SEO técnico: sitemap, robots, RSS, JSON-LD y prerender selectivo](./docs/04-seo.md)
- [M4 — Calidad: Testcontainers, cobertura, e2e, accesibilidad y rendimiento](./docs/05-calidad.md)

## Roadmap

- [x] **M0** Fundaciones: contrato, Flyway, seguridad base, ProblemDetail, Docker, CI
- [x] **M1** Lectura pública: artículos, categorías, bandas, búsqueda full-text, seed
- [x] **M2** Redacción: JWT con refresh en cookie HttpOnly, CRUD, medios, revisiones
- [x] **M3** SEO: sitemap, robots, RSS, JSON-LD y prerender selectivo
- [x] **M4** Calidad: Testcontainers, JaCoCo, Playwright, axe AA y Lighthouse
- [ ] **M5** Producción: VPS, TLS, backups, monitorización

## Licencia

MIT
