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

        // رشته‌کوه: مطلقاً غیرقابل عبور
        if (targetHex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;

        int dq = targetHex.getQ() - unit.getQ();
        int dr = targetHex.getR() - unit.getR();
        int ds = -dq - dr;

        boolean isNeighbor = (Math.max(Math.max(Math.abs(dq), Math.abs(dr)), Math.abs(ds)) == 1);
        if (!isNeighbor) return false;

        // ─── دریا: نیاز به Seafaring ──────────────────────────────────────────
        if (targetHex.getTerrainType() == TerrainType.SEA) {
            if (map == null) return false;
            if (!map.getTownHall().isSeafaringUnlocked()) return false;
            return unit.getCurrentAP() >= 1;
        }

        // ─── F-21: بررسی ظرفیت هکس مقصد برای یونیت‌های نظامی ────────────────
        if (map != null && !hasCapacityForUnit(unit, targetHex, map)) return false;

        // ─── محاسبه هزینه برای terrain‌های خشکی ─────────────────────────────
        int cost;
        if (map != null) {
            Hex fromHex = map.getHexAt(unit.getQ(), unit.getR());
            if (fromHex != null) {
                cost = calculateMoveCost(fromHex, targetHex, dq, dr,
                        map.getCurrentSeason());
            } else {
                cost = getBaseSeasonalCost(targetHex, map.getCurrentSeason());
            }
        } else {
            cost = targetHex.getTerrainType().getMovementCost();
        }

        return unit.getCurrentAP() >= cost;
    }

    public void executeMove(Unit unit, Hex targetHex, GameMap map) {
        if (unit == null || targetHex == null || map == null) return;
        if (!canMove(unit, targetHex, map)) return;

        // ورود به دریا: تمام AP مصرف می‌شود (طبق spec)
        if (targetHex.getTerrainType() == TerrainType.SEA) {
            unit.moveTo(targetHex.getQ(), targetHex.getR(), unit.getCurrentAP());
            map.updateFogOfWar();
            return;
        }

        Hex fromHex = map.getHexAt(unit.getQ(), unit.getR());
        int dq      = targetHex.getQ() - unit.getQ();
        int dr      = targetHex.getR() - unit.getR();

        int cost = (fromHex != null)
                ? calculateMoveCost(fromHex, targetHex, dq, dr, map.getCurrentSeason())
                : getBaseSeasonalCost(targetHex, map.getCurrentSeason());

        unit.moveTo(targetHex.getQ(), targetHex.getR(), cost);
        map.updateFogOfWar();
    }

    /**
     * F-21: بررسی ظرفیت هکس مقصد برای یونیت نظامی.
     * طبق spec: حداکثر ۲ Swordsman، ۲ Archer، ۱ Cavalry در هر hex.
     */
    private boolean hasCapacityForUnit(Unit unit, Hex targetHex, GameMap map) {
        UnitType type = unit.getType();
        if (type != UnitType.SWORDSMAN
                && type != UnitType.ARCHER
                && type != UnitType.CAVALRY) {
            return true; // غیرنظامی محدودیت ندارد
        }

        int tq = targetHex.getQ();
        int tr = targetHex.getR();

        long swords  = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.getQ() == tq && u.getR() == tr
                        && u.getType() == UnitType.SWORDSMAN).count();
        long archers = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.getQ() == tq && u.getR() == tr
                        && u.getType() == UnitType.ARCHER).count();
        long cavs    = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.getQ() == tq && u.getR() == tr
                        && u.getType() == UnitType.CAVALRY).count();

        return switch (type) {
            case SWORDSMAN -> swords  < 2;
            case ARCHER    -> archers < 2;
            case CAVALRY   -> cavs    < 1;
            default        -> true;
        };
    }

    /**
     * محاسبه هزینه کامل حرکت.
     * ۱. هزینه پایه terrain
     * ۲. Road bonus: هر دو hex جاده → cost = 1
     * ۳. River penalty: لبه رودخانه‌دار بدون پل → +2 AP
     * ۴. جریمه فصلی (زمستان +1 خشکی، پاییز +1 دریا)
     */
    private int calculateMoveCost(Hex fromHex, Hex toHex, int dq, int dr, Season season) {
        int cost = toHex.getTerrainType().getMovementCost();

        boolean roadConnected = fromHex.hasRoad() && toHex.hasRoad();
        if (roadConnected) cost = 1;

        int dir = getDirection(dq, dr);
        if (dir >= 0 && fromHex.hasRiver(dir)) {
            if (!roadConnected) cost += 2;
        }

        return applySeasonalPenalty(cost, toHex, season);
    }

    private int applySeasonalPenalty(int baseCost, Hex toHex, Season season) {
        if (season == Season.WINTER
                && toHex.getTerrainType() != TerrainType.SEA
                && toHex.getTerrainType() != TerrainType.MOUNTAIN_RANGE) {
            return baseCost + 1;
        } else if (season == Season.AUTUMN
                && toHex.getTerrainType() == TerrainType.SEA) {
            return baseCost + 1;
        }
        return baseCost;
    }

    private int getBaseSeasonalCost(Hex toHex, Season season) {
        return applySeasonalPenalty(toHex.getTerrainType().getMovementCost(), toHex, season);
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

    // ─── Worker ───────────────────────────────────────────────────────────────

    public boolean canStation(Worker worker, Hex hex) {
        if (worker == null || !worker.isAlive() || worker.isStationed()) return false;
        if (worker.getQ() != hex.getQ() || worker.getR() != hex.getR()) return false;
        if (worker.getCurrentAP() < Worker.getStationApCost()) return false;

        Building building = hex.getBuilding();
        if (building == null || building.isDestroyed()
                || building.getType() == BuildingType.TOWN_HALL) return false;

        ResourceType res = building.getType().getProducedResource();
        if (res != ResourceType.NONE && !hex.hasResource(res)) return false;

        return building.getStationedWorkers() < building.getMaxWorkers();
    }

    public boolean handleStation(Worker worker, Hex hex) {
        if (!canStation(worker, hex)) return false;
        return worker.stationIn(hex.getBuilding());
    }

    public void handleEject(Worker worker) {
        if (worker != null && worker.isStationed()) worker.eject();
    }

    public boolean canEject(Worker worker) {
        return worker != null && worker.isAlive() && worker.isStationed();
    }

    // ─── BorderExpander ───────────────────────────────────────────────────────

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

    // ─── Selection ────────────────────────────────────────────────────────────

    public Unit selectUnitAt(Hex hex, GameMap map) {
        java.util.List<Unit> unitsOnHex = map.getUnits().stream()
                .filter(u -> u.isAlive()
                        && u.getQ() == hex.getQ()
                        && u.getR() == hex.getR())
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