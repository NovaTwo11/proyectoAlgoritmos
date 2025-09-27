package co.edu.uniquindio.proyectoAlgoritmos.reader.api;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class DblpApiReader implements ApiDatasetReader {

    private static final String BASE = "https://dblp.org/search/publ/api?q=%s&h=1000&format=json";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<ScientificRecord> searchRecords(String searchQuery) throws Exception {
        String url = String.format(BASE, URLEncoder.encode(searchQuery, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Accept","application/json").build();
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("DBLP API error: " + resp.statusCode());
        }

        List<ScientificRecord> out = new ArrayList<>();
        JsonNode hits = mapper.readTree(resp.body()).path("result").path("hits").path("hit");
        if (!hits.isArray()) return out;

        for (JsonNode hit : hits) {
            JsonNode info = hit.path("info");

            // Autores
            List<String> authors = new ArrayList<>();
            JsonNode authorsNode = info.path("authors").path("author");
            if (authorsNode.isArray()) {
                for (JsonNode a : authorsNode) authors.add(a.path("text").asText(""));
            } else if (!authorsNode.isMissingNode()) {
                authors.add(authorsNode.path("text").asText(""));
            }

            String title = info.path("title").asText(null);
            String venue = info.path("venue").asText(null);
            String type = info.path("type").asText(""); // e.g., "Conference and Workshop Papers", "Journal Articles"
            Integer year = info.hasNonNull("year") ? info.get("year").asInt(0) : 0;
            String urlPaper = info.hasNonNull("ee") ? info.get("ee").asText() : info.path("url").asText(null);
            String doi = info.path("doi").asText(null);

            // Mapear journal vs conference
            String journal = null, conference = null;
            if (type.toLowerCase().contains("conference") || type.toLowerCase().contains("workshop")) {
                conference = venue;
            } else {
                journal = venue;
            }

            ScientificRecord rec = ScientificRecord.builder()
                    .id(info.path("key").asText(null))
                    .title(title)
                    .authors(authors)
                    .journal(journal)
                    .conference(conference)
                    .year(year != null ? year : 0)
                    .doi(doi)
                    .url(urlPaper)
                    .source("DBLP")
                    .build();

            out.add(rec);
        }
        return out;
    }

    @Override
    public String getSourceName() {
        return "DBLP";
    }
}