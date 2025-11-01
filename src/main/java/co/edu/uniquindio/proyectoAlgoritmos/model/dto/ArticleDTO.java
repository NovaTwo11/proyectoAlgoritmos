package co.edu.uniquindio.proyectoAlgoritmos.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ArticleDTO {
    private String id; // UUID interno
    private String title;
    private List<String> authors;
    private Integer year;
    private String pages;
    private List<String> keywords;
    private String abstractText;
}