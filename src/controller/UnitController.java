package controller;

import model.*;

public class UnitController {

    private Hex lastClickedHex = null;
    private int unitCycleIndex = 0;

    // متد Overloaded — بدون map، فقط terrain cost چک می‌شود (برای highlight UI)
    public boolean canMove(Unit unit, Hex targetHex) {
        return canMove(unit, targetHex, null);
    }

    public boolean canMove(Unit unit, Hex targetHex, GameMap map) {
        if (unit == null || !unit.isAlive() || targetHex == null) return false;
        if (unit instanceof Worker && ((Worker) unit).isStationed()) return false;

        // رشته‌کوه: مطلقاً غیرقابل عبور برای همه
        if (targetHex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;

        int dq = targetHex.getQ() - unit.getQ();
        int dr = targetHex.getR() - unit.getR();
        int ds = -dq - dr;

        boolean isNeighbor = (Math.max(Math.max(Math.abs(dq), Math.abs(dr)), Math.abs(ds)) == 1);
        if (!isNeighbor) return false;

        // ─── بررسی ویژه دریا ────────────────────────────────────────────────────
        // دریا فقط با تکنولوژی Seafaring قابل عبور است.
        // با Seafaring: کافی است یونیت حداقل ۱ AP داشته باشد
        // (تمام AP باقی‌مانده در لحظه ورود صفر می‌شود — طبق spec).
        if (targetHex.getTerrainType() == TerrainType.SEA) {
            if (map == null) return false; // بدون context مپ نمی‌توان بررسی کرد
            if (!map.getTownHall().isSeafaringUnlocked()) return false;
            return unit.getCurrentAP() >= 1;
        }

        // ─── محاسبه هزینه برای terrain‌های خشکی ────────────────────────────────
        int cost;
        if (map != null) {
            Hex fromHex = map.getHexAt(unit.getQ(), unit.getR());
            if (fromHex != null) {
                cost = calculateMoveCost(fromHex, targetHex, dq, dr, map.getCurrentSeason());
            } else {
                cost = getBaseSeasonalCost(targetHex, map.getCurrentSeason());
            }
        } else {
            // بدون map: فقط terrain cost پایه (برای highlight قبل از انتخاب)
            cost = targetHex.getTerrainType().getMovementCost();
        }

        return unit.getCurrentAP() >= cost;
    }

    public void executeMove(Unit unit, Hex targetHex, GameMap map) {
        if (unit == null || targetHex == null || map == null) return;
        if (!canMove(unit, targetHex, map)) return;

        // ─── ورود به دریا: تمام AP ترن مصرف می‌شود (طبق spec) ─────────────────
        if (targetHex.getTerrainType() == TerrainType.SEA) {
            // unit.moveTo با currentAP فراخوانی می‌شود تا همه AP صفر شود
            unit.moveTo(targetHex.getQ(), targetHex.getR(), unit.getCurrentAP());
            map.updateFogOfWar();
            return;
        }

        // ─── حرکت معمولی روی خشکی ──────────────────────────────────────────────
        Hex fromHex = map.getHexAt(unit.getQ(), unit.getR());
        int dq = targetHex.getQ() - unit.getQ();
        int dr = targetHex.getR() - unit.getR();

        int cost;
        if (fromHex != null) {
            cost = calculateMoveCost(fromHex, targetHex, dq, dr, map.getCurrentSeason());
        } else {
            cost = getBaseSeasonalCost(targetHex, map.getCurrentSeason());
        }

        unit.moveTo(targetHex.getQ(), targetHex.getR(), cost);
        map.updateFogOfWar();
    }

    /**
     * محاسبه هزینه کامل حرکت از fromHex به toHex با در نظر گرفتن:
     * ۱. هزینه پایه terrain
     * ۲. Road bonus: اگر هر دو hex جاده داشتند → هزینه = 1
     * ۳. River penalty: اگر لبه بین دو hex رودخانه داشت:
     *    - اگر جاده از هر دو طرف باشد → penalty حذف (جاده = پل)
     *    - اگر جاده نباشد → +2 AP
     * ۴. جریمه فصل زمستان (+1 روی خشکی) و پاییز (+1 روی دریا)
     */
    private int calculateMoveCost(Hex fromHex, Hex toHex, int dq, int dr, Season season) {
        // ۱. هزینه پایه terrain مقصد
        int cost = toHex.getTerrainType().getMovementCost();

        // ۲. Road bonus: هر دو hex باید جاده داشته باشند
        boolean roadConnected = fromHex.hasRoad() && toHex.hasRoad();
        if (roadConnected) {
            cost = 1; // جاده هزینه حرکت را به ۱ کاهش می‌دهد (صرف‌نظر از terrain)
        }

        // ۳. River penalty روی لبه بین دو hex
        int dir = getDirection(dq, dr);
        if (dir >= 0 && fromHex.hasRiver(dir)) {
            if (roadConnected) {
                // جاده روی هر دو طرف = پل → penalty رودخانه حذف می‌شود
                // (cost همان ۱ باقی می‌ماند)
            } else {
                // رودخانه بدون پل → +2 AP هزینه اضافه
                cost += 2;
            }
        }

        // ۴. جریمه فصلی
        cost = applySeasonalPenalty(cost, toHex, season);

        return cost;
    }

    /**
     * اعمال جریمه فصلی به هزینه نهایی.
     * زمستان: +1 روی همه هکس‌های خشکی
     * پاییز: +1 روی هکس‌های دریا (سختی حرکت کشتی‌ها)
     */
    private int applySeasonalPenalty(int baseCost, Hex toHex, Season season) {
        if (season == Season.WINTER
                && toHex.getTerrainType() != TerrainType.SEA
                && toHex.getTerrainType() != TerrainType.MOUNTAIN_RANGE) {
            return baseCost + 1;
        } else if (season == Season.AUTUMN && toHex.getTerrainType() == TerrainType.SEA) {
            return baseCost + 1;
        }
        return baseCost;
    }

    /**
     * fallback: فقط terrain + فصل، بدون road/river (وقتی fromHex در دسترس نیست)
     */
    private int getBaseSeasonalCost(Hex toHex, Season season) {
        return applySeasonalPenalty(toHex.getTerrainType().getMovementCost(), toHex, season);
    }

    /**
     * پیدا کردن direction (0-5) از dq و dr.
     * طبق DIRECTIONS در GameMap: {1,0},{1,-1},{0,-1},{-1,0},{-1,1},{0,1}
     */
    private int getDirection(int dq, int dr) {
        if (dq ==  1 && dr ==  0) return 0;
        if (dq ==  1 && dr == -1) return 1;
        if (dq ==  0 && dr == -1) return 2;
        if (dq == -1 && dr ==  0) return 3;
        if (dq == -1 && dr ==  1) return 4;
        if (dq ==  0 && dr ==  1) return 5;
        return -1;
    }

    public boolean canStation(Worker worker, Hex hex) {
        if (worker == null || !worker.isAlive() || worker.isStationed()) return false;
        if (worker.getQ() != hex.getQ() || worker.getR() != hex.getR()) return false;
        if (worker.getCurrentAP() < Worker.getStationApCost()) return false;

        Building building = hex.getBuilding();
        if (building == null || building.isDestroyed() || building.getType() == BuildingType.TOWN_HALL) return false;

        ResourceType producedRes = building.getType().getProducedResource();
        if (producedRes != ResourceType.NONE && !hex.hasResource(producedRes)) return false;

        return building.getStationedWorkers() < building.getMaxWorkers();
    }

    public boolean handleStation(Worker worker, Hex hex) {
        if (!canStation(worker, hex)) return false;
        Building building = hex.getBuilding();
        return worker.stationIn(building);
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

        if (unitsOnHex.isEmpty()) {
            lastClickedHex = null;
            return null;
        }

        if (hex == lastClickedHex) {
            unitCycleIndex = (unitCycleIndex + 1) % unitsOnHex.size();
        } else {
            unitCycleIndex = 0;
            lastClickedHex = hex;
        }
        return unitsOnHex.get(unitCycleIndex);
    }
}