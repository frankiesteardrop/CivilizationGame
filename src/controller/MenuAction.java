package controller;

public class MenuAction {
    private final String label;
    private final boolean isEnabled;
    private final Runnable action;

    public MenuAction(String label, boolean isEnabled, Runnable action) {
        this.label = label;
        this.isEnabled = isEnabled;
        this.action = action;
    }

    public String getLabel() { return label; }
    public boolean isEnabled() { return isEnabled; }

    public void execute() {
        if (isEnabled && action != null) {
            action.run();
        }
    }
}