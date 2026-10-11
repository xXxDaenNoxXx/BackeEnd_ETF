package bff_cajeros.banco.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bff_cajeros.banco.dto.CuentaCajeroSaldoDTO;
import bff_cajeros.banco.dto.RetiroRequestDTO;
import bff_cajeros.banco.dto.RetiroResponseDTO;
import bff_cajeros.banco.service.BffCajeroService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/cajero/cuentas/{id}")
@RequiredArgsConstructor
public class CuentaCajeroController {

    private final BffCajeroService bffCajeroService;

    @GetMapping("/saldo")
    public ResponseEntity<CuentaCajeroSaldoDTO> consultarSaldo(@PathVariable Long id) {
        CuentaCajeroSaldoDTO saldo = bffCajeroService.consultarSaldo(id);
        return (saldo != null) ? ResponseEntity.ok(saldo) : ResponseEntity.notFound().build();
    }

    @PostMapping("/retiro")
    public ResponseEntity<RetiroResponseDTO> retirar(@PathVariable Long id, @RequestBody RetiroRequestDTO request) {
        RetiroResponseDTO resultado = bffCajeroService.retirar(id, request.monto());
        if (resultado == null) return ResponseEntity.notFound().build();
        if (resultado.saldoActual() == null) return ResponseEntity.badRequest().body(resultado);
        return ResponseEntity.ok(resultado);
    }
}