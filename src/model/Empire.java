package model;

import java.util.LinkedList;
import java.util.Queue;

public class Empire {
    private String ownerId;
    private int level;
    private final Inventory inventory;
    private final Queue<ProductionCommand> productionQueue;

    private boolean stoneMineUnlocked;
    private boolean ironMineUnlocked;
    private boolean steelToolsUnlocked;
    private boolean seafaringUnlocked;
    private boolean defensiveArchUnlocked;

    private int happiness;
    private int discountedDocks = 0;

    public Empire(String ownerId) {
        this.ownerId = ownerId;
        this.level = 1;
        this.inventory = new Inventory();
        this.productionQueue = new LinkedList<>();
        this.happiness = 0;

        // اعمال منابع اولیه در لحظه ساخت امپراتوری
        inventory.applyStartingResources();
    }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public int getLevel() { return level; }
    public void upgradeLevel() {
        level++;
        if (level == 2) {
            inventory.upgradeToLevel2();
        } else if (level == 3) {
            inventory.upgradeToLevel3();
        }
    }

    public Inventory getInventory() { return inventory; }

    public Queue<ProductionCommand> getProductionQueue() { return productionQueue; }
    public boolean isProductionQueueEmpty() { return productionQueue.isEmpty(); }

    public boolean queueCommand(ProductionCommand command) {
        if (!productionQueue.isEmpty()) return false;
        productionQueue.offer(command);
        return true;
    }

    public void cancelCurrentProduction() {
        if (!productionQueue.isEmpty()) {
            ProductionCommand cmd = productionQueue.peek();
            if (cmd != null) {
                cmd.cancel();
                productionQueue.poll();
                GameEventDispatcher.fireNotification("🚫 Production Canceled! Resources lost.");
            }
        }
    }

    public void advanceProductionQueue(boolean isStarving) {
        if (productionQueue.isEmpty()) return;
        ProductionCommand cmd = productionQueue.peek();
        if (cmd == null || cmd.isCanceled()) { productionQueue.poll(); return; }
        if (isStarving && cmd.isPopulationTask()) return;

        cmd.decrementTurn();
        if (cmd.isCompleted()) {
            productionQueue.poll();
            cmd.execute();
            GameEventDispatcher.fireProductionCompleted(cmd.getName());
        }
    }

    public int getHappiness() { return happiness; }
    public void addHappiness(int amount) {
        this.happiness = Math.max(-20, Math.min(20, this.happiness + amount));
    }

    public void produceSafeguardResources() {
        inventory.addResource(ResourceType.FOOD, GameConfig.SAFEGUARD_FOOD_AMOUNT);
        inventory.addResource(ResourceType.WOOD, GameConfig.SAFEGUARD_WOOD_AMOUNT);
    }

    public boolean isStoneMineUnlocked() { return stoneMineUnlocked; }
    public void setStoneMineUnlocked(boolean v) { this.stoneMineUnlocked = v; }

    public boolean isIronMineUnlocked() { return ironMineUnlocked; }
    public void setIronMineUnlocked(boolean v) { this.ironMineUnlocked = v; }

    public boolean isSettlementUnlocked() { return level >= 2; }

    public boolean isSteelToolsUnlocked() { return steelToolsUnlocked; }
    public boolean isProfessionalToolsUnlocked() { return steelToolsUnlocked; }
    public void setSteelToolsUnlocked(boolean v) { this.steelToolsUnlocked = v; }

    public boolean isSeafaringUnlocked() { return seafaringUnlocked; }
    public void setSeafaringUnlocked(boolean v) { this.seafaringUnlocked = v; }

    public boolean isDefensiveArchUnlocked() { return defensiveArchUnlocked; }

    public void applyDefensiveArchitecture() {
        this.defensiveArchUnlocked = true;
    }

    public int getDiscountedDocks() { return discountedDocks; }
    public void addDiscountedDock() { discountedDocks++; }
    public void consumeDiscountedDock() { if (discountedDocks > 0) discountedDocks--; }
}