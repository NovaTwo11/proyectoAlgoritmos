package co.edu.uniquindio.proyectoAlgoritmos.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarityResponse {
    private List<String> labels;             // etiquetas usadas (títulos o ids)
    private double[][] distancesEuclidean;   // matriz NxN de distancias euclidianas
    private List<SimilarityPairDTO> topSimilar; // top pares por similitud coseno
}

