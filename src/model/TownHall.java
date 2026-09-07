package model;

import java.util.Queue;

public class TownHall extends Building {

    private final int q;
    private final int r;
    private Empire empire;

    public TownHall(int q, int r) {
        super(BuildingType.TOWN_HALL.getMaxWorkers());
        this.q = q;
        this.r = r;

        this.setDefense(10);
        this.maxHp = 200;
        this.hp    = 200;

        this.empire = new Empire(null);
    }

    public void setEmpire(Empire empire) { this.empire = empire; }
    public Empire getEmpire() { return empire; }

    @Override
    public BuildingType getType() { return BuildingType.TOWN_HALL; }

    public int getQ() { return q; }
    public int getR() { return r; }

    // 🔴 تمام متدهای مربوط به منابع، صرفاً یک واسط (Facade) به امپراتوری هستند
    public Inventory getInventory() { return empire.getInventory(); }
    public void produceSafeguardResources() { empire.produceSafeguardResources(); }
    public int getLevel() { return empire.getLevel(); }

    public void upgradeLevel() {
        empire.upgradeLevel();
        if (empire.getLevel() == 2) {
            this.heal(50);
        }
    }

    public Queue<ProductionCommand> getProductionQueue() { return empire.getProductionQueue(); }
    public boolean isProductionQueueEmpty() { return empire.isProductionQueueEmpty(); }
    public boolean queueCommand(ProductionCommand command) { return empire.queueCommand(command); }
    public void cancelCurrentProduction() { empire.cancelCurrentProduction(); }
    public void advanceProductionQueue(boolean isStarving) { empire.advanceProductionQueue(isStarving); }

    public int getHappiness() { return empire.getHappiness(); }
    public void addHappiness(int amount) { empire.addHappiness(amount); }

    public boolean isStoneMineUnlocked() { return empire.isStoneMineUnlocked(); }
    public void setStoneMineUnlocked(boolean v) { empire.setStoneMineUnlocked(v); }
    public boolean isIronMineUnlocked() { return empire.isIronMineUnlocked(); }
    public void setIronMineUnlocked(boolean v) { empire.setIronMineUnlocked(v); }
    public boolean isSettlementUnlocked() { return empire.isSettlementUnlocked(); }
    public boolean isSteelToolsUnlocked() { return empire.isSteelToolsUnlocked(); }
    public boolean isProfessionalToolsUnlocked() { return empire.isProfessionalToolsUnlocked(); }
    public void setSteelToolsUnlocked(boolean v) { empire.setSteelToolsUnlocked(v); }
    public boolean isSeafaringUnlocked() { return empire.isSeafaringUnlocked(); }
    public void setSeafaringUnlocked(boolean v) { empire.setSeafaringUnlocked(v); }

    public boolean isDefensiveArchUnlocked() { return empire.isDefensiveArchUnlocked(); }

    public void applyDefensiveArchitecture() {
        empire.applyDefensiveArchitecture();
        this.setMaxHp(350);
        this.setDefense(30);
        this.heal(350);
        GameEventDispatcher.fireNotification(
                "🏰 Defensive Architecture active! TH defense: 10→30, max HP: 200→350");
    }

    public int getDiscountedDocks() { return empire.getDiscountedDocks(); }
    public void addDiscountedDock() { empire.addDiscountedDock(); }
    public void consumeDiscountedDock() { empire.consumeDiscountedDock(); }
}