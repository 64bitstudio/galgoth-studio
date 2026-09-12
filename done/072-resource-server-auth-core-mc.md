# 072 — Resource Server de auth-core-mc (mecanismo, sin proteger rutas existentes)

## Objetivo
Ninguna API de galgoth-studio está protegida hoy — no existe usuario, sesión ni validación de token, y tampoco existe ningún concepto de "dueño" de un proyecto en el dominio (`MobProjectModel` no tiene ownership). Este ticket construye el mecanismo para validar los `accessToken` reales que auth-core-mc emite para el cliente `galgoth-studio` (ya dado de alta en dev/qa/prod, ver `auth-core-mc#052`) — sin la validación de `aud`, un token real de cualquier otro cliente futuro de auth-core-mc pasaría igual (blueprint `PROP-GS-AUTH-01`, Fig. 05).

**Depende de:** nada de este repo (es la primera pieza).

## Alcance — revisado explícitamente por Marco antes de implementar
Hallazgo real al empezar: `build.gradle` no trae ningún starter de seguridad — **las 14 APIs existentes están completamente abiertas hoy**, sin ningún modelo de ownership contra el cual reconciliarlas. Agregar Spring Security con su comportamiento por defecto (`anyRequest().authenticated()`) habría cortado el acceso a proyectos, generación de IA, etc. de la app real sin ningún mecanismo para decidir de quién es cada cosa — un cambio que rompe compatibilidad, no algo para decidir solo.

**Decisión explícita de Marco:** este ticket construye **solo el mecanismo** — el `JwtDecoder` con validación de emisor + audiencia. **Ninguna ruta existente (ni una nueva) pasa a requerir autenticación todavía.** Queda listo para que un ticket futuro (una vez exista un modelo de ownership de proyectos) decida qué rutas proteger y cómo.

**Incluye:**
- `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` (no estaban en el proyecto).
- `spring.security.oauth2.resourceserver.jwt.issuer-uri` configurable por ambiente (`auth-dev`/`auth-qa`/`auth.64bitstudio.com`, mismo patrón `${VAR:default}` que el resto del proyecto).
- `JwtDecoder` con validador de audiencia explícito: solo acepta tokens con `aud=galgoth-studio`.
- `SecurityConfig` que deja **todo** (`anyRequest()`) en `permitAll()` — el Resource Server queda configurado y disponible, pero no gatea nada todavía. CSRF deshabilitado (ya no había Spring Security en absoluto; sin esto, cualquier POST/PUT existente se rompería con Spring Security recién agregado).

**No incluye:**
- Proteger ninguna de las 14 rutas existentes, ni crear una ruta nueva protegida de prueba.
- Un modelo de ownership de proyectos (`userId`/`sub` en `MobProjectModel` o similar) — ticket futuro, una vez decidido.
- Nada de las pantallas de login/registro (ticket 073) — 073 no depende de que este ticket bloquee nada, solo de que el mecanismo de validación exista para cuando haga falta.
- Configurar CORS del lado de auth-core-mc (ya es responsabilidad de ese repo, ticket `auth-core-mc#054`).

## Criterios de aceptación (TDD)
- El contexto de Spring arranca igual que antes (`spring-boot-starter-security` no rompe nada) y **cualquier ruta existente sigue respondiendo sin `Authorization`**, exactamente igual que hoy — verificado explícitamente, no asumido.
- El `JwtDecoder` acepta un JWT real, válido, con `aud=galgoth-studio` (probado con un token real minteado por auth-core-mc DEV, decodificado directamente, sin pasar por HTTP ya que ninguna ruta lo exige).
- El mismo mecanismo **rechaza** un JWT real, válidamente firmado, pero con `aud` distinto — la prueba de que la frontera de tenant funciona, no solo que "hay un JWT".
- Un JWT expirado es rechazado por el decoder (no una excepción sin manejar).

## Hecho
- `build.gradle`: agregados `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` (no estaban en el proyecto).
- `SecurityConfig` nuevo (`config/`): `anyRequest().permitAll()` + `oauth2ResourceServer` wireado con el decoder propio; CSRF deshabilitado (nunca había Spring Security antes, dejarlo habría roto cualquier POST/PUT/DELETE existente).
- `AuthCoreMcJwtDecoderConfig` nuevo: `JwtDecoder` vía `NimbusJwtDecoder.withJwkSetUri(issuerUri + "/oauth2/jwks")` (resolución perezosa, nunca en el arranque) + validador de issuer+audiencia (`galgoth-studio`) extraído a un método estático testeable sin red.
- `application.properties`/`application-deploy.properties`: `spring.security.oauth2.resourceserver.jwt.issuer-uri` — default `http://localhost:8080` en local, sin default (falla fuerte) en deploy.
- `deploy/docker-compose.{dev,qa,prod}.yml`: `AUTH_CORE_MC_ISSUER` con el emisor real de cada ambiente.
- **Hallazgos reales, todos corregidos antes de que llegaran a producción** (detalle completo en `docs/ARQUITECTURA.md`, addendum de este ticket): (1) `JwtDecoders.fromIssuerLocation` hace un GET síncrono al arrancar — habría roto el contexto de cualquier test; (2) `JwtClaimValidator<List<String>>` sobre el claim `aud` crudo lanza `ClassCastException` porque el JSON real trae `aud` como string suelto, no arreglo; (3) `Jwt.getAudience()` devuelve `null` (no lista vacía) cuando el claim no existe.
- **Verificado en vivo contra DEV** (no permanente en la suite): un token real minteado por auth-core-mc para `galgoth-studio` se decodificó y validó de punta a punta contra el JWKS real — firma, issuer y audiencia reales, no simulados. Usuario de prueba eliminado al terminar.
- **6 tests nuevos** (`AuthCoreMcJwtDecoderConfigTest`) contra `Jwt` construidos a mano, sin red. Suite completa: 443/443 en verde — los 437 tests preexistentes no se tocaron (confirma que agregar Spring Security no rompió nada existente).
- **Quality Gate real, 3 hallazgos, confirmados por Marco desde el dashboard de SonarQube**: (1) `throws Exception` en `securityFilterChain` era una declaración muerta — ningún método de esta versión de `HttpSecurity` la lanza, corregido quitándola; (2) el mismo hallazgo generaba una segunda regla ("reemplazar excepción genérica") — resuelto por la misma corrección; (3) Security Hotspot real sobre `csrf().disable()` (`High`, CWE) — **no se resuelve con código**: es una decisión deliberada (esta API nunca usa cookies/sesión, el vector que CSRF protege), documentada en el Javadoc de `SecurityConfig` para que quien lo revise en el dashboard tenga el contexto completo. Pendiente de que se marque como revisado/seguro ahí.
- **Deduplicación adicional en el test** (mismo tipo de hallazgo real de duplicación que auth-core-mc ya había encontrado en su propio ticket 055): 3 bloques casi idénticos de construcción de `Jwt` se consolidaron en un solo `jwtFixture(issuer, issuedAt, expiresAt)` parametrizado, y el literal `"galgoth-studio"` repetido se extrajo a una constante.
