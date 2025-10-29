package co.edu.uniquindio.proyectoAlgoritmos.service.bibtex;

import co.edu.uniquindio.proyectoAlgoritmos.model.Article;
import lombok.extern.slf4j.Slf4j;
import org.jbibtex.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BibTeXParserService {

    public List<Article> parseBib(File file) {
        List<Article> out = new ArrayList<>();
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            BibTeXParser parser = new BibTeXParser();
            BibTeXDatabase db = parser.parse(r);
            for (Map.Entry<Key, BibTeXEntry> e : db.getEntries().entrySet()) {
                out.add(toArticle(e.getKey(), e.getValue()));
            }
            log.info("Parseado {} -> {} artículos (jbibtex)", file.getName(), out.size());
        } catch (Exception ex) {
            log.warn("Error parseando {} con jbibtex: {}. Intentando fallback...", file.getName(), ex.getMessage());
            try {
                List<Article> fb = parseBibFallback(file);
                log.info("Parseado {} -> {} artículos (fallback)", file.getName(), fb.size());
                out.addAll(fb);
            } catch (Exception ex2) {
                log.warn("Fallback también falló para {}: {}", file.getName(), ex2.getMessage());
            }
        }
        return out;
    }

    public List<Article> parseAll(List<File> files) {
        List<Article> all = new ArrayList<>();
        for (File f : files) all.addAll(parseBib(f));
        return all;
    }

    private Article toArticle(Key key, BibTeXEntry e) {
        Article a = new Article();
        a.ensureId();
        a.setOriginalBibtexKey(key != null ? key.getValue() : null);
        a.setEntryType(e.getType() != null ? e.getType().getValue() : "article");

        a.setTitle(val(e, BibTeXEntry.KEY_TITLE));
        a.setAbstractText(val(e, new Key("abstract")));
        a.setJournal(val(e, BibTeXEntry.KEY_JOURNAL));
        a.setBooktitle(val(e, BibTeXEntry.KEY_BOOKTITLE));
        a.setVolume(val(e, BibTeXEntry.KEY_VOLUME));
        a.setPages(val(e, BibTeXEntry.KEY_PAGES));
        a.setDoi(val(e, BibTeXEntry.KEY_DOI));
        a.setUrl(val(e, BibTeXEntry.KEY_URL));
        a.setIssn(val(e, new Key("issn")));
        a.setIsbn(val(e, new Key("isbn")));

        String y = val(e, BibTeXEntry.KEY_YEAR);
        if (y != null) try { a.setYear(Integer.parseInt(y.replaceAll("[^0-9]",""))); } catch (Exception ignore) {}

        String authors = val(e, BibTeXEntry.KEY_AUTHOR);
        if (authors != null) {
            a.setAuthors(Arrays.stream(authors.split("\\s+and\\s+"))
                    .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toList()));
        }
        String kw = val(e, new Key("keywords"));
        if (kw != null) {
            a.setKeywords(Arrays.stream(kw.split("[,;\\n]+"))
                    .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toList()));
        }
        return a;
    }

    private String val(BibTeXEntry e, Key k) {
        Value v = e.getField(k);
        if (v == null) return null;
        return clean(v.toUserString());
    }

    // limpieza básica LaTeX/escapes
    private String clean(String s) {
        if (s == null) return null;
        return s.replaceAll("\\\\[a-zA-Z]+\\s*", "")
                .replace("{", "").replace("}", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public void exportCustomBib(List<Article> arts, File out) throws IOException {
        out.getParentFile().mkdirs();
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
            for (Article a : arts) {
                w.write(formatAsBib(a));
                w.newLine();
            }
        }
    }

    private String formatAsBib(Article a) {
        String type = a.getEntryType() != null ? a.getEntryType() : "article";
        String key = a.getOriginalBibtexKey() != null ? a.getOriginalBibtexKey() : a.getId();
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
        if (a.safeAuthors().size() > 0) sb.append("author = {").append(String.join(" and ", a.safeAuthors())).append("}, ");
        if (a.safeKeywords().size() > 0) sb.append("keywords = {").append(String.join(", ", a.safeKeywords())).append("}, ");
        append(sb, "abstract", a.getAbstractText());
        // remover coma final si existe
        int len = sb.length();
        if (len >= 2 && sb.substring(len-2).equals(", ")) {
            sb.setLength(len-2);
            sb.append(" ");
        }
        sb.append("}");
        return sb.toString();
    }

    private void append(StringBuilder sb, String k, String v) {
        if (v != null && !v.isBlank()) sb.append(k).append(" = {").append(v).append("}, ");
    }

    public String formatDuplicateLine(Article a) {
        StringBuilder sb = new StringBuilder();
        sb.append("Article{").append(a.getId()).append(",  ");
        append(sb, "abstract", a.getAbstractText());
        sb.append("authors = ").append(a.safeAuthors()).append(",  ");
        sb.append("journal = ").append(a.getJournal() != null ? a.getJournal() : a.getBooktitle()).append(",  ");
        sb.append("keywords = ").append(a.safeKeywords()).append(",  ");
        append(sb, "title", a.getTitle());
        append(sb, "year", a.getYear() != null ? a.getYear().toString() : null);
        // remover coma final si existe
        int len = sb.length();
        if (len >= 2 && sb.substring(len-2).equals(", ")) {
            sb.setLength(len-2);
            sb.append(" ");
        }
        sb.append("}");
        return sb.toString();
    }

    public String formatFilteredArticle(Article a) {
        StringBuilder sb = new StringBuilder();
        sb.append("@Filtered Article{").append(a.getId()).append(",  ");
        append(sb, "abstract", a.getAbstractText());
        sb.append("authors = ").append(a.safeAuthors()).append(",  ");
        sb.append("journal = ").append(a.getJournal() != null ? a.getJournal() : a.getBooktitle()).append(",  ");
        sb.append("keywords = ").append(a.safeKeywords()).append(",  ");
        append(sb, "title", a.getTitle());
        append(sb, "year", a.getYear() != null ? a.getYear().toString() : null);
        int len = sb.length();
        if (len >= 2 && sb.substring(len-2).equals(", ")) { sb.setLength(len-2); sb.append(" "); }
        sb.append("}");
        return sb.toString();
    }

    public String formatDuplicatedArticle(Article a) {
        StringBuilder sb = new StringBuilder();
        sb.append("@Duplicated Article{").append(a.getId()).append(",  ");
        append(sb, "abstract", a.getAbstractText());
        sb.append("authors = ").append(a.safeAuthors()).append(",  ");
        sb.append("journal = ").append(a.getJournal() != null ? a.getJournal() : a.getBooktitle()).append(",  ");
        sb.append("keywords = ").append(a.safeKeywords()).append(",  ");
        append(sb, "title", a.getTitle());
        append(sb, "year", a.getYear() != null ? a.getYear().toString() : null);
        int len = sb.length();
        if (len >= 2 && sb.substring(len-2).equals(", ")) { sb.setLength(len-2); sb.append(" "); }
        sb.append("}");
        return sb.toString();
    }

    // Fallback parser basado en regex robusto (acepta valores entre llaves o comillas)
    private List<Article> parseBibFallback(File file) throws IOException {
        String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        List<Article> list = new ArrayList<>();
        Pattern entryPat = Pattern.compile("@\\s*([A-Za-z]+)\\s*\\{\\s*([^,}]+)\\s*,([\\s\\S]*?)\\}\\s*(?=\\n@|\\z)", Pattern.MULTILINE);
        Matcher em = entryPat.matcher(content);
        while (em.find()) {
            String type = em.group(1);
            String key = em.group(2);
            String body = em.group(3);
            Map<String,String> fields = new LinkedHashMap<>();
            Pattern fieldPat = Pattern.compile("(?mi)^\\s*([A-Za-z]+)\\s*=\\s*(\\{([\\s\\S]*?)\\}|\"([\\s\\S]*?)\")\\s*,?\\s*$");
            Matcher fm = fieldPat.matcher(body);
            while (fm.find()) {
                String fname = fm.group(1).toLowerCase(Locale.ROOT);
                String val = fm.group(3) != null ? fm.group(3) : fm.group(4);
                if (val != null) fields.put(fname, clean(val));
            }
            Article a = new Article();
            a.ensureId();
            a.setEntryType(type != null ? type.toLowerCase(Locale.ROOT) : "article");
            a.setOriginalBibtexKey(key);
            a.setTitle(fields.get("title"));
            a.setAbstractText(fields.get("abstract"));
            a.setJournal(fields.get("journal"));
            a.setBooktitle(fields.get("booktitle"));
            a.setVolume(fields.get("volume"));
            a.setPages(fields.get("pages"));
            a.setDoi(fields.get("doi"));
            a.setUrl(fields.get("url"));
            a.setIssn(fields.get("issn"));
            a.setIsbn(fields.get("isbn"));
            String y = fields.get("year");
            if (y != null) try { a.setYear(Integer.parseInt(y.replaceAll("[^0-9]",""))); } catch (Exception ignore) {}
            String authors = fields.get("author");
            if (authors != null) {
                a.setAuthors(Arrays.stream(authors.split("\\s+and\\s+"))
                        .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toList()));
            }
            String kw = fields.get("keywords");
            if (kw != null) {
                a.setKeywords(Arrays.stream(kw.split("[,;\\n]+"))
                        .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toList()));
            }
            list.add(a);
        }
        return list;
    }
}
