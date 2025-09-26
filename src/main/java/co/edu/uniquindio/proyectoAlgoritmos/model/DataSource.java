package co.edu.uniquindio.proyectoAlgoritmos.model;

public enum DataSource {
    ACM("ACM Digital Library"),
    SAGE("SAGE Journals"),
    SCIENCE_DIRECT("ScienceDirect"),
    UNKNOWN("Unknown Source");

    private final String displayName;

    DataSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
