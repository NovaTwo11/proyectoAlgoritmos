package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.PreprocessingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class Requirement4OrchestratorService {

    private final PreprocessingPipelineService preprocessingPipelineService;

    public ResponseEntity<?> run(List<ArticleDTO> articles) {
        ResponseEntity<PreprocessingResponse> preproc = preprocessingPipelineService.preprocessArticles(articles);
        return ResponseEntity.status(preproc.getStatusCode()).body(preproc.getBody());
    }
}


