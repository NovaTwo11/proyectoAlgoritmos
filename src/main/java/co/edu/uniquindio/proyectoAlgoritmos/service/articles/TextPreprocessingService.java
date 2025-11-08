package co.edu.uniquindio.proyectoAlgoritmos.service.articles;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.PreprocessingOptions;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TextPreprocessingService {

    private static final Set<String> STOP_ES = Set.of(
            "a","ante","bajo","cabe","con","contra","de","desde","en","entre","hacia","hasta","para","por","segun","sin","so","sobre","tras",
            "el","la","los","las","un","una","unos","unas","y","o","u","e","que","como","se","su","sus","es","son","fue","fueron","ser","esta","está","están","estamos","este","estos","estas"
    );

    private static final Set<String> STOP_EN = Set.of(
            "the","and","or","of","to","in","on","for","with","by","an","as","at","from","this","that","these","those","we","our","their","it","its",
            "be","are","is","was","were","have","has","had","can","could","may","might","into","across","using","use","used","via","results","result","show","shows","find","finds","based","approach","approaches","method","methods","data","paper","study"
    );

    private static final Pattern URL = Pattern.compile("https?://\\S+|www\\.\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9_.%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    public String normalize(String text) {
        if (text == null) return "";
        String s = text;
        s = URL.matcher(s).replaceAll(" ");
        s = EMAIL.matcher(s).replaceAll(" ");
        s = s.replaceAll("[\u2013\u2014]", "-");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replace('\u201C', '"').replace('\u201D', '"').replace('\u2018','\'').replace('\u2019','\'');
        s = s.toLowerCase(Locale.ROOT);
        s = s.replaceAll("\\\\[a-z]+", " ");
        s = s.replaceAll("\\d+", " ");
        s = s.replace('\u00A0', ' ');
        s = s.replaceAll("\n+", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    public List<String> tokenize(String normalized, PreprocessingOptions opts) {
        if (normalized == null || normalized.isBlank()) return List.of();
        String regex = (opts != null && opts.isKeepPlus()) ? "[^a-z0-9+]+" : "[^a-z0-9]+";
        return Arrays.stream(normalized.split(regex))
                .filter(t -> !t.isBlank())
                .collect(Collectors.toList());
    }

    public List<String> filterTokens(List<String> tokens, PreprocessingOptions opts) {
        if (tokens == null || tokens.isEmpty()) return List.of();
        int minLen = opts != null ? opts.getMinTokenLength() : 3;
        boolean rmNums = opts == null || opts.isRemoveNumbers();
        Set<String> stop = new HashSet<>();
        stop.addAll(STOP_ES);
        stop.addAll(STOP_EN);
        return tokens.stream()
                .filter(t -> t.length() >= minLen)
                .filter(t -> !stop.contains(t))
                .filter(t -> !(rmNums && t.chars().allMatch(Character::isDigit)))
                .collect(Collectors.toList());
    }

    public List<String> maybeBigrams(List<String> tokens, PreprocessingOptions opts) {
        if (opts == null || !opts.isUseBigrams() || tokens.size() < 2) return tokens;
        List<String> out = new ArrayList<>(tokens);
        for (int i=0;i<tokens.size()-1;i++) {
            out.add(tokens.get(i) + "_" + tokens.get(i+1));
        }
        return out;
    }
}
