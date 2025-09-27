package co.edu.uniquindio.proyectoAlgoritmos.reader;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;

import java.io.IOException;
import java.util.List;

/**
 * Interface para lectores de diferentes formatos de datasets científicos
 */
public interface DatasetReader {


    /**
     * Lee un archivo y retorna una lista de registros científicos
     * @param filePath Ruta del archivo a leer
     * @return Lista de registros científicos
     * @throws IOException Si hay error al leer el archivo
     */
    List<ScientificRecord> readDataset(String filePath) throws IOException;

    /**
     * Valida si el archivo es compatible con este lector
     * @param filePath Ruta del archivo
     * @return true si es compatible, false en caso contrario
     */
    boolean isCompatible(String filePath);

    /**
     * Retorna el nombre de la fuente de datos que maneja este lector
     * @return Nombre de la fuente (ej: "Scopus", "DBLP")
     */
    String getSourceName();
}