package model;

public abstract class ProductionCommand {
    private final String name;
    private int turnsRemaining;
    private final boolean isPopulationTask;

    public ProductionCommand(String name, int turnsRemaining, boolean isPopulationTask) {
        this.name = name;
        this.turnsRemaining = turnsRemaining;
        this.isPopulationTask = isPopulationTask;
    }

    public String getName() { return name; }
    public int getTurnsRemaining() { return turnsRemaining; }
    public boolean isPopulationTask() { return isPopulationTask; }
    public void decrementTurn() { turnsRemaining--; }
    public boolean isCompleted() { return turnsRemaining <= 0; }


    public abstract void execute();
}