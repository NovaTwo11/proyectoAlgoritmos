package co.edu.uniquindio.proyectoAlgoritmos.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore
    private double[][] distancesEuclidean;   // matriz NxN de distancias euclidianas (no serializar)
    private List<SimilarityPairDTO> topSimilar; // top pares por similitud coseno
}
