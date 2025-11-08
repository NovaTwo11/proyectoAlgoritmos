package co.edu.uniquindio.proyectoAlgoritmos.service.viz;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.MapCountryCountDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VizAggregationService {

    private static final String ENRICHED_BIB = "src/main/resources/data/output/resultados_unificados_autor_pais.bib";

    private static final Pattern ENTRY_PATTERN = Pattern.compile("@(?i)([a-z]+)\\s*\\{\\s*([^,]+),[\\s\\S]*?countryFirstAuthor\\s*=\\s*\\{([^}]+)\\}", Pattern.MULTILINE);

    public List<MapCountryCountDTO> aggregateFirstAuthorCountries() {
        Path p = Paths.get(ENRICHED_BIB);
        if (!Files.exists(p)) {
            log.warn("Archivo enriquecido no existe: {}", p.toAbsolutePath());
            return List.of();
        }
        String content;
        try { content = Files.readString(p, StandardCharsets.UTF_8); } catch (IOException e) { log.error("Error leyendo bib: {}", e.getMessage()); return List.of(); }

        Map<String,Integer> counts = new HashMap<>();
        Matcher m = ENTRY_PATTERN.matcher(content);
        int entries = 0;
        while (m.find()) {
            entries++;
            String country = m.group(3);
            if (country != null) country = country.trim();
            if (country == null || country.isBlank()) country = "UNKNOWN";
            // normalizar a mayúsculas para consistencia
            country = country.toUpperCase(Locale.ROOT);
            counts.merge(country, 1, Integer::sum);
        }
        log.info("Agregados países de {} entradas", entries);
        return counts.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(e -> new MapCountryCountDTO(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }
}

