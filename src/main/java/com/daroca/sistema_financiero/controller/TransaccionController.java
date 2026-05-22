package com.daroca.sistema_financiero.controller;

import com.daroca.sistema_financiero.entity.Transaccion;
import com.daroca.sistema_financiero.security.SecurityAuditService;
import com.daroca.sistema_financiero.service.TransaccionService;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transacciones")
@RequiredArgsConstructor
public class TransaccionController {

    private final TransaccionService transaccionService;
    private final SecurityAuditService securityAuditService;

    @GetMapping
    public ResponseEntity<List<Transaccion>> listarTodas() {
        securityAuditService.verificarRolAdmin();
        return ResponseEntity.ok(transaccionService.listarTodas());
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Transaccion>> listarPorCliente(@PathVariable Long clienteId) {
        securityAuditService.verificarAccesoCliente(clienteId);
        return ResponseEntity.ok(transaccionService.listarPorCliente(clienteId));
    }

    @PostMapping
    public ResponseEntity<Transaccion> crear(@RequestBody Transaccion transaccion) {
        securityAuditService.verificarUsuarioAutenticado();
        if (transaccion.getCliente() == null || transaccion.getCliente().getId() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "La transacción debe indicar el cliente");
        }
        securityAuditService.verificarAccesoCliente(transaccion.getCliente().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(transaccionService.crear(transaccion));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transaccion> actualizar(
            @PathVariable Long id, @RequestBody Transaccion transaccion) {
        Transaccion existente = transaccionService.obtenerPorId(id);
        securityAuditService.verificarAccesoCliente(existente.getCliente().getId());
        if (transaccion.getCliente() != null && transaccion.getCliente().getId() != null) {
            securityAuditService.verificarAccesoCliente(transaccion.getCliente().getId());
        }
        return ResponseEntity.ok(transaccionService.actualizar(id, transaccion));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        Transaccion existente = transaccionService.obtenerPorId(id);
        securityAuditService.verificarAccesoCliente(existente.getCliente().getId());
        transaccionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
