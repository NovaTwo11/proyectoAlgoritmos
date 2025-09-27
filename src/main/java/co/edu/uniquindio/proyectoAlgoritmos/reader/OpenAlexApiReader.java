package co.edu.uniquindio.proyectoAlgoritmos.reader;

import co.edu.uniquindio.proyectoAlgoritmos.model.DataSource;
import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lector de datos desde la API de OpenAlex con búsqueda semántica por conceptos
 * Ejecuta múltiples queries (una por concept ID) y unifica resultados
 * API Documentation: https://docs.openalex.org/
 */
@Component
@Slf4j
public class OpenAlexApiReader implements ApiDatasetReader {

    private static final String OPENALEX_API_BASE = "https://api.openalex.org/works";

    @Value("${api.openalex.max-results:1000}")
    private int defaultMaxResults;

    @Value("${api.openalex.mailto:}")
    private String mailto;

    @Value("${api.openalex.from-date:2018-01-01}")
    private String fromDate;

    @Value("${api.openalex.to-date:2025-12-31}")
    private String toDate;

    @Value("${api.openalex.fallback-query:artificial intelligence}")
    private String fallbackQuery;

    // Leemos una cadena CSV para evitar problemas de binding con listas YAML
    @Value("${api.openalex.concept-ids-csv:}")
    private String conceptIdsCsv;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAlexApiReader() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<ScientificRecord> downloadFromApi(String searchQuery, int maxResults) throws IOException {
        int effectiveMaxResults = (maxResults > 0) ? maxResults : defaultMaxResults;

        log.info("Descargando desde OpenAlex con query: '{}', max: {}", searchQuery, effectiveMaxResults);

        List<ScientificRecord> allRecords = new ArrayList<>();

        if (hasValidConceptIds()) {
            // Modo semántico: ejecutar una query por cada concept ID
            List<String> conceptIds = getConceptIds();
            log.info("Ejecutando {} queries semánticas separadas para concept IDs: {}", conceptIds.size(), conceptIds);

            // Dividir maxResults entre las queries
            int maxPerConcept = effectiveMaxResults / conceptIds.size();
            if (maxPerConcept < 100) maxPerConcept = 100; // Mínimo 100 por concepto

            for (String conceptId : conceptIds) {
                try {
                    log.info("Descargando para concept ID: {}", conceptId);
                    List<ScientificRecord> conceptRecords = downloadForSingleConcept(conceptId, maxPerConcept);
                    allRecords.addAll(conceptRecords);
                    log.info("Obtenidos {} registros para concept {}", conceptRecords.size(), conceptId);

                    // Rate limiting entre concepts
                    Thread.sleep(200);
                } catch (Exception e) {
                    log.error("Error descargando concept {}: {}", conceptId, e.getMessage());
                }
            }

            // Eliminar duplicados por ID de OpenAlex
            allRecords = removeDuplicates(allRecords);
            log.info("Después de eliminar duplicados: {} registros únicos", allRecords.size());

        } else {
            // Modo fallback: búsqueda por texto
            log.info("No hay concept IDs válidos, usando búsqueda por texto: {}", fallbackQuery);
            allRecords = downloadWithTextSearch(searchQuery != null ? searchQuery : fallbackQuery, effectiveMaxResults);
        }

        log.info("Total descargados desde OpenAlex: {} registros", allRecords.size());
        return allRecords;
    }

    /**
     * Descarga registros para un solo concept ID
     */
    private List<ScientificRecord> downloadForSingleConcept(String conceptId, int maxResults) throws IOException {
        List<ScientificRecord> records = new ArrayList<>();
        int resultsPerPage = Math.min(maxResults, 200); // OpenAlex max 200 per request
        int currentResults = 0;
        int page = 1;

        while (currentResults < maxResults) {
            try {
                String url = buildConceptUrl(conceptId, page, resultsPerPage);
                log.debug("OpenAlex URL para {}: {}", conceptId, url);

                String response = restTemplate.getForObject(url, String.class);

                if (response == null || response.trim().isEmpty()) {
                    log.warn("Respuesta vacía de OpenAlex API para concept {}", conceptId);
                    break;
                }

                List<ScientificRecord> pageRecords = parseResponse(response);

                if (pageRecords.isEmpty()) {
                    log.debug("No hay más resultados para concept {}", conceptId);
                    break;
                }

                records.addAll(pageRecords);
                currentResults += pageRecords.size();
                page++;

                // Rate limiting
                Thread.sleep(120);

            } catch (Exception e) {
                log.error("Error en página {} para concept {}: {}", page, conceptId, e.getMessage());
                break;
            }
        }

        return records;
    }

