package com.daroca.sistema_financiero.service;

import com.daroca.sistema_financiero.entity.Cliente;
import com.daroca.sistema_financiero.entity.Rol;
import com.daroca.sistema_financiero.repository.ClienteRepository;
import com.daroca.sistema_financiero.repository.UsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;

    public List<Cliente> listarTodos() {
        return clienteRepository.findAll();
    }

    public Cliente obtenerPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id: " + id));
    }

    public List<Cliente> obtenerClientesPorUsuario(Long usuarioId, Rol rol) {
        if (rol == Rol.ADMIN) {
            return clienteRepository.findAll();
        }
        return clienteRepository.findByAsesorId(usuarioId);
    }

    public Cliente crear(Cliente cliente) {
        if (cliente.getAsesor() == null || cliente.getAsesor().getId() == null) {
            throw new RuntimeException("El cliente debe tener un asesor asignado");
        }
        cliente.setAsesor(usuarioRepository.findById(cliente.getAsesor().getId())
                .orElseThrow(() -> new RuntimeException("Asesor no encontrado")));
        return clienteRepository.save(cliente);
    }

    public Cliente actualizar(Long id, Cliente clienteActualizado) {
        Cliente cliente = obtenerPorId(id);
        cliente.setNombre(clienteActualizado.getNombre());
        cliente.setEmail(clienteActualizado.getEmail());
        cliente.setPerfilRiesgo(clienteActualizado.getPerfilRiesgo());
        if (clienteActualizado.getAsesor() != null && clienteActualizado.getAsesor().getId() != null) {
            cliente.setAsesor(usuarioRepository.findById(clienteActualizado.getAsesor().getId())
                    .orElseThrow(() -> new RuntimeException("Asesor no encontrado")));
        }
        return clienteRepository.save(cliente);
    }

    public void eliminar(Long id) {
        if (!clienteRepository.existsById(id)) {
            throw new RuntimeException("Cliente no encontrado con id: " + id);
        }
        clienteRepository.deleteById(id);
    }
}
