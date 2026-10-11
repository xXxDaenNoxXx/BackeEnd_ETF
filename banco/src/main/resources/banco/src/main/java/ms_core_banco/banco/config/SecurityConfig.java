package ms_core_banco.banco.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ms-core-banco como OAuth2 Resource Server.
 * Solo acepta tokens JWT emitidos por auth-server (firma validada con su JWKS).
 * Los permisos se controlan por scope:
 *   core.read  -> consultas (GET)
 *   core.write -> retiros   (POST)
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/core/cuentas/**").hasAuthority("SCOPE_core.read")
                .requestMatchers(HttpMethod.POST, "/core/cuentas/*/retiro").hasAuthority("SCOPE_core.write")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