    /**
     * Descarga con búsqueda por texto (fallback)
     */
    private List<ScientificRecord> downloadWithTextSearch(String query, int maxResults) throws IOException {
        List<ScientificRecord> records = new ArrayList<>();
        int resultsPerPage = Math.min(maxResults, 200);
        int currentResults = 0;
        int page = 1;

        while (currentResults < maxResults) {
            try {
                String url = buildTextSearchUrl(query, page, resultsPerPage);
                log.debug("OpenAlex URL (texto): {}", url);

                String response = restTemplate.getForObject(url, String.class);

                if (response == null || response.trim().isEmpty()) {
                    log.warn("Respuesta vacía de OpenAlex API");
                    break;
                }

                List<ScientificRecord> pageRecords = parseResponse(response);

                if (pageRecords.isEmpty()) {
                    log.info("No hay más resultados en búsqueda por texto");
                    break;
                }

                records.addAll(pageRecords);
                currentResults += pageRecords.size();
                page++;

                Thread.sleep(120);

            } catch (Exception e) {
                log.error("Error en búsqueda por texto: {}", e.getMessage());
                break;
            }
        }

        return records;
    }

    /**
     * Construye URL para un concept ID específico
     */
    private String buildConceptUrl(String conceptId, int page, int perPage) throws IOException {
        StringBuilder url = new StringBuilder(OPENALEX_API_BASE);

        // Filtros para este concept específico
        List<String> filters = new ArrayList<>();
        filters.add("concepts.id:" + conceptId);
        filters.add("from_publication_date:" + fromDate);
        filters.add("to_publication_date:" + toDate);

        url.append("?filter=").append(String.join(",", filters));
        url.append("&per_page=").append(perPage);
        url.append("&page=").append(page);

        if (mailto != null && !mailto.trim().isEmpty()) {
            url.append("&mailto=").append(URLEncoder.encode(mailto, StandardCharsets.UTF_8));
        }

        return url.toString();
    }

