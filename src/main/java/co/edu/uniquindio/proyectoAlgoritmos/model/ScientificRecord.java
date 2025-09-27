package co.edu.uniquindio.proyectoAlgoritmos.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.*;

/**
 * Representa un registro científico con toda la información bibliométrica necesaria
 */
@AllArgsConstructor
@Data
@Builder
public class ScientificRecord {
    private String id;
    private String title;
    private List<String> authors;
    private String firstAuthor;
    private String abstractText;
    private List<String> keywords;
    private String journal;
    private String conference;
    private int year;
    private LocalDate publicationDate;
    private String doi;
    private String isbn;
    private String issn;
    private int citationCount;
    private String documentType; // Article, Conference Paper, Book Chapter, etc.
    private String source; // Scopus, DBLP, ACM, etc.
    private String affiliations;
    private String country;
    private String language;
    private String publisher;
    private int volume;
    private String issue;
    private String pages;
    private String url;
    private Map<String, String> additionalFields;

    // Constructor vacío
    public ScientificRecord() {
        this.authors = new ArrayList<>();
        this.keywords = new ArrayList<>();
        this.additionalFields = new HashMap<>();
    }

    // Constructor completo
    public ScientificRecord(String id, String title, List<String> authors, String abstractText) {
        this();
        this.id = id;
        this.title = title;
        this.authors = new ArrayList<>(authors);
        this.abstractText = abstractText;
        this.firstAuthor = authors.isEmpty() ? "" : authors.get(0);
    }

    public List<String> getAuthors() { return new ArrayList<>(authors); }
    public void setAuthors(List<String> authors) { 
        this.authors = new ArrayList<>(authors);
        this.firstAuthor = authors.isEmpty() ? "" : authors.get(0);
    }

    public Map<String, String> getAdditionalFields() { return new HashMap<>(additionalFields); }
    public void setAdditionalFields(Map<String, String> additionalFields) { 
        this.additionalFields = new HashMap<>(additionalFields); 
    }

    public void addAdditionalField(String key, String value) {
        this.additionalFields.put(key, value);
    }

    // Métodos utilitarios
    public void addAuthor(String author) {
        this.authors.add(author);
        if (this.firstAuthor == null || this.firstAuthor.isEmpty()) {
            this.firstAuthor = author;
        }
    }

    public void addKeyword(String keyword) {
        this.keywords.add(keyword);
    }

    /**
     * Genera un hash único basado en título y primer autor para detectar duplicados
     */
    public String generateUniqueHash() {
        String titleNormalized = title != null ? title.toLowerCase().replaceAll("[^a-zA-Z0-9]", "") : "";
        String authorNormalized = firstAuthor != null ? firstAuthor.toLowerCase().replaceAll("[^a-zA-Z0-9]", "") : "";
        return (titleNormalized + authorNormalized).hashCode() + "";
    }

    @Override
    public String toString() {
        return String.format("ScientificRecord{id='%s', title='%s', firstAuthor='%s', year=%d, source='%s'}", 
                           id, title, firstAuthor, year, source);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ScientificRecord that = (ScientificRecord) obj;
        return Objects.equals(generateUniqueHash(), that.generateUniqueHash());
    }

    @Override
    public int hashCode() {
        return Objects.hash(generateUniqueHash());
    }
}