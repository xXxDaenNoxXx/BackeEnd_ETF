package cl.banco.clientes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ms-clientes como OAuth2 Resource Server (JWT de auth-server). Autorizacion por scope:
 *   clientes.read  -> GET      clientes.write -> POST / PUT / PATCH
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.GET, "/clientes/**").hasAuthority("SCOPE_clientes.read")
                .requestMatchers(HttpMethod.POST, "/clientes/**").hasAuthority("SCOPE_clientes.write")
                .requestMatchers(HttpMethod.PUT, "/clientes/**").hasAuthority("SCOPE_clientes.write")
                .requestMatchers(HttpMethod.PATCH, "/clientes/**").hasAuthority("SCOPE_clientes.write")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
