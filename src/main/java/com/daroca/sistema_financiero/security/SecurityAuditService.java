package com.daroca.sistema_financiero.security;

import com.daroca.sistema_financiero.entity.Cliente;
import com.daroca.sistema_financiero.entity.Rol;
import com.daroca.sistema_financiero.entity.Usuario;
import com.daroca.sistema_financiero.repository.ClienteRepository;
import com.daroca.sistema_financiero.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;

    public Usuario obtenerUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Debe autenticarse para realizar esta operación");
        }

        return usuarioRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Usuario autenticado no encontrado en el sistema"));
    }

    public void verificarUsuarioAutenticado() {
        obtenerUsuarioAutenticado();
    }

    public void verificarRolAdmin() {
        Usuario usuario = obtenerUsuarioAutenticado();
        if (usuario.getRol() != Rol.ADMIN) {
            throw new AccessDeniedException("Solo los administradores pueden realizar esta operación");
        }
    }

    public void verificarAccesoCliente(Long clienteId) {
        Usuario usuario = obtenerUsuarioAutenticado();
        if (usuario.getRol() == Rol.ADMIN) {
            return;
        }

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new AccessDeniedException("Cliente no encontrado"));

        if (cliente.getAsesor() == null || !cliente.getAsesor().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permiso para acceder a la cartera de este cliente");
        }
    }

    public void verificarAccesoCliente(Cliente cliente) {
        if (cliente == null || cliente.getId() == null) {
            throw new AccessDeniedException("Cliente no especificado");
        }
        verificarAccesoCliente(cliente.getId());
    }

    public void verificarAsesorPuedeAsignarCliente(Cliente cliente) {
        Usuario usuario = obtenerUsuarioAutenticado();
        if (usuario.getRol() == Rol.ADMIN) {
            return;
        }

        if (cliente.getAsesor() == null || cliente.getAsesor().getId() == null
                || !cliente.getAsesor().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("Solo puede gestionar clientes asignados a su propia cuenta de asesor");
        }
    }

}
