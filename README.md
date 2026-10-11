# Banco XYZ – EFT Desarrollo Backend Avanzado: Spring Cloud y Batch (Semana 9)

Asignatura: Desarrollo Backend III (PBY2203) · Autor: Daniel Erices · Informe técnico: [`docs/Informe_Tecnico_EFT_Banco_XYZ.pdf`](docs/Informe_Tecnico_EFT_Banco_XYZ.pdf)

## Arquitectura EFT (16 contenedores)

| Parte | Módulo | Puerto |
|---|---|---|
| Batch (Spring Batch, 3 jobs) | `batch-service` | 8104 |
| BFF (HTTPS, OAuth2) | `banco_cajero/banco` (cajeros), `banco_web/banco`, `banco_movil/banco` | 8094 / 8091 / 8092 |
| Microservicios (escalables) | `ms-clientes`, `ms-cuentas`, `ms-pagos` | vía gateway |
| Spring Cloud | `config-server`, `eureka-server`, `api-gateway` | 8888 / 8761 / 8080 |
| Seguridad | `auth-server` (OAuth2, JWT) | 9000 |
| Core de datos legacy (S8) | `banco` (`ms-core-banco`) | 8090 |
| Infra | PostgreSQL (4 bases), Kafka, Zookeeper, Kafka UI | 8085 |

```bash
docker compose up -d --build                 # plataforma completa
MS_REPLICAS=1 docker compose up -d           # equipos con < 8 GB de RAM
docker compose up -d --scale ms-pagos=4      # escalado horizontal
HOST=localhost ./scripts/smoke-test.sh       # prueba end-to-end (OAuth2, saga, Kafka, batch, BFF)
```

Jobs batch: `POST /batch/jobs/{transacciones|intereses|estados}` (scope `batch.run`, vía gateway :8080). Reportes CSV en el volumen `batch-output` (`/data/output`). El detalle de diseño, pruebas y despliegue AWS está en el informe técnico.

> Los módulos de la EFT fueron escritos sin poder compilarse en el entorno de desarrollo; la primera compilación real es `docker compose build`.

---

# Anexo: Semana 8 (OAuth2 + Docker + docker-compose)


Asignatura: Desarrollo Backend III (PBY2203) · Autor: Daniel Erices

## 1. Objetivo

Preparar los microservicios del Banco XYZ (patrón BFF) para un entorno Cloud **seguro y resiliente**:

1. **Seguridad con OAuth2.0** (Spring Security + Spring Authorization Server).
2. **Dockerizar** cada microservicio (imágenes multi-etapa, usuario sin privilegios).
3. **Orquestar todo con `docker-compose.yml`** (Kafka, auth-server, core y BFF) en un solo comando.

Se mantiene lo construido en la semana 7: tolerancia a fallos con **Resilience4j** (Circuit Breaker + Retry) y arquitectura orientada a eventos con **Kafka**.

## 2. Propuesta técnica

```
 Cajero ──(1) token──────────▶ auth-server :9000   (emite JWT firmado con RSA)
   │                              ▲
   │(2) Bearer JWT                │(3) token de servicio (client_credentials)
   ▼                              │
 bff-cajeros :8094 (HTTPS) ───────┘
   │  Resource Server: valida JWT + scope cajero.*
   │(4) Bearer JWT (scope core.*)
   ▼
 ms-core-banco :8090   Resource Server: valida JWT + scope core.*
   │(5) evento "TransaccionRealizada"
   ▼
 Kafka ──▶ bff-cajeros (consumidor: genera comprobante)
```

**Flujo OAuth2 elegido: `client_credentials`**, porque los actores son sistemas (cajeros y servicios), no personas con login.
Se separan dos tipos de cliente (defensa en profundidad):

| Cliente | Tipo | Scopes | Para qué |
|---|---|---|---|
| `cajero-client` | canal | `cajero.read`, `cajero.write` | El cajero llama a `bff-cajeros` |
| `web-client` | canal | `web.read` | Canal web llama a `bff-web` |
| `movil-client` | canal | `movil.read` | Canal móvil llama a `bff-movil` |
| `bff-cajeros-service` | servicio | `core.read`, `core.write` | `bff-cajeros` llama al core |
| `bff-web-service` / `bff-movil-service` | servicio | `core.read` | BFF de lectura llaman al core |

Un token de canal **no sirve** para llamar directo al core (403), y sin token todo responde 401.

