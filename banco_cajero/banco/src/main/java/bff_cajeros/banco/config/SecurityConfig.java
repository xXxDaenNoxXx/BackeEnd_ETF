package bff_cajeros.banco.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * bff-cajeros como OAuth2 Resource Server (reemplaza el Basic Auth de la semana 7).
 * Exige un Bearer token JWT emitido por auth-server, con el scope correcto:
 *   cajero.read  -> consultar saldo
 *   cajero.write -> realizar retiros
 * Sin token -> 401. Token valido pero sin el scope -> 403.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/cajero/cuentas/*/saldo").hasAuthority("SCOPE_cajero.read")
                .requestMatchers(HttpMethod.POST, "/cajero/cuentas/*/retiro").hasAuthority("SCOPE_cajero.write")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
