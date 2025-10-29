package co.edu.uniquindio.proyectoAlgoritmos.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {
    // Identificación y tipo
    private String id;                 // UUID interno
    private String entryType;          // article, inproceedings, incollection, etc.
    private String originalBibtexKey;  // clave del .bib original

    // Campos bibliográficos
    private String title;
    private List<String> authors;
    private String journal;
    private String booktitle; // para conferencias/capítulos
    private Integer year;
    private String volume;
    private String pages;
    private String doi;
    private String url;
    private String issn;
    private String isbn;
    private List<String> keywords;
    private String abstractText;

    public void ensureId() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
    }

    public String getNormalizedTitle() {
        if (title == null) return "";
        return title.toLowerCase().replaceAll("[\\p{Punct}]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    public String getDeduplicationKey() {
        if (doi != null && !doi.isBlank()) return "doi:" + doi.trim().toLowerCase();
        return "title:" + getNormalizedTitle() + "|year:" + (year != null ? year : "");
    }

    public List<String> safeAuthors() { return authors != null ? authors : new ArrayList<>(); }
    public List<String> safeKeywords() { return keywords != null ? keywords : new ArrayList<>(); }
}

