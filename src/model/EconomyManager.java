package model;

public class EconomyManager {

    public static void processEndTurn(GameMap map) {
        TownHall townHall = map.getTownHall();
        Inventory inventory = townHall.getInventory();
        boolean hasProfTools = townHall.isProfessionalToolsUnlocked();

        // ۱. شارژ مجدد AP تمام یونیت‌های زنده
        for (Unit u : map.getUnits()) {
            u.resetAP();
        }

        // ۲. تولید منابع توسط ساختمان‌ها و Safeguard
        townHall.applySafeguard(); // چوب +1 و غذا +1

        for (Hex hex : map.getHexes()) {
            if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed()) {
                Building b = hex.getBuilding();
                int production = b.calculateProduction();

                // اعمال ارتقای ابزارآلات حرفه‌ای (ضریب 1.5 برای استخراج معادن)
                if (hasProfTools && (b.getType() == BuildingType.STONE_MINE || b.getType() == BuildingType.IRON_MINE)) {
                    production = (int) (production * 1.5);
                }

                // کسر منبع از هکس و اضافه کردن به انبار
                if (production > 0 && hex.getResourceType() != ResourceType.NONE) {
                    int extracted = hex.extractResource(production);
                    inventory.addResource(b.getType().getProducedResource(), extracted);
                }
            }
        }

        // ۳. کسر هزینه نگهداری ساختمان‌ها (Upkeep)
        for (Hex hex : map.getHexes()) {
            if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed()) {
                Building b = hex.getBuilding();
                if (b.getType().getUpkeepCost() > 0) {
                    boolean success = inventory.consumeResource(b.getType().getUpkeepResource(), b.getType().getUpkeepCost());
                    if (!success) {
                        b.registerFailedUpkeep();
                        // اگر کارگری در آن مستقر است و ساختمان خراب می‌شود، کارگران باید اخراج شوند
                        if (b.isDestroyed()) {
                            ejectWorkers(map, hex);
                            hex.setBuilding(null); // تخریب نهایی سازه
                        }
                    } else {
                        b.resetFailedUpkeep();
                    }
                }
            }
        }

        // ۴. مصرف غذا توسط یونیت‌ها و بررسی بحران قحطی (Starvation)
        int totalFoodNeeded = 0;
        for (Unit u : map.getUnits()) {
            if (u.isAlive()) totalFoodNeeded += u.getFoodConsumption();
        }

        if (!inventory.consumeResource(ResourceType.FOOD, totalFoodNeeded)) {
            // ورود به فاز بحران
            inventory.forceDecreaseResource(ResourceType.FOOD, totalFoodNeeded); // صفر شدن انبار

            // جریمه AP به دلیل ضعف ناشی از گرسنگی
            for (Unit u : map.getUnits()) {
                if (u.isAlive()) {
                    u.consumeAP(1);
                }
            }
        }
    }

    private static void ejectWorkers(GameMap map, Hex buildingHex) {
        for (Unit u : map.getUnits()) {
            if (u instanceof Worker && u.getQ() == buildingHex.getQ() && u.getR() == buildingHex.getR()) {
                ((Worker) u).setStationed(false);
            }
        }
    }

    // متد پیش‌بینی و نمایش نرخ تولید در HUD
    public static int calculateNetProduction(GameMap map, ResourceType type) {
        int net = 0;
        TownHall townHall = map.getTownHall();
        boolean hasProfTools = townHall.isProfessionalToolsUnlocked();

        // محاسبه تولید Safeguard
        if (type == ResourceType.WOOD || type == ResourceType.FOOD) net += 1;

        // محاسبه تولید و هزینه ساختمان‌ها
        for (Hex h : map.getHexes()) {
            if (h.getBuilding() != null && !h.getBuilding().isDestroyed()) {
                if (h.getBuilding().getType().getProducedResource() == type) {
                    int prod = h.getBuilding().calculateProduction();
                    if (hasProfTools && (h.getBuilding().getType() == BuildingType.STONE_MINE || h.getBuilding().getType() == BuildingType.IRON_MINE)) {
                        prod = (int) (prod * 1.5);
                    }
                    net += prod;
                }
                if (h.getBuilding().getType().getUpkeepResource() == type) {
                    net -= h.getBuilding().getType().getUpkeepCost();
                }
            }
        }

        // کسر غذای مصرفی یونیت‌ها در نرخ خالص
        if (type == ResourceType.FOOD) {
            for (Unit u : map.getUnits()) {
                if (u.isAlive()) net -= u.getFoodConsumption();
            }
        }
        return net;
    }
}