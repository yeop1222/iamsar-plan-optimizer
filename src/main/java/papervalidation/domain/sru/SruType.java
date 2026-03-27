package papervalidation.domain.sru;

public enum SruType {
    LARGE("Large (1000t+)", false),
    MEDIUM("Medium (300t+)", false),
    SMALL("Small (<300t)", false),
    BOAT("Boat (Special)", false),
    ROTARY("Rotary Wing", true),
    FIXED("Fixed Wing", true);

    private final String label;
    private final boolean aircraft;

    SruType(String label, boolean aircraft) {
        this.label = label;
        this.aircraft = aircraft;
    }

    public String getLabel() { return label; }
    public boolean isAircraft() { return aircraft; }
    public boolean isSurface() { return !aircraft; }
}
