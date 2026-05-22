package com.daroca.sistema_financiero.controller;

import com.daroca.sistema_financiero.entity.Transaccion;
import com.daroca.sistema_financiero.service.TransaccionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transacciones")
@RequiredArgsConstructor
public class TransaccionController {

    private final TransaccionService transaccionService;

    @GetMapping
    public ResponseEntity<List<Transaccion>> listarTodas() {
        return ResponseEntity.ok(transaccionService.listarTodas());
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Transaccion>> listarPorCliente(@PathVariable Long clienteId) {
        return ResponseEntity.ok(transaccionService.listarPorCliente(clienteId));
    }

    @PostMapping
    public ResponseEntity<Transaccion> crear(@RequestBody Transaccion transaccion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transaccionService.crear(transaccion));
    }
}
