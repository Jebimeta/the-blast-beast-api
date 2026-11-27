# M2 — Redacción

> Estado: implementado y verificado en ejecución el 2026-11-26. Este documento describe
> lo que hay construido, no lo que se planea.

## Alcance

La redacción puede entrar en un panel privado y publicar sin tocar SQL: crear y editar
noticias en Markdown, subir la portada propia de cada una, clasificarlas con su categoría,
bandas y etiquetas, consultar el historial de versiones y mover cada pieza entre borrador,
publicado y archivado.

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
- `media_assets`: almacena únicamente portadas de artículos. El original y sus variantes
  comparten tabla; las variantes apuntan al original con `parent_id`. No hay biblioteca
  compartida ni endpoint que liste medios.
- `article_revisions`: copia inmutable del contenido antes de cada actualización.
- `articles.cover_media_id`: portada obligatoria para publicar. Al reemplazarla o quitarla
  se borran el original y sus variantes; al eliminar el artículo se limpia también su
  portada si ya no está referenciada.

## Autenticación

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/v1/auth/login` | Email + contraseña; devuelve access token y fija la cookie de refresco |
| POST | `/api/v1/auth/refresh` | Rota el refresco y devuelve un access token nuevo |
| POST | `/api/v1/auth/logout` | Revoca el refresco y borra la cookie |
| GET | `/api/v1/auth/me` | Usuario autenticado |
| POST | `/api/v1/auth/password` | Cambia la contraseña y revoca las sesiones abiertas |

Diseño:

- **Access token JWT HS256** de 15 minutos, guardado solo en memoria del navegador
  (`Authorization: Bearer`). Nunca en `localStorage`.
- **Refresh token opaco** de 48 bytes aleatorios en cookie `bb_refresh`: `HttpOnly`,
  `SameSite=Lax`, `Path=/api/v1/auth`, `Secure` configurable y `Max-Age` de 30 días.
- En cada refresco se revoca el token usado y se emite otro. Si llega un token ya revocado,
  se revoca **toda la familia** (`family_id`) y se responde `401 REFRESH_TOKEN_INVALID`.
- El backend es stateless: `SessionCreationPolicy.STATELESS`, sin formulario ni Basic.
- Contraseñas con BCrypt.
- El login limita los fallos por IP y por IP + email (`AUTH_LOGIN_*`). Al superar el
  límite responde `429` con `Retry-After`.
- Cambiar la contraseña exige la actual, aplica una longitud mínima de 12 caracteres y
  revoca todos los refrescos del usuario. La sesión en memoria del navegador se limpia y
  se vuelve al login.

### Bootstrap del primer usuario

Si la tabla `users` está vacía y existen `ADMIN_EMAIL` + `ADMIN_PASSWORD`, la API crea un
ADMIN al arrancar. En `dev`/`local` el seed ya crea:

```
redaccion@theblastbeast.com / Redaccion2026!
```

Son credenciales de desarrollo: cámbialas o usa bootstrap en producción.

### Cambio y restablecimiento de contraseña

- En el panel, `/admin/cuenta` permite cambiar la contraseña con la actual. Al guardar se
  revocan los refrescos y se cierra la sesión.
- Si el administrador no puede entrar, el script `ops/reset-admin-password.sh` del repo de
  despliegue pide la nueva contraseña por consola y arranca la API con el perfil
  `reset-password`. No envía correo ni abre el puerto HTTP.
- El perfil `reset-password` se ejecuta como tarea de mantenimiento y termina con código 0
  si el cambio fue correcto. No se activa sobre la API en marcha: es un proceso aparte que
  se conecta a la misma base de datos.

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
| PUT | `/api/v1/admin/articles/{id}/cover` | Subir o reemplazar la portada (multipart, máx. 10 MB) |
| PATCH | `/api/v1/admin/articles/{id}/cover` | Editar texto alternativo y crédito |
| DELETE | `/api/v1/admin/articles/{id}/cover` | Quitar la portada y borrar sus archivos |
| GET | `/api/v1/admin/press-contacts` | CSV de contactos de prensa e imagen (solo autenticado) |

Reglas:

- El `slug` es opcional; si no se envía se genera desde el título y se deduplica.
- Publicar exige título, entradilla, cuerpo, categoría, slug único y portada (`ART-005`).
- Archivar un artículo hace que su URL pública responda `410`.
- Cada `PUT` guarda la versión anterior en `article_revisions` con editor y fecha.
- Las categorías con artículos no se pueden borrar (`CATEGORY_IN_USE`).
- La categoría (sección del portal) sigue siendo un desplegable con las secciones
  existentes. Bandas y etiquetas se escriben a mano en dos campos de chips, sin catálogo
  que consultar.
- Al guardar, cada nombre se resuelve contra la ficha existente (sin distinguir
  mayúsculas) o se crea una nueva con su slug. La banda escrita en el editor queda
  enlazada a `/bandas/{slug}`, que lista sus noticias publicadas.
- La sección «Noticias» filtra por la etiqueta `noticias`, no por la categoría: un
  artículo de la categoría Noticias solo aparece allí si lleva además esa etiqueta.

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

## Portada del artículo

No existe biblioteca de medios: cada artículo tiene su propia portada, creada desde su
editor y eliminada con él.

- `StorageService` abstrae el almacenamiento. La implementación actual es
  `LocalStorageService` (disco); en M5 se añadirá R2/S3 sin tocar los servicios.
- Al subir se guarda el original y se generan dos variantes WebP:
  - `thumb` (480 px de ancho) para tarjetas y listados.
  - `hero` (1600 px de ancho) para el detalle.
- Si la JVM no puede escribir WebP, las variantes caen a PNG en lugar de fallar.
- Formatos admitidos: JPEG, PNG, WebP y GIF; máximo 10 MB.
- El texto alternativo vacío cae al titular del artículo. El crédito es opcional.
- La API pública expone `cover` con sus `variants`, y el frontend elige `thumb` o `hero`
  según el contexto; nunca descarga el original en el portal.
- Reemplazar una portada borra la anterior y sus variantes. Quitarla o eliminar el
  artículo hace lo mismo con sus archivos; no quedan huérfanos.
- Las imágenes se sirven en `/media/**` con caché pública de 30 días (`immutable`).
- El panel sube la portada al seleccionar el archivo si el artículo ya existe y, en
  artículos nuevos, al guardar el borrador. Si la subida falla, el artículo se guarda y el
  editor permite reintentarla.

## Panel de redacción (Angular)

Rutas bajo `/admin`, renderizadas en cliente (`RenderMode.Client`):

| Ruta | Contenido |
|---|---|
| `/admin/login` | Acceso con email y contraseña |
| `/admin/articulos` | Listado con filtros por estado y texto, acciones de estado y borrado |
| `/admin/articulos/nuevo` | Editor de artículo nuevo |
| `/admin/articulos/:id` | Editor con vista previa, autoguardado, clasificación e historial |
| `/admin/contactos` | Contactos de prensa con búsqueda, filtro por categoría y descarga CSV/Excel |
| `/admin/cuenta` | Cambio de contraseña del usuario autenticado |

Detalles:

- `AuthService` mantiene el access token en memoria y restaura la sesión con la cookie al
  cargar. `authInterceptor` reintenta una vez tras refrescar y redirige al login si falla.
- `adminGuard` protege el layout; las rutas públicas siguen siendo SSR.
- El editor previsualiza Markdown con debounce de 500 ms y autoguarda a los 2,5 s de
  inactividad si el formulario es válido.
- La tarjeta «Portada» permite seleccionar imagen, alt y crédito, guardar metadatos y
  quitarla. Muestra la previsualización local antes de subir y la variante `thumb` después.
- La tarjeta «Clasificación» usa `NameListInput`: los nombres se añaden con Enter, coma
  o al salir del campo, se pegan varios separados por comas y se quitan con su botón o
  Backspace. Admite hasta 20 bandas y 20 etiquetas; las fichas se crean al guardar la
  noticia, sin una sección de taxonomía aparte.
- El historial permite restaurar cualquier versión anterior.

## Contactos de prensa e imagen

- La fuente única sigue siendo `docs/contactos-prensa-e-imagenes.csv` (coma) y
  `docs/contactos-prensa-e-imagenes-excel.csv` (punto y coma) en el repo raíz.
- `PressContactService` lee el CSV desde `blastbeast.press-contacts.csv-path` (por defecto
  `../docs/contactos-prensa-e-imagenes.csv`). En Docker, el compose monta `./docs` en
  `/app/docs` en solo lectura.
- `GET /api/v1/admin/press-contacts` exige sesión y devuelve las filas como JSON. No hay
  ruta pública ni copia estática en la web.
- `/admin/contactos` permite buscar, filtrar por categoría y descargar la selección en CSV
  o Excel. La descarga se genera en el navegador con los datos ya autenticados.
- Si el CSV no existe o le faltan columnas, la API responde `503 PRESS-001` y la página
  muestra un aviso; no se inventan contactos ni se degrada a una lista vacía.

## Seed de desarrollo

`src/main/resources/db/dev/R__seed.sql` crea/actualiza el usuario de redacción y mantiene
el contenido de ejemplo de M1. Es repetible y no se carga en producción.

## Pruebas

- Backend: 91 tests unitarios y 73 de integración con Testcontainers. La portada añade
  `AdminArticleCoverIT` (8 casos: subida, reemplazo, metadatos, borrado, limpieza con el
  artículo, publicación obligatoria, validación y autenticación); `AdminArticleIT` cubre
  además la creación manual de bandas y etiquetas y el enlace a la página de banda.
- Frontend: 43 tests unitarios (páginas públicas y de redacción, incluida la vista de
  contactos de prensa y el campo de chips `NameListInput`).
- Verificación manual en navegador: login, restauración de sesión, guard, CRUD de
  artículos y clasificación manual, subida y reemplazo de portada con variantes, borrado
  conjunto, revisiones, 404/410 públicos y logout.

Cubierto en M4: Testcontainers, e2e Playwright y auditoría a11y. Ver
[`05-calidad.md`](./05-calidad.md).

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
