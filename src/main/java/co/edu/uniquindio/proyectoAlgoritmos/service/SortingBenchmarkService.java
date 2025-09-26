package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Servicio encargado de:
 * - Ejecutar benchmarks de los distintos algoritmos de ordenamiento implementados.
 * - Medir tiempos de ejecución para diferentes tamaños de entrada.
 * - Exportar los resultados a un archivo CSV para análisis posterior.
 * - Ordenar productos académicos y procesar autores (para el taller de seguimiento).
 *
 * Esta clase usa los algoritmos implementados en {@link SortingAlgorithmsService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SortingBenchmarkService {

    private final SortingAlgorithmsService sortingService;

    /**
     * Clase interna que encapsula el resultado de un benchmark de un algoritmo.
     */
    public static class BenchmarkResult {
        public String algorithmName;      // Nombre del algoritmo
        public String complexity;         // Complejidad teórica
        public int arraySize;             // Tamaño de entrada usado
        public long executionTimeNanos;   // Tiempo en nanosegundos
        public double executionTimeMs;    // Tiempo en milisegundos

        public BenchmarkResult(String algorithmName, String complexity, int arraySize, long executionTimeNanos) {
            this.algorithmName = algorithmName;
            this.complexity = complexity;
            this.arraySize = arraySize;
            this.executionTimeNanos = executionTimeNanos;
            this.executionTimeMs = executionTimeNanos / 1_000_000.0;
        }
    }

    /**
     * Contenedor de definición de un algoritmo:
     * - Complejidad
     * - Implementación como un Consumer<int[]>
     */
    private static class AlgorithmInfo {
        String complexity;
        Consumer<int[]> algorithm;

        AlgorithmInfo(String complexity, Consumer<int[]> algorithm) {
            this.complexity = complexity;
            this.algorithm = algorithm;
        }
    }

    /**
     * Ejecuta benchmarks para todos los algoritmos de ordenamiento
     * @param arraySize tamaño del arreglo de entrada
     * @return lista con resultados de cada algoritmo
     */
    public List<BenchmarkResult> runCompleteBenchmark(int arraySize) {
        log.info("Iniciando benchmark con tamaño: {}", arraySize);

        List<BenchmarkResult> results = new ArrayList<>();

        // Definición de algoritmos probados
        Map<String, AlgorithmInfo> algorithms = new LinkedHashMap<>();
        algorithms.put("TimSort (Java Arrays.sort)", new AlgorithmInfo("O(n log n)", sortingService::timSort));
        algorithms.put("Comb Sort", new AlgorithmInfo("O(n²)", sortingService::combSort));
        algorithms.put("Selection Sort", new AlgorithmInfo("O(n²)", sortingService::selectionSort));
        algorithms.put("Tree Sort", new AlgorithmInfo("O(n log n)", sortingService::treeSort));
        algorithms.put("Pigeonhole Sort", new AlgorithmInfo("O(n + range)", sortingService::pigeonholeSort));
        algorithms.put("Bucket Sort", new AlgorithmInfo("O(n + k)", sortingService::bucketSort));
        algorithms.put("QuickSort", new AlgorithmInfo("O(n log n)", sortingService::quickSort));
        algorithms.put("HeapSort", new AlgorithmInfo("O(n log n)", sortingService::heapSort));
        algorithms.put("Bitonic Sort", new AlgorithmInfo("O(n log² n)", sortingService::bitonicSort));
        algorithms.put("Gnome Sort", new AlgorithmInfo("O(n²)", sortingService::gnomeSort));
        algorithms.put("Binary Insertion Sort", new AlgorithmInfo("O(n²)", sortingService::binaryInsertionSort));
        algorithms.put("Radix Sort", new AlgorithmInfo("O(d*(n + k))", sortingService::radixSort));

        for (Map.Entry<String, AlgorithmInfo> entry : algorithms.entrySet()) {
            String algorithmName = entry.getKey();
            AlgorithmInfo info = entry.getValue();

            try {
                long executionTime = measureSortingTime(info.algorithm, arraySize, algorithmName);
                results.add(new BenchmarkResult(algorithmName, info.complexity, arraySize, executionTime));
                log.info("✅ {} completado en {} ms", algorithmName, executionTime / 1_000_000.0);
            } catch (Exception e) {
                log.error("❌ {} falló: {}", algorithmName, e.getMessage());
                // Guardamos tiempo -1 para indicar error
                results.add(new BenchmarkResult(algorithmName, info.complexity, arraySize, -1));
            }
        }

        return results;
    }

    /**
     * Mide el tiempo de ejecución de un algoritmo dado sobre un array de tamaño arraySize.
     *
     * ⚠️ Casos especiales:
     * - Tree Sort usando TreeSet pierde duplicados. Si se quieren preservar TODOS los elementos idénticos,
     *   hay que implementar un Binary Search Tree manual. Ahora mismo el resultado puede ser incorrecto
     *   si hay duplicados.
     * - Bitonic Sort está diseñado solo para tamaños potencia de 2. Si no lo son, el algoritmo NO ordena
     *   perfectamente. Solución: rellenar con ceros hasta la próxima potencia de 2 antes de benchmark.
     *
     * @param sortingAlgorithm método de ordenamiento
     * @param arraySize tamaño del array
     * @param algorithmName nombre del algoritmo (para logs)
     * @return tiempo total de ejecución en nanosegundos
     */
    private long measureSortingTime(Consumer<int[]> sortingAlgorithm, int arraySize, String algorithmName) {
        int[] baseArray = generateRandomArray(arraySize);

        // Casos especiales
        if (algorithmName.contains("Bitonic")) {
            int newSize = nextPowerOfTwo(arraySize);
            if (newSize != arraySize) {
                baseArray = Arrays.copyOf(baseArray, newSize);
            }
        }

        int[] arrayToSort = baseArray.clone();

        // Medir ejecución
        long start = System.nanoTime();
        sortingAlgorithm.accept(arrayToSort);
        long end = System.nanoTime();

        // Verificar correcto ordenamiento (excepto Tree Sort que pierde duplicados en esta versión)
        if (!algorithmName.contains("Tree")) {
            if (!isSorted(arrayToSort)) {
                throw new RuntimeException("Array no quedó ordenado correctamente");
            }
        }

        return end - start;
    }

    /**
     * Genera un arreglo aleatorio de tamaño size.
     */
    private int[] generateRandomArray(int size) {
        Random rand = new Random(42); // semilla fija para reproducibilidad
        return rand.ints(size, 0, size * 10).toArray();
    }

    /**
     * Verifica si un arreglo está ordenado ascendentemente.
     */
    private boolean isSorted(int[] arr) {
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < arr[i - 1]) return false;
        }
        return true;
    }

    /**
     * Calcula la siguiente potencia de 2 mayor o igual a n.
     */
    private int nextPowerOfTwo(int n) {
        if (n <= 0) return 1;
        return 1 << (32 - Integer.numberOfLeadingZeros(n - 1));
    }

    /**
     * Exporta resultados de benchmark a un archivo CSV.
     */
    public void exportResultsToCSV(List<BenchmarkResult> results, String filename) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.append("Método de ordenamiento,Complejidad,Tamaño,Tiempo (ns),Tiempo (ms)\n");
            for (BenchmarkResult r : results) {
                writer.append(String.format(Locale.US, "%s,%s,%d,%d,%.3f\n",
                        r.algorithmName,
                        r.complexity,
                        r.arraySize,
                        r.executionTimeNanos,
                        r.executionTimeMs));
            }
        }
        log.info("Resultados exportados a CSV: {}", filename);
    }

    // ------------------------------------------------------------------------------------
    // Métodos adicionales para el Seguimiento 1
    // ------------------------------------------------------------------------------------

    /**
     * Ordena productos académicos por año (ascendente) y luego por título (ascendente).
     */
    public List<ScientificRecord> sortScientificRecords(List<ScientificRecord> records) {
        List<ScientificRecord> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparing(
                        (ScientificRecord r) -> Optional.ofNullable(r.getYear()).orElse(0))
                .thenComparing(r -> Optional.ofNullable(r.getTitle()).orElse("")));
        return sorted;
    }

    /**
     * Obtiene los 15 autores más frecuentes en los registros.
     */
    public List<Map.Entry<String, Integer>> getTop15Authors(List<ScientificRecord> records) {
        Map<String, Integer> count = new HashMap<>();
        for (ScientificRecord record : records) {
            if (record.getAuthors() != null) {
                for (String author : record.getAuthors()) {
                    count.put(author, count.getOrDefault(author, 0) + 1);
                }
            }
        }
        return count.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(15)
                .toList();
    }
}