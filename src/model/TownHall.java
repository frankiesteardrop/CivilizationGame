package model;

import java.util.LinkedList;
import java.util.Queue;

public class TownHall extends Building {

    private final int q;
    private final int r;
    private final Inventory inventory;

    private int level;
    private int happiness; // متغیر انباشته رضایت عمومی فاز 2

    private boolean stoneMineUnlocked;
    private boolean ironMineUnlocked;
    private boolean seafaringUnlocked;
    private boolean steelToolsUnlocked;
    private boolean defensiveArchUnlocked;

    private final Queue<ProductionCommand> productionQueue;

    public TownHall(int q, int r) {
        super(BuildingType.TOWN_HALL.getMaxWorkers());
        this.setMaxHp(200);
        this.hp = 200;
        this.defense = 10;
        this.happiness = 0; // شروع از 0

        this.q = q;
        this.r = r;
        this.inventory = new Inventory();
        this.productionQueue = new LinkedList<>();

        this.inventory.addResource(ResourceType.FOOD,  GameConfig.STARTING_FOOD);
        this.inventory.addResource(ResourceType.WOOD,  GameConfig.STARTING_WOOD);
        this.inventory.addResource(ResourceType.STONE, GameConfig.STARTING_STONE);
        this.inventory.addResource(ResourceType.IRON,  GameConfig.STARTING_IRON);

        this.level = 1;
        this.stoneMineUnlocked = false;
        this.ironMineUnlocked = false;
        this.seafaringUnlocked = false;
        this.steelToolsUnlocked = false;
        this.defensiveArchUnlocked = false;
    }

    @Override
    public BuildingType getType() {
        return BuildingType.TOWN_HALL;
    }

    // متدهای مربوط به رضایت
    public int getHappiness() { return happiness; }
    public void addHappiness(int amount) { this.happiness += amount; }

    public void produceSafeguardResources() {
        this.inventory.addResource(ResourceType.WOOD, GameConfig.SAFEGUARD_WOOD_AMOUNT);
        this.inventory.addResource(ResourceType.FOOD, GameConfig.SAFEGUARD_FOOD_AMOUNT);
    }

    public void advanceProductionQueue(boolean isStarving) {
        if (productionQueue.isEmpty()) return;

        ProductionCommand currentTask = productionQueue.peek();

        if (currentTask.isCanceled()) {
            productionQueue.poll();
            return;
        }

        if (isStarving && currentTask.isPopulationTask()) return;

        currentTask.decrementTurn();

        if (currentTask.isCompleted()) {
            productionQueue.poll();
            currentTask.execute();
            GameEventDispatcher.fireProductionCompleted(currentTask.getName());
        }
    }

    public boolean queueCommand(ProductionCommand command) {
        if (!productionQueue.isEmpty()) return false;
        productionQueue.add(command);
        return true;
    }

    public void cancelCurrentProduction() {
        if (!productionQueue.isEmpty()) {
            productionQueue.peek().cancel();
            productionQueue.poll();
            GameEventDispatcher.fireProductionCompleted("Canceled: Resources Lost");
        }
    }

    public boolean isProductionQueueEmpty() { return productionQueue.isEmpty(); }
    public int getLevel() { return level; }

    public void upgradeLevel() {
        if (level == 1) {
            level = 2;
            inventory.upgradeToLevel2();
            heal(50);
        } else if (level == 2) {
            level = 3;
            inventory.upgradeToLevel3();
        }
    }

    public void applyDefensiveArchitecture() {
        this.defensiveArchUnlocked = true;
        this.setMaxHp(350);
        this.setDefense(30);
        this.heal(150);
    }

    public boolean isSettlementUnlocked() { return level >= 2; }
    public boolean isProfessionalToolsUnlocked() { return steelToolsUnlocked; }

    public int getQ() { return q; }
    public int getR() { return r; }
    public Inventory getInventory() { return inventory; }
    public Queue<ProductionCommand> getProductionQueue() { return productionQueue; }

    public boolean isStoneMineUnlocked() { return stoneMineUnlocked; }
    public void setStoneMineUnlocked(boolean v) { this.stoneMineUnlocked = v; }
    public boolean isIronMineUnlocked() { return ironMineUnlocked; }
    public void setIronMineUnlocked(boolean v) { this.ironMineUnlocked = v; }

    public boolean isSeafaringUnlocked() { return seafaringUnlocked; }
    public void setSeafaringUnlocked(boolean v) { this.seafaringUnlocked = v; }
    public boolean isSteelToolsUnlocked() { return steelToolsUnlocked; }
    public void setSteelToolsUnlocked(boolean v) { this.steelToolsUnlocked = v; }
    public boolean isDefensiveArchUnlocked() { return defensiveArchUnlocked; }
}