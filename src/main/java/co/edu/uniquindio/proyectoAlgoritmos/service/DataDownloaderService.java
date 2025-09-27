package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.DataSource;
import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import co.edu.uniquindio.proyectoAlgoritmos.util.CsvUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataDownloaderService {

    private final CsvUtils csvUtils;

    /**
     * Simula la descarga de datos desde una fuente específica
     * En implementación real, aquí iría la lógica de scraping/API calls
     */
    public List<ScientificRecord> downloadFromSource(DataSource source, String searchQuery) {
        log.info("Iniciando descarga desde {} con query: {}", source.getDisplayName(), searchQuery);

        try {
            // Por ahora, lee archivos CSV pre-descargados
            String fileName = getFileNameForSource(source);
            File inputFile = new File("src/main/resources/data/input/" + fileName);

            if (!inputFile.exists()) {
                log.warn("Archivo no encontrado: {}. Creando datos de ejemplo.", fileName);
                return createSampleData(source, searchQuery);
            }

            List<ScientificRecord> records = csvUtils.readRecordsFromCsv(inputFile.getAbsolutePath());
            log.info("Descargados {} registros desde {}", records.size(), source.getDisplayName());

            return records;

        } catch (Exception e) {
            log.error("Error descargando desde {}: {}", source.getDisplayName(), e.getMessage());
            throw new RuntimeException("Error en descarga de datos", e);
        }
    }

    private String getFileNameForSource(DataSource source) {
        return switch (source) {
            case ACM -> "acm_generative_ai.csv";
            case SAGE -> "sage_generative_ai.csv";
            case SCIENCE_DIRECT -> "sciencedirect_generative_ai.csv";
            default -> "unknown_source.csv";
        };
    }

    private List<ScientificRecord> createSampleData(DataSource source, String searchQuery) {
        // Datos de ejemplo para testing
        return List.of(
                ScientificRecord.builder()
                        .title("Generative AI in Educational Settings: A Comprehensive Review")
                        .authors(List.of("Smith, J.", "Johnson, M."))
                        .abstractText("This paper explores the applications of generative artificial intelligence in educational contexts...")
                        .keywords(List.of("generative AI", "education", "machine learning"))
                        .journal("AI in Education Journal")
                        .year(2024)
                        .source(source.toString())
                        .country("USA")
                        .build(),

                ScientificRecord.builder()
                        .title("Ethical Considerations in Generative AI Systems")
                        .authors(List.of("Brown, A.", "Davis, K."))
                        .abstractText("We examine the ethical implications of deploying generative AI systems...")
                        .keywords(List.of("ethics", "AI", "generative models"))
                        .journal("Ethics in Technology")
                        .year(2023)
                        .source(source.toString())
                        .country("UK")
                        .build()
        );
    }
}