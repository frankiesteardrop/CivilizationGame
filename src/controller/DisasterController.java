package controller;

import model.*;
import java.util.*;
import java.util.stream.Collectors;

public class DisasterController {

    private final GameMap map;
    private final Random  random;

    // Bear AI constants
    private static final int BEAR_MAX_COUNT     = 2;   // حداکثر ۲ خرس همزمان
    private static final int BEAR_COOLDOWN_TURNS = 5;  // ۵ ترن cooldown بعد از spawn
    private static final int BEAR_LAIR_RADIUS   = 3;   // شعاع ماندن از لانه
    private static final int BEAR_DETECT_RADIUS = 5;   // شعاع تشخیص هدف

    public DisasterController(GameMap map) {
        this.map    = map;
        this.random = new Random();
    }

    // ─── Per-Turn Entry Points ────────────────────────────────────────────────

    /**
     * بررسی و trigger کردن بلایای طبیعی.
     * ۵٪ شانس در ابتدای هر ترن.
     *
     * F-33: Bear Attack فقط اگر cooldown == 0 باشد trigger می‌شود.
     * Cooldown در GameMap ذخیره می‌شود (DisasterController stateless است).
     */
    public void checkAndTriggerDisasters() {
        // کاهش cooldown خرس در هر ترن
        map.decrementBearCooldown();

        if (random.nextDouble() > 0.05) return;

        boolean isAutumn   = map.getCurrentSeason() == Season.AUTUMN;
        int     maxDisaster = isAutumn ? 3 : 2;
        int     disasterType = random.nextInt(maxDisaster);

        if (disasterType == 0) {
            triggerEarthquake();
        } else if (disasterType == 1) {
            // Bear attack فقط اگر cooldown تمام شده و سقف ۲ خرس پر نشده باشد
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

    /**
     * F-33: پردازش AI خرس‌های موجود در نقشه — فراخوانی از TurnController.
     *
     * رفتار طبق spec:
     * 1. اول Civilian (Worker/Builder/Explorer/BorderExpander) را هدف می‌گیرد
     * 2. اگر Civilian نبود، Military را هدف می‌گیرد
     * 3. هر ترن ۱ هکس به سمت هدف حرکت می‌کند (AP=2، مصرف ۱ برای حرکت)
     * 4. اگر مجاور هدف است، ۳۵ آسیب می‌زند (AP دوم مصرف می‌شود)
     * 5. در شعاع BEAR_LAIR_RADIUS از نزدیک‌ترین جنگل می‌ماند
     *
     * نکته: AP خرس‌ها قبلاً در TurnController.forceEndTurn() reset شده است.
     */
    public void processBearAI() {
        List<Unit> bears = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.getType() == UnitType.BEAR)
                .collect(Collectors.toList());

        if (bears.isEmpty()) return;

        for (Unit bear : bears) {
            processSingleBearAI(bear);
        }

        // حذف یونیت‌هایی که آسیب bear کشت (از جمله خود bear اگر مرده)
        map.removeDeadUnits();
    }

    // ─── Bear AI ─────────────────────────────────────────────────────────────

    private void processSingleBearAI(Unit bear) {
        if (!bear.isAlive() || bear.getCurrentAP() <= 0) return;

        // پیدا کردن لانه (نزدیک‌ترین هکس جنگل)
        Hex lairHex = findNearestForest(bear.getQ(), bear.getR());
        int lairQ   = (lairHex != null) ? lairHex.getQ() : bear.getQ();
        int lairR   = (lairHex != null) ? lairHex.getR() : bear.getR();

        // پیدا کردن هدف
        Unit target = findBearTarget(bear, lairQ, lairR);
        if (target == null) return;

        int distToTarget = map.getHexDistance(bear.getQ(), bear.getR(),
                target.getQ(), target.getR());

        // اگر مجاور نیست و AP داریم، حرکت کن
        if (distToTarget > 1 && bear.getCurrentAP() >= 1) {
            moveBearTowardTarget(bear, target, lairQ, lairR);
            // به‌روزرسانی فاصله بعد از حرکت
            distToTarget = map.getHexDistance(bear.getQ(), bear.getR(),
                    target.getQ(), target.getR());
        }

        // اگر الان مجاور است و AP داریم، حمله کن
        if (distToTarget <= 1 && bear.getCurrentAP() >= 1) {
            bear.consumeAP(1);
            target.takeDamage(35); // قدرت ۳۵ طبق spec

            if (!target.isAlive()) {
                GameEventDispatcher.fireNotification(
                        "🐻 A bear killed a unit! Stay vigilant.");
            }

            // انیمیشن: flash در hex خرس اگر visible باشد
            Hex bearHex = map.getHexAt(bear.getQ(), bear.getR());
            if (bearHex != null && bearHex.isVisible()) {
                GameEventDispatcher.fireNotification(
                        "🐻 Bear attack! A unit took 35 damage.");
            }
        }
    }

    /**
     * پیدا کردن هدف برای خرس.
     * اولویت: Civilian در شعاع DETECT → Military در شعاع DETECT → null
     */
    private Unit findBearTarget(Unit bear, int lairQ, int lairR) {
        // اول: نزدیک‌ترین Civilian در شعاع از لانه
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

        // بعد: نزدیک‌ترین Military در شعاع از لانه
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

    /**
     * حرکت یک هکس به سمت هدف، در محدوده BEAR_LAIR_RADIUS از لانه.
     * بهترین هکس مجاور که به هدف نزدیک‌تر می‌کند و در محدوده لانه است.
     */
    private void moveBearTowardTarget(Unit bear, Unit target,
                                      int lairQ, int lairR) {
        Hex   bestHex  = null;
        int   bestDist = Integer.MAX_VALUE;
        int[][] dirs = {{1,0},{1,-1},{0,-1},{-1,0},{-1,1},{0,1}};

        for (int[] dir : dirs) {
            int nq = bear.getQ() + dir[0];
            int nr = bear.getR() + dir[1];
            Hex neighbor = map.getHexAt(nq, nr);
            if (neighbor == null) continue;
            if (neighbor.getTerrainType() == TerrainType.SEA) continue;
            if (neighbor.getTerrainType() == TerrainType.MOUNTAIN_RANGE) continue;

            // در محدوده lair radius بماند
            if (map.getHexDistance(lairQ, lairR, nq, nr) > BEAR_LAIR_RADIUS) continue;

            int distToTarget = map.getHexDistance(nq, nr,
                    target.getQ(), target.getR());
            if (distToTarget < bestDist) {
                bestDist = distToTarget;
                bestHex  = neighbor;
            }
        }

        if (bestHex != null) {
            // moveTo مصرف AP می‌کند
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

    // ─── Disaster Triggers ───────────────────────────────────────────────────

    private void triggerEarthquake() {
        List<Hex> landHexes = map.getHexes().stream()
                .filter(h -> h.getTerrainType() != TerrainType.SEA)
                .collect(Collectors.toList());
        if (landHexes.isEmpty()) return;

        Hex       center       = landHexes.get(random.nextInt(landHexes.size()));
        List<Hex> affectedHexes = new ArrayList<>();

        for (Hex h : map.getHexes()) {
            if (map.getHexDistance(center.getQ(), center.getR(),
                    h.getQ(), h.getR()) <= 2) {
                affectedHexes.add(h);

                // آسیب به یونیت‌ها: -10 HP
                for (Unit u : map.getUnits()) {
                    if (u.isAlive() && u.getQ() == h.getQ() && u.getR() == h.getR()) {
                        u.takeDamage(10);
                    }
                }

                // آسیب به TownHall: -50 HP تا حداقل 1
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
        // سیل فقط در پاییز (طبق spec)
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

        Hex       center       = candidates.get(random.nextInt(candidates.size()));
        List<Hex> affectedHexes = new ArrayList<>();

        for (Hex h : map.getHexes()) {
            if (map.getHexDistance(center.getQ(), center.getR(),
                    h.getQ(), h.getR()) <= 1) {
                if (h.getTerrainType() == TerrainType.MOUNTAIN
                        || h.getTerrainType() == TerrainType.MOUNTAIN_RANGE) continue;

                affectedHexes.add(h);

                // آسیب به یونیت‌ها: -20 HP + AP → 0
                for (Unit u : map.getUnits()) {
                    if (u.isAlive() && u.getQ() == h.getQ() && u.getR() == h.getR()) {
                        u.takeDamage(20);
                        u.consumeAP(u.getCurrentAP());
                    }
                }

                h.setRoad(false); // جاده تخریب می‌شود

                Building b = h.getBuilding();
                if (b != null && !b.isDestroyed()) {
                    if (b.getType() == BuildingType.FARM) {
                        // مزرعه کاملاً نابود می‌شود
                        b.takeFloodDamage(9999);
                    } else {
                        // سایر ساختمان‌ها: -30 HP + توقف تولید تا ترن بعد
                        b.takeFloodDamage(30);
                    }
                }
            }
        }

        GameEventDispatcher.fireDisasterTriggered("FLOOD", center, affectedHexes);
        if (!center.isVisible()) {
            GameEventDispatcher.fireNotification(
                    "⚠️ A Flood struck a distant region in the Autumn rains!");
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

        // تعداد خرس جدید: ۱ یا ۲ (در محدوده سقف کلی ۲)
        int newBears = (int) Math.min(
                1 + random.nextInt(2),
                BEAR_MAX_COUNT - currentBears);

        if (newBears <= 0) return;

        for (int i = 0; i < newBears; i++) {
            // F-39: حالا می‌توان از UnitFactory استفاده کرد
            map.addUnit(UnitFactory.createUnit(UnitType.BEAR,
                    forestHex.getQ(), forestHex.getR()));
        }

        // F-33: تنظیم cooldown در GameMap (نه در این object که stateless است)
        map.setBearCooldown(BEAR_COOLDOWN_TURNS);

        // هکس‌های جنگلی در شعاع ۳ برای انیمیشن flash قهوه‌ای
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