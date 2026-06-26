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

        // ۲. تولید منابع توسط ساختمان‌ها و Safeguard تان‌هال
        townHall.applySafeguard();

        for (Hex hex : map.getHexes()) {
            if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed()) {
                Building b = hex.getBuilding();
                int production = b.calculateProduction();

                if (hasProfTools && (b.getType() == BuildingType.STONE_MINE || b.getType() == BuildingType.IRON_MINE)) {
                    production = (int) (production * 1.5);
                }

                if (production > 0 && hex.getResourceType() != ResourceType.NONE) {
                    int extracted = hex.extractResource(production);
                    inventory.addResource(b.getType().getProducedResource(), extracted);
                }
            }
        }

        // ۳. کسر هزینه نگهداری ساختمان‌ها (Upkeep) کاملاً پویا و Polymorphic
        for (Hex hex : map.getHexes()) {
            if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed()) {
                Building b = hex.getBuilding();
                if (b.getUpkeepAmount() > 0) {
                    boolean success = inventory.consumeResource(b.getUpkeepResource(), b.getUpkeepAmount());
                    if (!success) {
                        b.registerFailedUpkeep(); // مدیریت داخلی تعداد دفعات عدم پرداخت در خود مدل ساختمان
                        if (b.isDestroyed()) {
                            ejectWorkers(map, hex);
                            hex.setBuilding(null); // تخریب و حذف نهایی سازه از نقشه
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
            inventory.forceDecreaseResource(ResourceType.FOOD, totalFoodNeeded);
            for (Unit u : map.getUnits()) {
                if (u.isAlive()) {
                    u.consumeAP(1); // اعمال جریمه به علت قحطی
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