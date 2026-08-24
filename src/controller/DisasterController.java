package controller;

import model.*;
import java.util.*;
import java.util.stream.Collectors;

public class DisasterController {

    private final GameMap map;
    private final Random  random;

    private static final int BEAR_MAX_COUNT     = 2;
    private static final int BEAR_COOLDOWN_TURNS = 5;
    private static final int BEAR_LAIR_RADIUS   = 3;
    private static final int BEAR_DETECT_RADIUS = 3;

    public DisasterController(GameMap map) {
        this.map    = map;
        this.random = map.getRandom();
    }

    public void checkAndTriggerDisasters() {
        map.decrementBearCooldown();

        if (random.nextDouble() >= 0.05) return;

        boolean isAutumn   = map.getCurrentSeason() == Season.AUTUMN;
        int     maxDisaster = isAutumn ? 3 : 2;
        int     disasterType = random.nextInt(maxDisaster);

        if (disasterType == 0) {
            triggerEarthquake();
        } else if (disasterType == 1) {
            long currentBears = map.getUnits().stream()
                    .filter(u -> u.isAlive() && u.getType() == UnitType.BEAR)
                    .count();
            if (map.getBearCooldown() <= 0 && currentBears < BEAR_MAX_COUNT) {
                triggerBearAttack();
            }
        } else {
            triggerFlood();
        }
    }

    public void processBearAI() {
        List<Unit> bears = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.getType() == UnitType.BEAR)
                .collect(Collectors.toList());

        if (bears.isEmpty()) return;

        for (Unit bear : bears) {
            processSingleBearAI(bear);
        }

