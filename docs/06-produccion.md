# M5 - Producción: VPS, TLS, backups, monitorización y RGPD

> Estado: infraestructura implementada y validada en local el 2026-11-11. El despliegue
> real queda pendiente de VPS, dominio/DNS, acceso SSH y datos legales del titular.
> Este documento describe lo que hay construido y cómo operarlo, no un entorno ya publicado.

## Alcance

M5 convierte el proyecto en un sistema desplegable y operable: imágenes publicadas en
GHCR, TLS automático, secretos fuera del repositorio, backups con restauración probada,
monitorización con alertas, hardening del servidor y despliegue continuo por SSH.

| Pieza | Implementación | Verificación |
|---|---|---|
| Stack de producción | `docker-compose.prod.yml` | `docker compose config` en CI |
| TLS y cabeceras | Caddy + Let's Encrypt (`infra/Caddyfile.prod`) | `caddy validate` |
| Secretos | `ops/generate-env.sh` + `.env.prod` (600, gitignored) | plantilla `.env.prod.example` |
| Backups | `ops/backup.sh` + timer systemd | backup real y restauración aplicada |
| Restauración | `ops/restore-backup.sh` (verificación y `--apply`) | 10 artículos y medios restaurados |
| Monitorización | `ops/healthcheck.sh` + timer cada 2 min | ejecución contra stack real |
| Hardening | `ops/bootstrap-vps.sh` + UFW/fail2ban/SSH | documentado y validado por ShellCheck |
| CD | `.github/workflows/deploy.yml` + `ops/deploy.sh` | validación en CI; deploy manual |
| RGPD/LSSI | página de privacidad actualizada + checklist | pendiente de datos del titular |

Fuera de M5: newsletter pública, comentarios, CDN obligatoria y Search Console. La
verificación en Search Console se hace después del primer despliegue real.

## Topología

```
Internet
   │  80/443 (TLS)
   ▼
 Caddy  ──────────────►  web:4000   (SSR Angular)
   │                         │
   │  /api/*, /media/*       │ API_BASE_URL=http://api:8080
   │  /sitemap.xml           ▼
   └──────────────────►  api:8080
                             │
                             ▼
                          db:5432
```

- **Solo Caddy publica puertos.** PostgreSQL, la API y el SSR viven en redes internas de
  Docker y no son accesibles desde fuera del VPS.
- **Red `edge`:** Caddy, API y web. **Red `internal`:** API y PostgreSQL. La base de datos
  no tiene salida a Internet.
- **Imágenes de GHCR.** El servidor no compila: descarga `ghcr.io/jebimeta/the-blast-beast-api`
  y `...-web`, etiquetadas con `latest` y con el SHA del commit.
- **Volúmenes:** `pgdata`, `media`, `caddy_data` y `caddy_config`. Los backups no viven
  dentro de esos volúmenes.

`docker-compose.prod.yml` añade límites de memoria (512 MB db/web, 768 MB API, 128 MB
Caddy), logs `json-file` rotados (10 MB × 5), healthchecks, `init: true`,
`no-new-privileges`, parada graceful de la API y `shm_size` para PostgreSQL.

## Imágenes y flujo de despliegue

### CI de cada producto

- `the-blast-beast-api/.github/workflows/api.yml`: `mvn verify` con Testcontainers,
  JaCoCo y publicación de la imagen en GHCR.
- `the-blast-beast-web/.github/workflows/web.yml`: tests, build SSR, e2e + axe +
  Lighthouse y publicación de la imagen en GHCR.
- Ambas workflows normalizan el nombre a minúsculas porque GHCR no acepta el owner
  `Jebimeta` con mayúsculas.
- Etiquetas publicadas: `latest` y `${{ github.sha }}`. El deploy usa el SHA para poder
  volver a una versión concreta.

### `SITE_BASE_URL` en la imagen web

Las páginas `/sobre`, `/contacto` y `/privacidad` se prerenderizan en tiempo de build.
Por eso el `SITE_BASE_URL` que recibe el `Dockerfile` como `ARG` debe ser el dominio
público, no `localhost`. La workflow web lo pasa así:

```yaml
build-args: |
  SITE_BASE_URL=${{ vars.SITE_BASE_URL || 'https://theblastbeast.com' }}
```

