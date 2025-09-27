package co.edu.uniquindio.proyectoAlgoritmos.mapper;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Mapper específico para archivos CSV exportados de Scopus
 */
public class ScopusFieldMapper implements FieldMapper {

    @Override
    public ScientificRecord mapToScientificRecord(Map<String, String> fields) {
        String id = getField(fields, "EID", "Scopus ID");
        String title = cleanText(getField(fields, "Title"));
        String abstractText = cleanText(getField(fields, "Abstract"));

        List<String> authors = new ArrayList<>();
        String authorsStr = getField(fields, "Authors", "Author(s) ID");
        if (authorsStr != null && !authorsStr.isEmpty()) {
            authors = parseAuthors(authorsStr);
        }

        List<String> keywords = new ArrayList<>();
        String keywordsStr = getField(fields, "Author Keywords", "Index Keywords");
        if (keywordsStr != null && !keywordsStr.isEmpty()) {
            keywords = parseKeywords(keywordsStr);
        }

        Integer year = 0;
        String yearStr = getField(fields, "Year");
        if (yearStr != null && !yearStr.isEmpty()) {
            try {
                year = Integer.parseInt(yearStr);
            } catch (NumberFormatException ignored) {}
        }

        LocalDate publicationDate = null;
        String dateStr = getField(fields, "Publication Date", "Date");
        if (dateStr != null && !dateStr.isEmpty()) {
            publicationDate = parseDate(dateStr);
        }

        int citations = 0;
        String citationsStr = getField(fields, "Cited by", "Citation Count");
        if (citationsStr != null && !citationsStr.isEmpty()) {
            try {
                citations = Integer.parseInt(citationsStr);
            } catch (NumberFormatException ignored) {}
        }

        return ScientificRecord.builder()
                .id(id)
                .title(title)
                .abstractText(abstractText)
                .authors(authors)
                .keywords(keywords)
                .journal(getField(fields, "Source title", "Journal"))
                .publisher(getField(fields, "Publisher"))
                .doi(getField(fields, "DOI"))
                .issn(getField(fields, "ISSN"))
                .documentType(getField(fields, "Document Type"))
                .year(year)
                .publicationDate(publicationDate)
                .citationCount(citations)
                .affiliations(getField(fields, "Affiliations"))
                .country(extractCountry(getField(fields, "Affiliations")))
                .volume(parseInteger(getField(fields, "Volume")))
                .issue(getField(fields, "Issue"))
                .pages(getField(fields, "Page start", "Pages"))
                .language(getField(fields, "Language of Original Document"))
                .url(getField(fields, "Link", "URL"))
                .source("Scopus")
                .build();
    }

    private String getField(Map<String, String> fields, String... possibleNames) {
        for (String name : possibleNames) {
            String value = fields.get(name);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String cleanText(String text) {
        if (text == null) return null;
        return text.replaceAll("\s+", " ").trim();
    }

    private List<String> parseAuthors(String authorsStr) {
        List<String> authors = new ArrayList<>();
        if (authorsStr != null && !authorsStr.isEmpty()) {
            String[] authorArray = authorsStr.split("[;,]");
            for (String author : authorArray) {
                String cleanAuthor = author.trim();
                if (!cleanAuthor.isEmpty()) {
                    authors.add(cleanAuthor);
                }
            }
        }
        return authors;
    }

    private List<String> parseKeywords(String keywordsStr) {
        List<String> keywords = new ArrayList<>();
        if (keywordsStr != null && !keywordsStr.isEmpty()) {
            String[] keywordArray = keywordsStr.split("[;,]");
            for (String keyword : keywordArray) {
                String cleanKeyword = keyword.trim();
                if (!cleanKeyword.isEmpty()) {
                    keywords.add(cleanKeyword);
                }
            }
        }
        return keywords;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;

        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy")
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Continuar con el siguiente formato
            }
        }

        System.err.println("No se pudo parsear la fecha: " + dateStr);
        return null;
    }

    private int parseInteger(String str) {
        if (str == null || str.isEmpty()) return 0;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractCountry(String affiliations) {
        if (affiliations == null || affiliations.isEmpty()) return null;

        // Lógica simple para extraer país (último elemento después de coma)
        String[] parts = affiliations.split(",");
        if (parts.length > 0) {
            return parts[parts.length - 1].trim();
        }
        return null;
    }

    private boolean isMappedField(String fieldName) {
        Set<String> mappedFields = Set.of(
            "EID", "Scopus ID", "Title", "Abstract", "Authors", "Author(s) ID",
            "Author Keywords", "Index Keywords", "Source title", "Journal",
            "Publisher", "DOI", "ISSN", "Document Type", "Year", "Publication Date",
            "Date", "Cited by", "Citation Count", "Affiliations", "Volume",
            "Issue", "Page start", "Pages", "Language of Original Document",
            "Link", "URL"
        );
        return mappedFields.contains(fieldName);
    }

    @Override
    public String getSourceName() {
        return "Scopus";
    }
}