package co.edu.uniquindio.proyectoAlgoritmos.controller;

import co.edu.uniquindio.proyectoAlgoritmos.service.SortingBenchmarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sorting")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SortingBenchmarkController {

    private final SortingBenchmarkService benchmarkService;

    @PostMapping("/benchmark")
    public ResponseEntity<List<SortingBenchmarkService.BenchmarkResult>> runBenchmark(
            @RequestParam(defaultValue = "10000") int arraySize) {

        log.info("Ejecutando benchmark con tamaño: {}", arraySize);

        List<SortingBenchmarkService.BenchmarkResult> results = 
            benchmarkService.runCompleteBenchmark(arraySize);

        return ResponseEntity.ok(results);
    }

    @PostMapping("/benchmark/export")
    public ResponseEntity<String> runBenchmarkAndExport(
            @RequestParam(defaultValue = "10000") int arraySize,
            @RequestParam(defaultValue = "sorting_benchmark_results.csv") String filename) {

        try {
            List<SortingBenchmarkService.BenchmarkResult> results = 
                benchmarkService.runCompleteBenchmark(arraySize);

            benchmarkService.exportResultsToCSV(results, filename);

            return ResponseEntity.ok("Benchmark completado y exportado a: " + filename);
        } catch (IOException e) {
            log.error("Error exportando resultados", e);
            return ResponseEntity.internalServerError()
                .body("Error exportando resultados: " + e.getMessage());
        }
    }

    @GetMapping("/authors/top15")
    public ResponseEntity<List<Map.Entry<String, Integer>>> getTop15Authors() {
        // Aquí necesitarías obtener los registros unificados
        // Por ahora devolvemos una respuesta vacía
        return ResponseEntity.ok(List.of());
    }
}