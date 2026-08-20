package model;

import java.util.LinkedList;
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

    // ─── Concrete Commands ────────────────────────────────────────────────────

    public static class TechCommand extends ProductionCommand {
        private final String techId;

        public TechCommand(String name, int turnsRemaining, String techId) {
            super(name, turnsRemaining, false);
            this.techId = techId;
        }

        public String getTechId() { return techId; }

        @Override public String getCommandType() { return "TECH"; }

        @Override
        public void execute() {
            TownHall th = contextMap.getTownHall();
            switch (techId) {
                case "STONE_MINE"     -> th.setStoneMineUnlocked(true);
                case "IRON_MINE"      -> th.setIronMineUnlocked(true);
                case "PROF_TOOLS"     -> th.setSteelToolsUnlocked(true);
                case "SEAFARING"      -> th.setSeafaringUnlocked(true);
                case "DEFENSIVE_ARCH" -> {
                    th.applyDefensiveArchitecture();
                    buildWallsAroundTownHall();
                }
            }
        }

        private void buildWallsAroundTownHall() {
            TownHall th    = contextMap.getTownHall();
            Hex      thHex = contextMap.getHexAt(th.getQ(), th.getR());
            if (thHex == null) return;
            for (int i = 0; i < 6; i++) {
                thHex.setWall(i, true, 100);
                Hex neighbor = contextMap.getNeighbor(thHex, i);
                if (neighbor != null) neighbor.setWall((i + 3) % 6, true, 100);
            }
            GameEventDispatcher.fireNotification("🏰 Defensive walls built around Town Hall!");
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

            // M3: Notification هنگام رسیدن به سقف یونیت نظامی + کاهش Happiness
            if (unitType == UnitType.SWORDSMAN
                    || unitType == UnitType.ARCHER
                    || unitType == UnitType.CAVALRY) {
                if (contextMap.getMilitaryUnitCount() >= contextMap.getMilitaryUnitCap()) {
                    th.addHappiness(-1);
                    // M3: اطلاع‌رسانی واضح به بازیکن طبق spec
                    GameEventDispatcher.fireNotification(
                            "⚔️ Military Unit Cap reached! (Cap: "
                                    + contextMap.getMilitaryUnitCap() + ") -1 Happiness.");
                }
            }
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