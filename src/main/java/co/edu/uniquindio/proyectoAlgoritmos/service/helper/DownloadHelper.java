package co.edu.uniquindio.proyectoAlgoritmos.service.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

@Slf4j
@Service
public class DownloadHelper {

    public File waitAndRenameLatestBib(String dir, String sourcePrefix) {
        File folder = new File(dir);
        long start = System.currentTimeMillis();
        File last = null;
        while (System.currentTimeMillis() - start < 180000) { // 180s timeout (antes 60s)
            File[] bibs = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".bib"));
            File[] partial = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".crdownload") || n.toLowerCase().endsWith(".part"));
            if (partial != null && partial.length > 0) {
                sleep(800); // leve aumento
                continue;
            }
            if (bibs != null && bibs.length > 0) {
                Arrays.sort(bibs, Comparator.comparingLong(File::lastModified).reversed());
                last = bibs[0];
                break;
            }
            sleep(700);
        }
        if (last == null) {
            log.warn("No se detectó archivo .bib descargado en {} dentro del tiempo de espera", dir);
            return null;
        }
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String newName = sourcePrefix + "_" + ts + ".bib";
        Path dest = new File(folder, newName).toPath();
        try {
            Files.move(last.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Renombrado {} -> {}", last.getName(), dest.getFileName());
            return dest.toFile();
        } catch (Exception e) {
            log.warn("No se pudo renombrar {}, se mantiene nombre original. Causa: {}", last.getName(), e.getMessage());
            return last;
        }
    }

    // Nuevo: esperar un archivo específico (por nombre exacto) y renombrarlo con prefijo fuente
    public File waitAndRenameSpecificBib(String dir, String expectedFileName, String sourcePrefix) {
        File f = waitForSpecificFile(dir, expectedFileName, 180_000); // 180s
        if (f == null) return null;
        return renameWithPrefix(f, sourcePrefix);
    }

    // Espera hasta que exista el archivo esperado y no haya temporales activos
    public File waitForSpecificFile(String dir, String expectedFileName, long timeoutMs) {
        File folder = new File(dir);
        if (!folder.exists()) {
            log.warn("Directorio de descargas no existe: {}", dir);
            return null;
        }
        File expected = new File(folder, expectedFileName);
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            boolean hasTemp = hasTempDownloads(folder);
            if (expected.exists() && !hasTemp) {
                log.info("Detectado archivo esperado: {} (sin temporales activos)", expected.getName());
                return expected;
            }
            sleep(700);
        }
        log.warn("Timeout esperando {} en {}", expectedFileName, dir);
        return expected.exists() ? expected : null;
    }

    public File renameWithPrefix(File file, String sourcePrefix) {
        if (file == null) return null;
        File folder = file.getParentFile();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String newName = sourcePrefix + "_" + ts + ".bib";
        Path dest = new File(folder, newName).toPath();
        try {
            Files.move(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Renombrado {} -> {}", file.getName(), dest.getFileName());
            return dest.toFile();
        } catch (Exception e) {
            log.warn("No se pudo renombrar {}: {}", file.getName(), e.getMessage());
            return file;
        }
    }

    private boolean hasTempDownloads(File folder) {
        File[] partial = folder.listFiles((d, n) -> {
            String s = n.toLowerCase();
            return s.endsWith(".crdownload") || s.endsWith(".part") || s.endsWith(".tmp") || s.endsWith(".download");
        });
        return partial != null && partial.length > 0;
    }

    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
