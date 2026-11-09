# M3 — SEO técnico y sindicación

> Estado: implementado y verificado en ejecución el 2026-11-09. Este documento describe
> lo que hay construido, no lo que se planea.

## Alcance

M3 añade la capa que hace el portal rastreable y sindicable sin depender de servicios
externos:

- `sitemap.xml` con URLs absolutas, `lastmod` e imagen de portada.
- `robots.txt` dinámico que bloquea el panel y la API.
- RSS 2.0 global y por categoría, con el HTML completo y saneado de cada artículo.
- JSON-LD `WebSite`/`Organization` con `SearchAction`, `NewsArticle` y `BreadcrumbList`.
- Páginas estáticas `/sobre`, `/contacto` y `/privacidad` prerenderizadas en build.
- Enlaces de descubrimiento del feed y del sitemap en el `<head>`.

Fuera de M3: envío automático a Search Console, feeds por banda o etiqueta, AMP,
`hreflang` (el sitio es monolingüe) y despliegue en producción.

## Endpoints

Los recursos XML viven en la raíz del dominio, no bajo `/api/v1`, y son públicos:

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/sitemap.xml` | Sitemap de URLs indexables |
| GET | `/robots.txt` | Reglas de rastreo y referencia al sitemap |
| GET | `/feed.xml` | RSS 2.0 global, últimos 30 artículos |
| GET | `/categorias/{slug}/feed.xml` | RSS 2.0 de una categoría |

`SecurityConfig` permite estos `GET` sin autenticación. Caddy los enruta a la API antes
del catch-all del frontend; el resto del sitio sigue pasando por el SSR de Angular.

Todos los XML se generan a mano en `SitemapService` y `FeedService` con `XmlSupport`
(escape de texto, CDATA y fechas ISO-8601/RFC 1123). No se añaden dependencias ni se
contamina el contrato OpenAPI JSON.

## Sitemap

`SitemapService` incluye, en este orden:

| URL | `changefreq` | `priority` | `lastmod` |
|---|---|---|---|
| `/` | `hourly` | `1.0` | Actualización del artículo más reciente |
| `/articulos` | `daily` | `0.8` | Actualización del artículo más reciente |
| `/categorias/{slug}` | `daily` | `0.6` | Último artículo publicado de esa categoría |
| `/bandas/{slug}` | `weekly` | `0.5` | — |
| `/articulos/{slug}` | `weekly` | `0.7` | `updatedAt` del artículo |

Reglas:

- Solo entran artículos `PUBLISHED`. Los borradores y archivados responden 404/410 y no
  deben indexarse.
- Todas las URLs son absolutas y salen de `SITE_BASE_URL`; nunca rutas relativas.
- Si un artículo tiene portada, se añade la extensión de imágenes de Google con la
  variante `hero` en `<image:loc>`. El original no se publica.
- Las consultas usan `left join fetch` sobre la portada para evitar N+1.
- Sin artículos publicados, el sitemap sigue siendo válido y omite `lastmod`.

## Feeds RSS

`FeedService` genera RSS 2.0 con los 30 artículos publicados más recientes, global o
filtrado por categoría.

Cada canal incluye:

- `title`, `link`, `description`, `language` (`es-ES`), `lastBuildDate` y `generator`.
- `atom:link` con `rel="self"` para que los agregadores sepan la URL canónica del feed.

Cada ítem incluye:

- `title`, `link`, `guid` permanente, `pubDate`, `dc:creator` y `category`.
- `description` con la entradilla.
- `content:encoded` con el HTML completo del artículo en CDATA. El HTML ya viene saneado
  por `MarkdownService`; el feed no vuelve a interpretar Markdown.
- `media:content` con la variante `hero`, tipo MIME y dimensiones.

Un slug de categoría inexistente devuelve `404 CATEGORY_NOT_FOUND` con el formato de error
habitual.

## Caché y ETag

- Sitemap y feeds: `ETag` MD5 del cuerpo, `Cache-Control: max-age=300, public,
  s-maxage=600, stale-while-revalidate=86400`.
- `robots.txt`: `Cache-Control: max-age=3600, public`.
- Si `If-None-Match` coincide, la respuesta es `304 Not Modified` sin cuerpo.
- No hay caché en memoria en la API: el ETag evita transferir el cuerpo y una CDN puede
  absorber el resto. La generación es barata para el volumen actual.

## Frontend: metadatos y JSON-LD

`SeoService` centraliza todo lo que va en el `<head>`:

- `<title>`, `description` y canonical. En listados paginados el canonical conserva
  `?page=N` para no colapsar páginas distintas.
- Open Graph (`og:site_name`, `og:locale`, `og:type`, `og:url`, `og:title`,
  `og:description`, `og:image`) y Twitter Card.
- En artículos: `article:published_time`, `article:modified_time`, `article:section` y
  `author`.
- `robots` con `index,follow` en contenido público y `noindex,follow` en búsqueda,
  404 y 410. Las páginas no indexables no publican canonical.

JSON-LD (varios scripts gestionados por `id`, sin duplicados):

| Página | Esquema |
|---|---|
| Portada | `WebSite` + `Organization` con `SearchAction` a `/buscar?q=` |
| Detalle de artículo | `NewsArticle` con autor, sección, fechas, imagen y `mainEntityOfPage` |
| Detalle, categoría y banda | `BreadcrumbList` con posiciones y URLs absolutas |

Descubrimiento del feed:

- `index.html` declara `/feed.xml` como `alternate`.
- Las páginas de categoría añaden un `link[rel=alternate]` propio con
  `/categorias/{slug}/feed.xml`; `SeoService` lo elimina al cambiar de ruta.
- El footer enlaza el feed global y las páginas estáticas.

## Páginas estáticas y prerender selectivo

Rutas nuevas: `/sobre`, `/contacto` y `/privacidad`, con contenido propio y el mismo
layout público.

`app.routes.server.ts` queda así:

| Ruta | Modo |
|---|---|
| `/sobre`, `/contacto`, `/privacidad` | `RenderMode.Prerender` |
| `/admin/**` | `RenderMode.Client` |
| Resto | `RenderMode.Server` |

El prerender de las tres páginas ocurre en `npm run build`. Como no dependen de datos
dinámicos, el build no necesita la API. `CategoryStore.load()` falla de forma controlada
si la API no está disponible: el HTML prerenderizado sale sin las categorías en la
navegación y se hidrata en el navegador.

`SITE_BASE_URL` debe ser el origen público **en tiempo de build** para que el canonical y
Open Graph de las páginas prerenderizadas apunten al dominio real. El `Dockerfile` acepta
`ARG SITE_BASE_URL` (por defecto `http://localhost:8088`) y Compose lo pasa como build arg.
En producción hay que sobreescribirlo con el dominio definitivo.

## Caddy

`infra/Caddyfile` añade, antes del catch-all del frontend:

```
handle /sitemap.xml          -> api:8080
handle /robots.txt           -> api:8080
handle /feed.xml             -> api:8080
handle /categorias/*/feed.xml -> api:8080
```

El orden importa: los `handle` se evalúan de arriba abajo y estas rutas deben quedar antes
del `handle` general del SSR.

## Pruebas y verificación

Backend: **29 tests** (21 previos + 8 de M3):

- `SitemapServiceTest` (3): URLs públicas absolutas, variante `hero` como imagen y
  ausencia de `lastmod` sin artículos.
- `FeedServiceTest` (3): RSS global con contenido, autor y portada; feed de categoría;
  categoría desconocida.
- `SeoControllerTest` (2): ETag/304 del sitemap y reglas de `robots.txt`.

Frontend: build correcto y **7 tests** en verde (los 3 existentes más 4 de `SeoService`). El
build prerenderiza las tres rutas estáticas
(`dist/the-blast-beast-web/browser/{sobre,contacto,privacidad}/index.html`).

Verificación en Docker a través de Caddy (`http://localhost:8088`):

