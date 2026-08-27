package model;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public abstract class ProductionCommand {
    private final String name;
    private int turnsRemaining;
    private final boolean isPopulationTask;
    private boolean isCanceled;

    protected transient GameMap contextMap;

    public ProductionCommand(String name, int turnsRemaining, boolean isPopulationTask) {
        this.name = name;
        this.turnsRemaining = turnsRemaining;
        this.isPopulationTask = isPopulationTask;
        this.isCanceled = false;
    }

    public String getName()              { return name; }
    public int    getTurnsRemaining()    { return turnsRemaining; }
    public boolean isPopulationTask()   { return isPopulationTask; }
    public boolean isCanceled()         { return isCanceled; }
    public void setContextMap(GameMap map) { this.contextMap = map; }

    public void decrementTurn() {
        if (!isCanceled) turnsRemaining--;
    }

    public boolean isCompleted() { return turnsRemaining <= 0; }
    public void cancel()         { this.isCanceled = true; }

    public abstract void execute();
    public abstract String getCommandType();


    public static class TechCommand extends ProductionCommand {
        private final String techId;

        private static final Map<String, java.util.function.Consumer<GameMap>> registry = new HashMap<>();

        public static void registerTechExecution(String techId, java.util.function.Consumer<GameMap> action) {
            registry.put(techId, action);
        }

        public TechCommand(String name, int turnsRemaining, String techId) {
            super(name, turnsRemaining, false);
            this.techId = techId;
        }

        public String getTechId() { return techId; }

        @Override public String getCommandType() { return "TECH"; }

        @Override
        public void execute() {
            var action = registry.get(techId);
            if (action != null) {
                action.accept(contextMap);
            }
        }
    }

    public static class UnitCommand extends ProductionCommand {
        private final UnitType unitType;

        public UnitCommand(String name, int turnsRemaining, UnitType unitType) {
            super(name, turnsRemaining, true);
            this.unitType = unitType;
        }

        public UnitType getUnitType() { return unitType; }

        @Override public String getCommandType() { return "UNIT"; }

        @Override
        public void execute() {
            TownHall th       = contextMap.getTownHall();
            Hex      spawnHex = contextMap.findEmptySpawnHex(th.getQ(), th.getR());
            int tq = spawnHex != null ? spawnHex.getQ() : th.getQ();
            int tr = spawnHex != null ? spawnHex.getR() : th.getR();

            contextMap.addUnit(UnitFactory.createUnit(unitType, tq, tr));
        }
    }

    public static class UpgradeTHCommand extends ProductionCommand {
        public UpgradeTHCommand(String name, int turnsRemaining) {
            super(name, turnsRemaining, false);
        }

        @Override public String getCommandType() { return "UPGRADE_TH"; }

        @Override
        public void execute() {
            contextMap.getTownHall().upgradeLevel();
        }
    }
}