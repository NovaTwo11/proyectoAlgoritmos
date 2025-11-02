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
public class PreprocessingResponse {
    private int inputCount;
    private int outputCount;
    private String dedupCriteria;
    private PreprocessingOptions options;
    private List<PreprocessedArticleDTO> articles;
}