    /**
     * Construye URL para búsqueda por texto
     */
    private String buildTextSearchUrl(String query, int page, int perPage) throws IOException {
        StringBuilder url = new StringBuilder(OPENALEX_API_BASE);

        url.append("?search=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));

        // Filtros básicos (sin conceptos)
        List<String> filters = new ArrayList<>();
        filters.add("from_publication_date:" + fromDate);
        filters.add("to_publication_date:" + toDate);

        url.append("&filter=").append(String.join(",", filters));
        url.append("&per_page=").append(perPage);
        url.append("&page=").append(page);

        if (mailto != null && !mailto.trim().isEmpty()) {
            url.append("&mailto=").append(URLEncoder.encode(mailto, StandardCharsets.UTF_8));
        }

        return url.toString();
    }

    /**
     * Elimina duplicados basándose en el ID de OpenAlex
     */
    private List<ScientificRecord> removeDuplicates(List<ScientificRecord> records) {
        Map<String, ScientificRecord> uniqueRecords = new LinkedHashMap<>();

        for (ScientificRecord record : records) {
            String key = record.getId();
            if (key != null && !key.trim().isEmpty()) {
                // Si ya existe, mantener el que tenga más información
                if (!uniqueRecords.containsKey(key) ||
                        (record.getAbstractText() != null && uniqueRecords.get(key).getAbstractText() == null)) {
                    uniqueRecords.put(key, record);
                }
            } else {
                // Si no tiene ID, usar título + primer autor como clave alternativa
                String altKey = (record.getTitle() + "_" +
                        (record.getAuthors().isEmpty() ? "unknown" : record.getAuthors().get(0)))
                        .toLowerCase().replaceAll("\\s+", "_");
                if (!uniqueRecords.containsKey(altKey)) {
                    uniqueRecords.put(altKey, record);
                }
            }
        }

        return new ArrayList<>(uniqueRecords.values());
    }

    private boolean hasValidConceptIds() {
        if (conceptIdsCsv == null || conceptIdsCsv.trim().isEmpty()) return false;
        String cleaned = conceptIdsCsv.replace("[", "").replace("]", "");
        List<String> ids = Arrays.stream(cleaned.split("\\s*,\\s*"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return !ids.isEmpty();
    }

    private List<String> getConceptIds() {
        if (conceptIdsCsv == null) return List.of();
        String cleaned = conceptIdsCsv.replace("[", "").replace("]", "");
        return Arrays.stream(cleaned.split("\\s*,\\s*"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Filtro por tipos de documento

    private String buildTypeFilter() {
        String[] types = includeTypes.split("\\s*,\\s*");
        return "type:" + String.join("|", types);
    }
     */
    private List<ScientificRecord> parseResponse(String jsonResponse) throws IOException {
        List<ScientificRecord> records = new ArrayList<>();

        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode results = root.path("results");

        if (results.isArray()) {
            for (JsonNode work : results) {
                ScientificRecord record = mapToScientificRecord(work);
                if (record != null) {
                    records.add(record);
                }
            }
        }

        return records;
    }

    private ScientificRecord mapToScientificRecord(JsonNode work) {
        try {
            String title = work.path("display_name").asText();
            if (title == null || title.trim().isEmpty()) {
                return null; // Skip records without title
            }

            // Authors
            List<String> authors = new ArrayList<>();
            JsonNode authorships = work.path("authorships");
            if (authorships.isArray()) {
                for (JsonNode authorship : authorships) {
                    String authorName = authorship.path("author").path("display_name").asText();
                    if (!authorName.isEmpty()) {
                        authors.add(authorName);
                    }
                }
            }

            // Year
            Integer publicationYear = work.path("publication_year").asInt(0);

            // Venue
            String journal = null;
            String conference = null;
            JsonNode primaryLocation = work.path("primary_location");
            if (!primaryLocation.isMissingNode()) {
                JsonNode source = primaryLocation.path("source");
                if (!source.isMissingNode()) {
                    String venueName = source.path("display_name").asText();
                    if (!venueName.isEmpty()) {
                        if (isConference(venueName)) {
                            conference = venueName;
                        } else {
                            journal = venueName;
                        }
                    }
                }
            }

            // DOI
            String doi = work.path("doi").asText();
            if (doi != null && doi.startsWith("https://doi.org/")) {
                doi = doi.substring("https://doi.org/".length());
            }

            // URL
            String url = null;
            JsonNode openAccess = work.path("open_access");
            if (!openAccess.isMissingNode()) {
                url = openAccess.path("oa_url").asText();
            }
            if ((url == null || url.isEmpty()) && !primaryLocation.isMissingNode()) {
                url = primaryLocation.path("landing_page_url").asText();
            }

            // Abstract (OpenAlex da índice invertido)
            String abstractText = null;
            JsonNode abstractInverted = work.path("abstract_inverted_index");
            if (!abstractInverted.isMissingNode() && abstractInverted.size() > 0) {
                abstractText = "Abstract available";
            }

            return ScientificRecord.builder()
                    .id(work.path("id").asText())
                    .title(title)
                    .authors(authors)
                    .abstractText(abstractText)
                    .journal(journal)
                    .conference(conference)
                    .year(publicationYear > 0 ? publicationYear : 0)
                    .doi(doi)
                    .url(url)
                    .documentType(work.path("type").asText())
                    .citationCount(work.path("cited_by_count").asInt(0))
                    .source(DataSource.OPENALEX.toString())
                    .build();

        } catch (Exception e) {
            log.warn("Error mapeando registro OpenAlex: {}", e.getMessage());
            return null;
        }
    }

    private boolean isConference(String venue) {
        if (venue == null) return false;
        String v = venue.toLowerCase();
        return v.contains("conference") ||
                v.contains("symposium") ||
                v.contains("workshop") ||
                v.contains("proceedings") ||
                v.contains("international conference") ||
                v.matches(".*\\b(icml|nips|neurips|iclr|aaai|ijcai|acl|emnlp|cvpr|iccv|eccv)\\b.*");
    }

    @Override
    public String getSourceName() {
        return DataSource.OPENALEX.toString();
    }

    @Override
    public boolean isApiAvailable() {
        try {
            String testUrl = OPENALEX_API_BASE + "?search=test&per_page=1";
            String response = restTemplate.getForObject(testUrl, String.class);
            return response != null && !response.trim().isEmpty();
        } catch (Exception e) {
            log.warn("OpenAlex API no disponible: {}", e.getMessage());
            return false;
        }
    }
}