package co.edu.uniquindio.proyectoAlgoritmos.reader;

import co.edu.uniquindio.proyectoAlgoritmos.mapper.*;

/**
 * Factory para crear lectores de datasets según la fuente
 */
public class DatasetReaderFactory {

    public static DatasetReader createReader(String source) {
        switch (source.toLowerCase()) {
            case "scopus":
                return new CSVDatasetReader(new ScopusFieldMapper(), "Scopus");
            case "dblp":
                return new CSVDatasetReader(new DBLPFieldMapper(), "DBLP");
            case "acm":
                return new CSVDatasetReader(new ACMFieldMapper(), "ACM");
            default:
                throw new IllegalArgumentException("Fuente no soportada: " + source);
        }
    }

    public static DatasetReader createReaderWithDelimiter(String source, String delimiter) {
        CSVDatasetReader reader = (CSVDatasetReader) createReader(source);
        reader.setDelimiter(delimiter);
        return reader;
    }

    public static DatasetReader createCustomReader(FieldMapper mapper, String sourceName) {
        return new CSVDatasetReader(mapper, sourceName);
    }

    public static DatasetReader createCustomReader(FieldMapper mapper, String sourceName, String delimiter) {
        return new CSVDatasetReader(mapper, sourceName, delimiter);
    }
}