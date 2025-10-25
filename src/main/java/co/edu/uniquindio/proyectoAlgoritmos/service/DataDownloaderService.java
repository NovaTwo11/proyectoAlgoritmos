package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.DataSource;
import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import co.edu.uniquindio.proyectoAlgoritmos.reader.ApiDatasetReader;
import co.edu.uniquindio.proyectoAlgoritmos.reader.DblpApiReader;
import co.edu.uniquindio.proyectoAlgoritmos.reader.OpenAlexApiReader;
import co.edu.uniquindio.proyectoAlgoritmos.util.CsvUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataDownloaderService {

    private final CsvUtils csvUtils;
    private final DblpApiReader dblpApiReader;
    private final OpenAlexApiReader openAlexApiReader;

    @Value("${api.dblp.max-results:500}")
    private int dblpMaxResults;

    @Value("${api.openalex.max-results:500}")
    private int openAlexMaxResults;

    /**
     * Descarga datos desde APIs reales o archivos CSV locales como fallback
     */
    public List<ScientificRecord> downloadFromSource(DataSource source, String searchQuery) {
        log.info("Iniciando descarga desde {} con query: {}", source.getDisplayName(), searchQuery);

        try {
            switch (source) {
                case DBLP:
                    return downloadFromApi(dblpApiReader, searchQuery, dblpMaxResults);

                case OPENALEX:
                    return downloadFromApi(openAlexApiReader, searchQuery, openAlexMaxResults);

                default:
                    // Fallback a archivos CSV locales para otras fuentes
                    return downloadFromCsvFile(source, searchQuery);
            }

        } catch (Exception e) {
            log.error("Error descargando desde {}: {}", source.getDisplayName(), e.getMessage());

            // Fallback a CSV si la API falla
            log.info("Intentando fallback a archivo CSV para {}", source);
            return downloadFromCsvFile(source, searchQuery);
        }
    }

    /**
     * Descarga datos desde una API usando el reader correspondiente
     */
    private List<ScientificRecord> downloadFromApi(ApiDatasetReader apiReader, String searchQuery, int maxResults) {
        try {
            if (!apiReader.isApiAvailable()) {
                log.warn("API {} no disponible", apiReader.getSourceName());
                return new ArrayList<>();
            }

            List<ScientificRecord> records = apiReader.downloadFromApi(searchQuery, maxResults);
            log.info("Descargados {} registros desde API {}", records.size(), apiReader.getSourceName());
            return records;

        } catch (Exception e) {
            log.error("Error en API {}: {}", apiReader.getSourceName(), e.getMessage());
            throw new RuntimeException("Error descargando desde API " + apiReader.getSourceName(), e);
        }
    }

    /**
     * Fallback: lee desde archivos CSV pre-descargados
     */
    private List<ScientificRecord> downloadFromCsvFile(DataSource source, String searchQuery) {
        try {
            String fileName = getFileNameForSource(source);
            File inputFile = new File("src/main/resources/data/input/" + fileName);

            if (!inputFile.exists()) {
                log.warn("Archivo no encontrado: {}. Creando datos de ejemplo.", fileName);
                return createSampleData(source, searchQuery);
            }

            List<ScientificRecord> records = csvUtils.readRecordsFromCsv(inputFile.getAbsolutePath());
            log.info("Leídos {} registros desde archivo CSV {}", records.size(), fileName);

            return records;

        } catch (Exception e) {
            log.error("Error leyendo archivo CSV para {}: {}", source, e.getMessage());
            return createSampleData(source, searchQuery);
        }
    }

    private String getFileNameForSource(DataSource source) {
        return switch (source) {
            case DBLP -> "dblp_generative_ai.csv";
            case OPENALEX -> "openalex_generative_ai.csv";
            default -> "unknown_source.csv";
        };
    }

    private List<ScientificRecord> createSampleData(DataSource source, String searchQuery) {
        log.info("Creando datos de ejemplo para {} con query: {}", source, searchQuery);

        // Datos de ejemplo para testing
        return List.of(
                ScientificRecord.builder()
                        .id(source + "_sample_1")
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
                        .id(source + "_sample_2")
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

    /**
     * Descarga desde todas las fuentes principales (DBLP + OpenAlex)
     */
    public List<ScientificRecord> downloadFromAllSources(String searchQuery) {
        List<ScientificRecord> allRecords = new ArrayList<>();

        // Descargar desde DBLP
        try {
            List<ScientificRecord> dblpRecords = downloadFromSource(DataSource.DBLP, searchQuery);
            allRecords.addAll(dblpRecords);
            log.info("DBLP: {} registros descargados", dblpRecords.size());
        } catch (Exception e) {
            log.warn("Error descargando desde DBLP: {}", e.getMessage());
        }

        // Descargar desde OpenAlex
        try {
            List<ScientificRecord> openAlexRecords = downloadFromSource(DataSource.OPENALEX, searchQuery);
            allRecords.addAll(openAlexRecords);
            log.info("OpenAlex: {} registros descargados", openAlexRecords.size());
        } catch (Exception e) {
            log.warn("Error descargando desde OpenAlex: {}", e.getMessage());
        }

        log.info("Total de registros descargados de todas las fuentes: {}", allRecords.size());
        return allRecords;
    }
}