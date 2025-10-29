package co.edu.uniquindio.proyectoAlgoritmos.controller;

import co.edu.uniquindio.proyectoAlgoritmos.dto.ProcessingResultDto;
import co.edu.uniquindio.proyectoAlgoritmos.service.requirement1.DataAutomationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API REST para Requerimiento 1: Automatización de descarga y unificación
 */
@RestController
@RequestMapping("/api/v1/requirement1")
@RequiredArgsConstructor
@Slf4j
public class Requirement1Controller {

    private final DataAutomationService dataAutomationService;

    /**
     * POST /api/v1/requirement1/download-and-unify
     * Ejecuta el proceso completo de descarga y unificación
     */
    @PostMapping("/download-and-unify")
    public ResponseEntity<?> downloadAndUnify(@RequestParam String searchQuery) {
        log.info("Iniciando proceso de descarga y unificación para: {}", searchQuery);
        // TODO: Implementar endpoint
        return ResponseEntity.ok("Requerimiento 1 - Por implementar");
    }

    /**
     * GET /api/v1/requirement1/status
     * Obtiene el estado del proceso de automatización
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        // TODO: Implementar endpoint
        return ResponseEntity.ok("Estado del proceso");
    }
}

