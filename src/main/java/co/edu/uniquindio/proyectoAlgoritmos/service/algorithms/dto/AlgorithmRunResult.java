package co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlgorithmRunResult {
    private String algorithm;          // nombre del algoritmo
    private long totalTimeMs;          // duración total del run
    private int totalComparisons;      // pares generados
    private List<AlgorithmPairResult> results; // resultados por par
}

