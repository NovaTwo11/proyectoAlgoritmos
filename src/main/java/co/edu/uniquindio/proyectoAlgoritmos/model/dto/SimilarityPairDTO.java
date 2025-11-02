package co.edu.uniquindio.proyectoAlgoritmos.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimilarityPairDTO {
    private String a;       // etiqueta/id del primer documento
    private String b;       // etiqueta/id del segundo documento
    private double score;   // similitud coseno [0..1]
}

