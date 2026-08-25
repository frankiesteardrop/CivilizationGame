package controller;

import model.*;

public class EconomyController implements TurnListener, BuildingListener {

    private final MainController mainController;

    // ─── سیستم Stateful Delta Tracking برای مدیریت انباشته رضایت ───
    private int lastMonumentCount = 0;
    private boolean lastGarrisonState = false;
    private boolean lastCapState = false;
    private boolean isHappinessInitialized = false;

    public EconomyController(MainController mainController) {
        this.mainController = mainController;
        GameEventDispatcher.addListener(this);
    }

    private boolean isCoastalAllied(GameMap map) {
        return map.getHexes().stream()
                .anyMatch(h -> h.getBuilding() instanceof TribeCamp
                        && !h.getBuilding().isDestroyed()
                        && ((TribeCamp) h.getBuilding()).getTribe().isAllied()
                        && ((TribeCamp) h.getBuilding()).getTribe().getType()
                        == TribeType.COASTAL);
    }

    /**
     * همگام‌سازی رویدادمحور و انباشته‌ی متغیر رضایت (Happiness).
     * این متد تغییرات لحظه‌ای را بررسی کرده و اختلاف (Delta) را یک‌بار اعمال می‌کند.
     */
    private void updateHappinessState(GameMap map) {
        TownHall th = map.getTownHall();

        int currentMonuments = (int) map.getHexes().stream()
                .filter(h -> h.getBuilding() != null && !h.getBuilding().isDestroyed() && h.getBuilding().getType() == BuildingType.MONUMENT)
                .count();

        boolean currentGarrison = map.getUnits().stream()
                .anyMatch(u -> u.isAlive() && u.getQ() == th.getQ() && u.getR() == th.getR()
                        && (u.getType() == UnitType.SWORDSMAN || u.getType() == UnitType.ARCHER || u.getType() == UnitType.CAVALRY));

        boolean currentCapState = map.getMilitaryUnitCount() >= map.getMilitaryUnitCap();

        // مقداردهی اولیه برای جلوگیری از اعمال مجدد پاداش‌ها هنگام بارگذاری سیو
        if (!isHappinessInitialized) {
            lastMonumentCount = currentMonuments;
            lastGarrisonState = currentGarrison;
            lastCapState = currentCapState;
            isHappinessInitialized = true;
            return;
        }

        // اعمال پاداش Monument
        int monumentDiff = currentMonuments - lastMonumentCount;
        if (monumentDiff != 0) {
            th.addHappiness(monumentDiff * 2);
            lastMonumentCount = currentMonuments;
        }

        // اعمال پاداش پادگان (Garrison)
        if (currentGarrison != lastGarrisonState) {
            th.addHappiness(currentGarrison ? 1 : -1);
            lastGarrisonState = currentGarrison;
        }

        // اعمال جریمه سقف ارتش (و جبران آن در صورت خالی شدن ظرفیت)
        if (currentCapState != lastCapState) {
            th.addHappiness(currentCapState ? -1 : 1);
            if (currentCapState) {
                GameEventDispatcher.fireNotification("⚔️ Military Unit Cap reached! -1 Happiness.");
            } else {
                GameEventDispatcher.fireNotification("⚖️ Military Unit Cap relieved. +1 Happiness.");
            }
            lastCapState = currentCapState;
        }
    }

    public int getEffectiveHappiness(GameMap map) {
        updateHappinessState(map); // همیشه قبل از خواندن رضایت، وضعیت رویدادها را سینک می‌کنیم
        return map.getTownHall().getHappiness();
    }

    @Override
    public void onTurnEnded(int newTurn) {
        GameMap map = mainController.getGameMap();
        boolean isStarving = processEndTurn(map);
        map.setStarving(isStarving);

        if (isStarving) {
            for (Unit unit : map.getUnits()) {
                if (unit.isAlive() && unit.getType() != UnitType.BEAR) {
                    unit.consumeAP(1);
                }
            }
        }
        GameEventDispatcher.fireStarvationChanged(isStarving);
    }

    @Override
    public void onStarvationChanged(boolean isStarving) {}

    public boolean processEndTurn(GameMap map) {
        produceResources(map);
        processUpkeep(map);
        boolean isStarving = processFoodConsumption(map);
        map.getTownHall().advanceProductionQueue(isStarving);
        return isStarving;
    }

