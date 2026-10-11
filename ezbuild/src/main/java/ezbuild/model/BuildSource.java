package ezbuild.model;

public enum BuildSource {
    MANUAL("Manual"), AI("AI assistant");

    private final String label;

    BuildSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