Si el dominio final no es `theblastbeast.com`, define la variable de repositorio
`SITE_BASE_URL` en GitHub antes de publicar la imagen. La API recibe `SITE_BASE_URL` en
tiempo de ejecución desde `.env.prod`; la web también, para el SSR y los canonical.

### Workflow de deploy

`.github/workflows/deploy.yml` tiene dos jobs:

1. **Validar** (en cada push y en manual): `docker compose config`, `caddy validate` y
   ShellCheck sobre `ops/*.sh`.
2. **Desplegar** (solo `workflow_dispatch`): entra por SSH al VPS, actualiza el repo de
   deploy, hace login opcional en GHCR y ejecuta `./ops/deploy.sh <tag>`.

Secretos necesarios en GitHub:

| Secreto | Uso |
|---|---|
| `VPS_HOST` | IP o DNS del servidor |
| `VPS_USER` | Usuario de despliegue (no root) |
| `VPS_SSH_KEY` | Clave privada SSH |
| `VPS_SSH_PORT` | Puerto SSH (opcional, 22 por defecto) |
| `GHCR_USER` | Usuario de GitHub para GHCR |
| `GHCR_TOKEN` | PAT con `read:packages` (solo si los paquetes son privados) |

Si los paquetes de GHCR son públicos, el login no es necesario. Si son privados, el
servidor necesita `docker login ghcr.io` o las credenciales del workflow.

### `ops/deploy.sh`

Secuencia:

1. Backup previo si la base de datos está en marcha. En el primer despliegue se omite.
2. `docker compose pull` con el tag solicitado.
3. `docker compose up -d --remove-orphans`.
4. Espera a que API, web y Caddy estén `healthy`.
5. Smoke test: `/api/v1/ping`, `/` y `/sitemap.xml`.
6. Guarda el tag en `.deployed-tag` y limpia imágenes de más de 7 días.

Si algo falla, vuelve al tag anterior y repite healthchecks y smoke. **Las migraciones
Flyway son forward-only:** un rollback de imagen no revierte el esquema. Por eso el
backup previo es obligatorio en cada actualización.

Primer despliegue:

```bash
sudo SSH_PORT=22 ./ops/bootstrap-vps.sh
# copiar la clave pública, probar SSH sin contraseña y cerrar el login por contraseña
git clone git@github.com:Jebimeta/the-blast-beast-deploy.git /opt/the-blast-beast
cd /opt/the-blast-beast
./ops/generate-env.sh
# revisar BACKUP_AGE_RECIPIENT y ALERT_WEBHOOK_URL
docker login ghcr.io          # solo si los paquetes son privados
./ops/deploy.sh latest
```

## Secretos y configuración

`.env.prod` se genera con `ops/generate-env.sh`, se crea con `umask 077` y permisos 600,
y está en `.gitignore`. `.env.prod.example` es la plantilla versionada; no contiene
secretos reales.

| Variable | Obligatoria | Notas |
|---|---|---|
| `SITE_DOMAIN` | sí | Apex sin esquema ni `www` |
| `ACME_EMAIL` | sí | Correo para Let's Encrypt |
| `SITE_BASE_URL` | sí | `https://<dominio>`; canonical, OG y CORS |
| `ALLOWED_HOSTS` | sí | Dominio y `www` separados por coma |
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | sí | PostgreSQL interno |
| `JWT_SECRET` | sí | Mínimo 32 bytes; rotarlo invalida sesiones |
| `AUTH_COOKIE_SECURE` | sí | `true` en producción |
| `STORAGE_PUBLIC_BASE_URL` | sí | `https://<dominio>/media` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | no | Bootstrap del primer ADMIN si no hay usuarios |
| `IMAGE_TAG` | no | `latest` o SHA |
| `BACKUP_DIR`, `BACKUP_RETENTION_DAYS` | no | 14 días por defecto |
| `BACKUP_AGE_RECIPIENT` | recomendado | Clave pública age para cifrar backups |
| `ALERT_WEBHOOK_URL` | recomendado | Slack o Discord (`/slack` al final) |

Rotación:

- **JWT_SECRET:** cambia el valor y reinicia la API; todas las sesiones quedan invalidadas.
- **Contraseña de base de datos:** cámbiala en PostgreSQL y en `.env.prod`, y recrea los
  contenedores. No basta con reiniciar la API.
- **ADMIN_PASSWORD:** solo se usa en el bootstrap inicial. Cámbiala desde el panel o con
  el flujo de administración después del primer login.
