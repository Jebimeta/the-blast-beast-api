# M1 — Lectura pública

> Estado: implementado y verificado el 2026-11-07. Este documento describe lo que hay
> construido, no lo que se planea.

## Alcance

Un visitante puede leer el portal completo sin autenticarse: portada, archivo, secciones,
detalle, fichas de banda y búsqueda. Todo se renderiza en servidor y se hidrata en cliente.

Fuera de M1: autenticación, redacción, medios, RSS y sitemap.

## Modelo de datos implicado

```
users ──< articles >── categories
             │
             ├──< article_bands >── bands
             └──< article_tags  >── tags
```

- `articles.status`: `DRAFT | PUBLISHED | ARCHIVED`.
- `articles.search_vector`: columna generada `tsvector` en español, índice GIN.
- `articles.reading_minutes` se persiste; el seed ya trae valores realistas.

## Endpoints

### `GET /api/v1/articles`

Listado paginado.

| Parámetro | Tipo | Por defecto | Notas |
|---|---|---|---|
| `page` | int ≥ 0 | 0 | Valores negativos se normalizan a 0 |
| `size` | int 1–50 | 12 | Se recorta al máximo |
| `category` | slug | — | Filtro por categoría |
| `tag` | slug | — | Filtro por etiqueta |
| `band` | slug | — | Filtro por banda |
| `q` | texto | — | Búsqueda full-text |
| `sort` | enum | `latest` | `latest`, `oldest`, `relevance` |

Respuesta:

```json
{
  "content": [ { "id": 1, "slug": "…", "title": "…", "excerpt": "…",
                 "category": { "slug": "conciertos", "name": "Conciertos" },
                 "authorName": "Redacción Blast Beast",
                 "publishedAt": "2026-11-05T09:47:24.747737Z",
                 "readingMinutes": 4,
                 "bands": [ { "slug": "metallica", "name": "Metallica" } ],
                 "tags": [ { "slug": "giras", "name": "Giras" } ] } ],
  "page": 0, "size": 6, "totalElements": 8, "totalPages": 2,
  "first": true, "last": false
}
```

Solo aparecen artículos `PUBLISHED`. Los filtros se combinan con `AND`.

### `GET /api/v1/articles/featured`

Devuelve los cinco artículos publicados más recientes como `ArticleSummary[]`.

### `GET /api/v1/articles/{slug}`

- `200` con `ArticleDetail` (`bodyHtml`, `updatedAt`, SEO opcional).
- `404 ARTICLE_NOT_FOUND` si no existe o está en `DRAFT`.
- `410 ARTICLE_NOT_PUBLISHED` si está `ARCHIVED`.

El 410 es deliberado: la URL existió y no debe devolver un 404 genérico.

### `GET /api/v1/categories`

Categorías ordenadas con `articleCount` calculado sobre publicados.

### `GET /api/v1/bands/{slug}`

Banda con país, año de formación, género, enlaces y sus artículos publicados.

### `GET /api/v1/search?q=`

Atajo de `/articles?q=…&sort=relevance`. Exige al menos dos caracteres; si no,
`400 VALIDATION_ERROR`.

La consulta usa `plainto_tsquery('spanish', :q)` y ordena por `ts_rank`. Los filtros de
categoría, etiqueta y banda también aplican.

## Seed de desarrollo

`src/main/resources/db/dev/R__seed.sql` se aplica con los perfiles `dev` y `local`:

- 4 categorías: Noticias, Conciertos, Lanzamientos, Entrevistas.
- 8 noticias publicadas con cuerpo HTML, bandas y etiquetas.
- 1 borrador y 1 archivada para probar 404 y 410.
- 6 bandas (Metallica, Judas Priest, Opeth, Gojira, Sabaton, Nightwish) con enlaces.

Es un script repetible (`R__`): se reejecuta cuando cambia y deja la base en el mismo estado.
Ningún perfil de producción lo carga.

## Frontend

| Ruta | Resolver | Datos |
|---|---|---|
| `/` | `homeResolver` | destacados, últimas 6, categorías |
| `/articulos` | `articleListResolver` | página de 6, categorías, orden |
| `/categorias/:slug` | `articleListResolver` | página filtrada + categoría activa |
| `/articulos/:slug` | `articleDetailResolver` | detalle o `{ status: 404/410 }` |
| `/bandas/:slug` | `bandResolver` | banda o `{ status: 404 }` |
| `/buscar` | `searchResolver` | resultados de 12 en 12 |

Detalles de implementación:

- `withComponentInputBinding()`: los resolvers entregan `input.required`.
- `CategoryStore` se carga una vez por aplicación con `provideAppInitializer`.
- `PAGE_SIZE = 6` en el archivo; la búsqueda usa 12.
- Orden y paginación viven en query params, así que son enlazables y sobreviven a la recarga.

## SEO y códigos de estado

- `SeoService` centraliza title, description, canonical, OG, Twitter y JSON-LD `NewsArticle`.
- `noindex,follow` en búsqueda, 404 y 410; sin canonical en esas páginas.
- `RESPONSE_INIT` fija el status HTTP real en SSR (`404`/`410`), no solo el contenido.
- `SITE_BASE_URL` construye canonical y OG absolutos en SSR.

Verificado con `curl` contra Caddy en `:8088`:

| Ruta | Status |
|---|---|
| `/` | 200 |
| `/articulos` | 200 |
| `/categorias/conciertos` | 200 |
| `/buscar?q=gira` | 200 |
| `/bandas/opeth` | 200 |
| `/articulos/metallica-anuncia-gira-europea-2027` | 200 |
| `/articulos/articulo-archivado` | 410 |
| `/articulos/no-existe` | 404 |
| `/bandas/no-existe` | 404 |
| `/ruta-inexistente` | 404 |

## Pruebas

- `ArticleServiceTest`: 9 tests (paginación, defaults, destacados, detalle, 404/410,
  búsqueda y validación).
- `PingControllerTest`: 1 test.
- Frontend: 2 archivos, 3 tests (portada con datos y vacía, shell con header/footer).

Pendiente en M4: Testcontainers, e2e Playwright y auditoría a11y.
