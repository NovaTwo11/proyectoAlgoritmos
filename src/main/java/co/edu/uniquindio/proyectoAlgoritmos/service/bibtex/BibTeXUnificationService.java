package co.edu.uniquindio.proyectoAlgoritmos.service.bibtex;

import co.edu.uniquindio.proyectoAlgoritmos.model.Article;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.text.Normalizer;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BibTeXUnificationService {

    private final BibTeXParserService parser;

    public Map<String, List<Article>> unify(List<Article> input) {
        // Usamos clave compuesta: titulo normalizado + autores normalizados (orden-insensible)
        Map<String, Article> unique = new LinkedHashMap<>();
        List<Article> dups = new ArrayList<>();
        for (Article a : input) {
            a.ensureId();
            String tKey = titleKey(a.getTitle());
            if (tKey == null || tKey.isBlank()) {
                // Sin título: lo tratamos como único
                tKey = "no-title-" + a.getId();
            }
            String aKey = authorsKey(a.getAuthors());
            if (aKey == null || aKey.isBlank()) {
                aKey = "no-authors";
            }
            String key = tKey + "|" + aKey;

            if (!unique.containsKey(key)) {
                unique.put(key, a);
            } else {
                // Duplicado: mismo título y mismos autores (normalizados)
                Article merged = merge(unique.get(key), a);
                unique.put(key, merged);
                dups.add(a);
            }
        }
        Map<String, List<Article>> res = new HashMap<>();
        res.put("unified", new ArrayList<>(unique.values()));
        res.put("duplicates", dups);
        return res;
    }

    private String titleKey(String title) {
        if (title == null) return null;
        String s = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // remover diacríticos
        s = s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ") // dejar solo letras/números como separadores
                .trim()
                .replaceAll("\\s+", " "); // colapsar espacios
        return s;
    }

    private String authorsKey(List<String> authors) {
        if (authors == null || authors.isEmpty()) return null;
        List<String> norm = new ArrayList<>();
        for (String au : authors) {
            if (au == null) continue;
            String s = Normalizer.normalize(au, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");
            // Normalizar: minúsculas, quitar puntuación, colapsar espacios
            s = s.toLowerCase(Locale.ROOT)
                    .replaceAll("[.,;]+", " ")
                    .replaceAll("[^a-z0-9 ]+", " ")
                    .trim()
                    .replaceAll("\\s+", " ");
            if (!s.isBlank()) norm.add(s);
        }
        if (norm.isEmpty()) return null;
        Collections.sort(norm);
        return String.join("|", norm);
    }

    private Article merge(Article a, Article b) {
        Article.ArticleBuilder m = Article.builder();
        m.id(a.getId());
        m.entryType(or(a.getEntryType(), b.getEntryType()));
        m.originalBibtexKey(or(a.getOriginalBibtexKey(), b.getOriginalBibtexKey()));
        m.title(or(a.getTitle(), b.getTitle()));
        m.authors(mergeList(a.getAuthors(), b.getAuthors()));
        m.journal(or(a.getJournal(), b.getJournal()));
        m.booktitle(or(a.getBooktitle(), b.getBooktitle()));
        m.year(a.getYear() != null ? a.getYear() : b.getYear());
        m.volume(or(a.getVolume(), b.getVolume()));
        m.pages(or(a.getPages(), b.getPages()));
        m.doi(or(a.getDoi(), b.getDoi()));
        m.url(or(a.getUrl(), b.getUrl()));
        m.issn(or(a.getIssn(), b.getIssn()));
        m.isbn(or(a.getIsbn(), b.getIsbn()));
        m.keywords(mergeList(a.getKeywords(), b.getKeywords()));
        m.abstractText(longer(a.getAbstractText(), b.getAbstractText()));
        return m.build();
    }

    private String or(String x, String y) { return (x != null && !x.isBlank()) ? x : y; }
    private String longer(String x, String y) {
        if (x == null || x.isBlank()) return y;
        if (y == null || y.isBlank()) return x;
        return x.length() >= y.length() ? x : y;
    }

    private <T> List<T> mergeList(List<T> a, List<T> b) {
        LinkedHashSet<T> s = new LinkedHashSet<>();
        if (a != null) s.addAll(a);
        if (b != null) s.addAll(b);
        return new ArrayList<>(s);
    }

    public Map<String, List<Article>> processDownloaded(String downloadDir, String unifiedPath, String dupsPath) throws Exception {
        File dir = new File(downloadDir);
        File[] bibs = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".bib"));
        if (bibs == null || bibs.length == 0) {
            log.warn("No se encontraron .bib en {}", downloadDir);
            Map<String, List<Article>> empty = new HashMap<>();
            empty.put("unified", Collections.emptyList());
            empty.put("duplicates", Collections.emptyList());
            return empty;
        }
        // Loguear conteos por archivo
        int totalParsed = 0;
        for (File f : bibs) {
            List<Article> tmp = parser.parseBib(f);
            totalParsed += tmp.size();
        }
        log.info("Total de artículos parseados desde {}: {} ({} archivos)", downloadDir, totalParsed, bibs.length);

        // Parsear todo en conjunto para unificación
        List<Article> all = parser.parseAll(Arrays.asList(bibs));
        Map<String, List<Article>> res = unify(all);
        log.info("Unificación: únicos={} | duplicados={}", res.get("unified").size(), res.get("duplicates").size());

        // Exportar unificados al formato BibTeX solicitado (.bib)
        parser.exportCustomBib(res.get("unified"), new File(unifiedPath));

        // Exportar duplicados en .bib si se pasa ruta
        if (dupsPath != null && !dupsPath.isBlank()) {
            try {
                parser.exportCustomBib(res.get("duplicates"), new File(dupsPath));
                log.info("Duplicados exportados a (bib): {}", new File(dupsPath).getAbsolutePath());
            } catch (Exception ex) {
                log.warn("No se pudo exportar duplicados .bib en {}: {}", dupsPath, ex.getMessage());
            }
        }

        // Solo normalizados por fuente en src/main/resources/data/normalized
        File normalizedDir = new File("src/main/resources/data/normalized");
        if (!normalizedDir.exists() && !normalizedDir.mkdirs()) {
            log.warn("No se pudo crear el directorio: {}", normalizedDir.getAbsolutePath());
        }
        aggregateNormalizedBySource(downloadDir, normalizedDir);
        return res;
    }

    private void aggregateNormalizedBySource(String downloadDir, File normalizedDir) {
        File dir = new File(downloadDir);
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".bib"));
        if (files == null) return;
        try {
            java.nio.file.Path acmNorm = new File(normalizedDir, "acm_normalized.bib").toPath();
            java.nio.file.Path wosNorm = new File(normalizedDir, "webofscience_normalized.bib").toPath();
            java.nio.file.Files.deleteIfExists(acmNorm);
            java.nio.file.Files.deleteIfExists(wosNorm);

            List<File> acmFiles = new ArrayList<>();
            List<File> wosFiles = new ArrayList<>();
            for (File f : files) {
                String name = f.getName().toLowerCase();
                if (name.contains("acm")) {
                    acmFiles.add(f);
                } else if (name.contains("webofscience") || name.contains("wos")) {
                    wosFiles.add(f);
                }
            }
            if (!acmFiles.isEmpty()) {
                List<Article> acmArts = parser.parseAll(acmFiles);
                parser.exportCustomBib(acmArts, acmNorm.toFile());
            }
            if (!wosFiles.isEmpty()) {
                List<Article> wosArts = parser.parseAll(wosFiles);
                parser.exportCustomBib(wosArts, wosNorm.toFile());
            }
        } catch (Exception ignored) {}
    }
}
