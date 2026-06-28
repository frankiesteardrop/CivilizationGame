package model;

public class EconomyManager {

    public static void processEndTurn(GameMap map) {
        TownHall townHall = map.getTownHall();
        Inventory inventory = townHall.getInventory();
        boolean hasProfTools = townHall.isProfessionalToolsUnlocked();

        // 1. تولید ساختمان‌ها و استخراج
        for (Hex hex : map.getHexes()) {
            if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed()) {
                Building b = hex.getBuilding();
                int production = b.calculateProduction();

                if (hasProfTools && (b.getType() == BuildingType.STONE_MINE || b.getType() == BuildingType.IRON_MINE)) {
                    production = (int) (production * 1.5);
                }

                if (production > 0) {
                    ResourceType targetRes = b.getType().getProducedResource();
                    if (hex.hasResource(targetRes)) {
                        int extracted = hex.extractResource(targetRes, production);
                        inventory.addResource(targetRes, extracted);
                    }
                }
            }
        }

        // 2. محاسبه هزینه نگهداری (Upkeep) سازه‌ها
        for (Hex hex : map.getHexes()) {
            if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed()) {
                Building b = hex.getBuilding();
                if (b.getUpkeepAmount() > 0) {
                    boolean success = inventory.consumeResource(b.getUpkeepResource(), b.getUpkeepAmount());
                    if (!success) {
                        b.registerFailedUpkeep();
                        if (b.isDestroyed()) {
                            ejectWorkers(map, hex);
                            hex.setBuilding(null);
                        }
                    } else {
                        b.resetFailedUpkeep();
                    }
                }
            }
        }

        // 3. محاسبه مصرف غذای یونیت‌ها و مکانیزم بحران قحطی (Starvation)
        int totalFoodNeeded = 0;
        for (Unit u : map.getUnits()) {
            if (u.isAlive()) totalFoodNeeded += u.getFoodConsumption();
        }

        boolean isStarving = false;
        if (!inventory.consumeResource(ResourceType.FOOD, totalFoodNeeded)) {
            // در صورت قحطی، موجودی را تا صفر خالی می‌کنیم (نه منفی)
            inventory.forceDecreaseResource(ResourceType.FOOD, inventory.getResourceAmount(ResourceType.FOOD));
            isStarving = true;
        }

        // 4. بازنشانی AP یونیت‌ها (و اعمال جریمه قحطی)
        for (Unit u : map.getUnits()) {
            u.resetAP();
            if (isStarving && u.isAlive()) {
                u.consumeAP(1); // اعمال جریمه قحطی روی AP
            }
        }

        // 5. اعمال SafeGuard
        townHall.applySafeguard();
    }

    private static void ejectWorkers(GameMap map, Hex buildingHex) {
        for (Unit u : map.getUnits()) {
            if (u instanceof Worker && u.getQ() == buildingHex.getQ() && u.getR() == buildingHex.getR()) {
                ((Worker) u).eject();
            }
        }
    }

    public static int calculateNetProduction(GameMap map, ResourceType type) {
        int net = 0;
        TownHall townHall = map.getTownHall();
        boolean hasProfTools = townHall.isProfessionalToolsUnlocked();

        if (type == ResourceType.WOOD || type == ResourceType.FOOD) net += 1;

        for (Hex h : map.getHexes()) {
            if (h.getBuilding() != null && !h.getBuilding().isDestroyed()) {
                Building b = h.getBuilding();
                if (b.getType().getProducedResource() == type) {
                    int prod = b.calculateProduction();
                    if (hasProfTools && (b.getType() == BuildingType.STONE_MINE || b.getType() == BuildingType.IRON_MINE)) {
                        prod = (int) (prod * 1.5);
                    }
                    net += prod;
                }
                if (b.getUpkeepResource() == type) {
                    net -= b.getUpkeepAmount();
                }
            }
        }

        if (type == ResourceType.FOOD) {
            for (Unit u : map.getUnits()) {
                if (u.isAlive()) net -= u.getFoodConsumption();
            }
        }
        return net;
    }
}