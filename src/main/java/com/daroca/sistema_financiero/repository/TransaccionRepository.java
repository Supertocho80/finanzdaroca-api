package com.daroca.sistema_financiero.repository;

import com.daroca.sistema_financiero.entity.Transaccion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {

    List<Transaccion> findByClienteId(Long clienteId);
}
