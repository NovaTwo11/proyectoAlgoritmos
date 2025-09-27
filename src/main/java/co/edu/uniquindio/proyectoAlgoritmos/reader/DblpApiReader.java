package co.edu.uniquindio.proyectoAlgoritmos.reader;

import co.edu.uniquindio.proyectoAlgoritmos.model.DataSource;
import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector de datos desde la API de DBLP
 * API Documentation: https://dblp.org/faq/How+to+use+the+dblp+search+API.html
 */
@Component
@Slf4j
public class DblpApiReader implements ApiDatasetReader {

    private static final String DBLP_API_BASE = "https://dblp.org/search/publ/api";

    @Value("${api.dblp.max-results:500}")
    private int defaultMaxResults;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DblpApiReader() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<ScientificRecord> downloadFromApi(String searchQuery, int maxResults) throws IOException {
        int effectiveMaxResults = (maxResults > 0) ? maxResults : defaultMaxResults;
        log.info("Descargando desde DBLP con query: '{}', max: {}", searchQuery, effectiveMaxResults);

        List<ScientificRecord> allRecords = new ArrayList<>();
        int resultsPerPage = Math.min(maxResults, 1000); // DBLP max 1000 per request
        int currentResults = 0;
        int startIndex = 0;

        while (currentResults < maxResults) {
            try {
                String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
                String url = String.format("%s?q=%s&format=json&h=%d&f=%d",
                        DBLP_API_BASE, encodedQuery, resultsPerPage, startIndex);

                log.debug("Llamando DBLP API: {}", url);
                String response = restTemplate.getForObject(url, String.class);

                if (response == null || response.trim().isEmpty()) {
                    log.warn("Respuesta vacía de DBLP API");
                    break;
                }

                List<ScientificRecord> pageRecords = parseResponse(response);

                if (pageRecords.isEmpty()) {
                    log.info("No hay más resultados en DBLP");
                    break;
                }

                allRecords.addAll(pageRecords);
                currentResults += pageRecords.size();
                startIndex += resultsPerPage;

                // Evitar hacer demasiadas requests seguidas
                Thread.sleep(100);

            } catch (Exception e) {
                log.error("Error descargando desde DBLP: {}", e.getMessage());
                break;
            }
        }

        log.info("Descargados {} registros desde DBLP", allRecords.size());
        return allRecords;
    }

    private List<ScientificRecord> parseResponse(String jsonResponse) throws IOException {
        List<ScientificRecord> records = new ArrayList<>();

        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode result = root.path("result");
        JsonNode hits = result.path("hits").path("hit");

        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                JsonNode info = hit.path("info");
                ScientificRecord record = mapToScientificRecord(info);
                if (record != null) {
                    records.add(record);
                }
            }
        }

        return records;
    }

    private ScientificRecord mapToScientificRecord(JsonNode info) {
        try {
            String title = getTextValue(info, "title");
            if (title == null || title.trim().isEmpty()) {
                return null; // Skip records without title
            }

            // Extract authors
            List<String> authors = new ArrayList<>();
            JsonNode authorsNode = info.path("authors").path("author");
            if (authorsNode.isArray()) {
                for (JsonNode author : authorsNode) {
                    String authorName = author.path("text").asText();
                    if (!authorName.isEmpty()) {
                        authors.add(authorName);
                    }
                }
            } else if (authorsNode.isTextual()) {
                authors.add(authorsNode.asText());
            }

            // Extract year
            Integer year = null;
            String yearStr = getTextValue(info, "year");
            if (yearStr != null) {
                try {
                    year = Integer.parseInt(yearStr);
                } catch (NumberFormatException ignored) {}
            }

            // Determine venue type
            String venue = getTextValue(info, "venue");
            String journal = null;
            String conference = null;

            if (venue != null) {
                if (isConference(venue)) {
                    conference = venue;
                } else {
                    journal = venue;
                }
            }

            return ScientificRecord.builder()
                    .id(getTextValue(info, "key"))
                    .title(title)
                    .authors(authors)
                    .journal(journal)
                    .conference(conference)
                    .year(year != null ? year : 0)
                    .doi(getTextValue(info, "doi"))
                    .url(getTextValue(info, "ee"))
                    .documentType(getTextValue(info, "type"))
                    .source(DataSource.DBLP.toString())
                    .build();

        } catch (Exception e) {
            log.warn("Error mapeando registro DBLP: {}", e.getMessage());
            return null;
        }
    }

    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode()) {
            return null;
        }

        if (field.isTextual()) {
            return field.asText();
        } else if (field.isObject() && field.has("text")) {
            return field.path("text").asText();
        }

        return null;
    }

    private boolean isConference(String venue) {
        if (venue == null) return false;
        String venueLower = venue.toLowerCase();
        return venueLower.contains("conference") ||
                venueLower.contains("symposium") ||
                venueLower.contains("workshop") ||
                venueLower.contains("proceedings") ||
                venueLower.matches(".*\\b(icml|nips|iclr|aaai|ijcai|acl|emnlp|cvpr|iccv|eccv)\\b.*");
    }

    @Override
    public String getSourceName() {
        return DataSource.DBLP.toString();
    }

    @Override
    public boolean isApiAvailable() {
        try {
            String testUrl = DBLP_API_BASE + "?q=test&format=json&h=1";
            String response = restTemplate.getForObject(testUrl, String.class);
            return response != null && !response.trim().isEmpty();
        } catch (Exception e) {
            log.warn("DBLP API no disponible: {}", e.getMessage());
            return false;
        }
    }
}