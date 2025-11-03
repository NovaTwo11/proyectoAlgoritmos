package co.edu.uniquindio.proyectoAlgoritmos.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarityResponse {
    private List<String> labels;             // etiquetas usadas (títulos o ids)
    @JsonIgnore
    private double[][] distancesEuclidean;   // matriz NxN de distancias euclidianas (no serializar)
    private List<SimilarityPairDTO> topSimilar; // top pares por similitud coseno

    @JsonIgnore
    private List<Map<String, Double>> tfidfVectors; // vectores TF-IDF por documento (no serializar)
}
