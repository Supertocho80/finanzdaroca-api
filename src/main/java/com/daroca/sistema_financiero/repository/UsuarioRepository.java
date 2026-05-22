package com.daroca.sistema_financiero.repository;

import com.daroca.sistema_financiero.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
}
