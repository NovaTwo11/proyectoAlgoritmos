package co.edu.uniquindio.proyectoAlgoritmos.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FaviconController {

    // Evitar errores por peticiones automáticas del navegador a /favicon.ico
    @GetMapping(value = "/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}

