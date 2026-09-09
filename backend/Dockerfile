# Ticket 035: imagen Docker construida y ejecutada en la misma VM (OCI
# Ampere A1.Flex, ARM64) -- mismo criterio que auth-core-mc/backend/
# Dockerfile (sin registry externo, Java 25/Temurin, multi-stage: la
# etapa "build" nunca llega a la imagen final).
#
# *** Contexto de build fijo: SOLO `backend/` ***
# corePipeline.groovy (Shared Library de Jenkins, ver Jenkinsfile raíz)
# corre siempre `docker build ... ./backend` -- el contexto NUNCA incluye
# directorios hermanos (`frontend/`, `contracts/`), y no es configurable
# por proyecto. Por eso, ANTES de este build, el Jenkinsfile raíz:
#   1. Compila `frontend/` (build de producción, `VITE_API_BASE_URL`
#      vacío -- mismo origen que el backend, sin CORS de por medio en
#      despliegue real) y copia su `dist/` a
#      `backend/src/main/resources/static/` (gitignorado, ver
#      .gitignore) -- Spring Boot sirve classpath:/static automáticamente,
#      sin config adicional.
#   2. Copia `contracts/schemas` a `backend/contracts/schemas`
#      (gitignorado) -- `build.gradle` (tarea `processResources`) espera
#      encontrarlo en `../contracts/schemas` relativo al proyecto Gradle;
#      dentro de este Dockerfile ese mismo path relativo se satisface
#      copiando `contracts/` (ya viajó dentro del contexto `./backend`,
#      ver arriba) a `/contracts`, hermano de `/app` (WORKDIR de la etapa
#      build) -- MISMO checkout, solo reubicado para que el build de
#      imagen (que no tiene acceso a nada fuera de `./backend`) pueda
#      verlo igual que el build normal de Gradle en el host (que sí tiene
#      el checkout completo).
# Ver docs/ARQUITECTURA.md, "Build de la imagen: frontend + backend +
# contracts en un solo Dockerfile", para el detalle completo.
#
# Si se corre este Dockerfile a mano sin esos 2 pasos previos: el jar
# igual arranca (el backend sirve 404 en la SPA, ver SpaFallbackController)
# pero `processResources` falla en build.gradle si `backend/contracts/
# schemas` no existe -- por diseño, falla ruidoso en vez de empaquetar un
# validador de esquema roto en silencio.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon help >/dev/null 2>&1 || true

# Ver nota de cabecera -- "../contracts/schemas" (relativo a /app) debe
# resolver a /contracts/schemas.
COPY contracts /contracts
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test -x sonar

FROM eclipse-temurin:25-jre-noble AS runtime

# curl: usado por el healthcheck de deploy/docker-compose.*.yml contra
# /actuator/health -- no viene en la imagen base de Temurin.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Usuario no-root -- la imagen nunca corre la app como root.
RUN groupadd --system app && useradd --system --gid app --no-create-home app

WORKDIR /app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
