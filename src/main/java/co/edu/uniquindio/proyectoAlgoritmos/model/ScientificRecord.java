package co.edu.uniquindio.proyectoAlgoritmos.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScientificRecord {
    private String id;
    private String title;
    private List<String> authors;
    private String abstractText;
    private List<String> keywords;
    private String journal;
    private LocalDate publicationDate;
    private Integer year;
    private String doi;
    private String url;
    private DataSource source;
    private String country;
    private String language;

    // Método para generar ID único basado en título normalizado
    public String generateUniqueId() {
        if (title == null || title.trim().isEmpty()) {
            return "unknown_" + System.currentTimeMillis();
        }
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "_")
                .substring(0, Math.min(50, title.length()));
    }

    // Método para comparación de duplicados
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ScientificRecord that = (ScientificRecord) obj;
        return Objects.equals(normalizeTitle(), that.normalizeTitle());
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizeTitle());
    }

    private String normalizeTitle() {
        return title != null ? title.toLowerCase().trim() : "";
    }
}