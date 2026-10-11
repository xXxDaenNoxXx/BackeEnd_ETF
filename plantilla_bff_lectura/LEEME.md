# Plantilla: migrar bff-web y bff-movil a OAuth2

Tu zip de la semana 7 no incluía `banco_web/` ni `banco_movil/`. Copia tus carpetas de la semana 6
a la raíz del proyecto con esta estructura:

    banco_web/banco/      (bff-web,   puerto 8091)
    banco_movil/banco/    (bff-movil, puerto 8092)

y en cada una aplica estos 5 pasos (son los mismos que ya están hechos en `banco_cajero/banco/`):

1. **pom.xml** – agregar (si el BFF es Spring Boot 4.x; en 3.x se llaman `spring-boot-starter-oauth2-resource-server` y `...-oauth2-client`):
   - `spring-boot-starter-security-oauth2-resource-server`
   - `spring-boot-starter-security-oauth2-client`
2. **SecurityConfig.java** – reemplazar el Basic Auth / X-API-KEY por `SecurityConfig.java.txt` (ajustar `package` y el scope).
3. **OAuth2ClientConfig.java + OAuth2BearerInterceptor.java** – copiar desde `banco_cajero` (ajustar `package` y `.principal("bff-web")`).
4. **Config del RestClient** – agregar `.requestInterceptor(oauth2BearerInterceptor)` donde se construye el RestClient hacia el core.
5. **application.properties** – pegar `application.properties.snippet` y usar `${CORE_BASE_URL:http://localhost:8090}` como URL del core.
6. **Dockerfile** – copiar el de `banco_cajero/banco/Dockerfile` y cambiar `EXPOSE`.

Si el BFF usa Spring Boot 4, el core también (4.0.x) y la API de Security 7 es la misma que se usa aquí.
