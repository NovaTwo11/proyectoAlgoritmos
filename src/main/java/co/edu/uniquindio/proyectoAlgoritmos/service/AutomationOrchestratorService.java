package co.edu.uniquindio.proyectoAlgoritmos.service;

import co.edu.uniquindio.proyectoAlgoritmos.config.SeleniumConfig;
import co.edu.uniquindio.proyectoAlgoritmos.service.bibtex.BibTeXUnificationService;
import co.edu.uniquindio.proyectoAlgoritmos.service.scraper.ACMDigitalLibraryScraper;
import co.edu.uniquindio.proyectoAlgoritmos.service.scraper.WebOfScienceScraper;
import co.edu.uniquindio.proyectoAlgoritmos.service.selenium.WebDriverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationOrchestratorService {

    private final SeleniumConfig cfg;
    private final WebDriverService driverFactory;
    private final ACMDigitalLibraryScraper acm;
    private final WebOfScienceScraper wos;
    private final BibTeXUnificationService unifier;


    public void downloadArticles(String query) {
        WebDriver driver = null;
        try {
            //driver = driverFactory.createChromeDriver();
            //acm.download(driver, query);
            //wos.download(driver, query);
        } catch (Exception e) {
            log.error("Error durante la descarga de artículos: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            if (driver != null) try { driver.quit(); } catch (Exception ignore) {}
            executeRequirement1();
        }
    }


    public void executeRequirement1() {
        executeRequirement1(null);
    }

    public void executeRequirement1(String query) {
        WebDriver d = null;
        try {
            // Descargas (ACM / Web of Science) deben haberse realizado a cfg.getDownloadDirectory()

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
        } finally {
            if (d != null) try { d.quit(); } catch (Exception ignore) {}
        }
    }
}
