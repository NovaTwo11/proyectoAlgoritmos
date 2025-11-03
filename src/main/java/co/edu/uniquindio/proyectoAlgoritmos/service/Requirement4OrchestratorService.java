package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.PreprocessedArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.PreprocessingResponse;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.SimilarityResponse;
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
        // Limpiar carpeta de dendrogramas antes de procesar
        try { cleanDir(dendroOutDir); } catch (Exception ignore) {}

        // Paso 1: preprocesamiento
        ResponseEntity<PreprocessingResponse> preproc = preprocessingPipelineService.preprocessArticles(articles);
        PreprocessingResponse body = preproc.getBody();
        if (body == null || body.getArticles() == null || body.getArticles().isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No hay abstracts válidos tras preprocesamiento"));
        }
        List<PreprocessedArticleDTO> pre = body.getArticles();

        // Paso 2: similitud (TF-IDF + coseno y distancias euclidianas)
        SimilarityResponse sim = similarityService.computeTfidfCosineAndEuclidean(pre);
        double[][] d = sim.getDistancesEuclidean();
        List<String> labels = sim.getLabels();
        if (d == null || d.length == 0) {
            return ResponseEntity.ok(Map.of("message", "No fue posible calcular distancias"));
        }

        // Paso 3: clustering (single, complete, average)
        var single = hclust.agglomerate(d, HierarchicalClusteringCore.Linkage.SINGLE);
        var complete = hclust.agglomerate(d, HierarchicalClusteringCore.Linkage.COMPLETE);
        var average = hclust.agglomerate(d, HierarchicalClusteringCore.Linkage.AVERAGE);

        // Renderizar y guardar las imágenes
        var rSingle = renderer.renderAndSaveBase64(single, labels, dendroOutDir, "dendro_single");
        var rComplete = renderer.renderAndSaveBase64(complete, labels, dendroOutDir, "dendro_complete");
        var rAverage = renderer.renderAndSaveBase64(average, labels, dendroOutDir, "dendro_average");

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("format", "png-base64");
        out.put("images", Map.of(
                "single", Map.of("base64", rSingle.base64, "filePath", rSingle.filePath),
                "complete", Map.of("base64", rComplete.base64, "filePath", rComplete.filePath),
                "average", Map.of("base64", rAverage.base64, "filePath", rAverage.filePath)
        ));
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
