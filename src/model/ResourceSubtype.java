package model;


public enum ResourceSubtype {
    NONE("None", ""),
    WHEAT("Wheat", "Wh"),
    RICE("Rice", "Ri"),
    CATTLE("Cattle", "Ca"),
    SHEEP("Sheep", "Sh");

    private final String displayName;
    private final String shortSymbol;

    ResourceSubtype(String displayName, String shortSymbol) {
        this.displayName = displayName;
        this.shortSymbol = shortSymbol;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortSymbol() {
        return shortSymbol;
    }
}