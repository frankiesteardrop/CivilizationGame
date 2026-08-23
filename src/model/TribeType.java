package model;

import model.mission.MissionGoal;
import model.trade.TradeStrategy;
import java.util.function.BiConsumer;

public enum TribeType {

    FARMER(40, "Farmer", "کشاورز",
            (give, get, bonus) -> get == ResourceType.FOOD ? (int)(give * (0.75 + (bonus ? 0.1 : 0))) : 0,
            new MissionGoal() {
                public int getInitialTurns() { return 5; }
                public boolean isCompleted(GameMap map, TribeCamp camp) {
                    Inventory inv = map.getTownHall().getInventory();
                    return inv.hasEnough(ResourceType.WOOD, 20) && inv.hasEnough(ResourceType.STONE, 10);
                }
                public void grantReward(GameMap map, Tribe tribe, TribeCamp camp) {
                    Inventory inv = map.getTownHall().getInventory();
                    inv.consumeResource(ResourceType.WOOD, 20);
                    inv.consumeResource(ResourceType.STONE, 10);
                    inv.addResource(ResourceType.FOOD, 30);
                    tribe.addRelationship(15);
                }
            },
            (map, hex) -> {
                map.getTownHall().getInventory().addResource(ResourceType.FOOD, 40);
                GameEventDispatcher.fireNotification("⛺ Farmer tribe defeated! Looted: 40 Food.");
            }),

    WARRIOR(70, "Warrior", "جنگجو",
            (give, get, bonus) -> 0,
            new MissionGoal() {
                public int getInitialTurns() { return 8; }
                public boolean isCompleted(GameMap map, TribeCamp camp) { return false; /* Handled by progress */ }
                public void grantReward(GameMap map, Tribe tribe, TribeCamp camp) {
                    Hex hex = map.getHexOfBuilding(camp);
                    if (hex != null) {
                        for (int i=0; i<3; i++) {
                            Hex spawn = map.findEmptySpawnHex(hex.getQ(), hex.getR());
                            if (spawn != null) map.addUnit(UnitFactory.createUnit(UnitType.SWORDSMAN, spawn.getQ(), spawn.getR()));
                        }
                    }
                    tribe.addRelationship(20);
                }
            },
            (map, hex) -> {
                map.getTownHall().getInventory().addResource(ResourceType.IRON, 30);
                GameEventDispatcher.fireNotification("⛺ Warrior tribe defeated! Looted: 30 Iron.");
            }),

    MOUNTAIN(50, "Mountain", "کوهستانی",
            (give, get, bonus) -> (get == ResourceType.STONE || get == ResourceType.IRON) ? (int)(give * (0.75 + (bonus ? 0.1 : 0))) : 0,
            new MissionGoal() {
                public int getInitialTurns() { return 6; }
                public boolean isCompleted(GameMap map, TribeCamp camp) {
                    Inventory inv = map.getTownHall().getInventory();
                    return inv.hasEnough(ResourceType.WOOD, 15) && inv.hasEnough(ResourceType.IRON, 10);
                }
                public void grantReward(GameMap map, Tribe tribe, TribeCamp camp) {
                    Inventory inv = map.getTownHall().getInventory();
                    inv.consumeResource(ResourceType.WOOD, 15);
                    inv.consumeResource(ResourceType.IRON, 10);
                    inv.addResource(ResourceType.STONE, 20);
                    tribe.addRelationship(15);
                }
            },
            (map, hex) -> {
                map.getTownHall().getInventory().addResource(ResourceType.STONE, 25);
                map.getTownHall().getInventory().addResource(ResourceType.IRON, 15);
                GameEventDispatcher.fireNotification("⛺ Mountain tribe defeated! Looted: 25 Stone, 15 Iron.");
            }),

    COMMERCIAL(50, "Commercial", "تجاری",
            (give, get, bonus) -> (int)(give * (0.80 + (bonus ? 0.1 : 0))),
            new MissionGoal() {
                public int getInitialTurns() { return 10; }
                public boolean isCompleted(GameMap map, TribeCamp camp) { return map.isRoadConnectedToCamp(camp); }
                public void grantReward(GameMap map, Tribe tribe, TribeCamp camp) {
                    tribe.setTradeBonus(true);
                    tribe.addRelationship(20);
                }
            },
            (map, hex) -> {
                map.getTownHall().getInventory().addResource(ResourceType.FOOD, 15);
                map.getTownHall().getInventory().addResource(ResourceType.WOOD, 15);
                map.getTownHall().getInventory().addResource(ResourceType.STONE, 15);
                map.getTownHall().getInventory().addResource(ResourceType.IRON, 15);
                GameEventDispatcher.fireNotification("⛺ Commercial tribe defeated! Looted: Mixed resources.");
            }),

    COASTAL(50, "Coastal", "ساحلی",
            (give, get, bonus) -> get == ResourceType.FOOD ? (int)(give * (0.75 + (bonus ? 0.1 : 0))) : 0,
            new MissionGoal() {
                public int getInitialTurns() { return 10; }
                public boolean isCompleted(GameMap map, TribeCamp camp) { return map.hasDockWithinRadius(camp, 4); }
                public void grantReward(GameMap map, Tribe tribe, TribeCamp camp) {
                    map.getTownHall().getInventory().addResource(ResourceType.FOOD, 30);
                    map.getTownHall().addDiscountedDock();
                }
            },
            (map, hex) -> {
                map.getTownHall().getInventory().addResource(ResourceType.FOOD, 25);
                map.getTownHall().getInventory().addResource(ResourceType.WOOD, 25);
                GameEventDispatcher.fireNotification("⛺ Coastal tribe defeated! Looted: 25 Food, 25 Wood.");
            });

    private final int maxHp;
    private final String displayName;
    private final String persianName;
    private final TradeStrategy tradeStrategy;
    private final MissionGoal missionGoal;
    private final BiConsumer<GameMap, Hex> lootStrategy;

    TribeType(int maxHp, String displayName, String persianName, TradeStrategy tradeStrategy, MissionGoal missionGoal, BiConsumer<GameMap, Hex> lootStrategy) {
        this.maxHp = maxHp;
        this.displayName = displayName;
        this.persianName = persianName;
        this.tradeStrategy = tradeStrategy;
        this.missionGoal = missionGoal;
        this.lootStrategy = lootStrategy;
    }

    public int getMaxHp() { return maxHp; }
    public String getDisplayName() { return displayName; }
    public TradeStrategy getTradeStrategy() { return tradeStrategy; }
    public MissionGoal getMissionGoal() { return missionGoal; }

    public void grantLoot(GameMap map, Hex campHex) {
        if (lootStrategy != null) {
            lootStrategy.accept(map, campHex);
        }
    }
}