package ezbuild.dto;

public enum UseCase {
    GAMING("Gaming"), OFFICE("Office / study"), VIDEO_EDITING("Video editing"), RENDER_3D("3D rendering"),
    AI_ML("AI / machine learning"), STREAMING("Streaming"), GENERAL("General use");

    private final String label;

    UseCase(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
