package model.mission;

public enum MissionStateEnum implements MissionState {
    AVAILABLE(true, false, "Available"),
    ACTIVE(false, false, "Active"),
    READY_TO_DELIVER(false, true, "Ready to Deliver"),
    COMPLETED(false, false, "Completed"),
    FAILED(false, false, "Failed"),
    CANCELLED(false, false, "Cancelled");

    private final boolean accept;
    private final boolean deliver;
    private final String display;

    MissionStateEnum(boolean accept, boolean deliver, String display) {
        this.accept = accept;
        this.deliver = deliver;
        this.display = display;
    }

    @Override public boolean canAccept() { return accept; }
    @Override public boolean canDeliver() { return deliver; }
    @Override public String getDisplayName() { return display; }
}