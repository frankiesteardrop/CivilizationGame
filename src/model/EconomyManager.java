package model;

/**
 * مدیریت چرخه اقتصادی بازی.
 */
public class EconomyManager {

    public static boolean processEndTurn(GameMap map) {
        produceResources(map);
        processUpkeep(map);
        boolean isStarving = processFoodConsumption(map);

        GameEventDispatcher.fireStarvationChanged(isStarving);
        return isStarving;
    }

    private static void produceResources(GameMap map) {
        TownHall townHall = map.getTownHall();
        Inventory inventory = townHall.getInventory();

        townHall.produceSafeguardResources();

        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;

            ResourceType targetRes = b.getType().getProducedResource();
            if (targetRes == ResourceType.NONE) continue;

            // [گام ۵ - اصلاح]: برنامه‌نویسی تدافعی. اگر هکس قبل از شروع استخراج خالی است،
            // باید فوراً کارگران را بیرون بیندازیم تا برای همیشه گیر نیفتند.
            if (!hex.hasResource(targetRes)) {
                ejectWorkersFromHex(map, hex);
                continue;
            }

            int production = b.calculateProduction(townHall);
            if (production <= 0) continue;

            int extracted = hex.extractResource(targetRes, production);
            inventory.addResource(targetRes, extracted);

            // [گام ۵ - حفظ منطق درست]: بررسی مجدد پس از استخراج برای خالی شدن هکس در همین نوبت
            if (!hex.hasResource(targetRes)) {
                ejectWorkersFromHex(map, hex);
            }
        }

        townHall.advanceProductionQueue();
    }

    private static void processUpkeep(GameMap map) {
        Inventory inventory = map.getTownHall().getInventory();

        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;
            if (b.getUpkeepAmount() <= 0) continue;

            boolean paid = inventory.consumeResource(b.getUpkeepResource(), b.getUpkeepAmount());

            if (!paid) {
                b.registerFailedUpkeep();
                if (b.isDestroyed()) {
                    ejectWorkersFromHex(map, hex);
                    GameEventDispatcher.fireBuildingDestroyed(hex);
                }
            } else {
                b.resetFailedUpkeep();
            }
        }
    }

    private static boolean processFoodConsumption(GameMap map) {
        Inventory inventory = map.getTownHall().getInventory();

        int totalFoodNeeded = map.getUnits().stream()
                .filter(Unit::isAlive)
                .mapToInt(Unit::getFoodConsumption).sum();

        if (totalFoodNeeded == 0) return false;

        boolean canPay = inventory.consumeResource(ResourceType.FOOD, totalFoodNeeded);

        if (!canPay) {
            int currentFood = inventory.getResourceAmount(ResourceType.FOOD);
            if (currentFood > 0) {
                inventory.forceDecreaseResource(ResourceType.FOOD, currentFood);
            }
            return true; // Starvation فعال شد
        }

        return false;
    }

    public static void ejectWorkersFromHex(GameMap map, Hex buildingHex) {
        for (Unit u : map.getUnits()) {
            if (u instanceof Worker) {
                Worker w = (Worker) u;
                if (w.isStationed() && w.getQ() == buildingHex.getQ() && w.getR() == buildingHex.getR()) {
                    w.eject();
                }
            }
        }
    }

    public static int calculateNetProduction(GameMap map, ResourceType type) {
        int net = 0;
        TownHall townHall = map.getTownHall();

        if (type == ResourceType.WOOD || type == ResourceType.FOOD) net += 1;

        for (Hex h : map.getHexes()) {
            Building b = h.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;

            if (b.getType().getProducedResource() == type) {
                // [گام ۵ - اصلاح]: جلوگیری از نمایش نرخ تولید دروغین در HUD
                if (h.hasResource(type)) {
                    int prod = b.calculateProduction(townHall);
                    net += prod;
                }
            }

            if (b.getUpkeepResource() == type) {
                net -= b.getUpkeepAmount();
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