- **Token de GHCR:** usa un PAT con `read:packages` y caducidad; no reutilices el token
  de publicación.

La clave privada de age **no va al servidor**. Se guarda en el equipo del operador o en
un gestor de secretos; sin ella los backups cifrados no se pueden restaurar.

## TLS y cabeceras

`infra/Caddyfile.prod`:

- TLS automático con Let's Encrypt para el apex y `www`.
- Redirección 301 de `www.<dominio>` al apex.
- HSTS (`max-age=31536000; includeSubDomains`), `X-Content-Type-Options`,
  `Referrer-Policy`, `Permissions-Policy`, COOP, CORP y CSP.
- CSP estricta: `default-src 'self'`, `script-src 'self'` + hash del loader de CSS
  crítico de Beasties, `style-src 'self' 'unsafe-inline'` (Angular inyecta estilos),
  `img-src 'self' data: blob:`, `connect-src 'self'`.
- `upgrade-insecure-requests` solo en producción. El Caddyfile local mantiene las mismas
  cabeceras sin esa directiva porque sirve por HTTP.
- `request_body max_size 12MB`, alineado con el límite multipart de Spring.
- `respond 404` para `/actuator/*`, `/swagger-ui*` y `/v3/api-docs*`.
- Logs JSON a stdout, rotados por Docker.

El build de Angular desactiva la hidratación incremental (`withNoIncrementalHydration()`)
porque la app no usa `@defer` y así evita los scripts inline de event replay. Si en el
futuro se activan bloques `@defer`, habrá que añadir nonce o hash a la CSP. El hash actual
del loader de Beasties es:

```
sha256-LMY6wYoFV9I4wWzxaq1N/dTpl4iurQktw706UCHK3vM=
```

Si Angular cambia ese script, el navegador muestra el hash nuevo en el error de CSP y hay
que actualizarlo en `infra/Caddyfile` y `infra/Caddyfile.prod`.

Verificación:

```bash
curl -sSI https://<dominio>/ | grep -iE 'strict-transport|content-security|permissions'
docker run --rm -e SITE_DOMAIN=example.com -e ACME_EMAIL=admin@example.com \
  -v "$PWD/infra/Caddyfile.prod:/etc/caddy/Caddyfile:ro" \
  caddy:2-alpine caddy validate --config /etc/caddy/Caddyfile
```

## Backups y restauración

### Qué genera `ops/backup.sh`

Un archivo `blast-beast-<UTC>.tar.gz` (o `.age` si hay destinatario age) con:

- `db.dump`: `pg_dump --format=custom --no-owner --no-privileges`.
- `media.tar.gz`: contenido del volumen `media`.
- `manifest.txt`: timestamp, imagen, número de ficheros de medios y recuentos de tablas
  (`articles`, `users`, `categories`, `bands`, `tags`, `media_assets`,
  `article_revisions`, `refresh_tokens`, `newsletter_subscribers`).

Además escribe un `.sha256`. El script usa un lock para evitar solapamientos, aplica
retención por días y avisa si `BACKUP_AGE_RECIPIENT` está vacío. El timer de systemd se
ejecuta a las 03:30 UTC con hasta 20 minutos de dispersión y `Persistent=true`.

### Verificación y restauración

```bash
# Solo verifica: arranca un PostgreSQL temporal, restaura el dump y compara recuentos.
./ops/restore-backup.sh /var/backups/the-blast-beast/blast-beast-<UTC>.tar.gz

# Restaura de verdad. Pide escribir RESTAURAR salvo que se pase --yes.
./ops/restore-backup.sh <archivo> --apply
./ops/restore-backup.sh <archivo> --apply --yes   # automatización
```

El modo `--apply`:

1. Para API y web.
2. Cierra conexiones, borra y recrea la base de datos.
3. Restaura el dump y reemplaza el volumen de medios.
4. Arranca API y web, espera healthchecks y comprueba `/api/v1/ping` y `/`.

La extracción del tar conserva el propietario `999:999` (usuario `app` de la imagen API),
comprobado con un volumen de prueba. Si se cambia el UID del usuario en el Dockerfile,
hay que revisar ese detalle.

### Prueba realizada

El 2026-11-11 se ejecutó el ciclo completo sobre un stack desechable con PostgreSQL real:

- Backup de 12 KB con 10 artículos, 1 usuario, 4 categorías, 7 bandas, 9 etiquetas y 1
  fichero de medios.
