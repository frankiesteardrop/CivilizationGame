package controller;

import model.*;

public class EconomyController implements GameEventListener {

    private final MainController mainController;

    public EconomyController(MainController mainController) {
        this.mainController = mainController;
        GameEventDispatcher.addListener(this);
    }

    // متد کلیدی برای محاسبه رضایت نهایی (ثابت + حضور نیروی نظامی در تالار)
    public int getEffectiveHappiness(GameMap map) {
        int base = map.getTownHall().getHappiness();
        boolean hasMilitary = false;
        TownHall th = map.getTownHall();
        for (Unit u : map.getUnits()) {
            if (u.isAlive() && u.getQ() == th.getQ() && u.getR() == th.getR()) {
                // تمام نیروها به جز کارگران و استخراج‌گرها نظامی محسوب می‌شوند
                if (u.getType() != UnitType.WORKER && u.getType() != UnitType.BUILDER &&
                        u.getType() != UnitType.EXPLORER && u.getType() != UnitType.BORDER_EXPANDER) {
                    hasMilitary = true;
                    break;
                }
            }
        }
        return base + (hasMilitary ? 1 : 0);
    }

    @Override
    public void onTurnEnded(int newTurn) {
        GameMap map = mainController.getGameMap();
        boolean isStarving = processEndTurn(map);
        map.setStarving(isStarving);

        if (isStarving) {
            for (Unit unit : map.getUnits()) {
                if (unit.isAlive()) unit.consumeAP(1);
            }
        }
        GameEventDispatcher.fireStarvationChanged(isStarving);
    }

    public boolean processEndTurn(GameMap map) {
        produceResources(map);
        processUpkeep(map);
        boolean isStarving = processFoodConsumption(map);
        map.getTownHall().advanceProductionQueue(isStarving);
        return isStarving;
    }

    private void produceResources(GameMap map) {
        TownHall townHall = map.getTownHall();
        Inventory inventory = townHall.getInventory();
        int happiness = getEffectiveHappiness(map);

        townHall.produceSafeguardResources();
        int farmPairs = 0;

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

            // اعمال پاداش‌های مجاورت (Adjacency Bonus)
            if (b.getType() == BuildingType.LUMBER_MILL && targetRes == ResourceType.WOOD) {
                boolean nearSea = false;
                for(int i = 0; i < 6; i++) {
                    Hex n = map.getNeighbor(hex, i);
                    if (n != null && n.getTerrainType() == TerrainType.SEA) nearSea = true;
                }
                if (nearSea) production += 2;
            } else if (b.getType() == BuildingType.STONE_MINE || b.getType() == BuildingType.IRON_MINE) {
                int mCount = 0;
                for(int i = 0; i < 6; i++) {
                    Hex n = map.getNeighbor(hex, i);
                    if (n != null && n.getTerrainType() == TerrainType.MOUNTAIN) mCount++;
                }
                if (mCount >= 2) production += 1;
            }

            // اعمال جریمه‌ها و پاداش‌های رضایت
            if (happiness <= -3) production -= b.getStationedWorkers();
            if (happiness >= 3) production += production / 10;

            production = Math.max(0, production);
            if (production <= 0) continue;

            int currentAmount = inventory.getResourceAmount(targetRes);
            int capacity = inventory.getCapacity(targetRes);
            int availableSpace = Math.max(0, capacity - currentAmount);
            int actualToExtract = Math.min(production, availableSpace);

            if (actualToExtract > 0) {
                int extracted = hex.extractResource(targetRes, actualToExtract);
                inventory.addResource(targetRes, extracted);
            }

            if (!hex.hasResource(targetRes)) ejectWorkersFromHex(map, hex);

            // شناسایی مجاورت مزارع
            if (b.getType() == BuildingType.FARM) {
                for(int i = 0; i < 6; i++) {
                    Hex neighbor = map.getNeighbor(hex, i);
                    if (neighbor != null && neighbor.getBuilding() != null &&
                            !neighbor.getBuilding().isDestroyed() && neighbor.getBuilding().getType() == BuildingType.FARM) {
                        farmPairs++;
                    }
                }
            }
        }

