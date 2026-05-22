package com.daroca.sistema_financiero.service;

import com.daroca.sistema_financiero.entity.Transaccion;
import com.daroca.sistema_financiero.repository.ActivoFinancieroRepository;
import com.daroca.sistema_financiero.repository.ClienteRepository;
import com.daroca.sistema_financiero.repository.TransaccionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransaccionService {

    private final TransaccionRepository transaccionRepository;
    private final ClienteRepository clienteRepository;
    private final ActivoFinancieroRepository activoFinancieroRepository;

    public List<Transaccion> listarTodas() {
        return transaccionRepository.findAll();
    }

    public List<Transaccion> listarPorCliente(Long clienteId) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new RuntimeException("Cliente no encontrado con id: " + clienteId);
        }
        return transaccionRepository.findByClienteId(clienteId);
    }

    public Transaccion crear(Transaccion transaccion) {
        if (transaccion.getCliente() == null || transaccion.getCliente().getId() == null) {
            throw new RuntimeException("La transacción debe tener un cliente asignado");
        }
        if (transaccion.getActivoFinanciero() == null || transaccion.getActivoFinanciero().getId() == null) {
            throw new RuntimeException("La transacción debe tener un activo financiero asignado");
        }
        transaccion.setCliente(clienteRepository.findById(transaccion.getCliente().getId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado")));
        transaccion.setActivoFinanciero(activoFinancieroRepository.findById(transaccion.getActivoFinanciero().getId())
                .orElseThrow(() -> new RuntimeException("Activo financiero no encontrado")));
        return transaccionRepository.save(transaccion);
    }
}
