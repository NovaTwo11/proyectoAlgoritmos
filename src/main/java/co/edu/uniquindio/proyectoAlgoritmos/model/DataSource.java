package co.edu.uniquindio.proyectoAlgoritmos.model;

public enum DataSource {
    DBLP("DBLP Computer Science Bibliography"),
    OPENALEX("OpenAlex"),
    UNKNOWN("Unknown Source");

    private final String displayName;

    DataSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}