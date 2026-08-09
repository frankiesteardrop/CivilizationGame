package controller;

import model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class DisasterController {
    private final GameMap map;
    private final Random random;

    public DisasterController(GameMap map) {
        this.map = map;
        this.random = new Random();
    }

    public void checkAndTriggerDisasters() {
        // ۵٪ احتمال وقوع بلای طبیعی در ابتدای هر ترن
        if (random.nextDouble() > 0.05) return;

        boolean isAutumn = map.getCurrentSeason() == Season.AUTUMN;
        int maxDisaster = isAutumn ? 3 : 2;
        int disasterType = random.nextInt(maxDisaster);

        if (disasterType == 0) triggerEarthquake();
        else if (disasterType == 1) triggerBearAttack();
        else triggerFlood();
    }

    private void triggerEarthquake() {
        List<Hex> landHexes = map.getHexes().stream()
                .filter(h -> h.getTerrainType() != TerrainType.SEA)
                .collect(Collectors.toList());

        if (landHexes.isEmpty()) return;

        Hex center = landHexes.get(random.nextInt(landHexes.size()));

        // جمع‌آوری هکس‌های آسیب‌دیده برای نمایش گرافیکی
        List<Hex> affectedHexes = new ArrayList<>();

        for (Hex h : map.getHexes()) {
            if (map.getHexDistance(center.getQ(), center.getR(), h.getQ(), h.getR()) <= 2) {
                affectedHexes.add(h);

                // آسیب کم به یونیت‌ها (-10 HP)
                for (Unit u : map.getUnits()) {
                    if (u.isAlive() && u.getQ() == h.getQ() && u.getR() == h.getR()) {
                        u.takeDamage(10);
                    }
                }

                // آسیب سنگین به تالار شهر (-50 HP تا حداقل 1)
                Building b = h.getBuilding();
                if (b != null && !b.isDestroyed() && b instanceof TownHall) {
                    int currentHp = b.getHp();
                    int dmg = Math.min(50, currentHp - 1);
                    if (dmg > 0) b.takeDamage(dmg);
                }
            }
        }

        // ارسال رویداد به سیستم UI
        GameEventDispatcher.fireDisasterTriggered("EARTHQUAKE", center, affectedHexes);

        // اگر در دید پلیر بود → نمایش انیمیشن (توسط GamePanel)
        // اگر در تاریکی بود → نمایش پیام متنی در HUD
        if (!center.isVisible()) {
            GameEventDispatcher.fireNotification("⚠️ An Earthquake struck a distant region!");
        }
    }

    private void triggerFlood() {
        List<Hex> candidates = map.getHexes().stream()
                .filter(h -> h.getTerrainType() == TerrainType.PLAINS
                        || h.getTerrainType() == TerrainType.MEADOW
                        || h.getTerrainType() == TerrainType.FOREST)
                .filter(h -> {
                    for (int i = 0; i < 6; i++) {
                        Hex n = map.getNeighbor(h, i);
                        if (n != null && (n.getTerrainType() == TerrainType.SEA || h.hasRiver(i))) return true;
                    }
                    return false;
                }).collect(Collectors.toList());

        if (candidates.isEmpty()) return;
        Hex center = candidates.get(random.nextInt(candidates.size()));

        // جمع‌آوری هکس‌های آسیب‌دیده برای نمایش گرافیکی
        List<Hex> affectedHexes = new ArrayList<>();

        for (Hex h : map.getHexes()) {
            if (map.getHexDistance(center.getQ(), center.getR(), h.getQ(), h.getR()) <= 1) {
                // کوهستان و رشته‌کوه مصون هستند
                if (h.getTerrainType() == TerrainType.MOUNTAIN
                        || h.getTerrainType() == TerrainType.MOUNTAIN_RANGE) continue;

                affectedHexes.add(h);

                for (Unit u : map.getUnits()) {
                    if (u.isAlive() && u.getQ() == h.getQ() && u.getR() == h.getR()) {
                        u.takeDamage(20);
                        u.consumeAP(u.getCurrentAP()); // صفر شدن AP
                    }
                }

                h.setRoad(false); // تخریب جاده

                Building b = h.getBuilding();
                if (b != null && !b.isDestroyed()) {
                    if (b.getType() == BuildingType.FARM) {
                        b.takeDamage(9999); // نابودی کامل مزرعه
                    } else {
                        b.takeDamage(30);
                    }
                }
            }
        }

        // ارسال رویداد به سیستم UI
        GameEventDispatcher.fireDisasterTriggered("FLOOD", center, affectedHexes);

        if (!center.isVisible()) {
            GameEventDispatcher.fireNotification("⚠️ A Flood struck a distant region in the Autumn rains!");
        }
    }

    private void triggerBearAttack() {
        List<Hex> forests = map.getHexes().stream()
                .filter(h -> h.getTerrainType() == TerrainType.FOREST)
                .collect(Collectors.toList());

        if (forests.isEmpty()) return;

        Hex forestHex = forests.get(random.nextInt(forests.size()));

        // شمارش یونیت‌های بازیکن در شعاع ۳ هکسی
        long nearbyUnits = map.getUnits().stream()
                .filter(u -> u.isAlive()
                        && map.getHexDistance(forestHex.getQ(), forestHex.getR(), u.getQ(), u.getR()) <= 3)
                .count();

        int bearCount = (nearbyUnits >= 3) ? 2 : 1;
        for (int i = 0; i < bearCount; i++) {
            Bear bear = new Bear(forestHex.getQ(), forestHex.getR());
            map.addUnit(bear);
        }

        // لیست affected: هکس جنگل + هکس‌های مجاور در شعاع ۳ (برای flash گرافیکی)
        List<Hex> affectedHexes = new ArrayList<>();
        for (Hex h : map.getHexes()) {
            if (map.getHexDistance(forestHex.getQ(), forestHex.getR(), h.getQ(), h.getR()) <= 3
                    && h.getTerrainType() == TerrainType.FOREST) {
                affectedHexes.add(h);
            }
        }
        if (affectedHexes.isEmpty()) affectedHexes.add(forestHex);

        // ارسال رویداد به سیستم UI
        GameEventDispatcher.fireDisasterTriggered("BEAR_ATTACK", forestHex, affectedHexes);

        if (!forestHex.isVisible()) {
            GameEventDispatcher.fireNotification("⚠️ Wild Bears emerged from a distant forest!");
        }
    }
}