| Endpoint | Scope requerido |
|---|---|
| `GET /cajero/cuentas/{id}/saldo` | `cajero.read` |
| `POST /cajero/cuentas/{id}/retiro` | `cajero.write` |
| `GET /core/cuentas/**` | `core.read` |
| `POST /core/cuentas/{id}/retiro` | `core.write` |

## 3. Estructura del repositorio

```
├── auth-server/            # Servidor OAuth2 (Spring Boot 3.3.5 + Spring Authorization Server) :9000
├── banco/                  # ms-core-banco (Spring Boot 4.0.x)  :8090
├── banco_cajero/banco/     # bff-cajeros   (Spring Boot 3.3.5)  :8094 HTTPS
├── plantilla_bff_lectura/  # Guía para migrar bff-web y bff-movil a OAuth2
├── docker-compose.yml      # auth-server + core + bff-cajeros + Kafka + Zookeeper + Kafka UI
├── docker-compose.bff-lectura.yml   # bff-web y bff-movil (se suma al anterior)
├── PRUEBAS_S8.json         # Colección Postman
├── .github/workflows/      # CI/CD: construye y publica imágenes en Docker Hub y despliega en EC2
└── docs/                   # Evidencias de ejecución (capturas)
```

## 4. Docker

Cada microservicio tiene su `Dockerfile` **multi-etapa**: compila con Maven + JDK 17 y ejecuta en `eclipse-temurin:17-jre-alpine` con un usuario sin privilegios. La configuración que cambia entre local y contenedor (URLs de Kafka, auth-server y core) se inyecta por **variables de entorno**, sin tocar el código.

## 5. docker-compose

`docker-compose.yml` levanta: `zookeeper`, `kafka` (con healthcheck), `kafka-ui` (:8085), `auth-server` (:9000), `ms-core-banco` (:8090) y `bff-cajeros` (:8094). Dentro de la red de Docker los servicios se hablan por nombre (`kafka:29092`, `auth-server:9000`, `ms-core-banco:8090`). Los secretos se pueden cambiar copiando `.env.example` a `.env`.

## 6. Cómo ejecutar

Requisitos: Docker y Docker Compose.

```bash
docker compose up -d --build
docker compose ps
```

1. **Obtener un token** (cajero):
   ```bash
   curl -s -u cajero-client:cajero123 -d "grant_type=client_credentials" -d "scope=cajero.read cajero.write" http://localhost:9000/oauth2/token
   ```
2. **Consultar saldo y retirar** (reemplazar `TOKEN`):
   ```bash
   curl -k -H "Authorization: Bearer TOKEN" https://localhost:8094/cajero/cuentas/101/saldo
   curl -k -X POST -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" -d '{"monto":1000}' https://localhost:8094/cajero/cuentas/101/retiro
   ```
3. **Probar la seguridad:** sin token → `401`; token `web-client` en el cajero → `403`; `curl http://localhost:8090/core/cuentas` → `401`.
4. **Tolerancia a fallos:** `docker compose stop ms-core-banco`, repetir el retiro (el circuito se abre y responde “servicio no disponible”), luego `docker compose start ms-core-banco`.
5. **Evento Kafka:** `docker compose logs bff-cajeros` (comprobante generado) y Kafka UI en http://localhost:8085.

También puedes importar `PRUEBAS_S8.json` en Postman (desactivar *SSL certificate verification*).

Para incluir `bff-web` y `bff-movil`: `docker compose -f docker-compose.yml -f docker-compose.bff-lectura.yml up -d --build`.

## 7. Decisiones técnicas

- **auth-server separado** (no dentro del BFF): una única fuente de identidad; los servicios solo validan firmas con la clave pública (JWKS), nunca conocen secretos de firma.
- **JWT con RSA**, validación stateless (sin sesiones, CSRF deshabilitado al ser API REST).
- **Autorización por scope** con `hasAuthority("SCOPE_...")`, no solo “autenticado”.
- **Los errores de autenticación al pedir token** (auth-server caído) los cubre Resilience4j como cualquier falla del servicio.
- **Secretos por variables de entorno**; los valores por defecto son solo de demostración.
- **Versiones:** core en Spring Boot 4.0.x (starter `spring-boot-starter-security-oauth2-resource-server`); bff-cajeros y auth-server en 3.3.5 (por compatibilidad de `resilience4j-spring-boot3`).
- Si `4.0.9-SNAPSHOT` ya no se resuelve, cambiar la versión del parent del core a la última 4.0.x estable.

## 8. Evidencia de ejecución

Ver carpeta `docs/` (capturas de `docker compose ps`, tokens, 200/401/403, circuit breaker y evento Kafka).
