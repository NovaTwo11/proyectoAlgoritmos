package co.edu.uniquindio.proyectoAlgoritmos.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TimelineResponse {
    // Publicaciones totales por año
    private Map<Integer, Integer> publicationsByYear;
    // Publicaciones por año y por venue (journal/booktitle)
    // Estructura: {year -> {venue -> count}}
    private Map<Integer, Map<String, Integer>> publicationsByYearAndVenue;
    // Lista de venues únicos usados (para facilitar render en front)
    private List<String> venues;
}

