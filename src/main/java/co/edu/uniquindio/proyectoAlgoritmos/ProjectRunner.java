package co.edu.uniquindio.proyectoAlgoritmos;

import co.edu.uniquindio.proyectoAlgoritmos.dto.ProcessingResultDto;
import co.edu.uniquindio.proyectoAlgoritmos.service.DataUnificationService;
import co.edu.uniquindio.proyectoAlgoritmos.service.SortingAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProjectRunner implements CommandLineRunner {

    private final DataUnificationService dataUnificationService;
    private final SortingAnalysisService sortingAnalysisService;

    @Override
    public void run(String... args) throws Exception {
        log.info("=== INICIANDO PROYECTO ALGORITMOS - ANÁLISIS BIBLIOMÉTRICO ===");

        // Ejecutar proceso automático de unificación con DBLP + OpenAlex
        log.info("Iniciando descarga y unificación desde DBLP + OpenAlex...");

        CompletableFuture<ProcessingResultDto> future =
                dataUnificationService.processAndUnifyData("generative artificial intelligence");

        ProcessingResultDto result = future.get();
        printBibliometricResults(result);

        // 🔥 Seguimiento 1
        log.info("=== INICIANDO ANÁLISIS DE MÉTODOS DE ORDENAMIENTO ===");

        int[] testData = generateTestData(5000); // tamaño configurable
        Map<String, Long> sortingTimes = sortingAnalysisService.measureSortingTimes(testData);

        // Mostrar
        printSortingResults(sortingTimes);

        // Top 15 autores
        var topAuthors = sortingAnalysisService.getTop15Authors(result.getUnifiedRecords());
        System.out.println("\n=== TOP 15 AUTORES POR APARICIONES ===");
        topAuthors.forEach(a -> System.out.printf("   • %-30s %d%n", a.getKey(), a.getValue()));

        generateAuthorsBarChart(topAuthors, "src/main/resources/data/output/top_authors.png");
        exportTopAuthorsToCsv(topAuthors, "src/main/resources/data/output/top_authors.csv");

        log.info("✅ Comparativa autores: top_authors.png y top_authors.csv generados");

        // Guardar gráfico
        generateBarChart(sortingTimes, "src/main/resources/data/output/sorting_times.png");

        // Guardar CSV
        exportSortingTimesToCsv(sortingTimes, "src/main/resources/data/output/sorting_times.csv");

        log.info("✅ Resultados gráficos: sorting_times.png");
        log.info("✅ Resultados tabla: sorting_times.csv");
        log.info("=== PROCESO COMPLETADO ===");
    }

    private void printBibliometricResults(ProcessingResultDto result) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("           RESULTADO DEL PROCESO BIBLIOMÉTRICO");
        System.out.println("=".repeat(60));

        System.out.println("📊 Estado: " + result.getStatus());
        System.out.println("💬 Mensaje: " + result.getMessage());

        if (result.getStats() != null) {
            var stats = result.getStats();
            System.out.println("\n📈 ESTADÍSTICAS:");
            System.out.println("   • Total procesados: " + stats.getTotalRecordsProcessed());
            System.out.println("   • Registros únicos: " + stats.getUniqueRecords());
            System.out.println("   • Duplicados encontrados: " + stats.getDuplicatesFound());
            System.out.println("   • Registros de DBLP: " + stats.getRecordsFromSource1());
            System.out.println("   • Registros de OpenAlex: " + stats.getRecordsFromSource2());
            System.out.println("   • Porcentaje duplicados: " + String.format("%.2f%%", stats.getDuplicatePercentage()));
        }

        System.out.println("\n📁 ARCHIVOS GENERADOS:");
        if (result.getUnifiedFilePath() != null) {
            System.out.println("   ✅ Registros unificados: " + result.getUnifiedFilePath());
        }
        if (result.getDuplicatesFilePath() != null) {
            System.out.println("   ✅ Registros duplicados: " + result.getDuplicatesFilePath());
        }

        if (result.getStartTime() != null && result.getEndTime() != null) {
            System.out.println("\n⏱️  TIEMPO DE EJECUCIÓN:");
            System.out.println("   • Inicio: " + result.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            System.out.println("   • Fin: " + result.getEndTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        System.out.println("\n" + "=".repeat(60));
    }

    private void printSortingResults(Map<String, Long> sortingTimes) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("           RESULTADOS ORDENAMIENTO (Seguimiento 1)");
        System.out.println("=".repeat(60));

        sortingTimes.forEach((alg, time) -> {
            System.out.printf("   • %-20s  %10.4f ms%n", alg, time / 1_000_000.0);
        });

        System.out.println("=".repeat(60));
    }

    private int[] generateTestData(int size) {
        int[] data = new int[size];
        for (int i = 0; i < size; i++) {
            data[i] = (int) (Math.random() * 100000);
        }
        return data;
    }

    private void generateBarChart(Map<String, Long> sortingTimes, String outputPath) {
        int width = 1000;
        int height = 600;
        int padding = 60;
        int barWidth = (width - 2 * padding) / sortingTimes.size();

        long maxTime = sortingTimes.values().stream().mapToLong(Long::longValue).max().orElse(1);

        BufferedImage chartImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = chartImage.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        g2d.setColor(Color.BLACK);
        g2d.drawLine(padding, height - padding, width - padding, height - padding); // eje X
        g2d.drawLine(padding, padding, padding, height - padding); // eje Y

        int x = padding + 10;
        int i = 0;
        for (Map.Entry<String, Long> entry : sortingTimes.entrySet()) {
            String alg = entry.getKey();
            long time = entry.getValue();

            int barHeight = (int) ((double) time / maxTime * (height - 2 * padding));

            g2d.setColor(Color.getHSBColor((float) i / sortingTimes.size(), 0.7f, 0.9f));
            g2d.fillRect(x, height - padding - barHeight, barWidth - 10, barHeight);

            g2d.setColor(Color.BLACK);
            g2d.drawString(alg, x, height - padding + 15);
            g2d.drawString(String.format("%.1f ms", time / 1_000_000.0), x, height - padding - barHeight - 5);

            x += barWidth;
            i++;
        }

        g2d.dispose();

        try {
            File outFile = new File(outputPath);
            outFile.getParentFile().mkdirs();
            ImageIO.write(chartImage, "png", outFile);
        } catch (Exception e) {
            log.error("Error generando el diagrama de barras: {}", e.getMessage());
        }
    }

    private void exportSortingTimesToCsv(Map<String, Long> sortingTimes, String outputPath) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("Método de ordenamiento,Tamaño,Tiempo (ms)");
            for (Map.Entry<String, Long> entry : sortingTimes.entrySet()) {
                writer.printf("%s,%d,%.4f%n", entry.getKey(), 5000, entry.getValue() / 1_000_000.0);
            }
        } catch (Exception e) {
            log.error("Error escribiendo CSV de resultados: {}", e.getMessage());
        }
    }

    private void generateAuthorsBarChart(
            java.util.List<Map.Entry<String, Long>> topAuthors, String outputPath) {

        int width = 1000;
        int height = 600;
        int padding = 60;
        int barWidth = (width - 2 * padding) / topAuthors.size();

        long maxCount = topAuthors.stream().mapToLong(Map.Entry::getValue).max().orElse(1);

        BufferedImage chartImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = chartImage.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        g2d.drawLine(padding, height - padding, width - padding, height - padding);
        g2d.drawLine(padding, padding, padding, height - padding);

        int x = padding + 10;
        int i = 0;
        for (Map.Entry<String, Long> entry : topAuthors) {
            String author = entry.getKey();
            long count = entry.getValue();
            int barHeight = (int) ((double) count / maxCount * (height - 2 * padding));

            g2d.setColor(Color.getHSBColor((float) i / topAuthors.size(), 0.6f, 0.9f));
            g2d.fillRect(x, height - padding - barHeight, barWidth - 10, barHeight);

            g2d.setColor(Color.BLACK);
            g2d.drawString(author, x, height - padding + 15);
            g2d.drawString(String.valueOf(count), x, height - padding - barHeight - 5);

            x += barWidth;
            i++;
        }

        g2d.dispose();

        try {
            File outFile = new File(outputPath);
            outFile.getParentFile().mkdirs();
            ImageIO.write(chartImage, "png", outFile);
        } catch (Exception e) {
            log.error("Error generando gráfico de autores: {}", e.getMessage());
        }
    }

    private void exportTopAuthorsToCsv(java.util.List<Map.Entry<String, Long>> topAuthors, String outputPath) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("Autor,Apariciones");
            for (Map.Entry<String, Long> entry : topAuthors) {
                writer.printf("%s,%d%n", entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            log.error("Error guardando CSV de autores: {}", e.getMessage());
        }
    }
}