    private void produceResources(GameMap map) {
        TownHall  townHall  = map.getTownHall();
        Inventory inventory = townHall.getInventory();
        final int happiness = getEffectiveHappiness(map);
        Season    season    = map.getCurrentSeason();
        boolean   coastalAllied = isCoastalAllied(map);

        townHall.produceSafeguardResources();
        int farmPairs = 0;

        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;

            ResourceType targetRes = b.getType().getProducedResource();
            if (targetRes == ResourceType.NONE) continue;

            boolean canProduce = false;
            Hex targetExtractionHex = hex;

            if (b.getType() == BuildingType.DOCK && targetRes == ResourceType.FOOD) {
                for (int i = 0; i < 6; i++) {
                    Hex neighbor = map.getNeighbor(hex, i);
                    if (neighbor != null && neighbor.getTerrainType() == TerrainType.SEA && neighbor.hasResource(ResourceType.FOOD)) {
                        targetExtractionHex = neighbor;
                        canProduce = true;
                        break;
                    }
                }
            } else if (hex.hasResource(targetRes)) {
                canProduce = true;
            }

            if (!canProduce) {
                ejectWorkersFromHex(map, hex);
                continue;
            }

            int production = calculateBuildingGrossProduction(b, hex, map, townHall, season, happiness, coastalAllied);

            if (production <= 0) continue;

            int currentAmount = inventory.getResourceAmount(targetRes);
            int capacity = inventory.getCapacity(targetRes);
            int availableSpace = Math.max(0, capacity - currentAmount);
            int actualToExtract = Math.min(production, availableSpace);

            if (actualToExtract > 0) {
                int extracted = targetExtractionHex.extractResource(targetRes, actualToExtract);
                inventory.addResource(targetRes, extracted);
            }

            if (!targetExtractionHex.hasResource(targetRes))
                ejectWorkersFromHex(map, hex);

            if (b.getType() == BuildingType.FARM) {
                for (int i = 0; i < 6; i++) {
                    Hex neighbor = map.getNeighbor(hex, i);
                    if (neighbor != null && neighbor.getBuilding() != null && !neighbor.getBuilding().isDestroyed() && neighbor.getBuilding().getType() == BuildingType.FARM) {
                        farmPairs++;
                    }
                }
            }
        }

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
                    hex.setBuilding(null);
                    GameEventDispatcher.fireBuildingDestroyed(hex);
                    GameEventDispatcher.fireNotification(
                            "⚠️ " + b.getType().name() + " collapsed due to 3 turns of unpaid upkeep!"
                    );
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
                .mapToInt(Unit::getFoodConsumption)
                .sum();
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
            if (u instanceof Worker w) {
                if (w.isStationed() && w.getQ() == buildingHex.getQ() && w.getR() == buildingHex.getR()) {
                    w.eject(map);
                }
            }
        }
    }

    public int calculateNetProduction(GameMap map, ResourceType type) {
        TownHall townHall = map.getTownHall();
        Inventory inventory = townHall.getInventory();
        final int happiness = getEffectiveHappiness(map);
        Season season = map.getCurrentSeason();
        boolean coastalAllied = isCoastalAllied(map);

        int grossProduction = 0;
        int grossConsumption = 0;

        if (type == ResourceType.WOOD) grossProduction += GameConfig.SAFEGUARD_WOOD_AMOUNT;
        if (type == ResourceType.FOOD) grossProduction += GameConfig.SAFEGUARD_FOOD_AMOUNT;

        int farmPairs = 0;

        for (Hex h : map.getHexes()) {
            Building b = h.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;

            if (b.getType().getProducedResource() == type) {
                boolean hasResourceForNet = h.hasResource(type);
                if (b.getType() == BuildingType.DOCK && type == ResourceType.FOOD) {
                    hasResourceForNet = false;
                    for (int i = 0; i < 6; i++) {
                        Hex neighbor = map.getNeighbor(h, i);
                        if (neighbor != null && neighbor.getTerrainType() == TerrainType.SEA && neighbor.hasResource(ResourceType.FOOD)) {
                            hasResourceForNet = true;
                            break;
                        }
                    }
                }

                if (hasResourceForNet) {
                    int prod = calculateBuildingGrossProduction(b, h, map, townHall, season, happiness, coastalAllied);
                    grossProduction += prod;
                }
            }

            if (b.getType() == BuildingType.FARM && type == ResourceType.FOOD) {
                for (int i = 0; i < 6; i++) {
                    Hex neighbor = map.getNeighbor(h, i);
                    if (neighbor != null && neighbor.getBuilding() != null && !neighbor.getBuilding().isDestroyed() && neighbor.getBuilding().getType() == BuildingType.FARM) {
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

    private int calculateBuildingGrossProduction(Building b, Hex hex, GameMap map, TownHall townHall, Season season, int happiness, boolean coastalAllied) {
        int production = b.calculateProduction(townHall);
        ResourceType targetRes = b.getType().getProducedResource();

        if ((b.getType() == BuildingType.STONE_MINE || b.getType() == BuildingType.IRON_MINE)
                && townHall.isProfessionalToolsUnlocked()) {
            production = (int) Math.floor(production * 1.5);
        }

        if (season == Season.SPRING && (b.getType() == BuildingType.FARM || b.getType() == BuildingType.STABLE)) {
            production += 1;
        } else if (season == Season.WINTER && b.getType() == BuildingType.FARM) {
            production -= 1;
        }

        if (b.getType() == BuildingType.LUMBER_MILL && targetRes == ResourceType.WOOD) {
            boolean nearSea = false;
            for (int i = 0; i < 6; i++) {
                Hex n = map.getNeighbor(hex, i);
                if (n != null && n.getTerrainType() == TerrainType.SEA) {
                    nearSea = true;
                    break;
                }
            }
            if (nearSea) production += 2;
        } else if (b.getType() == BuildingType.STONE_MINE || b.getType() == BuildingType.IRON_MINE) {
            int mCount = 0;
            for (int i = 0; i < 6; i++) {
                Hex n = map.getNeighbor(hex, i);
                if (n != null && n.getTerrainType() == TerrainType.MOUNTAIN) mCount++;
            }
            if (mCount >= 2) production += 1;
        }

        if (b.getType() == BuildingType.DOCK && coastalAllied) {
            production += 2;
        }

        if (happiness <= -3) production -= b.getStationedWorkers();
        if (happiness >= 3)  production += production / 10;

        return Math.max(0, production);
    }

    @Override
    public void onBuildingDestroyed(Hex hex) {
        ejectWorkersFromHex(mainController.getGameMap(), hex);
    }

    @Override
    public void onBuildingConstructed(Hex hex) {}
}