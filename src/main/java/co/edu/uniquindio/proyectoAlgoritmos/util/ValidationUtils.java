package co.edu.uniquindio.proyectoAlgoritmos.util;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ValidationUtils {

    public boolean isValidScientificRecord(ScientificRecord record) {
        if (record == null) {
            return false;
        }

        // Validaciones básicas
        return hasValidTitle(record.getTitle()) &&
                hasValidAuthors(record.getAuthors());
    }

    public List<String> validateScientificRecord(ScientificRecord record) {
        List<String> errors = new ArrayList<>();

        if (record == null) {
            errors.add("El registro científico no puede ser nulo");
            return errors;
        }

        if (!hasValidTitle(record.getTitle())) {
            errors.add("El título es requerido y debe tener al menos 5 caracteres");
        }

        if (!hasValidAuthors(record.getAuthors())) {
            errors.add("Debe tener al menos un autor válido");
        }

        if (record.getYear() != 0 && (record.getYear() < 1900 || record.getYear() > 2030)) {
            errors.add("El año debe estar entre 1900 y 2030");
        }

        if (StringUtils.isNotBlank(record.getDoi()) && !isValidDoi(record.getDoi())) {
            errors.add("El formato del DOI no es válido");
        }

        return errors;
    }

    private boolean hasValidTitle(String title) {
        return StringUtils.isNotBlank(title) && title.trim().length() >= 5;
    }

    private boolean hasValidAuthors(List<String> authors) {
        return authors != null &&
                !authors.isEmpty() &&
                authors.stream().anyMatch(StringUtils::isNotBlank);
    }

    private boolean isValidDoi(String doi) {
        // Validación básica de formato DOI
        return doi.matches("^10\\.\\d{4,}/.*");
    }

    public boolean isValidSearchQuery(String query) {
        return StringUtils.isNotBlank(query) &&
                query.trim().length() >= 3 &&
                query.trim().length() <= 200;
    }

    public String sanitizeSearchQuery(String query) {
        if (StringUtils.isBlank(query)) {
            return "generative artificial intelligence";
        }

        return query.trim()
                .replaceAll("[^a-zA-Z0-9\\s]", "")
                .replaceAll("\\s+", " ");
    }
}