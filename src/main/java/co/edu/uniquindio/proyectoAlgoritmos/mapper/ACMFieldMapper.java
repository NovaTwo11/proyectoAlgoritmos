package co.edu.uniquindio.proyectoAlgoritmos.mapper;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Mapper específico para archivos CSV exportados de ACM Digital Library
 */
public class ACMFieldMapper implements FieldMapper {

    @Override
    public ScientificRecord mapToScientificRecord(Map<String, String> fields) {
        String id = getField(fields, "ACM ID", "DOI", "URL");
        String title = cleanText(getField(fields, "Title", "title"));
        String abstractText = cleanText(getField(fields, "Abstract", "abstract"));

        // Autores
        List<String> authors = new ArrayList<>();
        String authorsStr = getField(fields, "Authors", "Author", "authors");
        if (authorsStr != null && !authorsStr.isEmpty()) {
            authors = parseAuthors(authorsStr);
        }

        // Palabras clave
        List<String> keywords = new ArrayList<>();
        String keywordsStr = getField(fields, "Keywords", "Author Keywords", "Subject");
        if (keywordsStr != null && !keywordsStr.isEmpty()) {
            keywords = parseKeywords(keywordsStr);
        }

        // Información de publicación
        String journal = null;
        String conference = null;
        String publication = getField(fields, "Publication", "Source", "Venue");
        String proceedingsTitle = getField(fields, "Proceedings Title", "Conference");

        if (proceedingsTitle != null) {
            conference = proceedingsTitle;
        } else if (publication != null) {
            if (isJournal(publication)) {
                journal = publication;
            } else {
                conference = publication;
            }
        }

        LocalDate publicationDate = null;
        String dateStr = getField(fields, "Publication Date", "Date");
        if (dateStr != null && !dateStr.isEmpty()) {
            publicationDate = parseDate(dateStr);
        }

        Integer year = 0;
        String yearStr = getField(fields, "Year", "Publication Year");
        if (yearStr != null && !yearStr.isEmpty()) {
            try {
                year = Integer.parseInt(yearStr);
            } catch (NumberFormatException ignored) {}
        }

        int citations = 0;
        String citationsStr = getField(fields, "Citations", "Citation Count", "Times Cited");
        if (citationsStr != null && !citationsStr.isEmpty()) {
            try {
                citations = Integer.parseInt(citationsStr.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {}
        }

        return ScientificRecord.builder()
                .id(id)
                .title(title)
                .abstractText(abstractText)
                .authors(authors)
                .keywords(keywords)
                .journal(journal)
                .conference(conference)
                .publisher(getField(fields, "Publisher", "ACM"))
                .doi(getField(fields, "DOI", "doi"))
                .isbn(getField(fields, "ISBN", "isbn"))
                .issn(getField(fields, "ISSN", "issn"))
                .year(year)
                .publicationDate(publicationDate)
                .documentType(getField(fields, "Document Type", "Type", "Item Type"))
                .citationCount(citations)
                .volume(parseInteger(getField(fields, "Volume")))
                .issue(getField(fields, "Issue", "Number"))
                .pages(getField(fields, "Pages", "Page Range", "Start Page", "End Page"))
                .url(getField(fields, "URL", "Link"))
                .affiliations(getField(fields, "Affiliations", "Institution"))
                .source("ACM")
                .build();
    }

    private boolean isJournal(String publication) {
        if (publication == null) return false;
        String pubLower = publication.toLowerCase();
        return pubLower.contains("journal") ||
                pubLower.contains("transactions") ||
                pubLower.contains("magazine") ||
                pubLower.matches(".*\\b(acm|ieee)\\s+(trans|j\\.).*");
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
            String[] authorArray = authorsStr.split("[;,]|\sand\s");
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
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MMM dd, yyyy"),
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

    private boolean isMappedField(String fieldName) {
        Set<String> mappedFields = Set.of(
            "ACM ID", "DOI", "URL", "Title", "title", "Abstract", "abstract",
            "Authors", "Author", "authors", "Keywords", "Author Keywords", "Subject",
            "Publication", "Source", "Venue", "Proceedings Title", "Conference",
            "Publisher", "doi", "ISBN", "isbn", "ISSN", "issn", "Year", "Publication Year",
            "Publication Date", "Date", "Document Type", "Type", "Item Type",
            "Citations", "Citation Count", "Times Cited", "Volume", "Issue", "Number",
            "Pages", "Page Range", "Start Page", "End Page", "Link", "Affiliations", "Institution"
        );
        return mappedFields.contains(fieldName);
    }

    @Override
    public String getSourceName() {
        return "ACM";
    }
}