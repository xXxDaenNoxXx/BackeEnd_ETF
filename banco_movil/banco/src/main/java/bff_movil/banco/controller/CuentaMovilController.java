package bff_movil.banco.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bff_movil.banco.dto.ResumenMovilDTO;
import bff_movil.banco.dto.SaldoMovilDTO;
import bff_movil.banco.service.BffMovilService;
import lombok.RequiredArgsConstructor;

/**
 * BFF MOVIL: respuestas LIGERAS (sin datos personales innecesarios, 5 movimientos max, compresion gzip)
 * y Cache-Control corto para que la app no repita la consulta. Scope movil.read.
 */
@RestController
@RequestMapping("/movil/cuentas")
@RequiredArgsConstructor
public class CuentaMovilController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofSeconds(15)).cachePrivate();

    private final BffMovilService service;

    @GetMapping("/{id}/saldo")
    public ResponseEntity<SaldoMovilDTO> saldo(@PathVariable Long id) {
        SaldoMovilDTO s = service.saldo(id);
        return s != null ? ResponseEntity.ok().cacheControl(CACHE).body(s) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/resumen")
    public ResponseEntity<ResumenMovilDTO> resumen(@PathVariable Long id) {
        ResumenMovilDTO r = service.resumen(id);
        return r != null ? ResponseEntity.ok().cacheControl(CACHE).body(r) : ResponseEntity.notFound().build();
    }
}
