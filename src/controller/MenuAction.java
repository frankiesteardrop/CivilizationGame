package controller;

public class MenuAction {
    private final String label;
    private final boolean isEnabled;
    private final String disabledReason;
    private final Runnable action;

    private String confirmationMessage = null;

    public MenuAction(String label, boolean isEnabled, Runnable action) {
        this(label, isEnabled, "Requirements not met", action);
    }

    public MenuAction(String label, boolean isEnabled, String disabledReason, Runnable action) {
        this.label = label;
        this.isEnabled = isEnabled;
        this.disabledReason = disabledReason;
        this.action = action;
    }

    public MenuAction setConfirmation(String message) {
        this.confirmationMessage = message;
        return this;
    }

    public String getLabel() { return label; }
    public boolean isEnabled() { return isEnabled; }
    public String getDisabledReason() { return disabledReason; }

    public boolean requiresConfirmation() { return confirmationMessage != null; }
    public String getConfirmationMessage() { return confirmationMessage; }

    public void execute() {
        if (isEnabled && action != null) {
            action.run();
        }
    }
}