        map.removeDeadUnits();
    }

    private void processSingleBearAI(Unit bear) {
        if (!bear.isAlive() || bear.getCurrentAP() <= 0) return;

        Hex lairHex = findNearestForest(bear.getQ(), bear.getR());
        int lairQ   = (lairHex != null) ? lairHex.getQ() : bear.getQ();
        int lairR   = (lairHex != null) ? lairHex.getR() : bear.getR();

        Unit target = findBearTarget(bear, lairQ, lairR);

        if (target == null) {
            retreatBear(bear, lairQ, lairR);
            return;
        }

        int distToTarget = map.getHexDistance(bear.getQ(), bear.getR(),
                target.getQ(), target.getR());

        if (distToTarget > 1 && bear.getCurrentAP() >= 1) {
            moveBearTowardTarget(bear, target, lairQ, lairR);
            distToTarget = map.getHexDistance(bear.getQ(), bear.getR(),
                    target.getQ(), target.getR());
        }

        if (distToTarget <= 1 && bear.getCurrentAP() >= 1) {
            bear.consumeAP(1);

            // اصلاح گام ششم: تفکیک حمله به نیروی نظامی (با تاس) و غیرنظامی (آسیب مستقیم)
            boolean isMilitaryTarget = (target.getType() == UnitType.SWORDSMAN ||
                    target.getType() == UnitType.ARCHER ||
                    target.getType() == UnitType.CAVALRY);

            if (isMilitaryTarget) {
                int bearDie = random.nextInt(6) + 1;
                int defDie1 = random.nextInt(6) + 1;
                int defDie2 = random.nextInt(6) + 1;
                int maxDefDie = Math.max(defDie1, defDie2);

                List<Integer> atkList = Collections.singletonList(bearDie);
                List<Integer> defList = Arrays.asList(defDie1, defDie2);
                defList.sort(Collections.reverseOrder());

                if (bearDie > maxDefDie) {
                    // خرس برنده شد
                    int dmg = bear.getType().getBaseDamage();
                    target.takeDamage(dmg);
                    GameEventDispatcher.fireCombatTriggered(atkList, defList, 0, dmg);

                    if (!target.isAlive()) {
                        GameEventDispatcher.fireNotification("🐻 A bear overpowered your military unit! (Dice: " + bearDie + " vs " + maxDefDie + ")");
                    } else {
                        GameEventDispatcher.fireNotification("🐻 Bear attack! Military unit took " + dmg + " damage. (Dice: " + bearDie + " vs " + maxDefDie + ")");
                    }
                } else {
                    // مدافع مساوی کرد یا برد (تساوی به نفع بازیکن است)
                    bear.kill();
                    GameEventDispatcher.fireCombatTriggered(atkList, defList, bear.getMaxHp(), 0);
                    GameEventDispatcher.fireNotification("⚔️ Military unit successfully defended against the bear! (Dice: " + maxDefDie + " vs " + bearDie + ")");
                }
            } else {
                // حمله به غیرنظامی (بدون تاس دفاعی)
                int dmg = bear.getType().getBaseDamage();
                target.takeDamage(dmg);
                GameEventDispatcher.fireNotification("🐻 A wild bear attacked a defenseless civilian! Took " + dmg + " damage.");
            }
        }
    }

    private void retreatBear(Unit bear, int lairQ, int lairR) {
        int dist = map.getHexDistance(bear.getQ(), bear.getR(), lairQ, lairR);
        if (dist == 0) {
            bear.kill();
            GameEventDispatcher.fireNotification("🐻 A wild bear has retreated back into the deep forest and disappeared.");
            return;
        }

        if (bear.getCurrentAP() < 1) return;

        int bestDist = Integer.MAX_VALUE;
        Hex bestHex = null;
        int[][] dirs = {{1,0},{1,-1},{0,-1},{-1,0},{0,-1},{0,1}}; // اصلاح شد برای مسیرهای شش ضلعی

        for (int[] dir : dirs) {
            int nq = bear.getQ() + dir[0];
            int nr = bear.getR() + dir[1];
            Hex neighbor = map.getHexAt(nq, nr);
            if (neighbor == null || neighbor.getTerrainType() == TerrainType.SEA || neighbor.getTerrainType() == TerrainType.MOUNTAIN_RANGE) continue;

            int d = map.getHexDistance(nq, nr, lairQ, lairR);
            if (d < bestDist) {
                bestDist = d;
                bestHex = neighbor;
            }
        }

        if (bestHex != null) {
            bear.moveTo(bestHex.getQ(), bestHex.getR(), 1);
        }
    }

    private Unit findBearTarget(Unit bear, int lairQ, int lairR) {
        Optional<Unit> civilian = map.getUnits().stream()
                .filter(u -> u.isAlive()
                        && (u.getType() == UnitType.WORKER
                        || u.getType() == UnitType.BUILDER
                        || u.getType() == UnitType.EXPLORER
                        || u.getType() == UnitType.BORDER_EXPANDER)
                        && map.getHexDistance(lairQ, lairR,
                        u.getQ(), u.getR()) <= BEAR_DETECT_RADIUS)
                .min(Comparator.comparingInt(u -> map.getHexDistance(
                        bear.getQ(), bear.getR(), u.getQ(), u.getR())));

        if (civilian.isPresent()) return civilian.get();

        return map.getUnits().stream()
                .filter(u -> u.isAlive()
                        && (u.getType() == UnitType.SWORDSMAN
                        || u.getType() == UnitType.ARCHER
                        || u.getType() == UnitType.CAVALRY)
                        && map.getHexDistance(lairQ, lairR,
                        u.getQ(), u.getR()) <= BEAR_DETECT_RADIUS)
                .min(Comparator.comparingInt(u -> map.getHexDistance(
                        bear.getQ(), bear.getR(), u.getQ(), u.getR())))
                .orElse(null);
    }

    private void moveBearTowardTarget(Unit bear, Unit target,
                                      int lairQ, int lairR) {
        Hex  bestHex  = null;
        int  bestDist = Integer.MAX_VALUE;
        int[][] dirs = {{1,0},{1,-1},{0,-1},{-1,0},{-1,1},{0,1}};

        for (int[] dir : dirs) {
            int nq = bear.getQ() + dir[0];
            int nr = bear.getR() + dir[1];
            Hex neighbor = map.getHexAt(nq, nr);
            if (neighbor == null) continue;
            if (neighbor.getTerrainType() == TerrainType.SEA) continue;
            if (neighbor.getTerrainType() == TerrainType.MOUNTAIN_RANGE) continue;

            if (map.getHexDistance(lairQ, lairR, nq, nr) > BEAR_LAIR_RADIUS) continue;

            int distToTarget = map.getHexDistance(nq, nr,
                    target.getQ(), target.getR());
            if (distToTarget < bestDist) {
                bestDist = distToTarget;
                bestHex  = neighbor;
            }
        }

        if (bestHex != null) {
            bear.moveTo(bestHex.getQ(), bestHex.getR(), 1);
        }
    }

    private Hex findNearestForest(int q, int r) {
        return map.getHexes().stream()
                .filter(h -> h.getTerrainType() == TerrainType.FOREST)
                .min(Comparator.comparingInt(h ->
                        map.getHexDistance(q, r, h.getQ(), h.getR())))
                .orElse(null);
    }

    private void triggerEarthquake() {
        List<Hex> landHexes = map.getHexes().stream()
                .filter(h -> h.getTerrainType() != TerrainType.SEA)
                .collect(Collectors.toList());
        if (landHexes.isEmpty()) return;

        Hex        center        = landHexes.get(random.nextInt(landHexes.size()));
        List<Hex> affectedHexes = new ArrayList<>();

        for (Hex h : map.getHexes()) {
            if (map.getHexDistance(center.getQ(), center.getR(),
                    h.getQ(), h.getR()) <= 2) {
                affectedHexes.add(h);

                for (Unit u : map.getUnits()) {
                    if (u.isAlive() && u.getQ() == h.getQ() && u.getR() == h.getR()) {
                        u.takeDamage(10);
                    }
                }

                Building b = h.getBuilding();
                if (b != null && !b.isDestroyed() && b instanceof TownHall) {
                    int dmg = Math.min(50, b.getHp() - 1);
                    if (dmg > 0) b.takeDamage(dmg);
                }
            }
        }

        GameEventDispatcher.fireDisasterTriggered("EARTHQUAKE", center, affectedHexes);
        if (!center.isVisible()) {
            GameEventDispatcher.fireNotification("⚠️ An Earthquake struck a distant region!");
        }
    }

    private void triggerFlood() {
        if (map.getCurrentSeason() != Season.AUTUMN) return;

        List<Hex> candidates = map.getHexes().stream()
                .filter(h -> h.getTerrainType() == TerrainType.PLAINS
                        || h.getTerrainType() == TerrainType.MEADOW
                        || h.getTerrainType() == TerrainType.FOREST)
                .filter(h -> {
                    for (int i = 0; i < 6; i++) {
                        Hex n = map.getNeighbor(h, i);
                        if (n != null
                                && (n.getTerrainType() == TerrainType.SEA
                                || h.hasRiver(i))) return true;
                    }
                    return false;
                }).collect(Collectors.toList());

        if (candidates.isEmpty()) return;

        Hex        center        = candidates.get(random.nextInt(candidates.size()));
        List<Hex> affectedHexes = new ArrayList<>();

        int roadsDestroyed = 0;
        int farmsDestroyed = 0;

        for (Hex h : map.getHexes()) {
            if (map.getHexDistance(center.getQ(), center.getR(),
                    h.getQ(), h.getR()) <= 1) {
                if (h.getTerrainType() == TerrainType.MOUNTAIN
                        || h.getTerrainType() == TerrainType.MOUNTAIN_RANGE) continue;

                affectedHexes.add(h);

                for (Unit u : map.getUnits()) {
                    if (u.isAlive() && u.getQ() == h.getQ() && u.getR() == h.getR()) {
                        u.takeDamage(20);
                        u.consumeAP(u.getCurrentAP());
                    }
                }

                if (h.hasRoad()) {
                    h.setRoad(false);
                    roadsDestroyed++;
                }

                Building b = h.getBuilding();
                if (b != null && !b.isDestroyed()) {
                    if (b.getType() == BuildingType.FARM) {
                        b.takeFloodDamage(9999);
                        farmsDestroyed++;
                    } else {
                        b.takeFloodDamage(30);
                    }

                    if (b.isDestroyed()) {
                        GameEventDispatcher.fireBuildingDestroyed(h);
                    }
                }
            }
        }

        GameEventDispatcher.fireDisasterTriggered("FLOOD", center, affectedHexes);

        if (!center.isVisible()) {
            GameEventDispatcher.fireNotification(
                    "⚠️ A Flood struck a distant region in the Autumn rains!");
        }

        if (roadsDestroyed > 0 || farmsDestroyed > 0) {
            GameEventDispatcher.fireNotification(
                    String.format("🌊 Flood destroyed %d road(s) and %d farm(s)!",
                            roadsDestroyed, farmsDestroyed));
        }
    }

    private void triggerBearAttack() {
        List<Hex> forests = map.getHexes().stream()
                .filter(h -> h.getTerrainType() == TerrainType.FOREST)
                .collect(Collectors.toList());
        if (forests.isEmpty()) return;

        Hex  forestHex = forests.get(random.nextInt(forests.size()));
        long currentBears = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.getType() == UnitType.BEAR)
                .count();

        int newBears = (int) Math.min(
                1 + random.nextInt(2),
                BEAR_MAX_COUNT - currentBears);

        if (newBears <= 0) return;

        for (int i = 0; i < newBears; i++) {
            map.addUnit(UnitFactory.createUnit(UnitType.BEAR,
                    forestHex.getQ(), forestHex.getR()));
        }

        map.setBearCooldown(BEAR_COOLDOWN_TURNS);

        List<Hex> affectedHexes = map.getHexes().stream()
                .filter(h -> h.getTerrainType() == TerrainType.FOREST
                        && map.getHexDistance(forestHex.getQ(), forestHex.getR(),
                        h.getQ(), h.getR()) <= 3)
                .collect(Collectors.toList());
        if (affectedHexes.isEmpty()) affectedHexes.add(forestHex);

        GameEventDispatcher.fireDisasterTriggered("BEAR_ATTACK", forestHex, affectedHexes);
        if (!forestHex.isVisible()) {
            GameEventDispatcher.fireNotification(
                    "⚠️ Wild Bears have emerged from a distant forest!");
        }
    }
}