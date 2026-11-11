# M4 — Calidad: integración real, e2e, accesibilidad y rendimiento

> Estado: implementado y verificado en ejecución el 2026-11-11. Este documento describe
> lo que hay construido, no lo que se planea.

## Alcance

M4 convierte el proyecto en algo que se puede defender delante de alguien: los tests de
integración corren contra PostgreSQL real, el flujo editorial completo se prueba en un
navegador, la accesibilidad se mide con axe y el rendimiento con Lighthouse. Todo entra
en CI, así que una regresión rompe el build.

| Pieza | Herramienta | Criterio |
|---|---|---|
| Integración backend | Testcontainers + Failsafe | PostgreSQL 17 real, 41 tests |
| Cobertura | JaCoCo | Líneas de `com.theblastbeast.service` ≥ 70 % |
| E2E | Playwright | 12 tests sobre el stack completo |
| Accesibilidad | `@axe-core/playwright` | WCAG 2.1 AA, 0 violaciones |
| Rendimiento y SEO | Lighthouse CI | Móvil: 90 / 95 / 90 / 95 |

Fuera de M4: tests de carga, pruebas en Safari/Firefox (solo Chromium), `visual
regression` y monitorización de producción. Eso pertenece a M5.

## Backend: integración con Testcontainers

`AbstractIntegrationTest` es la base de todos los `*IT`:

- **Un contenedor por JVM.** `PostgreSQLContainer<>(postgres:17-alpine)` se arranca en un
  bloque `static` y se reutiliza entre clases; el coste de Docker se paga una vez.
- **Sin sustituto.** No se usa H2 ni un esquema paralelo: Flyway aplica las mismas
  migraciones que en producción y las consultas nativas (`search_vector`, `pg_trgm`)
  se ejecutan de verdad.
- **Aislamiento.** `@BeforeEach` hace `truncate ... restart identity cascade` de todas las
  tablas, así que cada test parte de cero sin recrear el esquema.
- **HTTP real.** `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`:
  se prueban filtros de seguridad, serialización, códigos de estado, cabeceras y
  negociación de contenido, no solo el servicio.
- **Utilidades de siembra.** `seedUser`, `seedCategory`, `seedBand`, `seedTag`,
  `seedArticle`, `loginAsAdmin` y `refreshCookie` evitan repetir *setup* en cada test.

Distribución de los 41 tests de integración:

| Clase | Tests | Qué cubre |
|---|---|---|
| `PublicApiIT` | 10 | Listado, destacados, detalle, categorías, bandas, búsqueda, 404/410 |
| `AdminArticleIT` | 9 | CRUD, estados, revisiones, publicación y borrado |
| `AuthIT` | 7 | Login, rotación de refresco, reuso, sesión y logout |
| `AdminTaxonomyIT` | 6 | Categorías, bandas y etiquetas |
| `AdminMediaIT` | 5 | Subida multipart, variantes WebP y protección de imágenes en uso |
| `SeoIT` | 4 | Sitemap, robots, feeds y cabeceras de caché |

Los unitarios (29, Surefire) siguen cubriendo la lógica pura de servicios, mappers y
controladores con Mockito. `mvn clean verify` encadena ambas fases: Surefire en `test`,
Failsafe en `verify`.

## Cobertura

JaCoCo se ejecuta en `verify` y aplica una regla explícita:

```xml
<limit>
  <counter>LINE</counter>
  <value>COVEREDRATIO</value>
  <minimum>0.70</minimum>
</limit>
```

Solo sobre el paquete `com.theblastbeast.service`: es donde vive la lógica de negocio y
donde una regla global se diluiría entre mappers y VOs generados.

| Métrica (servicios) | Valor |
|---|---|
| Líneas | **770 / 823 = 93,6 %** |
| Ramas | 150 / 229 = 65,5 % |

El informe HTML queda en `target/site/jacoco/index.html` y CI publica el `.exec`, el XML y
los reportes de Surefire/Failsafe como artefactos.

## E2E con Playwright

`the-blast-beast-web` incluye `playwright.config.ts` y la carpeta `e2e/`, que atacan el
stack completo (Caddy → SSR → API → PostgreSQL) en `http://localhost:8088`. `E2E_BASE_URL`
permite apuntar a otro entorno.

| Spec | Tests | Contenido |
|---|---|---|
| `e2e/public.spec.ts` | 7 | Portada, detalle, archivo, búsqueda, 404 real, estáticas, sitemap/robots/feed |
| `e2e/admin.spec.ts` | 3 | Login obligatorio, ciclo crear→publicar→verificar→borrar, filtros |
| `e2e/a11y.spec.ts` | 2 | axe sobre páginas públicas y sobre login/panel |

Detalles que hacen la suite fiable:

- El test editorial crea un artículo con marca de tiempo, comprueba que aparece en el
  portal y **lo borra al final**; si falla antes, deja rastro y hay que limpiarlo a mano.
- Los tests de accesibilidad recorren las rutas principales y el panel autenticado, no
  solo una página de muestra.
- En CI, `retries: 1` y `workers: 2`; en local, paralelismo completo.
- Playwright genera `playwright-report/` y guarda traza, captura y vídeo solo cuando algo
  falla. Ambos directorios están en `.gitignore`.
- `npm run e2e` y `npm run lighthouse` se ejecutan contra el mismo stack levantado con
  `docker-compose.e2e.yml`; el job de CI lo destruye con `down -v` al terminar.

