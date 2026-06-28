package model;

/**
 * مدیریت چرخه اقتصادی بازی.
 *
 * ترتیب دقیق اجرا در هر پایان نوبت (طبق داک):
 *   فاز ۱: تولید منابع ساختمان‌ها + Safeguard تان‌هال
 *   فاز ۲: پیشرفت صف تولید تان‌هال (فقط اگر Starvation نباشد)
 *   فاز ۳: کسر Upkeep ساختمان‌ها
 *   فاز ۴: کسر غذای یونیت‌ها + تشخیص و اعمال Starvation
 *
 * توجه: resetAP یونیت‌ها در این کلاس انجام نمی‌شود —
 * این کار مختص GameMap.nextTurn() است.
 */
public class EconomyManager {

    /**
     * نقطه ورود اصلی — اجرای کامل چرخه پایان نوبت به ترتیب صحیح.
     * @return true اگر بازی وارد فاز Starvation شده باشد
     */
    public static boolean processEndTurn(GameMap map) {
        // فاز ۱: تولید منابع ساختمان‌ها + Safeguard تان‌هال
        produceResources(map);

        // فاز ۳: کسر Upkeep ساختمان‌ها
        processUpkeep(map);

        // فاز ۴: کسر غذا + تشخیص Starvation
        boolean isStarving = processFoodConsumption(map);

        // فاز ۲: پیشرفت صف تولید — فقط اگر Starvation نباشد
        // طبق داک: در حالت Starvation رشد جمعیت شهر کاملاً متوقف می‌شود
        if (!isStarving) {
            map.getTownHall().advanceProductionQueue();
        }

        // ارسال رویداد Starvation به لایه View از طریق Event System
        GameEventDispatcher.fireStarvationChanged(isStarving);

        return isStarving;
    }

    // =========================================================
    // فاز ۱: تولید منابع ساختمان‌ها + Safeguard تان‌هال
    // =========================================================
    private static void produceResources(GameMap map) {
        TownHall townHall = map.getTownHall();
        Inventory inventory = townHall.getInventory();
        boolean hasProfTools = townHall.isProfessionalToolsUnlocked();

        // تولید Safeguard تان‌هال (حداقل تولید پایه — همیشه اجرا می‌شود)
        townHall.produceSafeguardResources();

        // تولید ساختمان‌های فعال
        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed()) continue;

            int production = b.calculateProduction();
            if (production <= 0) continue;

            // اعمال ضریب ابزارآلات حرفه‌ای فقط برای معادن
            if (hasProfTools &&
                    (b.getType() == BuildingType.STONE_MINE ||
                            b.getType() == BuildingType.IRON_MINE)) {
                production = (int)(production * 1.5);
            }

            ResourceType targetRes = b.getType().getProducedResource();
            if (targetRes == ResourceType.NONE) continue;

            if (hex.hasResource(targetRes)) {
                int extracted = hex.extractResource(targetRes, production);
                inventory.addResource(targetRes, extracted);

                // اگر منبع هکس تمام شد، کارگران را auto-eject کن
                if (!hex.hasResource(targetRes)) {
                    ejectWorkersFromHex(map, hex);
                }
            }
        }
    }

    // =========================================================
    // فاز ۳: کسر Upkeep ساختمان‌ها
    // =========================================================
    private static void processUpkeep(GameMap map) {
        Inventory inventory = map.getTownHall().getInventory();

        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed()) continue;
            if (b.getUpkeepAmount() <= 0) continue;

            boolean paid = inventory.consumeResource(b.getUpkeepResource(), b.getUpkeepAmount());

            if (!paid) {
                b.registerFailedUpkeep();
                // اگر ۳ نوبت پشت سر هم Upkeep پرداخت نشد، ساختمان تخریب می‌شود
                if (b.isDestroyed()) {
                    ejectWorkersFromHex(map, hex);
                    hex.setBuilding(null);
                }
            } else {
                b.resetFailedUpkeep();
            }
        }
    }

    // =========================================================
    // فاز ۴: کسر غذای یونیت‌ها + تشخیص Starvation
    // =========================================================
    private static boolean processFoodConsumption(GameMap map) {
        Inventory inventory = map.getTownHall().getInventory();

        int totalFoodNeeded = 0;
        for (Unit u : map.getUnits()) {
            if (u.isAlive()) totalFoodNeeded += u.getFoodConsumption();
        }

        if (totalFoodNeeded == 0) return false;

        boolean canPay = inventory.consumeResource(ResourceType.FOOD, totalFoodNeeded);

        if (!canPay) {
            // غذا به صفر می‌رسد — نمی‌تواند منفی شود
            int currentFood = inventory.getResourceAmount(ResourceType.FOOD);
            if (currentFood > 0) {
                inventory.forceDecreaseResource(ResourceType.FOOD, currentFood);
            }
            return true; // Starvation فعال شد
        }

        return false;
    }

    // =========================================================
    // متد کمکی: بیرون انداختن کارگران از یک هکس (Auto-Eject)
    // =========================================================
    public static void ejectWorkersFromHex(GameMap map, Hex buildingHex) {
        for (Unit u : map.getUnits()) {
            if (u instanceof Worker) {
                Worker w = (Worker) u;
                if (w.isStationed() &&
                        w.getQ() == buildingHex.getQ() &&
                        w.getR() == buildingHex.getR()) {
                    w.eject();
                }
            }
        }
    }

    // =========================================================
    // محاسبه نرخ خالص تولید برای HUD (فقط خواندنی، بدون تغییر state)
    // =========================================================
    public static int calculateNetProduction(GameMap map, ResourceType type) {
        int net = 0;
        TownHall townHall = map.getTownHall();
        boolean hasProfTools = townHall.isProfessionalToolsUnlocked();

        // Safeguard تان‌هال
        if (type == ResourceType.WOOD || type == ResourceType.FOOD) net += 1;

        for (Hex h : map.getHexes()) {
            Building b = h.getBuilding();
            if (b == null || b.isDestroyed()) continue;

            // تولید این ساختمان
            if (b.getType().getProducedResource() == type) {
                int prod = b.calculateProduction();
                if (hasProfTools &&
                        (b.getType() == BuildingType.STONE_MINE ||
                                b.getType() == BuildingType.IRON_MINE)) {
                    prod = (int)(prod * 1.5);
                }
                net += prod;
            }

            // Upkeep این ساختمان
            if (b.getUpkeepResource() == type) {
                net -= b.getUpkeepAmount();
            }
        }

        // مصرف غذای یونیت‌ها
        if (type == ResourceType.FOOD) {
            for (Unit u : map.getUnits()) {
                if (u.isAlive()) net -= u.getFoodConsumption();
            }
        }

        return net;
    }
}