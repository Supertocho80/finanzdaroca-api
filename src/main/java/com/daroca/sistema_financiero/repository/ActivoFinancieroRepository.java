package com.daroca.sistema_financiero.repository;

import com.daroca.sistema_financiero.entity.ActivoFinanciero;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivoFinancieroRepository extends JpaRepository<ActivoFinanciero, Long> {
}
