package co.edu.uniquindio.proyectoAlgoritmos.reader.api;

import co.edu.uniquindio.proyectoAlgoritmos.model.ScientificRecord;

import java.util.List;

public interface ApiDatasetReader {
    String getSourceName();
    List<ScientificRecord> searchRecords(String searchQuery) throws Exception;
}