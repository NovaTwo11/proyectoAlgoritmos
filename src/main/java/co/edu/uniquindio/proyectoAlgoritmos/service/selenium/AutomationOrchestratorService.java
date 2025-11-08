package co.edu.uniquindio.proyectoAlgoritmos.service.selenium;

import co.edu.uniquindio.proyectoAlgoritmos.config.SeleniumConfig;
import co.edu.uniquindio.proyectoAlgoritmos.service.bibtex.BibTeXUnificationService;
import co.edu.uniquindio.proyectoAlgoritmos.service.scraper.ACMDigitalLibraryScraper;
import co.edu.uniquindio.proyectoAlgoritmos.service.scraper.WebOfScienceScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationOrchestratorService {

    private final SeleniumConfig cfg;
    private final WebDriverService driverFactory;
    private final ACMDigitalLibraryScraper acm;
    private final WebOfScienceScraper wos;
    private final BibTeXUnificationService unifier;

    @Value("${automation.dendrogramas.output-dir:src/main/resources/data/dendrogramas}")
    private String dendroOutDir;


    public ResponseEntity<String> downloadArticles(String query) {
        WebDriver driver = null;
        try {
            String effectiveQuery = (query != null && !query.isBlank()) ? query : cfg.getSearchQuery();
            String last = readLastQuery();
            boolean sameQuery = last != null && last.equalsIgnoreCase(effectiveQuery);

            if (!sameQuery) {
                // Limpiar carpetas indicadas
                cleanDir("src/main/resources/data/normalized");
                cleanDir("src/main/resources/data/output");
                cleanDir(cfg.getDownloadDirectory()); // src/main/resources/downloads
                cleanDir(dendroOutDir); // limpiar también dendrogramas
                writeLastQuery(effectiveQuery);
            } else {
                log.info("La consulta es la misma que la última ejecutada; se omite limpieza de carpetas y descarga si ya existen archivos.");
            }

            boolean needsDownload = !sameQuery || isEmptyDir(cfg.getDownloadDirectory());
            if (needsDownload) {
                driver = driverFactory.createChromeDriver();
                acm.download(driver, effectiveQuery);
                wos.download(driver, effectiveQuery);
            } else {
                log.info("Se detectan archivos previos en {} y la búsqueda no cambió; saltando descarga.", cfg.getDownloadDirectory());
            }
            return ResponseEntity.ok("Proceso completado exitosamente.");
        } catch (Exception e) {
            log.error("Error durante la descarga de artículos: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } finally {
            if (driver != null) try { driver.quit(); } catch (Exception ignore) {}
            downloadAll();
        }
    }



    public void downloadAll () {
        try {
            // Directorio de salida principal: data/output
            String outDir = "src/main/resources/data/output";
            try {
                Path p = Paths.get(outDir);
                if (!Files.exists(p)) Files.createDirectories(p);
            } catch (Exception e) {
                log.warn("No se pudo crear el directorio de salida {}: {}", outDir, e.getMessage());
            }
            // Exportar .bib solicitados en data/output
            String unified = outDir + "/resultados_unificados.bib";
            String dups = outDir + "/resultados_duplicados.bib";
            var res = unifier.processDownloaded(cfg.getDownloadDirectory(), unified, dups);
            log.info("Resumen final: únicos={} | duplicados={}", res.getOrDefault("unified", java.util.Collections.emptyList()).size(), res.getOrDefault("duplicates", java.util.Collections.emptyList()).size());
        } catch (Exception e) {
            log.error("Error ejecutando Requerimiento 1: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private String readLastQuery() {
        try {
            Path f = Paths.get("src/main/resources/data/last_query.txt");
            if (Files.exists(f)) {
                return Files.readString(f, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            log.debug("No se pudo leer last_query.txt: {}", e.getMessage());
        }
        return null;
    }

    private void writeLastQuery(String q) {
        try {
            Path dir = Paths.get("src/main/resources/data");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path f = dir.resolve("last_query.txt");
            Files.writeString(f, q == null ? "" : q, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Última búsqueda persistida en {}", f.toAbsolutePath());
        } catch (Exception e) {
            log.warn("No se pudo escribir last_query.txt: {}", e.getMessage());
        }
    }

    private void cleanDir(String dir) {
        try {
            Path p = Paths.get(dir);
            if (!Files.exists(p)) { Files.createDirectories(p); return; }
            try (var walk = Files.walk(p)) {
                walk.sorted(Comparator.reverseOrder())
                        .filter(pp -> !pp.equals(p)) // conservar el directorio raíz
                        .forEach(pp -> {
                            try { Files.deleteIfExists(pp); } catch (IOException ignore) {}
                        });
            }
            log.info("Directorio limpiado: {}", p.toAbsolutePath());
        } catch (Exception e) {
            log.warn("No se pudo limpiar {}: {}", dir, e.getMessage());
        }
    }

    private boolean isEmptyDir(String dir) {
        try {
            Path p = Paths.get(dir);
            if (!Files.exists(p)) return true;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
                return !ds.iterator().hasNext();
            }
        } catch (Exception e) {
            return true;
        }
    }
}
