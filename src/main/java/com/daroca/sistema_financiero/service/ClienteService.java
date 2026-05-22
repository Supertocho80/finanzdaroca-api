package com.daroca.sistema_financiero.service;

import com.daroca.sistema_financiero.entity.ActivoFinanciero;
import com.daroca.sistema_financiero.entity.Cliente;
import com.daroca.sistema_financiero.entity.Rol;
import com.daroca.sistema_financiero.entity.TipoOperacion;
import com.daroca.sistema_financiero.entity.Transaccion;
import com.daroca.sistema_financiero.integration.YahooFinanceChartClient;
import com.daroca.sistema_financiero.repository.ClienteRepository;
import com.daroca.sistema_financiero.repository.TransaccionRepository;
import com.daroca.sistema_financiero.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final TransaccionRepository transaccionRepository;
    private final YahooFinanceChartClient yahooFinanceChartClient;

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

    public Double calcularPatrimonio(Long clienteId) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new RuntimeException("Cliente no encontrado con id: " + clienteId);
        }

        List<Transaccion> transacciones = transaccionRepository.findByClienteId(clienteId);
        BigDecimal patrimonio = BigDecimal.ZERO;
        Map<String, BigDecimal> tiposCambioCache = new HashMap<>();

        for (Transaccion transaccion : transacciones) {
            ActivoFinanciero activo = transaccion.getActivoFinanciero();
            BigDecimal valorPosicion = BigDecimal.valueOf(transaccion.getCantidad())
                    .multiply(BigDecimal.valueOf(activo.getPrecioMercado()));

            BigDecimal valorEnEur = convertirAEuros(valorPosicion, activo.getMoneda(), tiposCambioCache);

            if (transaccion.getTipoOperacion() == TipoOperacion.COMPRA) {
                patrimonio = patrimonio.add(valorEnEur);
            } else {
                patrimonio = patrimonio.subtract(valorEnEur);
            }
        }

        return patrimonio.setScale(2, RoundingMode.DOWN).doubleValue();
    }

    private BigDecimal convertirAEuros(BigDecimal valor, String moneda, Map<String, BigDecimal> tiposCambioCache) {
        if (moneda == null || "EUR".equalsIgnoreCase(moneda)) {
            return valor;
        }

        BigDecimal tipoCambio = tiposCambioCache.computeIfAbsent(moneda, this::obtenerTipoCambioAEur);
        return valor.multiply(tipoCambio);
    }

    private BigDecimal obtenerTipoCambioAEur(String moneda) {
        String parDivisa = moneda + "EUR=X";
        return yahooFinanceChartClient.fetchRegularMarketPrice(parDivisa)
                .map(BigDecimal::valueOf)
                .orElseThrow(() -> new RuntimeException(
                        "No se pudo obtener el tipo de cambio para " + moneda + " hacia EUR"));
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
