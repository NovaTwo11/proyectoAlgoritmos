package co.edu.uniquindio.proyectoAlgoritmos.service.bibtex;

import co.edu.uniquindio.proyectoAlgoritmos.model.Article;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAlexCountryEnrichmentService {

    private final BibTeXParserService bibtex;

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true);

    public void enrichFirstAuthorCountryAsync(String inputBibPath, String outputBibPath) {
        // Ejecutar en paralelo sin bloquear el flujo principal
        CompletableFuture.runAsync(() -> {
            try {
                enrichFirstAuthorCountry(inputBibPath, outputBibPath);
            } catch (Exception e) {
                log.error("Fallo enriqueciendo país del primer autor: {}", e.getMessage(), e);
            }
        });
    }

    public void enrichFirstAuthorCountry(String inputBibPath, String outputBibPath) throws Exception {
        Path in = Paths.get(inputBibPath);
        if (!Files.exists(in)) {
            log.warn("No existe el archivo unificado: {}", in.toAbsolutePath());
            return;
        }
        var articles = bibtex.parseBib(in.toFile());
        log.info("Enriqueciendo país del primer autor para {} artículos", articles.size());

        RestTemplate http = new RestTemplate();
        List<String> lines = new ArrayList<>();
        for (Article a : articles) {
            String country = "unknown"; // valor por defecto
            try {
                String doi = normalizeDoi(a.getDoi());
                if (doi != null) {
                    String url = "https://api.openalex.org/works/https://doi.org/" + encodePath(doi);
                    country = fetchCountryFromOpenAlex(http, url);
                }
            } catch (Exception ex) {
                log.debug("Fallo consultando OpenAlex para '{}': {}. Intentando fallback", a.getDoi(), ex.getMessage());
                try {
                    country = countryFromFallbackSample();
                } catch (Exception ignore) {
                    // mantener unknown
                }
            }
            lines.add(formatAsBibWithCountry(a, country));
        }
        // Escribir salida
        Path out = Paths.get(outputBibPath);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out.toFile()), StandardCharsets.UTF_8))) {
            for (String s : lines) { w.write(s); w.newLine(); }
        }
        log.info("Archivo enriquecido generado en {}", out.toAbsolutePath());
    }

    private String fetchCountryFromOpenAlex(RestTemplate http, String url) throws Exception {
        ResponseEntity<String> res = http.getForEntity(url, String.class);
        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) throw new IllegalStateException("HTTP " + res.getStatusCode());
        JsonNode root = mapper.readTree(res.getBody());
        return extractFirstAuthorCountry(root);
    }

    private String countryFromFallbackSample() throws Exception {
        // Cargar JSON de ejemplo desde resources/Instrucciones.txt
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("Instrucciones.txt")) {
            if (is == null) throw new IllegalStateException("Instrucciones.txt no encontrado en classpath");
            JsonNode root = mapper.readTree(is);
            return extractFirstAuthorCountry(root);
        }
    }

    private String extractFirstAuthorCountry(JsonNode root) {
        if (root == null) return "unknown";
        JsonNode authorships = root.get("authorships");
        if (authorships == null || !authorships.isArray() || authorships.isEmpty()) return "unknown";

        // Buscar explícitamente el autor con author_position == "first"
        JsonNode first = null;
        for (JsonNode n : authorships) {
            if (n.hasNonNull("author_position") && Objects.equals(n.get("author_position").asText(), "first")) { first = n; break; }
        }
        if (first == null) first = authorships.get(0);

        // 1) countries[0]
        if (first.has("countries") && first.get("countries").isArray() && !first.get("countries").isEmpty()) {
            String c = first.get("countries").get(0).asText(null);
            if (c != null && !c.isBlank()) return c;
        }
        // 2) institutions[0].country_code
        if (first.has("institutions") && first.get("institutions").isArray() && !first.get("institutions").isEmpty()) {
            JsonNode inst = first.get("institutions").get(0);
            if (inst.hasNonNull("country_code")) {
                String c = inst.get("country_code").asText(null);
                if (c != null && !c.isBlank()) return c;
            }
        }
        return "unknown";
    }

    private static String normalizeDoi(String doi) {
        if (doi == null) return null;
        String d = doi.trim();
        if (d.isBlank()) return null;
        // remover prefijos comunes
        d = d.replaceAll("(?i)^https?://(dx\\.)?doi\\.org/", "");
        return d;
    }

    private static String encodePath(String s) {
        // Codificar sólo lo necesario para path; DOIs pueden contener caracteres especiales
        return s.replace(" ", "%20");
    }

    private String formatAsBibWithCountry(Article a, String countryCode) {
        String type = a.getEntryType() != null ? a.getEntryType() : "article";
        String key = a.getOriginalBibtexKey() != null ? a.getOriginalBibtexKey() : (a.getId() != null ? a.getId() : "key");
        StringBuilder sb = new StringBuilder();
        sb.append("@").append(type).append("{").append(key).append(", ");
        append(sb, "title", a.getTitle());
        if (a.getJournal() != null) append(sb, "journal", a.getJournal());
        if (a.getBooktitle() != null) append(sb, "booktitle", a.getBooktitle());
        append(sb, "volume", a.getVolume());
        append(sb, "pages", a.getPages());
        append(sb, "year", a.getYear() != null ? a.getYear().toString() : null);
        append(sb, "issn", a.getIssn());
        append(sb, "isbn", a.getIsbn());
        append(sb, "doi", a.getDoi());
        append(sb, "url", a.getUrl());
        List<String> authors = a.getAuthors() != null ? a.getAuthors() : new ArrayList<>();
        if (!authors.isEmpty()) sb.append("author = {").append(escapeBibValue(String.join(" and ", authors))).append("}, ");
        List<String> keywords = a.getKeywords() != null ? a.getKeywords() : new ArrayList<>();
        if (!keywords.isEmpty()) sb.append("keywords = {").append(escapeBibValue(String.join(", ", keywords))).append("}, ");
        append(sb, "abstract", a.getAbstractText());
        // Campo adicional
        append(sb, "countryFirstAuthor", countryCode != null ? countryCode.toUpperCase(Locale.ROOT) : "unknown");
        // quitar coma final
        int len = sb.length();
        if (len >= 2 && sb.substring(len-2).equals(", ")) { sb.setLength(len-2); sb.append(" "); }
        sb.append("}");
        return sb.toString();
    }

    private static void append(StringBuilder sb, String k, String v) {
        String vv = escapeBibValue(v);
        if (vv != null && !vv.isBlank()) sb.append(k).append(" = {").append(vv).append("}, ");
    }

    private static String escapeBibValue(String v) {
        if (v == null) return null;
        String s = v;
        s = s.replaceAll("(?<!\\\\)%", "\\\\%");
        s = s.replaceAll("(?<!\\\\)&", "\\\\&");
        return s;
    }
}
