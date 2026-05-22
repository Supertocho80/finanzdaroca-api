package com.daroca.sistema_financiero.controller;

import com.daroca.sistema_financiero.entity.Cliente;
import com.daroca.sistema_financiero.entity.Rol;
import com.daroca.sistema_financiero.service.ClienteService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping
    public ResponseEntity<List<Cliente>> listar(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) Rol rol) {
        if (usuarioId != null && rol != null) {
            return ResponseEntity.ok(clienteService.obtenerClientesPorUsuario(usuarioId, rol));
        }
        return ResponseEntity.ok(clienteService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Cliente> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.obtenerPorId(id));
    }

    @GetMapping("/{id}/patrimonio")
    public ResponseEntity<Map<String, Object>> obtenerPatrimonio(
            @PathVariable Long id,
            @RequestParam(defaultValue = "EUR") String divisa) {
        String divisaDestino = divisa.toUpperCase();
        BigDecimal patrimonio = clienteService.calcularPatrimonio(id, divisaDestino);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("clienteId", id);
        respuesta.put("patrimonio", patrimonio);
        respuesta.put("moneda", divisaDestino);

        return ResponseEntity.ok(respuesta);
    }

    @PostMapping
    public ResponseEntity<Cliente> crear(@RequestBody Cliente cliente) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.crear(cliente));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Cliente> actualizar(@PathVariable Long id, @RequestBody Cliente cliente) {
        return ResponseEntity.ok(clienteService.actualizar(id, cliente));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        clienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
