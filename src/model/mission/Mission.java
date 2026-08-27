package model.mission;

import model.state.mission.AvailableState;
import model.state.mission.MissionState;

public class Mission {
    private MissionState state;

    private transient MissionGoal goal;

    private int turnsRemaining;
    private int progress;

    public Mission(MissionGoal goal) {
        this.goal = goal;
        this.state = new AvailableState();
        this.turnsRemaining = goal.getInitialTurns();
        this.progress = 0;
    }

    public MissionState getState() { return state; }
    public void setState(MissionState state) { this.state = state; }
    public MissionGoal getGoal() { return goal; }

    public void setGoal(MissionGoal goal) { this.goal = goal; }

    public int getTurnsRemaining() { return turnsRemaining; }
    public void decrementTurn() { if (turnsRemaining > 0) turnsRemaining--; }
    public int getProgress() { return progress; }
    public void addProgress(int p) { this.progress += p; }
}