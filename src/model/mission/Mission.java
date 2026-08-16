package model.mission;

public class Mission {
    private MissionStateEnum state;
    private int turnsRemaining;
    private int progress;

    public Mission(int turnsRemaining) {
        this.state = MissionStateEnum.AVAILABLE;
        this.turnsRemaining = turnsRemaining;
        this.progress = 0;
    }

    public MissionStateEnum getState() { return state; }
    public void setState(MissionStateEnum state) { this.state = state; }

    public int getTurnsRemaining() { return turnsRemaining; }
    public void decrementTurn() { if (turnsRemaining > 0) turnsRemaining--; }

    public int getProgress() { return progress; }
    public void addProgress(int p) { this.progress += p; }
}