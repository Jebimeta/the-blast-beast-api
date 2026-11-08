# M2 — Redacción

> Estado: implementado y verificado en ejecución el 2026-11-08. Este documento describe
> lo que hay construido, no lo que se planea.

## Alcance

La redacción puede entrar en un panel privado y publicar sin tocar SQL: crear y editar
noticias en Markdown, subir imágenes, gestionar categorías, bandas y etiquetas, consultar
el historial de versiones y mover cada pieza entre borrador, publicado y archivado.

Fuera de M2: cuentas de personal con permisos distintos de ADMIN (el modelo ya los soporta),
RSS, sitemap, prerender y despliegue en producción.

## Modelo de datos añadido

```
users ──< refresh_tokens
  │
  └──< articles >── media_assets (cover_media_id)
             │
             └──< article_revisions

media_assets ──< media_assets (parent_id: original -> thumb / hero)
```

- `users`: `password_hash`, `role` (`ADMIN | EDITOR`), `status` (`ACTIVE | DISABLED`) y
  timestamps. Añadir redactores no requiere cambios de esquema.
- `refresh_tokens`: solo se guarda el hash SHA-256; `family_id` permite revocar toda la
  cadena cuando se detecta reutilización.
- `media_assets`: el original y sus variantes comparten tabla; las variantes apuntan al
  original con `parent_id`.
- `article_revisions`: copia inmutable del contenido antes de cada actualización.
- `articles.cover_media_id`: portada opcional; una imagen en uso no se puede borrar.

## Autenticación

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/v1/auth/login` | Email + contraseña; devuelve access token y fija la cookie de refresco |
| POST | `/api/v1/auth/refresh` | Rota el refresco y devuelve un access token nuevo |
| POST | `/api/v1/auth/logout` | Revoca el refresco y borra la cookie |
| GET | `/api/v1/auth/me` | Usuario autenticado |

Diseño:

- **Access token JWT HS256** de 15 minutos, guardado solo en memoria del navegador
  (`Authorization: Bearer`). Nunca en `localStorage`.
- **Refresh token opaco** de 48 bytes aleatorios en cookie `bb_refresh`: `HttpOnly`,
  `SameSite=Lax`, `Path=/api/v1/auth`, `Secure` configurable y `Max-Age` de 30 días.
- En cada refresco se revoca el token usado y se emite otro. Si llega un token ya revocado,
  se revoca **toda la familia** (`family_id`) y se responde `401 REFRESH_TOKEN_INVALID`.
- El backend es stateless: `SessionCreationPolicy.STATELESS`, sin formulario ni Basic.
- Contraseñas con BCrypt.

### Bootstrap del primer usuario

Si la tabla `users` está vacía y existen `ADMIN_EMAIL` + `ADMIN_PASSWORD`, la API crea un
ADMIN al arrancar. En `dev`/`local` el seed ya crea:

```
redaccion@theblastbeast.com / Redaccion2026!
```

Son credenciales de desarrollo: cámbialas o usa bootstrap en producción.

## Endpoints de redacción

Todos exigen `Authorization: Bearer` y responden `401`/`403` si falta el token o el rol.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/admin/articles` | Listado con `status`, `q`, `page`, `size` |
| POST | `/api/v1/admin/articles` | Crear borrador |
| GET | `/api/v1/admin/articles/{id}` | Detalle editable (Markdown incluido) |
| PUT | `/api/v1/admin/articles/{id}` | Actualizar y guardar revisión |
| DELETE | `/api/v1/admin/articles/{id}` | Eliminar |
| POST | `/api/v1/admin/articles/{id}/publish` | Publicar |
| POST | `/api/v1/admin/articles/{id}/unpublish` | Volver a borrador |
| POST | `/api/v1/admin/articles/{id}/archive` | Archivar |
| GET | `/api/v1/admin/articles/{id}/revisions` | Historial de versiones |
| POST | `/api/v1/admin/articles/{id}/revisions/{revisionId}/restore` | Restaurar versión |
| POST | `/api/v1/admin/markdown/preview` | Renderizar y sanear Markdown |
| GET/POST | `/api/v1/admin/categories` | Listar (con `articleCount`) y crear |
| PUT/DELETE | `/api/v1/admin/categories/{id}` | Editar y borrar |
| GET/POST | `/api/v1/admin/bands` | Listar y crear |
| PUT/DELETE | `/api/v1/admin/bands/{id}` | Editar y borrar |
| GET/POST | `/api/v1/admin/tags` | Listar y crear |
| PUT/DELETE | `/api/v1/admin/tags/{id}` | Editar y borrar |
| GET | `/api/v1/admin/media` | Biblioteca paginada con búsqueda |
| POST | `/api/v1/admin/media` | Subir imagen (multipart, máx. 10 MB) |
| PUT | `/api/v1/admin/media/{id}` | Editar texto alternativo y crédito |
| DELETE | `/api/v1/admin/media/{id}` | Borrar; `409 MEDIA_IN_USE` si es portada |

