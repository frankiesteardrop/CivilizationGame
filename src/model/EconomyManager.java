package model;

/**
 * مدیریت چرخه اقتصادی بازی.
 *
 * ترتیب دقیق اجرا در هر پایان نوبت (طبق داک):
 *   فاز ۱: تولید منابع ساختمان‌ها + Safeguard تان‌هال
 *   فاز ۲: پیشرفت صف تولید تان‌هال
 *   فاز ۳: کسر Upkeep ساختمان‌ها
 *   فاز ۴: کسر غذای یونیت‌ها + تشخیص و اعمال Starvation
 *
 * توجه: resetAP یونیت‌ها در این کلاس انجام نمی‌شود —
 * این کار مختص GameMap.nextTurn() است و قبل از فراخوانی این کلاس انجام می‌شود.
 */
public class EconomyManager {

    /**
     * نقطه ورود اصلی — اجرای کامل چرخه پایان نوبت به ترتیب صحیح طبق داک.
     *
     * @param map نقشه بازی جاری
     * @return true اگر بازی وارد فاز Starvation شده باشد
     */
    public static boolean processEndTurn(GameMap map) {

        // فاز ۱: تولید منابع ساختمان‌ها + Safeguard تان‌هال
        produceResources(map);

        // فاز ۲: پیشرفت صف تولید تان‌هال (طبق داک باید قبل از Upkeep باشد)
        map.getTownHall().advanceProductionQueue();

        // فاز ۳: کسر Upkeep ساختمان‌ها
        processUpkeep(map);

        // فاز ۴: کسر غذا + تشخیص Starvation
        boolean isStarving = processFoodConsumption(map);

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

            // TownHall تولید خود را از طریق produceSafeguardResources انجام می‌دهد
            if (b.getType() == BuildingType.TOWN_HALL) continue;

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

            // فقط اگر هکس منبع مورد نظر را داشت، استخراج کن
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

            // TownHall هزینه نگهداری ندارد
            if (b.getType() == BuildingType.TOWN_HALL) continue;

            if (b.getUpkeepAmount() <= 0) continue;

            boolean paid = inventory.consumeResource(b.getUpkeepResource(), b.getUpkeepAmount());

            if (!paid) {
                b.registerFailedUpkeep();
                // اگر ۳ نوبت پشت سر هم Upkeep پرداخت نشد، ساختمان تخریب می‌شود
                if (b.isDestroyed()) {
                    ejectWorkersFromHex(map, hex);
                    hex.setBuilding(null);
                    GameEventDispatcher.fireBuildingConstructed(hex); // آپدیت UI
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
            // غذا به حداقل صفر می‌رسد — هرگز منفی نمی‌شود
            int currentFood = inventory.getResourceAmount(ResourceType.FOOD);
            if (currentFood > 0) {
                inventory.forceDecreaseResource(ResourceType.FOOD, currentFood);
            }
            return true; // Starvation فعال شد
        }

        return false;
    }

    // =========================================================
    // متد کمکی عمومی: بیرون انداختن کارگران از یک هکس (Auto-Eject)
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

        // Safeguard تان‌هال — همیشه +۱ برای چوب و غذا
        if (type == ResourceType.WOOD || type == ResourceType.FOOD) net += 1;

        for (Hex h : map.getHexes()) {
            Building b = h.getBuilding();
            if (b == null || b.isDestroyed()) continue;
            if (b.getType() == BuildingType.TOWN_HALL) continue;

            // تولید این ساختمان برای نوع منبع مورد نظر
            if (b.getType().getProducedResource() == type) {
                int prod = b.calculateProduction();
                if (hasProfTools &&
                        (b.getType() == BuildingType.STONE_MINE ||
                                b.getType() == BuildingType.IRON_MINE)) {
                    prod = (int)(prod * 1.5);
                }
                net += prod;
            }

            // کسر Upkeep این ساختمان اگر با نوع منبع مورد نظر مطابقت داشت
            if (b.getUpkeepResource() == type) {
                net -= b.getUpkeepAmount();
            }
        }

        // کسر مصرف غذای یونیت‌ها
        if (type == ResourceType.FOOD) {
            for (Unit u : map.getUnits()) {
                if (u.isAlive()) net -= u.getFoodConsumption();
            }
        }

        return net;
    }
}