- Verificación en PostgreSQL temporal: todos los recuentos coinciden.
- Se introdujo deriva (`DELETE` de 5 artículos y un fichero `drift.txt`).
- `--apply` restauró los 10 artículos, eliminó `drift.txt`, conservó el medio original y
  dejó API, web y base de datos `healthy`; el smoke test respondió 200.

### RPO, RTO y copias externas

- **RPO:** hasta 24 horas con el timer diario; el deploy hace un backup adicional antes
  de cada actualización.
- **RTO:** minutos; la restauración completa se midió en menos de 2 minutos en local.
- Copia los `.tar.gz.age` fuera del VPS (almacenamiento de objetos, otro servidor o tu
  equipo). Un backup que solo vive en el mismo disco no protege ante pérdida del VPS.
- Haz una restauración de prueba al mes o al trimestre y anota el resultado.
- Los backups contienen correos, hashes de contraseña y tokens de refresco. Cifrarlos
  con age antes de sacarlos del servidor y limitar el acceso al directorio.

## Monitorización y alertas

- **Docker healthchecks:** PostgreSQL (`pg_isready`), API (`/actuator/health`), web
  (`wget` a `127.0.0.1:4000`) y Caddy (`nc` a 80/443).
- **`ops/healthcheck.sh` + timer cada 2 minutos:** API `/api/v1/ping`, portada,
  `sitemap.xml`, `robots.txt`, PostgreSQL, uso de disco (<90 %) y caducidad del
  certificado TLS (≥14 días).
- **Alertas:** `ALERT_WEBHOOK_URL` compatible con Slack; Discord acepta la misma URL con
  `/slack` al final. El script guarda estado en `/var/lib/the-blast-beast/health.state`
  para no repetir la alerta en cada ejecución y avisar cuando el servicio se recupera.
- **Logs:** `docker compose logs -f --tail=200 api web caddy`; los ficheros de Docker se
  rotan a 10 MB × 5. `journalctl -u the-blast-beast-healthcheck` para el timer.

Comandos útiles:

```bash
systemctl list-timers 'the-blast-beast-*'
systemctl status the-blast-beast-backup.timer
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
./ops/healthcheck.sh
```

Se recomienda además un monitor externo (UptimeRobot, Better Stack o similar) que
compruebe `https://<dominio>/` desde fuera del VPS: el healthcheck local no detecta una
caída de la red o del proveedor.

## Hardening del VPS

`ops/bootstrap-vps.sh` (Ubuntu/Debian, como root) instala Docker, UFW, fail2ban,
unattended-upgrades, age, jq y utilidades; activa el firewall solo para SSH, 80 y 443;
configura el daemon de Docker con rotación y `live-restore`; añade swap si hay menos de
4 GB de RAM; crea `/opt/the-blast-beast` y `/var/backups/the-blast-beast`; e instala los
timers de backup y healthcheck.

El cambio de SSH **no se automatiza** para no dejar el servidor inaccesible. Hazlo a mano:

1. Copia tu clave pública a `~/.ssh/authorized_keys` del usuario de despliegue.
2. Abre una segunda sesión SSH y comprueba que entra sin contraseña.
3. Edita `/etc/ssh/sshd_config` (o un drop-in en `/etc/ssh/sshd_config.d/`):
   `PermitRootLogin no`, `PasswordAuthentication no`, `PubkeyAuthentication yes`.
4. Ejecuta `sshd -t` y solo si no hay errores reinicia `ssh`.
5. Mantén la sesión antigua abierta hasta confirmar el acceso nuevo.

Otras medidas:

- `fail2ban` con 5 intentos, 10 minutos de ventana y 1 hora de bloqueo.
- `unattended-upgrades` para parches de seguridad; reinicia cuando toque.
- Contenedores sin privilegios, `no-new-privileges`, sin socket de Docker montado y con
  la API/web ejecutándose como usuario no root.
- Caddy con `admin off`; el Caddyfile se monta en solo lectura.
- No se publican Actuator, Swagger ni el contrato OpenAPI.
- Reconstruye las imágenes con CI para recibir actualizaciones de base; `deploy.sh` limpia
  imágenes antiguas.
- Cloudflare es opcional. Si se añade, configura el origen para que solo acepte tráfico
  de Cloudflare o mantén el firewall cerrado a 80/443 salvo por sus rangos.

## RGPD, LSSI y contenido legal

Principios ya aplicados:

