package com.daroca.sistema_financiero.controller;

import com.daroca.sistema_financiero.dto.LoginRequestDto;
import com.daroca.sistema_financiero.entity.Usuario;
import com.daroca.sistema_financiero.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequestDto loginRequest,
            HttpServletRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.username(), loginRequest.password()));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            request.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            Usuario usuario = usuarioRepository.findByUsername(loginRequest.username())
                    .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado en BD"));

            return ResponseEntity.ok(usuario);
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales inválidas"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession().invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada correctamente"));
    }
}
