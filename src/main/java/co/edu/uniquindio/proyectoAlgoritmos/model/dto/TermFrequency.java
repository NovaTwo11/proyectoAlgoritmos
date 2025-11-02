package co.edu.uniquindio.proyectoAlgoritmos.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TermFrequency {
    private String term;
    private int frequency;

    public TermFrequency(String key, Integer value) {
        this.term = key;
        this.frequency = value;
    }
}

