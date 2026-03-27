package papervalidation.domain.sru;

public enum SearchObjectCategory {
    PIW("익수자"),
    RAFT("구명정"),
    POWERBOAT("동력선"),
    SAILBOAT("범선"),
    SHIP("대형 선박");

    private final String label;

    SearchObjectCategory(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
