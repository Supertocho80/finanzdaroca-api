package com.daroca.sistema_financiero.service;

import com.daroca.sistema_financiero.entity.TipoOperacion;
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
        return transaccionRepository.findByClienteIdOrderByFechaDesc(clienteId);
    }

    public Transaccion obtenerPorId(Long id) {
        return transaccionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transacción no encontrada con id: " + id));
    }

    public Transaccion crear(Transaccion transaccion) {
        if (transaccion.getCliente() == null || transaccion.getCliente().getId() == null) {
            throw new RuntimeException("La transacción debe tener un cliente asignado");
        }

        transaccion.setCliente(clienteRepository.findById(transaccion.getCliente().getId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado")));

        prepararSegunTipo(transaccion);
        return transaccionRepository.save(transaccion);
    }

    public Transaccion actualizar(Long id, Transaccion datos) {
        Transaccion transaccion = obtenerPorId(id);

        if (datos.getCliente() != null && datos.getCliente().getId() != null) {
            transaccion.setCliente(clienteRepository.findById(datos.getCliente().getId())
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado")));
        }

        if (datos.getTipoOperacion() != null) {
            transaccion.setTipoOperacion(datos.getTipoOperacion());
        }
        if (datos.getCantidad() != null) {
            transaccion.setCantidad(datos.getCantidad());
        }
        if (datos.getPrecioEjecucion() != null) {
            transaccion.setPrecioEjecucion(datos.getPrecioEjecucion());
        }
        if (datos.getMoneda() != null) {
            transaccion.setMoneda(datos.getMoneda());
        }
        if (datos.getFecha() != null) {
            transaccion.setFecha(datos.getFecha());
        }

        transaccion.setActivoFinanciero(datos.getActivoFinanciero());

        prepararSegunTipo(transaccion);
        return transaccionRepository.save(transaccion);
    }

    public void eliminar(Long id) {
        if (!transaccionRepository.existsById(id)) {
            throw new RuntimeException("Transacción no encontrada con id: " + id);
        }
        transaccionRepository.deleteById(id);
    }

    private void prepararSegunTipo(Transaccion transaccion) {
        TipoOperacion tipo = transaccion.getTipoOperacion();
        if (tipo == null) {
            throw new RuntimeException("El tipo de operación es obligatorio");
        }

        if (tipo == TipoOperacion.DEPOSITO || tipo == TipoOperacion.RETIRO) {
            if (transaccion.getMoneda() == null || transaccion.getMoneda().isBlank()) {
                throw new RuntimeException("DEPOSITO y RETIRO requieren el campo moneda");
            }
            if (transaccion.getPrecioEjecucion() == null || transaccion.getPrecioEjecucion() <= 0) {
                throw new RuntimeException("DEPOSITO y RETIRO requieren precioEjecucion como importe");
            }
            transaccion.setMoneda(transaccion.getMoneda().toUpperCase());
            transaccion.setActivoFinanciero(null);
            if (transaccion.getCantidad() == null) {
                transaccion.setCantidad(1);
            }
        } else {
            if (transaccion.getActivoFinanciero() == null || transaccion.getActivoFinanciero().getId() == null) {
                throw new RuntimeException(
                        "COMPRA, VENTA, DIVIDENDO y ALQUILER requieren un activo financiero asignado");
            }
            if (transaccion.getCantidad() == null || transaccion.getCantidad() <= 0) {
                throw new RuntimeException(
                        "COMPRA, VENTA, DIVIDENDO y ALQUILER requieren cantidad positiva");
            }
            if (transaccion.getPrecioEjecucion() == null || transaccion.getPrecioEjecucion() <= 0) {
                throw new RuntimeException(
                        "COMPRA, VENTA, DIVIDENDO y ALQUILER requieren precioEjecucion positivo");
            }
            transaccion.setActivoFinanciero(activoFinancieroRepository.findById(
                            transaccion.getActivoFinanciero().getId())
                    .orElseThrow(() -> new RuntimeException("Activo financiero no encontrado")));
            transaccion.setMoneda(null);
        }
    }
}
