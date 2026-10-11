package ezbuild.model;

public enum TicketStatus {
    OPEN("Open"), ANSWERED("Answered"), CLOSED("Closed");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
