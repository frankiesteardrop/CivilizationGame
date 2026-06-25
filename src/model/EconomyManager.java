package model;

public class EconomyManager {

    public static void processEndTurn(GameMap map) {
        TownHall townHall = map.getTownHall();
        Inventory inventory = townHall.getInventory();
        boolean starvationMode = false;

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
            starvationMode = true;
            inventory.forceDecreaseResource(ResourceType.FOOD, totalFoodNeeded); // صفر شدن انبار

            // جریمه AP به دلیل ضعف ناشی از گرسنگی
            for (Unit u : map.getUnits()) {
                if (u.isAlive()) {
                    u.consumeAP(1); // از دست دادن مقداری AP در شرایط بحران
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
}