        // اعمال یکباره سینرژی مزارع به کل امپراتوری
        farmPairs /= 2;
        if (farmPairs > 0) {
            int space = inventory.getCapacity(ResourceType.FOOD) - inventory.getResourceAmount(ResourceType.FOOD);
            int actualBonus = Math.min(farmPairs, space);
            if (actualBonus > 0) inventory.addResource(ResourceType.FOOD, actualBonus);
        }
    }

    private void processUpkeep(GameMap map) {
        Inventory inventory = map.getTownHall().getInventory();
        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;
            if (b.getUpkeepAmount() <= 0) continue;

            if (!inventory.consumeResource(b.getUpkeepResource(), b.getUpkeepAmount())) {
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

    private boolean processFoodConsumption(GameMap map) {
        Inventory inventory = map.getTownHall().getInventory();
        int totalFoodNeeded = map.getUnits().stream().filter(Unit::isAlive).mapToInt(Unit::getFoodConsumption).sum();
        if (totalFoodNeeded == 0) return false;

        int currentFood = inventory.getResourceAmount(ResourceType.FOOD);
        if (currentFood >= totalFoodNeeded) {
            inventory.consumeResource(ResourceType.FOOD, totalFoodNeeded);
            return false;
        } else {
            if (currentFood > 0) inventory.consumeResource(ResourceType.FOOD, currentFood);
            return true;
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
        TownHall townHall = map.getTownHall();
        Inventory inventory = townHall.getInventory();
        int happiness = getEffectiveHappiness(map);

        int grossProduction = 0;
        int grossConsumption = 0;

        if (type == ResourceType.WOOD) grossProduction += GameConfig.SAFEGUARD_WOOD_AMOUNT;
        if (type == ResourceType.FOOD) grossProduction += GameConfig.SAFEGUARD_FOOD_AMOUNT;

        int farmPairs = 0;

        for (Hex h : map.getHexes()) {
            Building b = h.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;

            if (b.getType().getProducedResource() == type) {
                if (h.hasResource(type)) {
                    int prod = b.calculateProduction(townHall);

                    if (b.getType() == BuildingType.LUMBER_MILL && type == ResourceType.WOOD) {
                        boolean nearSea = false;
                        for(int i = 0; i < 6; i++) {
                            Hex n = map.getNeighbor(h, i);
                            if (n != null && n.getTerrainType() == TerrainType.SEA) nearSea = true;
                        }
                        if (nearSea) prod += 2;
                    } else if (b.getType() == BuildingType.STONE_MINE || b.getType() == BuildingType.IRON_MINE) {
                        int mCount = 0;
                        for(int i = 0; i < 6; i++) {
                            Hex n = map.getNeighbor(h, i);
                            if (n != null && n.getTerrainType() == TerrainType.MOUNTAIN) mCount++;
                        }
                        if (mCount >= 2) prod += 1;
                    }

                    if (happiness <= -3) prod -= b.getStationedWorkers();
                    if (happiness >= 3) prod += prod / 10;

                    prod = Math.max(0, prod);
                    int actualProduction = Math.min(prod, h.getResources().getOrDefault(type, 0));
                    grossProduction += actualProduction;
                }
            }

            if (b.getType() == BuildingType.FARM && type == ResourceType.FOOD) {
                for(int i = 0; i < 6; i++) {
                    Hex neighbor = map.getNeighbor(h, i);
                    if (neighbor != null && neighbor.getBuilding() != null &&
                            !neighbor.getBuilding().isDestroyed() && neighbor.getBuilding().getType() == BuildingType.FARM) {
                        farmPairs++;
                    }
                }
            }

            if (b.getUpkeepResource() == type) {
                grossConsumption += b.getUpkeepAmount();
            }
        }

        if (type == ResourceType.FOOD) {
            grossProduction += (farmPairs / 2);
            for (Unit u : map.getUnits()) {
                if (u.isAlive()) grossConsumption += u.getFoodConsumption();
            }
        }

        int currentAmount = inventory.getResourceAmount(type);
        int availableSpace = Math.max(0, inventory.getCapacity(type) - currentAmount);
        return Math.min(grossProduction, availableSpace) - grossConsumption;
    }

    @Override public void onResourceChanged(ResourceType type, int newAmount) {}
    @Override public void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) {}
    @Override public void onUnitKilled(Unit unit) {}
    @Override public void onProductionCompleted(String itemName) {}
    @Override public void onStarvationChanged(boolean isStarving) {}
    @Override public void onUnitStateChanged(Unit unit) {}
    @Override public void onBuildingConstructed(Hex hex) {}
    @Override public void onBuildingDestroyed(Hex hex) {}
    @Override public void onBorderExpanded(int centerQ, int centerR) {}
}