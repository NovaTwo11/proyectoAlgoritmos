package co.edu.uniquindio.proyectoAlgoritmos.reader.api;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Component
public class ScopusApiReader implements ApiDatasetReader {

    @Value("${scopus.api.key:}")
    private String apiKey;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // Usamos consulta por título-abstract-keywords (más precisa para la temática)
    private static final String BASE = "https://api.elsevier.com/content/search/scopus?query=TITLE-ABS-KEY(%s)&count=50&start=%d";

    @Override
    public List<ScientificRecord> searchRecords(String searchQuery) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Scopus API Key not configured (scopus.api.key).");
        }

        // Paginación simple: primeras 200 entradas aprox
        int start = 0;
        int maxPages = 4;
        List<ScientificRecord> results = new ArrayList<>();

        for (int page = 0; page < maxPages; page++) {
            String q = searchQuery.replace("\"", "\\\"");
            String url = String.format(BASE, "\"" + q + "\"", start);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-ELS-APIKey", apiKey)
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) break;

            JsonNode root = mapper.readTree(resp.body());
            JsonNode entries = root.path("search-results").path("entry");
            if (!entries.isArray() || entries.isEmpty()) break;

            for (JsonNode e : entries) {
                List<String> authors = new ArrayList<>();
                if (e.has("author") && e.get("author").isArray()) {
                    for (JsonNode a : e.get("author")) {
                        String name = a.path("authname").asText(null);
                        if (name != null && !name.isBlank()) authors.add(name);
                    }
                }

                String title = e.path("dc:title").asText(null);
                String doi = e.path("prism:doi").asText(null);
                String journal = e.path("prism:publicationName").asText(null);
                String urlPaper = e.path("prism:url").asText(null);

                int year = 0;
                if (e.hasNonNull("prism:coverDate")) {
                    String cover = e.get("prism:coverDate").asText("");
                    if (cover.length() >= 4) {
                        try { year = Integer.parseInt(cover.substring(0,4)); } catch (Exception ignored) {}
                    }
                }

                ScientificRecord rec = ScientificRecord.builder()
                        .id(e.path("dc:identifier").asText(null)) // e.g., "SCOPUS_ID:..."
                        .title(title)
                        .authors(authors)
                        .journal(journal)
                        .year(year)
                        .doi(doi)
                        .url(urlPaper)
                        .source("SCOPUS")
                        .build();
                results.add(rec);
            }

            start += 50;
        }
        return results;
    }

    @Override
    public String getSourceName() {
        return "SCOPUS";
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}