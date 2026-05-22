package com.daroca.sistema_financiero.repository;

import com.daroca.sistema_financiero.entity.Cliente;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    List<Cliente> findByAsesorId(Long asesorId);
}
