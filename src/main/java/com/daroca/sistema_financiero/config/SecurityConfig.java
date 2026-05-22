package com.daroca.sistema_financiero.config;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String JSON_NO_AUTENTICADO =
            "{\"error\":\"Autenticación requerida. Inicie sesión en POST /api/login para obtener la cookie JSESSIONID.\"}";
    private static final String JSON_ACCESO_DENEGADO =
            "{\"error\":\"Acceso denegado. No tiene permisos para este recurso.\"}";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(JSON_NO_AUTENTICADO);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            String mensaje = accessDeniedException.getMessage();
                            if (mensaje == null || mensaje.isBlank()) {
                                response.getWriter().write(JSON_ACCESO_DENEGADO);
                            } else {
                                response.getWriter().write("{\"error\":\"" + escapeJson(mensaje) + "\"}");
                            }
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/login", "/api/logout").permitAll()
                        .requestMatchers("/api/usuarios/**").authenticated()
                        .requestMatchers(
                                "/api/clientes/**",
                                "/api/transacciones/**",
                                "/api/activos-financieros/**")
                        .authenticated()
                        .anyRequest().authenticated());
        return http.build();
    }

    private static String escapeJson(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
