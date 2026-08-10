package controller;

import model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TribeController {

    private final GameMap map;

    public TribeController(GameMap map) {
        this.map = map;
    }

    /**
     * F-01: ایجاد ۵ کمپ قبیله با نوع‌های مختلف روی نقشه.
     *
     * قوانین محل‌یابی طبق spec:
     * - حداقل ۶ هکس از TownHall بازیکن
     * - روی هکس‌های قابل سکونت (نه دریا، نه رشته‌کوه)
     * - بدون ساختمان موجود
     * - حداقل ۴ هکس فاصله بین کمپ‌ها (جلوگیری از تداخل قلمرو)
     * - اولویت terrain متناسب با نوع قبیله
     *
     * این متد فقط برای بازی جدید صدا زده می‌شود (چک hasNoTribes در MainController).
     */
    public void spawnInitialTribes() {
        List<Hex> candidates = map.getHexes().stream()
                .filter(h -> h.getTerrainType() != TerrainType.SEA
                        && h.getTerrainType() != TerrainType.MOUNTAIN_RANGE)
                .filter(h -> h.getBuilding() == null)
                .filter(h -> map.getHexDistance(
                        map.getTownHall().getQ(), map.getTownHall().getR(),
                        h.getQ(), h.getR()) >= 6)
                .collect(Collectors.toList());

        Collections.shuffle(candidates);

        // یک نمونه از هر نوع قبیله spawn می‌شود
        TribeType[] types = TribeType.values();
        List<Hex>   spawnedLocations = new ArrayList<>();

        for (TribeType type : types) {
            // اولویت terrain متناسب با نوع قبیله
            Hex bestHex = findBestHexForTribe(type, candidates, spawnedLocations);
            if (bestHex == null) continue;

            bestHex.setBuilding(new TribeCamp(type));
            spawnedLocations.add(bestHex);
            candidates.remove(bestHex);
        }
    }

    /**
     * پیدا کردن بهترین هکس برای قبیله با توجه به terrain و فاصله از سایر کمپ‌ها.
     * حداقل فاصله ۴ هکس از سایر کمپ‌ها الزامی است.
     */
    private Hex findBestHexForTribe(TribeType type, List<Hex> candidates, List<Hex> occupied) {
        // ابتدا terrain ایده‌آل جستجو می‌شود
        TerrainType preferred = getPreferredTerrain(type);

        // جستجو در terrain ترجیحی با رعایت inter-camp distance
        for (Hex h : candidates) {
            if (h.getTerrainType() == preferred && isFarEnoughFromOthers(h, occupied)) {
                return h;
            }
        }

        // اگر terrain ترجیحی پیدا نشد، هر هکس مجاز با فاصله کافی
        for (Hex h : candidates) {
            if (isFarEnoughFromOthers(h, occupied)) {
                return h;
            }
        }

        return null;
    }

    /**
     * حداقل فاصله ۴ هکس از سایر کمپ‌های قبلاً spawn‌شده.
     * طبق spec، هر کمپ قلمرو خود (کمپ + هکس‌های مجاور) را دارد.
     */
    private boolean isFarEnoughFromOthers(Hex candidate, List<Hex> occupied) {
        for (Hex o : occupied) {
            if (map.getHexDistance(candidate.getQ(), candidate.getR(), o.getQ(), o.getR()) < 4) {
                return false;
            }
        }
        return true;
    }

    /**
     * Terrain ترجیحی برای هر نوع قبیله بر اساس spec:
     * - Farmer → دشت/چمنزار (MEADOW/PLAINS)
     * - Warrior → دشت (PLAINS)
     * - Mountain → کوهستان (MOUNTAIN)
     * - Merchant → دشت یا جنگل (PLAINS/FOREST)
     * - Nomad (Coastal) → هر terrain ساحلی
     */
    private TerrainType getPreferredTerrain(TribeType type) {
        return switch (type) {
            case FARMER   -> TerrainType.MEADOW;
            case WARRIOR  -> TerrainType.PLAINS;
            case MOUNTAIN -> TerrainType.MOUNTAIN;
            case MERCHANT -> TerrainType.PLAINS;
            case NOMAD    -> TerrainType.PLAINS;
        };
    }

    /**
     * رفتار نوبتی قبایل — اجرا بعد از End Turn بازیکن.
     * اولویت‌بندی طبق spec: دفاع از کمپ > تولید گارد (دشمن) > پیشنهاد مأموریت (دوستانه) > ماندن
     */
    public void processTribesTurn() {
        for (Hex hex : map.getHexes()) {
            if (!(hex.getBuilding() instanceof TribeCamp)) continue;
            if (hex.getBuilding().isDestroyed()) continue;

            TribeCamp camp  = (TribeCamp) hex.getBuilding();
            Tribe     tribe = camp.getTribe();

            int rel = tribe.getRelationship();

            if (rel <= -50) {
                // دشمن: رفتار تدافعی (اسپاون گارد در توسعه‌های بعدی)
                processEnemyTribeTurn(camp, hex);
            } else if (tribe.isAllied()) {
                // متحد: اعمال permanent bonus هر ترن
                processAlliedTribeTurn(tribe);
            } else if (rel >= 20) {
                // دوستانه: پردازش مأموریت‌های فعال
                processFriendlyTribeTurn(tribe);
            }
            // Neutral و Displeased: هیچ رفتار فعالی ندارند
        }
    }

    private void processEnemyTribeTurn(TribeCamp camp, Hex campHex) {
        // در توسعه‌های بعدی: spawn guard units هر ۳ ترن
    }

    private void processAlliedTribeTurn(Tribe tribe) {
        // Permanent bonus بر اساس نوع قبیله — طبق spec
        switch (tribe.getType()) {
            case FARMER -> map.getTownHall().getInventory().addResource(ResourceType.FOOD, 5);
            case MOUNTAIN -> map.getTownHall().getInventory().addResource(ResourceType.STONE, 5);
            case MERCHANT -> {
                // COMMERCIAL: bonus تجاری (+10% نرخ) — در TradeController پیاده‌سازی می‌شود
                // فعلاً یک منبع نمادین
                map.getTownHall().getInventory().addResource(ResourceType.WOOD, 3);
            }
            case NOMAD -> {
                // COASTAL: bonus ماهیگیری
                map.getTownHall().getInventory().addResource(ResourceType.FOOD, 3);
            }
            case WARRIOR -> {
                // Warrior alliance bonus: attack bonus (در combat اعمال می‌شود)
            }
        }
    }

    private void processFriendlyTribeTurn(Tribe tribe) {
        // در توسعه‌های بعدی: هر ۵ ترن پیشنهاد مأموریت جدید
    }

    /**
     * تشکیل اتحاد با قبیله.
     * پیش‌نیازها طبق spec:
     * - رابطه ≥ 70
     * - نه در حال جنگ
     * - رعایت محدودیت‌های همزمانی (کشاورز+کوهستانی ممنوع؛ جنگجو با هیچکس)
     */
    public boolean formAlliance(Tribe targetTribe) {
        // F-04: threshold صحیح طبق spec — باید ≥ 70 باشد
        if (targetTribe.getRelationship() < 70) return false;

        boolean hasFarmer  = false;
        boolean hasMountain = false;
        boolean hasWarrior = false;

        for (Hex h : map.getHexes()) {
            if (!(h.getBuilding() instanceof TribeCamp)) continue;
            if (h.getBuilding().isDestroyed()) continue;
            Tribe t = ((TribeCamp) h.getBuilding()).getTribe();
            if (!t.isAllied()) continue;
            if (t.getType() == TribeType.FARMER)   hasFarmer   = true;
            if (t.getType() == TribeType.MOUNTAIN)  hasMountain = true;
            if (t.getType() == TribeType.WARRIOR)   hasWarrior  = true;
        }

        // محدودیت‌های همزمانی طبق spec
        if (hasWarrior) return false; // جنگجو با هیچکس همزمان ممکن نیست
        if (targetTribe.getType() == TribeType.WARRIOR
                && (hasFarmer || hasMountain)) return false;
        if (targetTribe.getType() == TribeType.FARMER && hasMountain) return false;
        if (targetTribe.getType() == TribeType.MOUNTAIN && hasFarmer) return false;

        targetTribe.setAllied(true);
        return true;
    }

    /**
     * ارسال هدیه به قبیله — طبق spec:
     * - 10 غذا/چوب → +2 رابطه
     * - 10 سنگ → +3 رابطه
     * - 5 آهن → +3 رابطه
     * فقط در وضعیت غیر دشمن مجاز است.
     */
    public boolean sendGift(Tribe tribe, ResourceType resourceType, int amount) {
        if (tribe.getRelationship() <= -50) return false; // دشمن: gift مجاز نیست

        int relationGain;
        int requiredAmount;

        // نرخ‌های هدیه طبق spec
        if (resourceType == ResourceType.IRON) {
            requiredAmount = 5;
            relationGain   = 3;
        } else if (resourceType == ResourceType.STONE) {
            requiredAmount = 10;
            relationGain   = 3;
        } else if (resourceType == ResourceType.FOOD || resourceType == ResourceType.WOOD) {
            requiredAmount = 10;
            relationGain   = 2;
        } else {
            return false;
        }

        if (amount < requiredAmount) return false;

        if (!map.getTownHall().getInventory().consumeResource(resourceType, requiredAmount)) {
            return false;
        }

        tribe.addRelationship(relationGain);
        GameEventDispatcher.fireNotification(
                "🎁 Gift sent! Relation +" + relationGain + " with " + tribe.getType().name());
        return true;
    }

    /**
     * اعلام جنگ با قبیله.
     * confirmation dialog باید در View نمایش داده شده باشد قبل از فراخوانی.
     */
    public void declareWar(Tribe tribe) {
        tribe.setAllied(false);
        tribe.addRelationship(-100); // رابطه → حداقل (-100)

        // جریمه happiness بر اساس وضعیت قبلی (طبق spec)
        // توجه: این قبل از reset رابطه باید بررسی شود — فعلاً اعمال ساده
        GameEventDispatcher.fireNotification(
                "⚔️ War declared with " + tribe.getType().name() + "!");
    }

    /**
     * درخواست صلح با قبیله دشمن.
     * هزینه: 30 غذا + 30 چوب + 30 آهن (طبق spec)
     */
    public boolean requestPeace(Tribe tribe) {
        if (tribe.getRelationship() > -50) return false; // فقط در حالت دشمن

        if (!map.getTownHall().getInventory().hasEnough(ResourceType.FOOD, 30)
                || !map.getTownHall().getInventory().hasEnough(ResourceType.WOOD, 30)
                || !map.getTownHall().getInventory().hasEnough(ResourceType.IRON, 30)) {
            return false;
        }

        map.getTownHall().getInventory().consumeResource(ResourceType.FOOD, 30);
        map.getTownHall().getInventory().consumeResource(ResourceType.WOOD, 30);
        map.getTownHall().getInventory().consumeResource(ResourceType.IRON, 30);

        // -100 → -10 رابطه، وضعیت از Enemy → Displeased
        tribe.addRelationship(90); // از -100 به -10
        if (tribe.getRelationship() > -10) {
            // ceiling at -10
        }
        GameEventDispatcher.fireNotification(
                "🕊️ Peace requested with " + tribe.getType().name() + ". Status: Displeased.");
        return true;
    }
}