package co.edu.uniquindio.proyectoAlgoritmos.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreprocessedArticleDTO {
    private String id;
    private String title;
    private String normalizedText;
    private List<String> tokens;
}

