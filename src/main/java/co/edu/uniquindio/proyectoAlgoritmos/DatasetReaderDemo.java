package co.edu.uniquindio.proyectoAlgoritmos;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import co.edu.uniquindio.proyectoAlgoritmos.reader.DatasetReader;
import co.edu.uniquindio.proyectoAlgoritmos.reader.DatasetReaderFactory;
import co.edu.uniquindio.proyectoAlgoritmos.util.DatasetMerger;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/**
 * Clase de demostración para el lector de datasets reales
 */
public class DatasetReaderDemo {

    public static void main(String[] args) {
        System.out.println("=== Demo: Lector de Datasets Científicos ===\n");

        // Ejemplo 1: Leer un dataset de Scopus
        demoScopusReader();

        // Ejemplo 2: Leer un dataset de DBLP
        demoDBLPReader();

        // Ejemplo 3: Leer un dataset de ACM
        demoACMReader();

        // Ejemplo 4: Unificar múltiples datasets
        demoDatasetMerging();

        // Ejemplo 5: Análisis básico de los datos
        demoBasicAnalysis();
    }

    private static void demoScopusReader() {
        System.out.println("1. Leyendo dataset de Scopus...");
        try {
            DatasetReader scopusReader = DatasetReaderFactory.createReader("scopus");

            // Simular lectura (en la práctica, usarías un archivo real)
            String filePath = "scopus_generative_ai.csv";
            System.out.println("Archivo: " + filePath);
            System.out.println("Compatible: " + scopusReader.isCompatible(filePath));
            System.out.println("Fuente: " + scopusReader.getSourceName());

            // En un caso real:
            // List<ScientificRecord> records = scopusReader.readDataset(filePath);
            // System.out.println("Registros leídos: " + records.size());

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        System.out.println();
    }

    private static void demoDBLPReader() {
        System.out.println("2. Leyendo dataset de DBLP...");
        try {
            DatasetReader dblpReader = DatasetReaderFactory.createReader("dblp");

            String filePath = "dblp_generative_ai.csv";
            System.out.println("Archivo: " + filePath);
            System.out.println("Compatible: " + dblpReader.isCompatible(filePath));
            System.out.println("Fuente: " + dblpReader.getSourceName());

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        System.out.println();
    }

    private static void demoACMReader() {
        System.out.println("3. Leyendo dataset de ACM...");
        try {
            DatasetReader acmReader = DatasetReaderFactory.createReader("acm");

            String filePath = "acm_generative_ai.csv";
            System.out.println("Archivo: " + filePath);
            System.out.println("Compatible: " + acmReader.isCompatible(filePath));
            System.out.println("Fuente: " + acmReader.getSourceName());

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        System.out.println();
    }

    private static void demoDatasetMerging() {
        System.out.println("4. Unificando múltiples datasets...");

        // Crear datos de ejemplo
        List<ScientificRecord> scopusRecords = createSampleRecords("Scopus", 5);
        List<ScientificRecord> dblpRecords = createSampleRecords("DBLP", 3);
        List<ScientificRecord> acmRecords = createSampleRecords("ACM", 4);

        // Agregar algunos duplicados
        scopusRecords.add(dblpRecords.get(0)); // Duplicado
        acmRecords.add(scopusRecords.get(1));  // Duplicado

        List<List<ScientificRecord>> datasets = Arrays.asList(
                scopusRecords, dblpRecords, acmRecords
        );

        DatasetMerger.MergeResult result = DatasetMerger.mergeDatasets(datasets);
        result.printStatistics();
        System.out.println();
    }

    private static void demoBasicAnalysis() {
        System.out.println("5. Análisis básico de datos...");

        List<ScientificRecord> records = createSampleRecords("Mixed", 10);

        // Análisis por año
        Map<Integer, Long> recordsByYear = records.stream()
                .filter(r -> r.getYear() != 0 && r.getYear() > 0)
                .collect(java.util.stream.Collectors.groupingBy(
                        ScientificRecord::getYear,
                        java.util.stream.Collectors.counting()
                ));

        System.out.println("Distribución por año:");
        recordsByYear.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println("  " + entry.getKey() + ": " + entry.getValue()));

        // Análisis por tipo de documento
        Map<String, Long> recordsByType = records.stream()
                .filter(r -> r.getDocumentType() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        ScientificRecord::getDocumentType,
                        java.util.stream.Collectors.counting()
                ));

        System.out.println("\nDistribución por tipo:");
        recordsByType.forEach((type, count) ->
                System.out.println("  " + type + ": " + count));

        System.out.println();
    }

    /**
     * Crea registros de ejemplo usando el Builder de ScientificRecord
     */
    private static List<ScientificRecord> createSampleRecords(String source, int count) {
        List<ScientificRecord> records = new ArrayList<>();
        Random random = new Random();

        String[] titles = {
                "Generative AI in Education: A Comprehensive Review",
                "Machine Learning Approaches for Text Generation",
                "Ethical Considerations in AI-Generated Content",
                "Multimodal Generative Models for Creative Applications",
                "Fine-tuning Large Language Models for Domain Adaptation"
        };

        String[] authors = {
                "Smith, J.", "Johnson, M.", "Williams, R.", "Brown, L.", "Davis, K."
        };

        String[] types = {"Article", "Conference Paper", "Book Chapter"};

        for (int i = 0; i < count; i++) {
            ScientificRecord record = ScientificRecord.builder()
                    .id(source + "_" + (i + 1))
                    .title(titles[random.nextInt(titles.length)] + " " + (i + 1))
                    .authors(List.of(authors[random.nextInt(authors.length)]))
                    .abstractText("This is a sample abstract for record " + (i + 1))
                    .documentType(types[random.nextInt(types.length)])
                    .year(2020 + random.nextInt(5))
                    .source(source)
                    .publicationDate(LocalDate.of(2020 + random.nextInt(5), 1 + random.nextInt(12), 1))
                    .build();

            records.add(record);
        }

        return records;
    }

    /**
     * Método para procesar archivos reales
     */
    public static void processRealDatasets(String[] filePaths, String[] sources) {
        System.out.println("=== Procesando Datasets Reales ===");

        List<List<ScientificRecord>> allDatasets = new ArrayList<>();

        for (int i = 0; i < filePaths.length && i < sources.length; i++) {
            try {
                DatasetReader reader = DatasetReaderFactory.createReader(sources[i]);
                List<ScientificRecord> records = reader.readDataset(filePaths[i]);
                allDatasets.add(records);

                System.out.println("Procesado " + sources[i] + ": " + records.size() + " registros");

            } catch (IOException e) {
                System.err.println("Error procesando " + sources[i] + ": " + e.getMessage());
            }
        }

        if (!allDatasets.isEmpty()) {
            DatasetMerger.MergeResult result = DatasetMerger.mergeDatasets(allDatasets);
            result.printStatistics();

            // Guardar resultados
            saveResults(result);
        }
    }

    private static void saveResults(DatasetMerger.MergeResult result) {
        // Aquí implementarías la lógica para guardar:
        // 1. Archivo unificado con registros únicos
        // 2. Archivo con duplicados eliminados
        System.out.println("\n[INFO] Los resultados se guardarían en:");
        System.out.println("  - unified_dataset.csv (registros únicos)");
        System.out.println("  - duplicates_removed.csv (duplicados eliminados)");
    }
}