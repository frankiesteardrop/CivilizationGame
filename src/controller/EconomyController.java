package controller;

import model.*;

public class EconomyController {

    private final MainController mainController;

    public EconomyController(MainController mainController) {
        this.mainController = mainController;
    }

    public boolean processEndTurn(GameMap map) {
        produceResources(map);
        processUpkeep(map);
        boolean isStarving = processFoodConsumption(map);

        GameEventDispatcher.fireStarvationChanged(isStarving);
        return isStarving;
    }

    private void produceResources(GameMap map) {
        TownHall townHall = map.getTownHall();
        Inventory inventory = townHall.getInventory();

        townHall.produceSafeguardResources();

        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;

            ResourceType targetRes = b.getType().getProducedResource();
            if (targetRes == ResourceType.NONE) continue;

            if (!hex.hasResource(targetRes)) {
                ejectWorkersFromHex(map, hex);
                continue;
            }

            int production = b.calculateProduction(townHall);
            if (production <= 0) continue;

            int extracted = hex.extractResource(targetRes, production);
            inventory.addResource(targetRes, extracted);

            if (!hex.hasResource(targetRes)) {
                ejectWorkersFromHex(map, hex);
            }
        }

        townHall.advanceProductionQueue(map.isStarving());
    }

    private void processUpkeep(GameMap map) {
        Inventory inventory = map.getTownHall().getInventory();

        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;
            if (b.getUpkeepAmount() <= 0) continue;

            boolean paid = inventory.consumeResource(b.getUpkeepResource(), b.getUpkeepAmount());

            if (!paid) {
                b.registerFailedUpkeep();
                // اگر ساختمان مخروبه شد، بلافاصله تمام کارگران با تابع قفل‌شده eject بیرون پرتاب می‌شوند
                if (b.isDestroyed()) {
                    ejectWorkersFromHex(map, hex);
                    GameEventDispatcher.fireBuildingDestroyed(hex);
                }
            } else {
                b.resetFailedUpkeep();
            }
        }
    }

    private boolean processFoodConsumption(GameMap map) {
        Inventory inventory = map.getTownHall().getInventory();

        int totalFoodNeeded = map.getUnits().stream()
                .filter(Unit::isAlive)
                .mapToInt(Unit::getFoodConsumption).sum();

        if (totalFoodNeeded == 0) return false;

        int currentFood = inventory.getResourceAmount(ResourceType.FOOD);

        // رفع باگ پرش منطقی: مقایسه و مصرف اصولی موجودی حتی در زمان قحطی
        if (currentFood >= totalFoodNeeded) {
            inventory.consumeResource(ResourceType.FOOD, totalFoodNeeded);
            return false;
        } else {
            if (currentFood > 0) {
                // موجودی را رسماً از طریق سیستم استاندارد مصرف می‌کنیم تا ایونت‌ها درست شلیک شوند
                inventory.consumeResource(ResourceType.FOOD, currentFood);
            }
            return true; // تایید ورود به فاز بحران و قحطی
        }
    }

    public void ejectWorkersFromHex(GameMap map, Hex buildingHex) {
        for (Unit u : map.getUnits()) {
            if (u instanceof Worker) {
                Worker w = (Worker) u;
                if (w.isStationed() && w.getQ() == buildingHex.getQ() && w.getR() == buildingHex.getR()) {
                    w.eject();
                }
            }
        }
    }

    public int calculateNetProduction(GameMap map, ResourceType type) {
        int net = 0;
        TownHall townHall = map.getTownHall();

        // اضافه کردن تولید Safeguard به صورت پیش‌فرض
        if (type == ResourceType.WOOD) net += GameConfig.SAFEGUARD_WOOD_AMOUNT;
        if (type == ResourceType.FOOD) net += GameConfig.SAFEGUARD_FOOD_AMOUNT;

        for (Hex h : map.getHexes()) {
            Building b = h.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;

            // محاسبه تولید خالص
            if (b.getType().getProducedResource() == type) {
                if (h.hasResource(type)) {
                    int prod = b.calculateProduction(townHall);
                    // رفع باگ UI: محدود کردن تولید نمایشی به میزان منبع واقعی موجود در هکس
                    int availableResource = h.getResources().getOrDefault(type, 0);
                    int actualProduction = Math.min(prod, availableResource);

                    net += actualProduction;
                }
            }

            // کسر هزینه‌های نگهداری (Upkeep)
            if (b.getUpkeepResource() == type) {
                net -= b.getUpkeepAmount();
            }
        }

        // کسر مصرف غذای یونیت‌های زنده
        if (type == ResourceType.FOOD) {
            for (Unit u : map.getUnits()) {
                if (u.isAlive()) net -= u.getFoodConsumption();
            }
        }

        return net;
    }
}