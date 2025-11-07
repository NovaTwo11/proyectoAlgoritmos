package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.PreprocessedArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.PreprocessingResponse;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.SimilarityResponse;
import co.edu.uniquindio.proyectoAlgoritmos.util.DendroMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class Requirement4OrchestratorService {

    private final PreprocessingPipelineService preprocessingPipelineService;
    private final SimilarityService similarityService;
    private final HierarchicalClusteringCore hclust;
    private final DendrogramRendererService renderer;

    @Value("${automation.dendrogramas.output-dir:src/main/resources/data/dendrogramas}")
    private String dendroOutDir;

    public ResponseEntity<?> run(List<ArticleDTO> articles) {
        // 0) Limpiar carpeta de salida (opcional, mantiene guardado en disco)
        try { cleanDir(dendroOutDir); } catch (Exception ignore) {}

        // 1) Preprocesamiento
        ResponseEntity<PreprocessingResponse> preproc = preprocessingPipelineService.preprocessArticles(articles);
        PreprocessingResponse body = preproc.getBody();
        if (body == null || body.getArticles() == null || body.getArticles().isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No hay abstracts válidos tras preprocesamiento"));
        }
        List<PreprocessedArticleDTO> pre = body.getArticles();

        // 2) Similitud/Distancias (TF-IDF + coseno; matriz de distancias euclidianas)
        SimilarityResponse sim = similarityService.computeTfidfCosineAndEuclidean(pre);
        double[][] d = sim.getDistancesEuclidean();
        List<String> labels = sim.getLabels();
        if (d == null || d.length == 0) {
            return ResponseEntity.ok(Map.of("message", "No fue posible calcular distancias"));
        }

        // 3) Clustering jerárquico (single, complete, average)
        var single   = hclust.agglomerate(d, HierarchicalClusteringCore.Linkage.SINGLE);
        var complete = hclust.agglomerate(d, HierarchicalClusteringCore.Linkage.COMPLETE);
        var average  = hclust.agglomerate(d, HierarchicalClusteringCore.Linkage.AVERAGE);

        // 4) Métrica de coherencia (Cophenetic Correlation Coefficient - CCC)
        double cccSingle   = DendroMetrics.copheneticCorrelation(d, single);
        double cccComplete = DendroMetrics.copheneticCorrelation(d, complete);
        double cccAverage  = DendroMetrics.copheneticCorrelation(d, average);

        String best = (cccSingle >= cccComplete && cccSingle >= cccAverage) ? "single"
                : (cccComplete >= cccAverage ? "complete" : "average");

        // 5) Render a PNG base64 (y guardar en disco en paralelo)
        var rSingle   = renderer.renderAndSaveBase64(single,   labels, dendroOutDir, "dendro_single");
        var rComplete = renderer.renderAndSaveBase64(complete, labels, dendroOutDir, "dendro_complete");
        var rAverage  = renderer.renderAndSaveBase64(average,  labels, dendroOutDir, "dendro_average");

        // 6) Respuesta para el front (Vite + Vue): data URIs + paths guardados + métricas
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", "png-base64");
        out.put("labels", labels);
        out.put("images", Map.of(
                "single",   Map.of(
                        "dataUri",  "data:image/png;base64," + rSingle.base64,
                        "filePath", rSingle.filePath
                ),
                "complete", Map.of(
                        "dataUri",  "data:image/png;base64," + rComplete.base64,
                        "filePath", rComplete.filePath
                ),
                "average",  Map.of(
                        "dataUri",  "data:image/png;base64," + rAverage.base64,
                        "filePath", rAverage.filePath
                )
        ));
        out.put("metrics", Map.of(
                "cophenetic", Map.of(
                        "single",   cccSingle,
                        "complete", cccComplete,
                        "average",  cccAverage
                ),
                "best", best
        ));
        // Información adicional útil para UI/depuración
        out.put("distance", Map.of(
                "type", "euclidean",
                "space", "TF-IDF L2-normalized"
        ));
        out.put("linkages", List.of("single", "complete", "average"));

        return ResponseEntity.ok(out);
    }


    private void cleanDir(String dir) {
        try {
            Path p = Paths.get(dir);
            if (!Files.exists(p)) { Files.createDirectories(p); return; }
            try (var walk = Files.walk(p)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .filter(pp -> !pp.equals(p))
                        .forEach(pp -> { try { Files.deleteIfExists(pp); } catch (IOException ignore) {} });
            }
        } catch (Exception e) {
            // no interrumpir el flujo por errores de limpieza
        }
    }
}