Reglas:

- El `slug` es opcional; si no se envía se genera desde el título y se deduplica.
- Publicar exige título, entradilla, cuerpo, categoría y slug único.
- Archivar un artículo hace que su URL pública responda `410`.
- Cada `PUT` guarda la versión anterior en `article_revisions` con editor y fecha.
- Las categorías con artículos no se pueden borrar (`CATEGORY_IN_USE`).

## Markdown y saneado

`MarkdownService` usa commonmark con tablas GFM y autolinks. El HTML resultante pasa por
la lista blanca de OWASP (`HtmlPolicyBuilder`):

- Etiquetas: párrafos, encabezados, listas, citas, código, enlaces, imágenes, tablas y
  `figure`/`figcaption`.
- Enlaces con `rel="nofollow"` y solo `http`, `https` y `mailto`.
- Se eliminan `script`, `style`, manejadores `on*` y cualquier atributo fuera de la lista.

El HTML crudo se permite para que la redacción pueda maquetar con `<figure>`/`<img>`, pero
siempre se sanea antes de guardarlo o servirlo. El mismo servicio calcula los minutos de
lectura.

## Medios

- `StorageService` abstrae el almacenamiento. La implementación actual es
  `LocalStorageService` (disco); en M5 se añadirá R2/S3 sin tocar los servicios.
- Al subir se guarda el original y se generan dos variantes WebP:
  - `thumb` (480 px de ancho) para tarjetas y listados.
  - `hero` (1600 px de ancho) para el detalle.
- Si la JVM no puede escribir WebP, las variantes caen a PNG en lugar de fallar.
- Las imágenes se sirven en `/media/**` con caché pública de 30 días (`immutable`).
- La API pública expone `cover` con sus `variants`, y el frontend elige `thumb` o `hero`
  según el contexto; nunca descarga el original en el portal.
- Borrar una imagen usada como portada devuelve `409 MEDIA_IN_USE`.

## Panel de redacción (Angular)

Rutas bajo `/admin`, renderizadas en cliente (`RenderMode.Client`):

| Ruta | Contenido |
|---|---|
| `/admin/login` | Acceso con email y contraseña |
| `/admin/articulos` | Listado con filtros por estado y texto, acciones de estado y borrado |
| `/admin/articulos/nuevo` | Editor de artículo nuevo |
| `/admin/articulos/:id` | Editor con vista previa, autoguardado e historial |
| `/admin/taxonomia` | Pestañas de categorías, bandas y etiquetas |
| `/admin/medios` | Biblioteca con subida, edición y borrado |

Detalles:

- `AuthService` mantiene el access token en memoria y restaura la sesión con la cookie al
  cargar. `authInterceptor` reintenta una vez tras refrescar y redirige al login si falla.
- `adminGuard` protege el layout; las rutas públicas siguen siendo SSR.
- El editor previsualiza Markdown con debounce de 500 ms y autoguarda a los 2,5 s de
  inactividad si el formulario es válido.
- El historial permite restaurar cualquier versión anterior.
- La taxonomía recarga `CategoryStore` para que la navegación pública refleje los cambios
  sin recargar la página.

## Seed de desarrollo

`src/main/resources/db/dev/R__seed.sql` crea/actualiza el usuario de redacción y mantiene
el contenido de ejemplo de M1. Es repetible y no se carga en producción.

## Pruebas

- Backend: 21 tests unitarios (`ArticleService` 9, `AuthenticationService` 6,
  `MarkdownService` 3, `SlugService` 2, `PingController` 1).
- Frontend: 3 tests (portada con datos y vacía, shell con header/footer).
- Verificación manual en navegador: login, restauración de sesión, guard, CRUD de
  artículos y taxonomía, subida de medios con variantes, protección de medios en uso,
  revisiones, 404/410 públicos y logout.

Pendiente en M4: Testcontainers, e2e Playwright y auditoría a11y.

## Docker

- La imagen de la API usa `eclipse-temurin:21-jre-jammy` (glibc) porque `webp-imageio`
  necesita libc nativa. Con Alpine las variantes caerían a PNG.
- El volumen `media` persiste `/app/data/media`.
- Caddy enruta `/media/*` a la API además de `/api/*` y `/actuator/*`.
- Variables nuevas en Compose: `JWT_SECRET`, `AUTH_COOKIE_SECURE`, `AUTH_COOKIE_DOMAIN`,
  `STORAGE_PUBLIC_BASE_URL`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_DISPLAY_NAME`.
- `JWT_SECRET` es obligatorio en producción (mínimo 32 bytes). Los perfiles `dev`/`local`
  y el Compose por defecto usan una clave de desarrollo; nunca reutilices esa clave fuera
  de tu máquina.
