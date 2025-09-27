package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SortingAnalysisService {

    private final SortingAlgorithmsService sortingService;

    /**
     * Ordena los registros científicos por año y título (como pide el seguimiento)
     */
    public List<ScientificRecord> sortRecordsByYearAndTitle(List<ScientificRecord> records, String algorithm) {
        // Convertir a array de enteros para usar tus algoritmos
        // Aquí necesitarías adaptar tus algoritmos para trabajar con objetos
        // O crear una versión que ordene por criterios específicos

        List<ScientificRecord> sortedRecords = new ArrayList<>(records);

        // Ordenamiento personalizado: primero por año, luego por título
        sortedRecords.sort((r1, r2) -> {
            int yearComparison = Integer.compare(r1.getYear(), r2.getYear());
            if (yearComparison != 0) {
                return yearComparison;
            }
            return r1.getTitle().compareToIgnoreCase(r2.getTitle());
        });

        return sortedRecords;
    }

    /**
     * Encuentra los 15 autores con más apariciones
     */
    public List<Map.Entry<String, Long>> getTop15Authors(List<ScientificRecord> records) {
        return records.stream()
                .flatMap(record -> record.getAuthors().stream())
                .collect(Collectors.groupingBy(
                        author -> author,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .collect(Collectors.toList());
    }

    /**
     * Mide el tiempo de ejecución de cada algoritmo
     */
    public Map<String, Long> measureSortingTimes(int[] testData) {
        Map<String, Long> results = new LinkedHashMap<>();

        // TimSort
        int[] data = testData.clone();
        long start = System.nanoTime();
        sortingService.timSort(data);
        results.put("TimSort", System.nanoTime() - start);

        // Comb Sort
        data = testData.clone();
        start = System.nanoTime();
        sortingService.combSort(data);
        results.put("Comb Sort", System.nanoTime() - start);

        // Selection Sort
        data = testData.clone();
        start = System.nanoTime();
        sortingService.selectionSort(data);
        results.put("Selection Sort", System.nanoTime() - start);

        // Tree Sort
        data = testData.clone();
        start = System.nanoTime();
        sortingService.treeSort(data);
        results.put("Tree Sort", System.nanoTime() - start);

        // Pigeonhole Sort
        data = testData.clone();
        start = System.nanoTime();
        sortingService.pigeonholeSort(data);
        results.put("Pigeonhole Sort", System.nanoTime() - start);

        // Bucket Sort
        data = testData.clone();
        start = System.nanoTime();
        sortingService.bucketSort(data);
        results.put("Bucket Sort", System.nanoTime() - start);

        // QuickSort
        data = testData.clone();
        start = System.nanoTime();
        sortingService.quickSort(data);
        results.put("QuickSort", System.nanoTime() - start);

        // HeapSort
        data = testData.clone();
        start = System.nanoTime();
        sortingService.heapSort(data);
        results.put("HeapSort", System.nanoTime() - start);

        // Bitonic Sort
        data = testData.clone();
        start = System.nanoTime();
        sortingService.bitonicSort(data);
        results.put("Bitonic Sort", System.nanoTime() - start);

        // Gnome Sort
        data = testData.clone();
        start = System.nanoTime();
        sortingService.gnomeSort(data);
        results.put("Gnome Sort", System.nanoTime() - start);

        // Binary Insertion Sort
        data = testData.clone();
        start = System.nanoTime();
        sortingService.binaryInsertionSort(data);
        results.put("Binary Insertion Sort", System.nanoTime() - start);

        // Radix Sort
        data = testData.clone();
        start = System.nanoTime();
        sortingService.radixSort(data);
        results.put("Radix Sort", System.nanoTime() - start);

        return results;
    }
}