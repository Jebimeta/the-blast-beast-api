# Auditoría de los repos de referencia

Repos analizados:

- `Jebimeta/OnlineShopBackend` — Spring Boot 3.2.6, Java 17, MySQL, Maven, `main`
- `Jebimeta/onlineShopFronted` — Angular 19.2, TypeScript 5.7, `master`

Objetivo: extraer el **perfil de estilo** para que el nuevo proyecto sea coherente con tu forma de trabajar, y corregir lo que no aguanta una revisión técnica.

---

## 1. Lo que se conserva (y por qué)

| Práctica | Evidencia en tu repo | Por qué se mantiene |
|---|---|---|
| **API-first con OpenAPI** | `swagger.yml` + `openapi-generator-maven-plugin` 7.3.0 con `delegatePattern=true`, paquetes `apifirst.api` y `domain.vo` | Es el patrón más profesional de los dos repos. El contrato manda, el controlador implementa `*ApiDelegate` y los modelos se generan. Elimina la deriva entre documentación y código. |
| **MapStruct como converter de Spring** | `ProductToProductResponseMapper extends Converter<Product, ProductResponse>` con `componentModel = "spring"` | Mapeo entidad → VO sin reflexión, testeable y sin exponer entidades JPA. |
| **VO de dominio separados de entidades** | `domain.vo.ProductResponse` vs `repository.entities.Product` | Separación correcta. Nunca se serializa una entidad. |
| **Servicios como interfaz + impl** | `ProductCreateService` / `ProductCreateServiceImpl` | Contrato explícito e inyección por interfaz. |
| **Excepciones de negocio con códigos** | `BusinessException` + `AppErrorCode` + `RestResponseEntityExceptionHandler` | Errores tipados y respuestas homogéneas. Muy por encima de la media. |
| **`@ConfigurationProperties` tipadas** | `JwtProperties`, `MailProperties`, `UploadProperties` | Nada de `@Value` disperso. |
| **JWT stateless + refresh token + logout handler** | `JwtAuthenticationFilter`, `CustomLogoutHandler`, `SessionCreationPolicy.STATELESS` | Base de autenticación correcta. |
| **Logging con marcadores** | `INIT - Clase -> metodo()` / `END - Clase -> metodo()` | Trazabilidad legible. Se mantiene el formato. |
| **`open-in-view: false`** | `application.yml` | Evita el anti-patrón de sesión abierta en la vista. |
| **Dockerfile + Jenkinsfile + colección Postman + dump SQL** | raíz del repo | Cultura de entrega. Se mantiene, migrando Jenkins a GitHub Actions. |
| **Angular standalone + lazy `loadComponent`** | `app.routes.ts` | Base moderna, sin NgModules. |
| **Guards e interceptor funcionales** | `CanActivateFn`, `CanMatchFn`, `HttpInterceptorFn` | Patrón actual de Angular. |
| **`inject()` en lugar de constructor** | servicios y componentes | Consistente y moderno. |
| **TypeScript estricto** | `strict`, `strictTemplates`, `noImplicitReturns`, `noPropertyAccessFromIndexSignature` | Nivel alto. Se mantiene tal cual. |
| **Convención `feature/feature-page.component.ts`** | `pages/shop/product-list/product-list-page.component.ts` | Clara y predecible. |

---

## 2. Lo que se cambia (y por qué)

| # | Problema | Dónde | Acción en el proyecto nuevo |
|---|---|---|---|
| 1 | **Credenciales en el repositorio**: usuario/contraseña de MySQL de Railway y `secretKey` de JWT versionados en claro | `src/main/resources/config/application.yml` | Todo por variables de entorno. Rotar ya las credenciales actuales. Añadir `gitleaks` al CI. |
| 2 | **`ddl-auto: update` en producción** | `application.yml` | Flyway con migraciones versionadas. `ddl-auto: validate` siempre. |
| 3 | **Casi sin tests**: un único test de contexto | `src/test/java/.../RitaRougeApplicationTests.java` | Testcontainers + tests de integración por endpoint + tests unitarios de servicios. |
| 4 | **Sin versionado de API** | rutas `/products`, `/login`... | Prefijo `/api/v1`. |
| 5 | **URLs de imagen hardcodeadas en el mapper y en `application.yml`** | `ProductToProductResponseMapper.BASE_URL`, `ritarouge.upload.baseUrl` | `StorageProperties` con `baseUrl` por perfil. |
| 6 | **Mezcla de Bootstrap 5 + PrimeNG + PrimeFlex + CSS por componente** | `angular.json`, `styles.css` | Un solo sistema: PrimeNG + Tailwind (capas `@layer`). Fuera Bootstrap. |
| 7 | **Lógica de expiración de JWT duplicada** en guard e interceptor | `auth.guard.ts`, `auth.interceptor.ts` | Extraer a `TokenService` (`isExpired`, `decode`, `getRole`). |
| 8 | **`localStorage` para access y refresh token** | `auth.service.ts` | Access token en memoria; refresh token en cookie `HttpOnly; Secure; SameSite=Strict`. |
| 9 | **`localStorage.clear()` en logout** | `auth.service.ts` | Borrar solo las claves propias. |
| 10 | **`console.log` y `console.error` en producción** | varios | Logger centralizado o eliminación en build. |
| 11 | **`cdr.detectChanges()` manual** | páginas de listado | `signals` + `OnPush`; sin `ChangeDetectorRef` salvo caso justificado. |
| 12 | **Sin SSR** | `angular.json` | `@angular/ssr` con prerender de artículos: obligatorio en un portal de noticias. |
| 13 | **Sin ESLint/Prettier ni CI en el front** | repo front | ESLint + Prettier + GitHub Actions. |
| 14 | **`export default` en componentes y servicios** | todo el front | Export nombrado (convención Angular, mejor refactor y tree-shaking). |
| 15 | **Un servicio por operación en CRUD trivial** (~40 clases) | `service/**` | Un servicio por agregado con métodos por caso de uso. Se mantiene el patrón para operaciones complejas. |
| 16 | **`environments.prod.ts` / `environments.ts`** (nombre y typo) | `src/app/environments/` | `environment.ts` / `environment.production.ts` en `src/environments/`. |
| 17 | **README con rutas locales absolutas** (`C:/Users/Mi Jozé/...`) | `README.md` | Variables de entorno documentadas en `.env.example`. |
| 18 | **Nombre del repo con typo**: `onlineShopFronted` | repo | `metalnews-web` (o el nombre que elijas). |
| 19 | **Sin LICENSE** | ambos | MIT o propietaria, pero decidida. |
| 20 | **Jenkins** | `Jenkinsfile.dev` | GitHub Actions (es lo que mira un reclutador). |

---

## 3. Veredicto

**Nivel actual:** por encima de la media en arquitectura de backend (API-first, MapStruct, códigos de error, properties tipadas) y en fundamentos de Angular (standalone, functional guards, TS estricto).

**Lo que separa esto de un proyecto "profesional":** tests, migraciones, gestión de secretos, SSR/SEO y CI. No es arquitectura: es **disciplina de entrega**. Es exactamente lo que se cubre en las fases M0, M3 y M4 de la arquitectura.

**Prioridad inmediata sobre el repo antiguo (independiente del nuevo proyecto):**

1. Rotar la contraseña de MySQL de Railway y la clave JWT.
2. Purgar `application.yml` del historial de git o, como mínimo, rotar y externalizar.
3. Activar GitHub Secret Scanning / Dependabot.
