package co.edu.uniquindio.proyectoAlgoritmos.service.articles;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import org.jbibtex.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArticlesService {

    private static final Pattern EN_DASH_PAGES = Pattern.compile("pages\\s*=\\s*\\{([0-9]+)[\\u2013–]([0-9]+)\\}");
    private static final Pattern PAGES_RANGE = Pattern.compile(".*?([0-9]+)\\s*(?:--|-|\\u2013|–)\\s*([0-9]+).*?");
    private static final String BIB_PATH = "src/main/resources/data/output/resultados_unificados.bib";

    public ResponseEntity<List<ArticleDTO>> getArticles() {
        try {
            String raw = Files.readString(Path.of(BIB_PATH), StandardCharsets.UTF_8);
            String sanitized = sanitizeBibtex(raw); // evita los %/& en abstracts y otros detallitos

            BibTeXDatabase db = parseBibtex(sanitized);
            List<ArticleDTO> articles = mapToDto(db);

            return ResponseEntity.ok(articles);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        } catch (ParseException e) {
            // si falla el parser, devuelve 422 para distinguir error de formato
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(List.of());
        }
    }

    // --- Helpers ---

    private static BibTeXDatabase parseBibtex(String content) throws ParseException {
        try (Reader reader = new StringReader(content)) {
            BibTeXParser parser = new BibTeXParser();
            return parser.parse(reader);
        } catch (IOException io) {
            // no debería ocurrir con StringReader, re-lanzar como ParseException
            throw new ParseException(io.getMessage());
        }
    }

    private static List<ArticleDTO> mapToDto(BibTeXDatabase db) {
        List<ArticleDTO> list = new ArrayList<>();
        for (Map.Entry<Key, BibTeXEntry> entry : db.getEntries().entrySet()) {
            BibTeXEntry be = entry.getValue();

            ArticleDTO dto = new ArticleDTO();
            dto.setId(resolveId(be)); // doi | entry key | uuid
            dto.setTitle(getString(be, "title"));
            dto.setAuthors(splitAuthors(getString(be, "author")));
            dto.setYear(parseYear(getString(be, "year")));
            // Nuevos campos
            dto.setJournal(getString(be, "journal"));
            dto.setBooktitle(getString(be, "booktitle"));

            // Páginas: usar numpages si está; si no, calcular desde rango (start-end/en-dash)
            String pagesField = getString(be, "pages");
            String numpages = getString(be, "numpages");
            String pagesOut = null;
            if (numpages != null && !numpages.isBlank()) {
                pagesOut = onlyDigits(numpages);
            } else if (pagesField != null && !pagesField.isBlank()) {
                pagesOut = computePageCountFromRange(pagesField);
                if (pagesOut == null) {
                    // si no pudimos computar, conservar el texto original (sin llaves)
                    pagesOut = pagesField;
                }
            }
            dto.setPages(pagesOut);

            String kwRaw = getStringAny(be, "keywords", "keyword", "Keywords", "KEYWORDS", "KeyWords", "Keywords-Plus", "KeyWords-Plus", "keywordsplus");
            dto.setKeywords(splitKeywords(kwRaw));
            // algunos exports usan "abstract" o "abstractNote"
            String abs = getString(be, "abstract");
            if (abs == null || abs.isBlank()) abs = getString(be, "abstractnote");
            dto.setAbstractText(abs);

            list.add(dto);
        }
        return list;
    }

    private static String resolveId(BibTeXEntry be) {
        String doi = getString(be, "doi");
        if (doi != null && !doi.isBlank()) return doi;
        if (be.getKey() != null) return be.getKey().toString();
        return UUID.randomUUID().toString();
    }

    private static String getString(BibTeXEntry be, String fieldName) {
        Value v = be.getField(new Key(fieldName));
        if (v == null) return null;
        // toUserString() ya quita llaves externas; aún puede quedar latex (\% etc.)
        return v.toUserString().trim();
    }

    private static String getStringAny(BibTeXEntry be, String... names) {
        for (String n : names) {
            String s = getString(be, n);
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }

    private static Integer parseYear(String yearStr) {
        if (yearStr == null) return null;
        try {
            return Integer.parseInt(yearStr.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> splitAuthors(String authors) {
        if (authors == null || authors.isBlank()) return List.of();
        // BibTeX separa autores por " and "
        return Arrays.stream(authors.split("\\s+and\\s+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static List<String> splitKeywords(String kw) {
        if (kw == null || kw.isBlank()) return List.of();
        // separar por coma, punto y coma o salto de línea
        return Arrays.stream(kw.split("[,;\\n]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static String onlyDigits(String s) {
        if (s == null) return null;
        String d = s.replaceAll("[^0-9]", "");
        return d.isBlank() ? null : d;
    }

    // Calcula el número de páginas a partir de un rango como "1159-1183", "1159--1183" o con en-dash.
    // Se asume inclusivo (end - start + 1). Si no se detecta el rango, devuelve null.
    private static String computePageCountFromRange(String pages) {
        Matcher m = PAGES_RANGE.matcher(pages);
        if (m.matches()) {
            try {
                int start = Integer.parseInt(m.group(1));
                int end = Integer.parseInt(m.group(2));
                if (end >= start) {
                    int count = end - start + 1; // inclusivo
                    return String.valueOf(count);
                }
            } catch (Exception ignore) { }
        }
        return null;
    }

    /**
     * Sanitiza problemas frecuentes de ACM/IEEE:
     *  - '%' dentro de abstracts => escapa como '\%'
     *  - '&' => escapa como '\&'
     *  - en-dash en pages => convierte a '--'
     *  - keywords partidas fuera de llaves => re-unir (heurístico)
     *  - asegura coma antes del siguiente campo
     */
    private static String sanitizeBibtex(String bib) {
        String s = bib;

        // 1) Escapar '%' y '&' globalmente (simple y seguro para el parser)
        s = s.replace("%", "\\%");
        s = s.replace("&", "\\&");

        // 2) En-dash -> '--' en páginas (dos variantes unicode)
        s = EN_DASH_PAGES.matcher(s).replaceAll("pages = {$1--$2}");

        // 3) Reunir keywords que hayan quedado fuera de llaves (heurística común)
        //    from: "keywords = {a, b}, c, d},"  ->  "keywords = {a, b, c, d},"
        s = s.replaceAll(
                "(?is)(keywords\\s*=\\s*\\{[^}]*\\}),\\s*([^}\\n]+)\\s*\\}",
                "$1, $2}"
        );

        // 4) Si falta coma antes del siguiente campo:  "},\n  field ="  (asegurar)
        s = s.replaceAll("}\\s*\\n\\s*([a-zA-Z]+)\\s*=", "},\n  $1 =");

        return s;
    }
}
