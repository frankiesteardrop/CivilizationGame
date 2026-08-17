package model;

import model.mission.MissionGoal;
import model.trade.TradeStrategy;

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
            });

    private final int maxHp;
    private final String displayName;
    private final String persianName;
    private final TradeStrategy tradeStrategy;
    private final MissionGoal missionGoal;

    TribeType(int maxHp, String displayName, String persianName, TradeStrategy tradeStrategy, MissionGoal missionGoal) {
        this.maxHp = maxHp;
        this.displayName = displayName;
        this.persianName = persianName;
        this.tradeStrategy = tradeStrategy;
        this.missionGoal = missionGoal;
    }

    public int getMaxHp() { return maxHp; }
    public String getDisplayName() { return displayName; }
    public TradeStrategy getTradeStrategy() { return tradeStrategy; }
    public MissionGoal getMissionGoal() { return missionGoal; }
}