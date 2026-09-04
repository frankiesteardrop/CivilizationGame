package controller;

import model.*;

public class UnitController {

    private Hex lastClickedHex = null;
    private int unitCycleIndex = 0;

    public boolean canMove(Unit unit, Hex targetHex) {
        return canMove(unit, targetHex, null);
    }

    public boolean canMove(Unit unit, Hex targetHex, GameMap map) {
        if (unit == null || !unit.isAlive() || targetHex == null) return false;
        if (unit instanceof Worker && ((Worker) unit).isStationed()) return false;

        if (targetHex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;

        int dq = targetHex.getQ() - unit.getQ();
        int dr = targetHex.getR() - unit.getR();
        int ds = -dq - dr;

        boolean isNeighbor = (Math.max(Math.max(Math.abs(dq), Math.abs(dr)), Math.abs(ds)) == 1);
        if (!isNeighbor) return false;

        if (targetHex.getTerrainType() == TerrainType.SEA) {
            if (map == null || !map.getTownHall().isSeafaringUnlocked()) return false;
        }

        if (map != null && !hasCapacityForUnit(unit, targetHex, map)) return false;

        int cost;
        if (map != null) {
            Hex fromHex = map.getHexAt(unit.getQ(), unit.getR());

            if (targetHex.getTerrainType() == TerrainType.SEA && fromHex != null && fromHex.getTerrainType() != TerrainType.SEA) {
                return unit.getCurrentAP() >= 1;
            }

            if (fromHex != null) {
                cost = calculateMoveCost(fromHex, targetHex, dq, dr, map.getCurrentSeason());
            } else {
                cost = getBaseSeasonalCost(targetHex, map.getCurrentSeason());
            }
        } else {
            cost = (targetHex.getTerrainType() == TerrainType.SEA) ? 1 : targetHex.getTerrainType().getMovementCost();
        }

        return unit.getCurrentAP() >= cost;
    }

    public void executeMove(Unit unit, Hex targetHex, GameMap map) {
        if (unit == null || targetHex == null || map == null) return;
        if (!canMove(unit, targetHex, map)) return;

        Hex fromHex = map.getHexAt(unit.getQ(), unit.getR());
        int dq      = targetHex.getQ() - unit.getQ();
        int dr      = targetHex.getR() - unit.getR();

        int cost;
        if (targetHex.getTerrainType() == TerrainType.SEA && fromHex != null && fromHex.getTerrainType() != TerrainType.SEA) {
            cost = unit.getCurrentAP();
        } else {
            cost = (fromHex != null)
                    ? calculateMoveCost(fromHex, targetHex, dq, dr, map.getCurrentSeason())
                    : getBaseSeasonalCost(targetHex, map.getCurrentSeason());
        }

        unit.moveTo(targetHex.getQ(), targetHex.getR(), cost);
        map.updateFogOfWar();
    }

    private boolean hasCapacityForUnit(Unit unit, Hex targetHex, GameMap map) {
        UnitType type = unit.getType();
        // Only military units have hex capacity limits
        if (type != UnitType.SWORDSMAN && type != UnitType.ARCHER
                && type != UnitType.CAVALRY && type != UnitType.CATAPULT) {
            return true;
        }

        int tq = targetHex.getQ();
        int tr = targetHex.getR();

        long swords    = map.getUnits().stream().filter(u -> u.isAlive() && u.getQ() == tq && u.getR() == tr && u.getType() == UnitType.SWORDSMAN).count();
        long archers   = map.getUnits().stream().filter(u -> u.isAlive() && u.getQ() == tq && u.getR() == tr && u.getType() == UnitType.ARCHER).count();
        long cavs      = map.getUnits().stream().filter(u -> u.isAlive() && u.getQ() == tq && u.getR() == tr && u.getType() == UnitType.CAVALRY).count();
        long catapults = map.getUnits().stream().filter(u -> u.isAlive() && u.getQ() == tq && u.getR() == tr && u.getType() == UnitType.CATAPULT).count(); // B15

        return switch (type) {
            case SWORDSMAN -> swords    < 2;
            case ARCHER    -> archers   < 2;
            case CAVALRY   -> cavs      < 1;
            case CATAPULT  -> catapults < 1; // B15: max 1 catapult per hex
            default        -> true;
        };
    }

    private int calculateMoveCost(Hex fromHex, Hex toHex, int dq, int dr, Season season) {
        int cost = toHex.getTerrainType().getMovementCost();

        if (toHex.getTerrainType() == TerrainType.SEA) cost = 1;

        boolean roadConnected = fromHex.hasRoad() && toHex.hasRoad();
        if (roadConnected) cost = 1;

        int dir = getDirection(dq, dr);
        boolean crossesRiver = false;

        if (dir >= 0) {
            if (fromHex.hasRiver(dir) || toHex.hasRiver((dir + 3) % 6)) {
                crossesRiver = true;
            }
        }

        if (crossesRiver && !roadConnected) {
            cost += 1;
        }

        return applySeasonalPenalty(cost, toHex, season);
    }

    private int applySeasonalPenalty(int baseCost, Hex toHex, Season season) {
        if (season == Season.WINTER && toHex.getTerrainType() != TerrainType.SEA && toHex.getTerrainType() != TerrainType.MOUNTAIN_RANGE) {
            return baseCost + 1;
        } else if (season == Season.AUTUMN && toHex.getTerrainType() == TerrainType.SEA) {
            return baseCost + 1;
        }
        return baseCost;
    }

    private int getBaseSeasonalCost(Hex toHex, Season season) {
        int cost = toHex.getTerrainType().getMovementCost();
        if (toHex.getTerrainType() == TerrainType.SEA) cost = 1;
        return applySeasonalPenalty(cost, toHex, season);
    }

    private int getDirection(int dq, int dr) {
        if (dq ==  1 && dr ==  0) return 0;
        if (dq ==  1 && dr == -1) return 1;
        if (dq ==  0 && dr == -1) return 2;
        if (dq == -1 && dr ==  0) return 3;
        if (dq == -1 && dr ==  1) return 4;
        if (dq ==  0 && dr ==  1) return 5;
        return -1;
    }

    public boolean canStation(Worker worker, Hex hex, GameMap map) {
        if (worker == null || !worker.isAlive() || worker.isStationed()) return false;
        if (worker.getQ() != hex.getQ() || worker.getR() != hex.getR()) return false;
        if (worker.getCurrentAP() < Worker.getStationApCost()) return false;

        Building building = hex.getBuilding();
        if (building == null || building.isDestroyed() || building.getType() == BuildingType.TOWN_HALL) return false;

        ResourceType res = building.getType().getProducedResource();

        if (building.getType() == BuildingType.DOCK) {
            if (map == null) return false;
            boolean hasFish = false;
            for (int i = 0; i < 6; i++) {
                Hex neighbor = map.getNeighbor(hex, i);
                if (neighbor != null && neighbor.getTerrainType() == TerrainType.SEA && neighbor.hasResource(ResourceType.FOOD)) {
                    hasFish = true;
                    break;
                }
            }
            if (!hasFish) return false;
        } else if (res != ResourceType.NONE && !hex.hasResource(res)) {
            return false;
        }

        return building.getStationedWorkers() < building.getMaxWorkers();
    }

    public boolean handleStation(Worker worker, Hex hex, GameMap map) {
        if (!canStation(worker, hex, map)) return false;
        return worker.stationIn(hex.getBuilding());
    }

    public void handleEject(Worker worker) {
        if (worker != null && worker.isStationed()) worker.eject();
    }

    public boolean canEject(Worker worker) {
        return worker != null && worker.isAlive() && worker.isStationed();
    }

    public boolean handleExpandBorder(BorderExpander expander, GameMap map) {
        if (!expander.canExpand(map)) return false;
        int q = expander.getQ();
        int r = expander.getR();
        expander.consumeAP(GameConfig.EXPAND_AP_COST);
        map.expandBorderAt(q, r);
        map.updateFogOfWar();
        expander.kill();
        GameEventDispatcher.fireBorderExpanded(q, r);
        return true;
    }

    public Unit selectUnitAt(Hex hex, GameMap map) {
        java.util.List<Unit> unitsOnHex = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.getQ() == hex.getQ() && u.getR() == hex.getR())
                .collect(java.util.stream.Collectors.toList());

        if (unitsOnHex.isEmpty()) { lastClickedHex = null; return null; }

        if (hex == lastClickedHex) {
            unitCycleIndex = (unitCycleIndex + 1) % unitsOnHex.size();
        } else {
            unitCycleIndex = 0;
            lastClickedHex = hex;
        }
        return unitsOnHex.get(unitCycleIndex);
    }
}