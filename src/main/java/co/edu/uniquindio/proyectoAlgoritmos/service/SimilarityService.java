package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.PreprocessedArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.SimilarityPairDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.SimilarityResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimilarityService {

    public SimilarityResponse computeTfidfCosineAndEuclidean(List<PreprocessedArticleDTO> preprocessed) {
        List<String> labels = preprocessed.stream().map(p -> p.getTitle() != null ? p.getTitle() : p.getId()).toList();
        List<String> docs = preprocessed.stream().map(p -> String.join(" ", p.getTokens())).toList();
        int n = docs.size();
        if (n == 0) return SimilarityResponse.builder().labels(List.of()).distancesEuclidean(new double[0][0]).topSimilar(List.of()).build();

        // TF y DF
        List<Map<String,Integer>> tfs = new ArrayList<>(n);
        Map<String,Integer> df = new HashMap<>();
        for (String d : docs) {
            Map<String,Integer> tf = new HashMap<>();
            for (String w : d.split("\\s+")) if (!w.isBlank()) tf.merge(w, 1, Integer::sum);
            tfs.add(tf);
            for (String term : tf.keySet()) df.merge(term, 1, Integer::sum);
        }

        // Filtrado por DF como en min_df>=2 y max_df<=0.9
        int minDf = 2;
        double maxDfFrac = 0.9;
        int maxDfAbs = Math.max(1, (int)Math.floor(maxDfFrac * n));
        Set<String> vocab = df.entrySet().stream()
                .filter(e -> e.getValue() >= minDf && e.getValue() <= maxDfAbs)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // IDF suavizado sobre el vocab filtrado
        Map<String,Double> idf = new HashMap<>();
        for (String term : vocab) {
            int dfi = df.getOrDefault(term, 1);
            idf.put(term, Math.log((n + 1.0) / (dfi + 1.0)) + 1.0);
        }

        // Vectores TF-IDF normalizados (L2)
        List<Map<String,Double>> vecs = new ArrayList<>(n);
        for (Map<String,Integer> tf : tfs) {
            Map<String,Double> v = new HashMap<>();
            double sum2=0.0;
            for (Map.Entry<String,Integer> e : tf.entrySet()) {
                String term = e.getKey();
                if (!vocab.contains(term)) continue;
                double w = (1.0 + Math.log(e.getValue())) * idf.getOrDefault(term, 0.0);
                v.put(term, w); sum2 += w*w;
            }
            double norm = sum2>0 ? Math.sqrt(sum2) : 1.0;
            double inv = norm>0 ? 1.0/norm : 1.0;
            for (Map.Entry<String,Double> e : v.entrySet()) e.setValue(e.getValue()*inv);
            vecs.add(v);
        }

        // Coseno (pares) y matriz euclidiana
        double[][] eu = new double[n][n];
        List<SimilarityPairDTO> pairs = new ArrayList<>();
        for (int i=0;i<n;i++) {
            eu[i][i]=0.0;
            for (int j=i+1;j<n;j++) {
                double cos = dot(vecs.get(i), vecs.get(j));
                pairs.add(new SimilarityPairDTO(labels.get(i), labels.get(j), cos));
                double euc = euclidean(vecs.get(i), vecs.get(j));
                eu[i][j]=euc; eu[j][i]=euc;
            }
        }
        pairs.sort((a,b) -> Double.compare(b.getScore(), a.getScore()));
        return SimilarityResponse.builder()
                .labels(labels)
                .distancesEuclidean(eu)
                .topSimilar(pairs.stream().limit(50).collect(Collectors.toList()))
                .build();
    }

    private double dot(Map<String,Double> a, Map<String,Double> b) {
        Map<String,Double> s = a.size()<=b.size()?a:b; Map<String,Double> l = a.size()>b.size()?a:b;
        double sum=0; for (Map.Entry<String,Double> e : s.entrySet()) { Double w = l.get(e.getKey()); if (w!=null) sum += e.getValue()*w; }
        return Math.max(0.0, Math.min(1.0, sum));
    }

    private double euclidean(Map<String,Double> a, Map<String,Double> b) {
        Set<String> union = new HashSet<>();
        union.addAll(a.keySet());
        union.addAll(b.keySet());
        double s = 0.0;
        for (String k : union) {
            double da = a.getOrDefault(k, 0.0);
            double db = b.getOrDefault(k, 0.0);
            double d = da - db;
            s += d * d;
        }
        return Math.sqrt(s);
    }
}
