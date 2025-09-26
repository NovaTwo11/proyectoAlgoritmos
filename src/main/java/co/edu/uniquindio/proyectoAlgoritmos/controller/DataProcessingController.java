package co.edu.uniquindio.proyectoAlgoritmos.controller;

import co.edu.uniquindio.proyectoAlgoritmos.dto.ProcessingResultDto;
import co.edu.uniquindio.proyectoAlgoritmos.service.DataUnificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/data-processing")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DataProcessingController {

    private final DataUnificationService unificationService;

    @PostMapping("/unify")
    public CompletableFuture<ResponseEntity<ProcessingResultDto>> unifyData(
            @RequestParam(defaultValue = "generative artificial intelligence") String searchQuery) {

        log.info("Recibida solicitud de unificación con query: {}", searchQuery);

        return unificationService.processAndUnifyData(searchQuery)
                .thenApply(result -> {
                    if (result.getStatus().name().equals("COMPLETED")) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.internalServerError().body(result);
                    }
                });
    }

    @GetMapping("/status/{processId}")
    public ResponseEntity<String> getProcessStatus(@PathVariable String processId) {
        // En una implementación real, aquí consultaríamos el estado del proceso
        return ResponseEntity.ok("Process status for: " + processId);
    }
}
