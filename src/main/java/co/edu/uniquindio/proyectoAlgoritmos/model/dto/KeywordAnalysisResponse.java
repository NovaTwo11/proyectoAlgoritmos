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
public class KeywordAnalysisResponse {
    // Frecuencia de palabras asociadas (input)
    private String category;
    private List<TermFrequency> givenKeywordFrequencies;

    // Palabras nuevas extraídas (máx N) con frecuencia
    private List<TermFrequency> discoveredKeywords;

    // Métrica sencilla de precisión respecto a categoría
    private double precision; // 0..1 (porcentaje de relevantes)
    private String precisionExplanation;
}

