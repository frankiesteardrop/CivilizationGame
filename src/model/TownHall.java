package model;

import java.util.LinkedList;
import java.util.Queue;

public class TownHall extends Building {

    private static final int HAPPINESS_MIN = -20;
    private static final int HAPPINESS_MAX = +20;

    private int level;
    private final int q;
    private final int r;
    private final Inventory inventory;
    private final Queue<ProductionCommand> productionQueue;

    private boolean stoneMineUnlocked;
    private boolean ironMineUnlocked;
    private boolean steelToolsUnlocked;
    private boolean seafaringUnlocked;
    private boolean defensiveArchUnlocked;

    private int happiness;

    public TownHall(int q, int r) {
        super(BuildingType.TOWN_HALL.getMaxWorkers());
        this.q               = q;
        this.r               = r;
        this.level           = 1;
        this.inventory       = new Inventory();
        this.productionQueue = new LinkedList<>();
        this.happiness       = 0;

        this.setDefense(10);
        this.maxHp = 200;
        this.hp    = 200;

        inventory.addResource(ResourceType.FOOD,  GameConfig.STARTING_FOOD);
        inventory.addResource(ResourceType.WOOD,  GameConfig.STARTING_WOOD);
        inventory.addResource(ResourceType.STONE, GameConfig.STARTING_STONE);
        inventory.addResource(ResourceType.IRON,  GameConfig.STARTING_IRON);
    }

    @Override
    public BuildingType getType() { return BuildingType.TOWN_HALL; }

    public int getQ() { return q; }
    public int getR() { return r; }

    public Inventory getInventory() { return inventory; }

    public void produceSafeguardResources() {
        inventory.addResource(ResourceType.FOOD, GameConfig.SAFEGUARD_FOOD_AMOUNT);
        inventory.addResource(ResourceType.WOOD, GameConfig.SAFEGUARD_WOOD_AMOUNT);
    }


    public int getLevel() { return level; }


    public void upgradeLevel() {
        level++;
        if (level == 2) {
            inventory.upgradeToLevel2();
            this.heal(50);
        } else if (level == 3) {
            inventory.upgradeToLevel3();
        }
    }


    public Queue<ProductionCommand> getProductionQueue() { return productionQueue; }
    public boolean isProductionQueueEmpty()              { return productionQueue.isEmpty(); }

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
        this.happiness = Math.max(HAPPINESS_MIN,
                Math.min(HAPPINESS_MAX, this.happiness + amount));
    }


    public boolean isStoneMineUnlocked()            { return stoneMineUnlocked; }
    public void    setStoneMineUnlocked(boolean v)  { this.stoneMineUnlocked = v; }

    public boolean isIronMineUnlocked()             { return ironMineUnlocked; }
    public void    setIronMineUnlocked(boolean v)   { this.ironMineUnlocked = v; }

    public boolean isSettlementUnlocked()           { return level >= 2; }

    public boolean isSteelToolsUnlocked()           { return steelToolsUnlocked; }
    public boolean isProfessionalToolsUnlocked()    { return steelToolsUnlocked; }
    public void    setSteelToolsUnlocked(boolean v) { this.steelToolsUnlocked = v; }

    public boolean isSeafaringUnlocked()            { return seafaringUnlocked; }
    public void    setSeafaringUnlocked(boolean v)  { this.seafaringUnlocked = v; }

    public boolean isDefensiveArchUnlocked()        { return defensiveArchUnlocked; }

    public void applyDefensiveArchitecture() {
        this.defensiveArchUnlocked = true;
        this.setMaxHp(350);
        this.setDefense(30);
        this.heal(350);

        GameEventDispatcher.fireNotification(
                "🏰 Defensive Architecture active! TH defense: 10→30, max HP: 200→350");
    }

    private int discountedDocks = 0;

    public int getDiscountedDocks() { return discountedDocks; }
    public void addDiscountedDock() { discountedDocks++; }
    public void consumeDiscountedDock() { if (discountedDocks > 0) discountedDocks--; }
}