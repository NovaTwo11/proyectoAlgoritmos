package co.edu.uniquindio.proyectoAlgoritmos.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TermFrequency {
    private String term;
    private int count;
}