## Accesibilidad

`@axe-core/playwright` analiza con los tags `wcag2a`, `wcag2aa`, `wcag21a` y `wcag21aa`.
Criterio: **cero violaciones** en cada página escaneada.

Lo que se corrigió al medir:

- Contraste insuficiente en metadatos del listado editorial (`text-muted/60` → `text-muted`).
- `<select>` de portada sin etiqueta asociada en el editor.
- `<input type="file">` de medios sin nombre accesible (label `sr-only`).

También se mantienen el enlace "Saltar al contenido", los landmarks (`header`, `nav`,
`main`, `footer`), el foco visible y los `aria-label` de la navegación.

## Rendimiento con Lighthouse

`lighthouserc.cjs` mide en móvil (`formFactor: mobile`, 412×823, DPR 1.75) con throttling
simulado, sobre cuatro URLs: portada, archivo, detalle y `/sobre`.

| Categoría | Umbral |
|---|---|
| Performance | 0,90 |
| Accesibilidad | 0,95 |
| Buenas prácticas | 0,90 |
| SEO | 0,95 |

**Tres pasadas por URL y mediana.** Lighthouse tiene una varianza enorme entre
ejecuciones (arranque en frío de SSR, JIT de la API, cachés vacías): la misma portada
puede dar 0,73 y 0,97 seguidas. Con `numberOfRuns: 3` y `aggregationMethod: 'median'` el
resultado no lo decide un pico puntual, pero una regresión real sigue suspendiendo.

Mediana de la última ejecución completa:

| URL | Performance | A11y | Buenas prácticas | SEO |
|---|---|---|---|---|
| `/` | 0,92 | 1,00 | 1,00 | 1,00 |
| `/articulos` | 0,92 | 0,98 | 1,00 | 1,00 |
| `/articulos/metallica-anuncia-gira-europea-2027` | 0,96 | 1,00 | 1,00 | 1,00 |
| `/sobre` | 0,96 | 1,00 | 1,00 | 1,00 |

### Lo que costó llegar ahí

Dos problemas reales, no cosméticos:

**1. El transfer cache no se reutilizaba.** El SSR llamaba a la API por
`http://api:8080` y el navegador por rutas relativas, así que las claves de
`TransferState` no coincidían y la hidratación repetía todas las peticiones. Ahora:

- En navegador, `API_BASE_URL` resuelve a `location.origin` (sigue siendo same-origin).
- `HTTP_TRANSFER_CACHE_ORIGIN_MAP` mapea el origen interno de la API al origen público
  del sitio, pero solo se provee en el servidor.

Resultado: la portada se hidrata con **cero peticiones `/api`**.

**2. La API impedía el transfer cache.** Spring Security enviaba `no-store` por defecto,
y Angular excluye del transfer cache las respuestas con `Cache-Control` no cacheable.
`CacheControlFilter` decide ahora la política:

| Ruta | Cabecera |
|---|---|
| `GET /api/v1/**` público (artículos, categorías, búsqueda, ping) | `public, max-age=60, stale-while-revalidate=30` |
| `/api/v1/auth/**` y `/api/v1/admin/**` | `no-store` |
| `/sitemap.xml`, `/robots.txt`, `/feed.xml` | Las suyas propias, de M3 |

El filtro corre con `Ordered.HIGHEST_PRECEDENCE + 10` para escribir antes de que Spring
Security fije sus cabeceras, y `SecurityConfig` desactiva su `cacheControl` por defecto.

**3. `/sobre` bloqueaba la hidratación.** Como página prerenderizada no tiene transfer
cache y el inicializador esperaba a las categorías. Ahora espera en SSR (para que la
navegación vaya en el HTML) y no bloquea en navegador: la barra se rellena cuando llega
la respuesta.

### Nota sobre Chrome en Windows

En local, Chrome completo añade una tarea de *rasterización* de imagen no atribuible de
1,5–2 s que hunde el TBT. Los números de arriba se midieron con el *headless shell* de
Playwright (`chromium_headless_shell-*`) apuntando `CHROME_PATH` a él. En CI Ubuntu se
usa `/usr/bin/google-chrome` y no hace falta este ajuste: la misma aplicación puntúa
igual.

## Cómo ejecutarlo

```bash
# Backend: unitarios + integración + cobertura
./mvnw verify

# Frontend: unitarios
npm test -- --watch=false

# Stack completo para e2e y Lighthouse
docker compose -f docker-compose.e2e.yml up -d --build
E2E_BASE_URL=http://localhost:8088 npm run e2e
E2E_BASE_URL=http://localhost:8088 npm run lighthouse
```

En Windows, con PowerShell, hay que usar `npm.cmd` y añadir al `PATH` del workspace
`.toolchain\node` y `.toolchain\git\cmd`.

## CI

- **API**: `mvn clean verify` con Docker disponible en `ubuntu-latest`; publica reportes
  de surefire, failsafe y JaCoCo.
- **Web**: job `build` (unitarios + build SSR) y job `e2e` que hace checkout de la API
  como repo hermano, levanta `docker-compose.e2e.yml`, espera a `/api/v1/ping` y a `/`,
  ejecuta Playwright y Lighthouse, sube `quality-reports` y baja el stack siempre.

Los informes quedan como artefacto durante 7 días. Si el repo de la API fuese privado,
`API_REPO_TOKEN` debe tener permiso de lectura.
