package co.edu.uniquindio.proyectoAlgoritmos.controller;

import co.edu.uniquindio.proyectoAlgoritmos.service.AutomationOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/algoritmos")
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationOrchestratorService orchestrator;

    @PostMapping("/download-articles")
    public ResponseEntity<String> runReq1(@RequestParam(required = false) String query) {

        orchestrator.downloadArticles(query);
        return ResponseEntity.ok("Requirement 1 ejecutado. Revisa: resources/downloads (archivos .bib descargados) y resources/data/output/downloads para resultados unificados.");
    }
}
