package com.daroca.sistema_financiero.service;

import com.daroca.sistema_financiero.entity.Usuario;
import com.daroca.sistema_financiero.repository.UsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario obtenerPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));
    }

    public Usuario crear(Usuario usuario) {
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        return usuarioRepository.save(usuario);
    }

    public Usuario actualizar(Long id, Usuario usuarioActualizado) {
        Usuario usuario = obtenerPorId(id);
        usuario.setUsername(usuarioActualizado.getUsername());
        usuario.setRol(usuarioActualizado.getRol());
        if (usuarioActualizado.getPassword() != null && !usuarioActualizado.getPassword().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(usuarioActualizado.getPassword()));
        }
        return usuarioRepository.save(usuario);
    }

    public void eliminar(Long id) {
        if (!usuarioRepository.existsById(id)) {
            throw new RuntimeException("Usuario no encontrado con id: " + id);
        }
        usuarioRepository.deleteById(id);
    }
}
