package model.mission;

import model.state.mission.AvailableState;
import model.state.mission.MissionState;

public class Mission {
    private MissionState state;

    // اصلاح C1: goal باید transient باشد چون MissionGoal یک anonymous inner class است
    // که Gson نمی‌تواند آن را serialize کند. پس از Load در SaveLoadController بازسازی می‌شود.
    private transient MissionGoal goal;

    private int turnsRemaining;
    private int progress;

    public Mission(MissionGoal goal) {
        this.goal = goal;
        this.state = new AvailableState(); // پترن استیت
        this.turnsRemaining = goal.getInitialTurns();
        this.progress = 0;
    }

    public MissionState getState() { return state; }
    public void setState(MissionState state) { this.state = state; }
    public MissionGoal getGoal() { return goal; }

    // اصلاح C1: متد جدید برای بازسازی goal پس از Load (چون transient است)
    public void setGoal(MissionGoal goal) { this.goal = goal; }

    public int getTurnsRemaining() { return turnsRemaining; }
    public void decrementTurn() { if (turnsRemaining > 0) turnsRemaining--; }
    public int getProgress() { return progress; }
    public void addProgress(int p) { this.progress += p; }
}