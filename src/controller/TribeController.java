package controller;

import model.GameMap;
import model.Hex;
import model.Tribe;
import model.TribeCamp;
import model.TribeType;
import model.TerrainType;
import model.ResourceType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TribeController {
    private final GameMap map;

    public TribeController(GameMap map) {
        this.map = map;
    }

    // رفع باگ 01: محل‌یابی دقیق و اسپاون کمپ‌های 5 قبیله
    public void spawnInitialTribes() {
        List<Hex> validHexes = map.getHexes().stream()
                .filter(h -> h.getTerrainType() != TerrainType.SEA && h.getTerrainType() != TerrainType.MOUNTAIN_RANGE)
                .filter(h -> h.getBuilding() == null)
                // فاصله حداقل 6 هکس از TownHall طبق داک
                .filter(h -> map.getHexDistance(map.getTownHall().getQ(), map.getTownHall().getR(), h.getQ(), h.getR()) >= 6)
                .collect(Collectors.toList());

        Collections.shuffle(validHexes);
        TribeType[] types = TribeType.values(); // 5 نوع قبیله که در مدل ساختیم

        for (int i = 0; i < Math.min(types.length, validHexes.size()); i++) {
            Hex hex = validHexes.get(i);
            hex.setBuilding(new TribeCamp(types[i]));
        }
    }

    public void processTribesTurn() {
        // اصلاح باگ 01: پیاده‌سازی رفتار نوبتی قبایل در پایان هر ترن
        for (Hex hex : map.getHexes()) {
            if (hex.getBuilding() instanceof TribeCamp && !hex.getBuilding().isDestroyed()) {
                TribeCamp camp = (TribeCamp) hex.getBuilding();
                Tribe tribe = camp.getTribe();

                if (tribe.getRelationship() <= -50) {
                    // قبیله متخاصم: آمادگی برای Spawn Barbarian در توسعه‌های بعدی
                } else if (tribe.isAllied()) {
                    // پاداش‌های اتحاد در ترن اعمال می‌شود
                    if (tribe.getType() == TribeType.FARMER) {
                        map.getTownHall().getInventory().addResource(ResourceType.FOOD, 5);
                    } else if (tribe.getType() == TribeType.MOUNTAIN) {
                        map.getTownHall().getInventory().addResource(ResourceType.STONE, 5);
                    } else if (tribe.getType() == TribeType.MERCHANT) {
                        map.getTownHall().getInventory().addResource(ResourceType.WOOD, 5);
                    }
                }
            }
        }
    }

    // بررسی و ثبت قوانین محدودیت اتحاد همزمان
    public boolean formAlliance(Tribe targetTribe) {
        if (targetTribe.getRelationship() < 50) return false;

        boolean hasFarmer = false;
        boolean hasMountain = false;
        boolean hasWarrior = false;

        for (Hex h : map.getHexes()) {
            if (h.getBuilding() instanceof TribeCamp && !h.getBuilding().isDestroyed()) {
                Tribe t = ((TribeCamp) h.getBuilding()).getTribe();
                if (t.isAllied()) {
                    if (t.getType() == TribeType.FARMER) hasFarmer = true;
                    if (t.getType() == TribeType.MOUNTAIN) hasMountain = true;
                    if (t.getType() == TribeType.WARRIOR) hasWarrior = true;
                }
            }
        }

        // محدودیت‌ها: جنگجو با هیچکس؛ کشاورز و کوهستانی باهم ممنوع
        if (hasWarrior) return false;
        if (targetTribe.getType() == TribeType.WARRIOR && (hasFarmer || hasMountain)) return false;
        if (targetTribe.getType() == TribeType.FARMER && hasMountain) return false;
        if (targetTribe.getType() == TribeType.MOUNTAIN && hasFarmer) return false;

        targetTribe.setAllied(true);
        return true;
    }
}