- **Minimización:** no hay analítica de terceros, píxeles publicitarios, botones sociales
  que rastreen ni formularios de registro de lectores.
- **Tipografías propias:** Inter y Oswald se sirven desde el mismo dominio; no hay
  peticiones a Google Fonts.
- **Cookies:** la única cookie propia es `bb_refresh`, técnica, `HttpOnly`, para la
  sesión del personal. No requiere banner de consentimiento para lectores.
- **Conservación:** logs técnicos rotados; backups 14 días; datos editoriales mientras
  el medio esté activo.
- **Seguridad:** TLS, contraseñas con BCrypt, JWT corto + refresco rotado, backups
  cifrables, acceso al servidor por clave y principio de mínimo privilegio.

Pendiente de datos del titular antes de publicar:

| Dato | Uso |
|---|---|
| Nombre o razón social | Aviso legal y política de privacidad |
| NIF/CIF | Aviso legal |
| Domicilio | Aviso legal |
| Correo de contacto legal | Privacidad y ejercicio de derechos |
| Proveedor de hosting y país | Encargados del tratamiento |
| Proveedor de correo | Encargados del tratamiento |

Cuando estén disponibles:

1. Añadir `/aviso-legal` con titular, NIF, domicilio, correo y condiciones de uso.
2. Actualizar `/privacidad` con la identidad del responsable y, si aplica, transferencias
   internacionales.
3. Enlazar el aviso legal desde el pie.
4. Revisar si el proveedor de hosting exige un contrato de encargado del tratamiento
   (DPA) y guardarlo.
5. Si se activa la newsletter, implementar doble opt-in, registro de consentimiento y
   enlace de baja antes de exponer el formulario. La tabla
   `newsletter_subscribers` existe, pero no hay endpoint público.

Derechos de las personas: acceso, rectificación, supresión, oposición, limitación y
portabilidad. El correo de contacto debe atender estas solicitudes; también se puede
reclamar ante la AEPD.

## Search Console y lanzamiento

Después del primer despliegue real:

1. Crear la propiedad de dominio en Search Console y verificarla por DNS TXT.
2. Enviar `https://<dominio>/sitemap.xml`.
3. Comprobar `robots.txt`, canonical, Open Graph y JSON-LD en la portada y en un detalle.
4. Revisar que las URLs prerenderizadas (`/sobre`, `/contacto`, `/privacidad`) llevan el
   dominio correcto, no `localhost`.
5. Solicitar indexación de la portada y de las secciones principales.
6. Configurar el monitor externo y el webhook de alertas.
7. Ejecutar un backup manual y una restauración de prueba en el VPS real.
8. Revisar los logs de Caddy y de la API tras las primeras visitas.

## Runbook rápido

```bash
# Estado general
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f --tail=200 api web caddy

# Backup manual y verificación
./ops/backup.sh
./ops/restore-backup.sh /var/backups/the-blast-beast/<archivo>.tar.gz

# Restauración real (destructiva)
./ops/restore-backup.sh /var/backups/the-blast-beast/<archivo>.tar.gz --apply

# Desplegar un tag concreto o volver al anterior
./ops/deploy.sh <sha>
./ops/deploy.sh "$(cat .deployed-tag)"

# Healthcheck y timers
./ops/healthcheck.sh
systemctl list-timers 'the-blast-beast-*'

# Certificado y cabeceras
curl -sSI https://<dominio>/ | grep -iE 'strict-transport|content-security'
echo | openssl s_client -servername <dominio> -connect <dominio>:443 2>/dev/null \
  | openssl x509 -noout -enddate
```

## Pendiente para cerrar M5

- [ ] VPS contratado y accesible por SSH con clave.
- [ ] Dominio y DNS apuntando al VPS; `www` configurado.
- [ ] Paquetes de GHCR públicos o `docker login ghcr.io` en el servidor.
- [ ] `.env.prod` generado con el dominio real y `BACKUP_AGE_RECIPIENT` configurado.
- [ ] `SITE_BASE_URL` de GitHub Actions ajustado si el dominio no es `theblastbeast.com`.
- [ ] Primer despliegue y verificación de TLS, sitemap, robots y feeds.
- [ ] Backup real + restauración de prueba en el VPS.
- [ ] Monitor externo y webhook de alertas.
- [ ] Datos legales del titular y `/aviso-legal`.
- [ ] Search Console verificada y sitemap enviado.
