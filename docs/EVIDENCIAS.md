# Evidencias de ejecución (guardar aquí las capturas)

| Archivo sugerido | Qué debe mostrar |
|---|---|
| 01_docker_images.png | `docker images` con las imágenes de cada microservicio |
| 02_compose_ps.png | `docker compose ps` con todos los servicios *Up* |
| 03_token.png | Respuesta de `/oauth2/token` (access_token JWT) |
| 04_saldo_200.png | Saldo con token → 200 |
| 05_retiro_200.png | Retiro exitoso con token → 200 |
| 06_sin_token_401.png | Petición sin token → 401 |
| 07_scope_403.png | Token de `web-client` en cajero → 403 |
| 08_core_directo_401.png | `GET :8090/core/cuentas` sin token → 401 |
| 09_circuit_breaker.png | Core detenido: respuesta “servicio no disponible” |
| 10_kafka_evento.png | Log del comprobante y/o tópico en Kafka UI |
