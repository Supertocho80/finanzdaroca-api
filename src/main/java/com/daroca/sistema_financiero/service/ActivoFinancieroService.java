package com.daroca.sistema_financiero.service;

import com.daroca.sistema_financiero.entity.ActivoFinanciero;
import com.daroca.sistema_financiero.repository.ActivoFinancieroRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivoFinancieroService {

    private final ActivoFinancieroRepository activoFinancieroRepository;

    public List<ActivoFinanciero> listarTodos() {
        return activoFinancieroRepository.findAll();
    }

    public ActivoFinanciero obtenerPorId(Long id) {
        return activoFinancieroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activo financiero no encontrado con id: " + id));
    }

    public ActivoFinanciero crear(ActivoFinanciero activoFinanciero) {
        return activoFinancieroRepository.save(activoFinanciero);
    }

    public ActivoFinanciero actualizar(Long id, ActivoFinanciero activoActualizado) {
        ActivoFinanciero activo = obtenerPorId(id);
        activo.setTicker(activoActualizado.getTicker());
        activo.setNombre(activoActualizado.getNombre());
        activo.setPrecioMercado(activoActualizado.getPrecioMercado());
        activo.setMoneda(activoActualizado.getMoneda());
        return activoFinancieroRepository.save(activo);
    }

    public void eliminar(Long id) {
        if (!activoFinancieroRepository.existsById(id)) {
            throw new RuntimeException("Activo financiero no encontrado con id: " + id);
        }
        activoFinancieroRepository.deleteById(id);
    }
}
