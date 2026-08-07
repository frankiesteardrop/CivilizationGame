package model;

public abstract class ProductionCommand {
    private final String name;
    private int turnsRemaining;
    private final boolean isPopulationTask;
    private boolean isCanceled;

    public ProductionCommand(String name, int turnsRemaining, boolean isPopulationTask) {
        this.name = name;
        this.turnsRemaining = turnsRemaining;
        this.isPopulationTask = isPopulationTask;
        this.isCanceled = false;
    }

    public String getName() { return name; }
    public int getTurnsRemaining() { return turnsRemaining; }
    public boolean isPopulationTask() { return isPopulationTask; }
    public boolean isCanceled() { return isCanceled; }

    public void decrementTurn() {
        if (!isCanceled) {
            turnsRemaining--;
        }
    }

    public boolean isCompleted() { return turnsRemaining <= 0; }

    public void cancel() {
        this.isCanceled = true;
    }

    public abstract void execute();
}