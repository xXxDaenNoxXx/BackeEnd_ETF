package bff_web.banco.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bff_web.banco.dto.CuentaWebDTO;
import bff_web.banco.dto.ResumenWebDTO;
import bff_web.banco.service.BffWebService;
import lombok.RequiredArgsConstructor;

/** BFF WEB: respuestas COMPLETAS (titular, historial, totales) para interfaces ricas en navegador. Scope web.read. */
@RestController
@RequestMapping("/web/cuentas")
@RequiredArgsConstructor
public class CuentaWebController {

    private final BffWebService service;

    @GetMapping
    public List<CuentaWebDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CuentaWebDTO> obtener(@PathVariable Long id) {
        CuentaWebDTO c = service.obtener(id);
        return c != null ? ResponseEntity.ok(c) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/resumen")
    public ResponseEntity<ResumenWebDTO> resumen(@PathVariable Long id) {
        ResumenWebDTO r = service.resumen(id);
        return r != null ? ResponseEntity.ok(r) : ResponseEntity.notFound().build();
    }
}
