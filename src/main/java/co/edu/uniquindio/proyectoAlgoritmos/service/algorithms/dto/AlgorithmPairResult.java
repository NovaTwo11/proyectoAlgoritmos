package co.edu.uniquindio.proyectoAlgoritmos.service.algorithms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlgorithmPairResult {
    private String idA;
    private String idB;
    private int distance;            // distancia Levenshtein (o métrica del algoritmo)
    private double score;            // [0..1]
    private double similarityPercent; // [0..100]
    private long timeMs;             // tiempo por comparación
}

