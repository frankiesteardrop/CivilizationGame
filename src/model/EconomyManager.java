package model;

/**
 * مدیریت چرخه اقتصادی بازی.
 * * [اصلاح حیاتی QA]:
 * ترتیب فازها به گونه‌ای تغییر یافت که قبل از پیشرفت صف تولید، وضعیت قحطی بررسی شود.
 * اگر قحطی (Starvation) برقرار باشد، صف تولید متوقف می‌ماند.
 */
public class EconomyManager {

    public static boolean processEndTurn(GameMap map) {
        produceResources(map);
        processUpkeep(map);
        boolean isStarving = processFoodConsumption(map);

        if (!isStarving) {
            map.getTownHall().advanceProductionQueue();
        }

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

            int production = b.calculateProduction(townHall);
            if (production <= 0) continue;

            ResourceType targetRes = b.getType().getProducedResource();
            if (targetRes == ResourceType.NONE) continue;

            if (hex.hasResource(targetRes)) {
                int extracted = hex.extractResource(targetRes, production);
                inventory.addResource(targetRes, extracted);

                if (!hex.hasResource(targetRes)) {
                    ejectWorkersFromHex(map, hex);
                }
            }
        }
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

        // اصلاح گام ۴: استفاده از جاوا Streams برای بهینه‌سازی و رعایت اهداف آموزشی
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
                int prod = b.calculateProduction(townHall);
                net += prod;
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