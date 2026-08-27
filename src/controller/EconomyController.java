// 1. EconomyController.java
package controller;

import model.*;
import java.util.List;

public class EconomyController implements TurnListener, BuildingListener {

    private final MainController mainController;

    private int lastMonumentCount = 0;
    private int lastSettlementCount = 0;
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
                        && ((TribeCamp) h.getBuilding()).getTribe().getType() == TribeType.COASTAL);
    }

    private void updateHappinessState(GameMap map) {
        TownHall th = map.getTownHall();
        int currentMonuments = (int) map.getHexes().stream()
                .filter(h -> h.getBuilding() != null && !h.getBuilding().isDestroyed() && h.getBuilding().getType() == BuildingType.MONUMENT)
                .count();
        int currentSettlements = (int) map.getHexes().stream()
                .filter(h -> h.getBuilding() != null && !h.getBuilding().isDestroyed() && h.getBuilding().getType() == BuildingType.SETTLEMENT)
                .count();
        boolean currentGarrison = map.getUnits().stream()
                .anyMatch(u -> u.isAlive() && !u.isEnemy() && u.getQ() == th.getQ() && u.getR() == th.getR()
                        && (u.getType() == UnitType.SWORDSMAN || u.getType() == UnitType.ARCHER || u.getType() == UnitType.CAVALRY));
        boolean currentCapState = map.getMilitaryUnitCount() >= map.getMilitaryUnitCap();

        if (!isHappinessInitialized) {
            lastMonumentCount = currentMonuments;
            lastSettlementCount = currentSettlements;
            lastGarrisonState = currentGarrison;
            lastCapState = currentCapState;
            isHappinessInitialized = true;
            return;
        }

        int monumentDiff = currentMonuments - lastMonumentCount;
        if (monumentDiff != 0) {
            th.addHappiness(monumentDiff * 2);
            lastMonumentCount = currentMonuments;
        }

        int settlementDiff = currentSettlements - lastSettlementCount;
        if (settlementDiff != 0) {
            th.addHappiness(-settlementDiff);
            lastSettlementCount = currentSettlements;
        }

        if (currentGarrison != lastGarrisonState) {
            th.addHappiness(currentGarrison ? 1 : -1);
            lastGarrisonState = currentGarrison;
        }

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
        updateHappinessState(map);
        return map.getTownHall().getHappiness();
    }

    @Override
    public void onTurnEnded(int newTurn) {
        GameMap map = mainController.getGameMap();
        boolean isStarving = processEndTurn(map);
        map.setStarving(isStarving);

        if (isStarving) {
            for (Unit unit : map.getUnits()) {
                if (unit.isAlive() && !unit.isEnemy() && unit.getType() != UnitType.BEAR) {
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

    // ─── Single Source of Truth for Resource Extraction (DRY) ───
    private Hex resolveExtractionHex(GameMap map, Hex hex, Building b, ResourceType targetRes) {
        if (b.getType() == BuildingType.DOCK && targetRes == ResourceType.FOOD) {
            for (int i = 0; i < 6; i++) {
                Hex neighbor = map.getNeighbor(hex, i);
                if (neighbor != null && neighbor.getTerrainType() == TerrainType.SEA && neighbor.hasResource(ResourceType.FOOD)) {
                    return neighbor;
                }
            }
            return null;
        }
        return hex.hasResource(targetRes) ? hex : null;
    }

    // ─── Single Source of Truth for Farm Synergy (DRY) ───
    private int calculateFarmSynergy(GameMap map) {
        int farmPairs = 0;
        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b != null && !b.isDestroyed() && b.getType() == BuildingType.FARM) {
                for (int i = 0; i < 6; i++) {
                    Hex n = map.getNeighbor(hex, i);
                    if (n != null && n.getBuilding() != null && !n.getBuilding().isDestroyed() && n.getBuilding().getType() == BuildingType.FARM) {
                        farmPairs++;
                    }
                }
            }
        }
        return farmPairs / 2;
    }

    private void produceResources(GameMap map) {
        TownHall townHall = map.getTownHall();
        Inventory inventory = townHall.getInventory();
        final int happiness = getEffectiveHappiness(map);
        Season season = map.getCurrentSeason();
        boolean coastalAllied = isCoastalAllied(map);

        townHall.produceSafeguardResources();

        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;

            ResourceType targetRes = b.getType().getProducedResource();
            if (targetRes == ResourceType.NONE) continue;

            Hex targetExtractionHex = resolveExtractionHex(map, hex, b, targetRes);
            if (targetExtractionHex == null) {
                ejectWorkersFromHex(map, hex);
                continue;
            }

            int production = calculateBuildingGrossProduction(b, hex, map, townHall, season, happiness, coastalAllied);
            if (production <= 0) continue;

            int availableSpace = Math.max(0, inventory.getCapacity(targetRes) - inventory.getResourceAmount(targetRes));
            int actualToExtract = Math.min(production, availableSpace);

            if (actualToExtract > 0) {
                int extracted = targetExtractionHex.extractResource(targetRes, actualToExtract);
                inventory.addResource(targetRes, extracted);
            }

            if (!targetExtractionHex.hasResource(targetRes)) {
                ejectWorkersFromHex(map, hex);
            }
        }

        int synergyBonus = calculateFarmSynergy(map);
        if (synergyBonus > 0) {
            int availableFoodSpace = Math.max(0, inventory.getCapacity(ResourceType.FOOD) - inventory.getResourceAmount(ResourceType.FOOD));
            int actualBonus = Math.min(synergyBonus, availableFoodSpace);
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
                    GameEventDispatcher.fireNotification("⚠️ " + b.getType().name() + " collapsed due to 3 turns of unpaid upkeep!");
                }
            } else {
                b.resetFailedUpkeep();
            }
        }
    }

    private boolean processFoodConsumption(GameMap map) {
        Inventory inventory = map.getTownHall().getInventory();
        int totalFoodNeeded = map.getUnits().stream()
                .filter(u -> u.isAlive() && !u.isEnemy() && u.getType() != UnitType.BEAR)
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

        for (Hex h : map.getHexes()) {
            Building b = h.getBuilding();
            if (b == null || b.isDestroyed() || b.getType() == BuildingType.TOWN_HALL) continue;

            if (b.getType().getProducedResource() == type) {
                if (resolveExtractionHex(map, h, b, type) != null) {
                    grossProduction += calculateBuildingGrossProduction(b, h, map, townHall, season, happiness, coastalAllied);
                }
            }

            if (b.getUpkeepResource() == type) {
                grossConsumption += b.getUpkeepAmount();
            }
        }

        if (type == ResourceType.FOOD) {
            grossProduction += calculateFarmSynergy(map);
            for (Unit u : map.getUnits()) {
                if (u.isAlive() && !u.isEnemy() && u.getType() != UnitType.BEAR) {
                    grossConsumption += u.getFoodConsumption();
                }
            }
        }

        int availableSpace = Math.max(0, inventory.getCapacity(type) - inventory.getResourceAmount(type));
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

        // [OCP FIX]: حذف هاردکدها. منطق محاسبه مجاورت به Model (BuildingType) واگذار شد.
        production += b.getType().calculateAdjacencyBonus(hex, map, coastalAllied);

        if (happiness <= -3) production -= b.getStationedWorkers();
        if (happiness >= 3) {
            production += (int) Math.ceil(production * 0.1);
        }

        return Math.max(0, production);
    }

    @Override
    public void onBuildingDestroyed(Hex hex) {
        ejectWorkersFromHex(mainController.getGameMap(), hex);
    }

    @Override
    public void onBuildingConstructed(Hex hex) {}
}