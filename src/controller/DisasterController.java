package controller;

import model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class DisasterController {
    private final GameMap map;
    private final Random random;

    private static int bearCooldown = 0;

    public DisasterController(GameMap map) {
        this.map = map;
        this.random = new Random();
    }

    public void checkAndTriggerDisasters() {
        if (bearCooldown > 0) bearCooldown--;

        if (random.nextDouble() > 0.05) return;

        boolean isAutumn = map.getCurrentSeason() == Season.AUTUMN;
        int maxDisaster = isAutumn ? 3 : 2;
        int disasterType = random.nextInt(maxDisaster);

        if (disasterType == 0) triggerEarthquake();
        else if (disasterType == 1) triggerBearAttack();
        else triggerFlood();
    }

    public void processBearAI() {
        List<Unit> bears = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.getClass().getSimpleName().equals("Bear"))
                .collect(Collectors.toList());

        for (Unit bear : bears) {
            if (bear.getCurrentAP() <= 0) continue;

            Unit target = null;
            int minDist = Integer.MAX_VALUE;

            for (Unit u : map.getUnits()) {
                if (u.isAlive() && (u instanceof Worker || u instanceof Builder)) {
                    int d = map.getHexDistance(bear.getQ(), bear.getR(), u.getQ(), u.getR());
                    if (d < minDist) {
                        minDist = d;
                        target = u;
                    }
                }
            }

            if (target != null) {
                if (minDist <= 1) {
                    target.takeDamage(20);
                    bear.consumeAP(bear.getCurrentAP());
                    GameEventDispatcher.fireNotification("⚠️ A Bear attacked your citizens!");
                } else {
                    int dq = Integer.signum(target.getQ() - bear.getQ());
                    int dr = Integer.signum(target.getR() - bear.getR());
                    bear.moveTo(bear.getQ() + dq, bear.getR() + dr, 1);
                }
            }
        }
    }

    private void triggerEarthquake() {
        List<Hex> landHexes = map.getHexes().stream()
                .filter(h -> h.getTerrainType() != TerrainType.SEA)
                .collect(Collectors.toList());

        if (landHexes.isEmpty()) return;

        Hex center = landHexes.get(random.nextInt(landHexes.size()));
        List<Hex> affectedHexes = new ArrayList<>();

        for (Hex h : map.getHexes()) {
            if (map.getHexDistance(center.getQ(), center.getR(), h.getQ(), h.getR()) <= 2) {
                affectedHexes.add(h);

                for (Unit u : map.getUnits()) {
                    if (u.isAlive() && u.getQ() == h.getQ() && u.getR() == h.getR()) {
                        u.takeDamage(10);
                    }
                }

                Building b = h.getBuilding();
                if (b != null && !b.isDestroyed() && b instanceof TownHall) {
                    int currentHp = b.getHp();
                    int dmg = Math.min(50, currentHp - 1);
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
        // رفع باگ 27: اصلاح منطق هدف‌گیری هکس‌ها (چک کردن رودخانه در یال‌ها)
        List<Hex> candidates = map.getHexes().stream()
                .filter(h -> h.getTerrainType() == TerrainType.PLAINS
                        || h.getTerrainType() == TerrainType.MEADOW
                        || h.getTerrainType() == TerrainType.FOREST)
                .filter(h -> {
                    // بررسی اینکه آیا خود هکس یا همسایه‌های مستقیم آن، یالِ رودخانه دارند
                    for (int i = 0; i < 6; i++) {
                        if (h.hasRiver(i)) return true;
                        Hex n = map.getNeighbor(h, i);
                        if (n != null) {
                            for (int j = 0; j < 6; j++) {
                                if (n.hasRiver(j)) return true;
                            }
                        }
                    }
                    return false;
                }).collect(Collectors.toList());

        if (candidates.isEmpty()) return;
        Hex center = candidates.get(random.nextInt(candidates.size()));
        List<Hex> affectedHexes = new ArrayList<>();

        for (Hex h : map.getHexes()) {
            if (map.getHexDistance(center.getQ(), center.getR(), h.getQ(), h.getR()) <= 1) {
                if (h.getTerrainType() == TerrainType.MOUNTAIN
                        || h.getTerrainType() == TerrainType.MOUNTAIN_RANGE) continue;

                affectedHexes.add(h);

                for (Unit u : map.getUnits()) {
                    if (u.isAlive() && u.getQ() == h.getQ() && u.getR() == h.getR()) {
                        u.takeDamage(20);
                        u.consumeAP(u.getCurrentAP());
                    }
                }

                h.setRoad(false);

                Building b = h.getBuilding();
                if (b != null && !b.isDestroyed()) {
                    if (b.getType() == BuildingType.FARM) {
                        b.takeFloodDamage(9999);
                    } else {
                        // رفع باگ 24: فراخوانی متد جدید برای اعمال توقف تولید
                        b.takeFloodDamage(30);
                    }
                }
            }
        }

        GameEventDispatcher.fireDisasterTriggered("FLOOD", center, affectedHexes);

        if (!center.isVisible()) {
            GameEventDispatcher.fireNotification("⚠️ A Flood struck a distant region in the Autumn rains!");
        }
    }

    private void triggerBearAttack() {
        if (bearCooldown > 0) return;

        List<Hex> forests = map.getHexes().stream()
                .filter(h -> h.getTerrainType() == TerrainType.FOREST)
                .collect(Collectors.toList());

        if (forests.isEmpty()) return;

        bearCooldown = 5;
        Hex forestHex = forests.get(random.nextInt(forests.size()));

        long nearbyUnits = map.getUnits().stream()
                .filter(u -> u.isAlive()
                        && map.getHexDistance(forestHex.getQ(), forestHex.getR(), u.getQ(), u.getR()) <= 3)
                .count();

        int bearCount = (nearbyUnits >= 3) ? 2 : 1;
        for (int i = 0; i < bearCount; i++) {
            Bear bear = new Bear(forestHex.getQ(), forestHex.getR());
            map.addUnit(bear);
        }

        List<Hex> affectedHexes = new ArrayList<>();
        for (Hex h : map.getHexes()) {
            if (map.getHexDistance(forestHex.getQ(), forestHex.getR(), h.getQ(), h.getR()) <= 3
                    && h.getTerrainType() == TerrainType.FOREST) {
                affectedHexes.add(h);
            }
        }
        if (affectedHexes.isEmpty()) affectedHexes.add(forestHex);

        GameEventDispatcher.fireDisasterTriggered("BEAR_ATTACK", forestHex, affectedHexes);

        if (!forestHex.isVisible()) {
            GameEventDispatcher.fireNotification("⚠️ Wild Bears emerged from a distant forest!");
        }
    }
}