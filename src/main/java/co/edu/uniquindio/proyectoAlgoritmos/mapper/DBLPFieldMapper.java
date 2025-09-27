package co.edu.uniquindio.proyectoAlgoritmos.mapper;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Mapper específico para archivos CSV exportados de DBLP
 */
public class DBLPFieldMapper implements FieldMapper {

    @Override
    public ScientificRecord mapToScientificRecord(Map<String, String> fields) {
        String id = getField(fields, "key", "dblp_key");
        String title = cleanText(getField(fields, "title"));
        String abstractText = cleanText(getField(fields, "abstract", "note"));
        List<String> authors = extractAuthors(fields);

        // venue (journal o conference)
        String journal = null, conference = null;
        String venue = getField(fields, "venue", "booktitle", "journal");
        if (venue != null) {
            if (isConference(venue)) {
                conference = venue;
            } else {
                journal = venue;
            }
        }

        Integer year = 0;
        String yearStr = getField(fields, "year");
        if (yearStr != null && !yearStr.isEmpty()) {
            try {
                year = Integer.parseInt(yearStr);
            } catch (NumberFormatException ignored) {}
        }

        String type = getField(fields, "type", "document_type");
        if (type == null) {
            type = (getField(fields, "booktitle") != null) ? "Conference Paper" :
                    (getField(fields, "journal") != null) ? "Article" : null;
        }

        return ScientificRecord.builder()
                .id(id)
                .title(title)
                .abstractText(abstractText)
                .authors(authors)
                .journal(journal)
                .conference(conference)
                .publisher(getField(fields, "publisher"))
                .doi(getField(fields, "doi"))
                .isbn(getField(fields, "isbn"))
                .issn(getField(fields, "issn"))
                .year(year)
                .documentType(type)
                .volume(parseInteger(getField(fields, "volume")))
                .issue(getField(fields, "number", "issue"))
                .pages(getField(fields, "pages"))
                .url(getField(fields, "url", "ee"))
                .source("DBLP")
                .build();
    }

    private List<String> extractAuthors(Map<String, String> fields) {
        List<String> authors = new ArrayList<>();

        // DBLP puede tener author, author1, author2, etc.
        String authorField = getField(fields, "author");
        if (authorField != null) {
            authors.addAll(parseAuthors(authorField));
        }

        // Buscar campos author numerados
        for (int i = 1; i <= 20; i++) {
            String authorN = getField(fields, "author" + i);
            if (authorN != null && !authorN.isEmpty()) {
                authors.add(authorN.trim());
            }
        }

        return authors;
    }

    private List<String> parseAuthors(String authorsStr) {
        List<String> authors = new ArrayList<>();
        if (authorsStr != null && !authorsStr.isEmpty()) {
            String[] authorArray = authorsStr.split(" and |;|,");
            for (String author : authorArray) {
                String cleanAuthor = author.trim();
                if (!cleanAuthor.isEmpty()) {
                    authors.add(cleanAuthor);
                }
            }
        }
        return authors;
    }

    private boolean isConference(String venue) {
        if (venue == null) return false;
        String venueLower = venue.toLowerCase();
        return venueLower.contains("conference") || 
               venueLower.contains("symposium") || 
               venueLower.contains("workshop") ||
               venueLower.contains("proceedings") ||
               venueLower.matches(".*\b(icml|nips|iclr|aaai|ijcai|acl|emnlp|cvpr|iccv|eccv)\b.*");
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

    private int parseInteger(String str) {
        if (str == null || str.isEmpty()) return 0;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isMappedField(String fieldName) {
        Set<String> mappedFields = Set.of(
                "key", "dblp_key", "title", "abstract", "note", "author", "venue",
                "booktitle", "journal", "publisher", "doi", "isbn", "issn", "year",
                "type", "document_type", "volume", "number", "issue", "pages", "url", "ee"
        );

        // También incluir campos author numerados
        if (fieldName.matches("author\\d+")) {
            return true;
        }

        return mappedFields.contains(fieldName);
    }

    @Override
    public String getSourceName() {
        return "DBLP";
    }
}