- `/sitemap.xml`: 200, XML, URLs absolutas, `lastmod`, `image:loc` con la variante `hero`.
- `/robots.txt`: 200, bloquea `/admin` y `/api/`, apunta al sitemap absoluto.
- `/feed.xml`: 200, RSS 2.0, `content:encoded` en CDATA, `dc:creator`, `media:content`,
  UTF-8 correcto.
- `/categorias/conciertos/feed.xml`: 200 con título y `atom:link` propios;
  `/categorias/inexistente/feed.xml`: 404.
- ETag del sitemap: segunda petición con `If-None-Match` devuelve 304.
- `/sobre`, `/contacto`, `/privacidad`: 200 con canonical absoluto al origen público.
- Portada: enlace RSS y JSON-LD `WebSite`/`SearchAction`.
- Artículo: `NewsArticle`, `BreadcrumbList`, canonical y `og:image` con la variante `hero`.
- Categoría paginada: canonical con `?page=1`, feed propio y `BreadcrumbList`.
- Artículo inexistente: 404 real con `noindex`.

## Límites conocidos

- Un único sitemap. El límite de 50 000 URLs / 50 MB se alcanzará muy lejos con el ritmo
  actual; cuando ocurra, se parte en un índice de sitemaps.
- Los feeds devuelven 30 ítems y no paginan; es el comportamiento habitual en RSS.
- El sitemap se regenera en cada petición. Si el tráfico o el catálogo crecen, el siguiente
  paso es cachear el XML en memoria con invalidación al publicar.
- `SITE_BASE_URL` incorrecto en build produce canonical y OG incorrectos en las páginas
  prerenderizadas aunque el resto del sitio funcione.
