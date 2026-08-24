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
                public String getDescription() { return "Mission: Build Food Storage\n──────────────────────\nRequirement: Pay 20 Wood + 10 Stone to the tribe.\nReward: 30 Food + 15 relation\nDeadline: 5 turns"; }
            },
            (map, hex) -> {
                map.getTownHall().getInventory().addResource(ResourceType.FOOD, 40);
                GameEventDispatcher.fireNotification("⛺ Farmer tribe defeated! Looted: 40 Food.");
            },
            "• Trade: Give any resource → receive Food at 75% rate\n• Missions: Pay resources to receive Food bonuses\n• Cannot ally simultaneously with Mountain Tribe",
            "• Passive: +5 Food added to storage each turn\n• All Friendly bonuses remain active"),

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
                public String getDescription() { return "Mission: Military Aid\n──────────────────────\nRequirement: Defeat 2 enemy or barbarian units\nwithin 5 hexes of this camp.\nReward: 3 Swordsmen + 20 relation\nDeadline: 8 turns"; }
            },
            (map, hex) -> {
                map.getTownHall().getInventory().addResource(ResourceType.IRON, 30);
                GameEventDispatcher.fireNotification("⛺ Warrior tribe defeated! Looted: 30 Iron.");
            },
            "• Military missions: Defeat enemies near camp for rewards\n• Bonus combat strength near Warrior camp\n• ⚠ Allying Warrior blocks ALL other tribe alliances",
            "• Passive: Military unit support (3 Swordsmen on alliance)\n• Increased combat effectiveness near Warrior camp\n• Note: Blocks alliance with all other tribes"),

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
                public String getDescription() { return "Mission: Mining Tools\n──────────────────────\nRequirement: Pay 15 Wood + 10 Iron to the tribe.\nReward: 20 Stone + 15 relation\nDeadline: 6 turns"; }
            },
            (map, hex) -> {
                map.getTownHall().getInventory().addResource(ResourceType.STONE, 25);
                map.getTownHall().getInventory().addResource(ResourceType.IRON, 15);
                GameEventDispatcher.fireNotification("⛺ Mountain tribe defeated! Looted: 25 Stone, 15 Iron.");
            },
            "• Trade: Give any resource → receive Stone or Iron at 75% rate\n• Missions: Pay resources to receive Stone bonuses\n• Cannot ally simultaneously with Farmer Tribe",
            "• Passive: +5 Stone added to storage each turn\n• All Friendly bonuses remain active"),

    COMMERCIAL(50, "Commercial", "تجاری",
            (give, get, bonus) -> (int)(give * (0.80 + (bonus ? 0.1 : 0))),
            new MissionGoal() {
                public int getInitialTurns() { return 10; }
                public boolean isCompleted(GameMap map, TribeCamp camp) { return map.isRoadConnectedToCamp(camp); }
                public void grantReward(GameMap map, Tribe tribe, TribeCamp camp) {
                    tribe.setTradeBonus(true);
                    tribe.addRelationship(20);
                }
                public String getDescription() { return "Mission: Connect Trade Route\n──────────────────────\nRequirement: Build a continuous road from one of your buildings\nto a hex adjacent to this camp.\nReward: +10% trade rate + 20 relation\nDeadline: 10 turns"; }
            },
            (map, hex) -> {
                map.getTownHall().getInventory().addResource(ResourceType.FOOD, 15);
                map.getTownHall().getInventory().addResource(ResourceType.WOOD, 15);
                map.getTownHall().getInventory().addResource(ResourceType.STONE, 15);
                map.getTownHall().getInventory().addResource(ResourceType.IRON, 15);
                GameEventDispatcher.fireNotification("⛺ Commercial tribe defeated! Looted: Mixed resources.");
            },
            "• Trade: Give any resource → receive any resource at 80% rate\n• Best trade rates of all tribe types\n• Road mission bonus: +10% trade rate",
            "• Passive: +3 Wood added to storage each turn\n• Trade bonus: +10% rate (if road mission completed)\n• All Friendly bonuses remain active"),

    COASTAL(50, "Coastal", "ساحلی",
            (give, get, bonus) -> get == ResourceType.FOOD ? (int)(give * (0.75 + (bonus ? 0.1 : 0))) : 0,
            new MissionGoal() {
                public int getInitialTurns() { return 10; }
                public boolean isCompleted(GameMap map, TribeCamp camp) { return map.hasDockWithinRadius(camp, 4); }
                public void grantReward(GameMap map, Tribe tribe, TribeCamp camp) {
                    map.getTownHall().getInventory().addResource(ResourceType.FOOD, 30);
                    map.getTownHall().addDiscountedDock();
                }
                public String getDescription() { return "Mission: Coastal Development\n──────────────────────\nRequirement: Build a Dock within 4 hexes of this camp.\nReward: 30 Food + discounted Dock cost\nDeadline: 10 turns"; }
            },
            (map, hex) -> {
                map.getTownHall().getInventory().addResource(ResourceType.FOOD, 25);
                map.getTownHall().getInventory().addResource(ResourceType.WOOD, 25);
                GameEventDispatcher.fireNotification("⛺ Coastal tribe defeated! Looted: 25 Food, 25 Wood.");
            },
            "• Trade: Give any resource → receive Food at 75% rate\n• Dock missions: Coastal development bonuses\n• Discount on building future Dock structures",
            "• Passive: +3 Food added to storage each turn\n• Discounted Dock construction cost\n• All Friendly bonuses remain active");

    private final int maxHp;
    private final String displayName;
    private final String persianName;
    private final TradeStrategy tradeStrategy;
    private final MissionGoal missionGoal;
    private final BiConsumer<GameMap, Hex> lootStrategy;
    private final String friendlyRewardDescription;
    private final String alliedRewardDescription;

    TribeType(int maxHp, String displayName, String persianName, TradeStrategy tradeStrategy, MissionGoal missionGoal, BiConsumer<GameMap, Hex> lootStrategy, String friendlyRewardDescription, String alliedRewardDescription) {
        this.maxHp = maxHp;
        this.displayName = displayName;
        this.persianName = persianName;
        this.tradeStrategy = tradeStrategy;
        this.missionGoal = missionGoal;
        this.lootStrategy = lootStrategy;
        this.friendlyRewardDescription = friendlyRewardDescription;
        this.alliedRewardDescription = alliedRewardDescription;
    }

    public int getMaxHp() { return maxHp; }
    public String getDisplayName() { return displayName; }
    public TradeStrategy getTradeStrategy() { return tradeStrategy; }
    public MissionGoal getMissionGoal() { return missionGoal; }
    public String getFriendlyRewardDescription() { return friendlyRewardDescription; }
    public String getAlliedRewardDescription() { return alliedRewardDescription; }

    public void grantLoot(GameMap map, Hex campHex) {
        if (lootStrategy != null) {
            lootStrategy.accept(map, campHex);
        }
    }
}