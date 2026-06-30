package model;

/**
 * مدیریت چرخه اقتصادی بازی.
 * * [اصلاح حیاتی QA]:
 * ترتیب فازها به گونه‌ای تغییر یافت که قبل از پیشرفت صف تولید، وضعیت قحطی بررسی شود.
 * اگر قحطی (Starvation) برقرار باشد، صف تولید متوقف می‌ماند.
 */
public class EconomyManager {

    public static boolean processEndTurn(GameMap map) {
        // فاز ۱: تولید منابع ساختمان‌ها + Safeguard تان‌هال
        produceResources(map);

        // فاز ۲: کسر Upkeep ساختمان‌ها (تخریب در صورت عدم پرداخت ۳ نوبته)
        processUpkeep(map);

        // فاز ۳: کسر غذا + تشخیص Starvation
        boolean isStarving = processFoodConsumption(map);

        // فاز ۴: پیشرفت صف تولید تان‌هال
        // [اصلاح حیاتی]: صف تولید فقط زمانی پیش می‌رود که قحطی نباشد!
        if (!isStarving) {
            map.getTownHall().advanceProductionQueue();
        }

        // ارسال رویداد Starvation به لایه View از طریق Event System
        GameEventDispatcher.fireStarvationChanged(isStarving);

        return isStarving;
    }

    private static void produceResources(GameMap map) {
        TownHall townHall = map.getTownHall();
        Inventory inventory = townHall.getInventory();
        boolean hasProfTools = townHall.isProfessionalToolsUnlocked();

        townHall.produceSafeguardResources();

        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;

            int production = b.calculateProduction();
            if (production <= 0) continue;

            if (hasProfTools && (b.getType() == BuildingType.STONE_MINE || b.getType() == BuildingType.IRON_MINE)) {
                production = (int)(production * 1.5);
            }

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
                    hex.setBuilding(null);
                    GameEventDispatcher.fireBuildingConstructed(hex); // Update UI to remove building
                }
            } else {
                b.resetFailedUpkeep();
            }
        }
    }

    private static boolean processFoodConsumption(GameMap map) {
        Inventory inventory = map.getTownHall().getInventory();

        int totalFoodNeeded = 0;
        for (Unit u : map.getUnits()) {
            if (u.isAlive()) totalFoodNeeded += u.getFoodConsumption();
        }

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
        boolean hasProfTools = townHall.isProfessionalToolsUnlocked();

        if (type == ResourceType.WOOD || type == ResourceType.FOOD) net += 1;

        for (Hex h : map.getHexes()) {
            Building b = h.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;

            if (b.getType().getProducedResource() == type) {
                int prod = b.calculateProduction();
                if (hasProfTools && (b.getType() == BuildingType.STONE_MINE || b.getType() == BuildingType.IRON_MINE)) {
                    prod = (int)(prod * 1.5);
                }
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