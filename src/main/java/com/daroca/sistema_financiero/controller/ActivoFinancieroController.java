package com.daroca.sistema_financiero.controller;

import com.daroca.sistema_financiero.entity.ActivoFinanciero;
import com.daroca.sistema_financiero.service.ActivoFinancieroService;
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
@RequestMapping("/api/activos-financieros")
@RequiredArgsConstructor
public class ActivoFinancieroController {

    private final ActivoFinancieroService activoFinancieroService;

    @GetMapping
    public ResponseEntity<List<ActivoFinanciero>> listarTodos() {
        return ResponseEntity.ok(activoFinancieroService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivoFinanciero> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(activoFinancieroService.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<ActivoFinanciero> crear(@RequestBody ActivoFinanciero activoFinanciero) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activoFinancieroService.crear(activoFinanciero));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ActivoFinanciero> actualizar(@PathVariable Long id, @RequestBody ActivoFinanciero activoFinanciero) {
        return ResponseEntity.ok(activoFinancieroService.actualizar(id, activoFinanciero));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        activoFinancieroService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
