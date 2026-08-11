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
     * - حداقل ۴ هکس فاصله بین کمپ‌ها
     * - اولویت terrain متناسب با نوع قبیله
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

        TribeType[] types = TribeType.values();
        List<Hex>   spawnedLocations = new ArrayList<>();

        for (TribeType type : types) {
            Hex bestHex = findBestHexForTribe(type, candidates, spawnedLocations);
            if (bestHex == null) continue;

            bestHex.setBuilding(new TribeCamp(type));
            spawnedLocations.add(bestHex);
            candidates.remove(bestHex);
        }
    }

    private Hex findBestHexForTribe(TribeType type, List<Hex> candidates, List<Hex> occupied) {
        TerrainType preferred = getPreferredTerrain(type);

        // اولویت: terrain ترجیحی + فاصله کافی
        for (Hex h : candidates) {
            if (h.getTerrainType() == preferred && isFarEnoughFromOthers(h, occupied)) return h;
        }

        // fallback: هر terrain قابل قبول با فاصله کافی
        for (Hex h : candidates) {
            if (isFarEnoughFromOthers(h, occupied)) return h;
        }

        return null;
    }

    /** حداقل فاصله ۴ هکس از سایر کمپ‌های spawn‌شده. */
    private boolean isFarEnoughFromOthers(Hex candidate, List<Hex> occupied) {
        for (Hex o : occupied) {
            if (map.getHexDistance(candidate.getQ(), candidate.getR(), o.getQ(), o.getR()) < 4)
                return false;
        }
        return true;
    }

    /**
     * Terrain ترجیحی برای هر نوع قبیله طبق spec:
     * - FARMER    → چمنزار (MEADOW)
     * - WARRIOR   → دشت (PLAINS)
     * - MOUNTAIN  → کوهستان (MOUNTAIN)
     * - COMMERCIAL → دشت یا جنگل (PLAINS) — اصلاح F-37
     * - COASTAL   → دشت ساحلی (PLAINS) — اصلاح F-37
     */
    private TerrainType getPreferredTerrain(TribeType type) {
        return switch (type) {
            case FARMER     -> TerrainType.MEADOW;
            case WARRIOR    -> TerrainType.PLAINS;
            case MOUNTAIN   -> TerrainType.MOUNTAIN;
            case COMMERCIAL -> TerrainType.PLAINS;   // اصلاح: قبلاً MERCHANT
            case COASTAL    -> TerrainType.PLAINS;   // اصلاح: قبلاً NOMAD
        };
    }

    /**
     * رفتار نوبتی قبایل — اجرا بعد از End Turn بازیکن.
     * اولویت طبق spec: دفاع > تولید گارد (دشمن) > mission (دوستانه) > ماندن
     */
    public void processTribesTurn() {
        for (Hex hex : map.getHexes()) {
            if (!(hex.getBuilding() instanceof TribeCamp)) continue;
            if (hex.getBuilding().isDestroyed()) continue;

            TribeCamp camp  = (TribeCamp) hex.getBuilding();
            Tribe     tribe = camp.getTribe();

            // F-03: استفاده از getStatus() با بازه‌های صحیح
            switch (tribe.getStatus()) {
                case "Enemy"     -> processEnemyTribeTurn(camp, hex);
                case "Allied"    -> processAlliedTribeTurn(tribe);
                case "Friendly"  -> processFriendlyTribeTurn(tribe);
                // Neutral و Displeased: بدون رفتار فعال
            }
        }
    }

    private void processEnemyTribeTurn(TribeCamp camp, Hex campHex) {
        // توسعه بعدی: spawn guard units هر ۳ ترن
        // Warrior: تا ۵ گارد؛ بقیه: تا ۳ گارد
    }

    /**
     * Permanent bonus قبایل متحد هر ترن — طبق spec:
     * - FARMER    → +۵ غذا/ترن
     * - MOUNTAIN  → +۵ سنگ/ترن
     * - COMMERCIAL → +۱۰٪ نرخ تجارت (نمادین: +۳ چوب تا پیاده‌سازی کامل TradeController)
     * - COASTAL   → bonus ماهیگیری (نمادین: +۳ غذا تا پیاده‌سازی Dock bonus)
     * - WARRIOR   → attack bonus نزدیک کمپ (در CombatController اعمال می‌شود)
     *
     * اصلاح F-37: MERCHANT → COMMERCIAL، NOMAD → COASTAL
     */
    private void processAlliedTribeTurn(Tribe tribe) {
        switch (tribe.getType()) {
            case FARMER -> map.getTownHall().getInventory()
                    .addResource(ResourceType.FOOD, 5);

            case MOUNTAIN -> map.getTownHall().getInventory()
                    .addResource(ResourceType.STONE, 5);

            case COMMERCIAL ->
                // اصلاح F-37 + F-26: COMMERCIAL bonus تجاری
                // در پیاده‌سازی کامل TradeController، نرخ تجارت +۱۰٪ می‌شود.
                // فعلاً یک منبع نمادین تا وقتی TradeController این flag را بررسی کند.
                    map.getTownHall().getInventory()
                            .addResource(ResourceType.WOOD, 3);

            case COASTAL ->
                // اصلاح F-37 + F-26: COASTAL bonus ماهیگیری
                // در پیاده‌سازی کامل: Dock production افزایش می‌یابد.
                    map.getTownHall().getInventory()
                            .addResource(ResourceType.FOOD, 3);

            case WARRIOR -> {
                // Warrior alliance: attack bonus نزدیک کمپ
                // این در CombatController پیاده‌سازی می‌شود.
            }
        }
    }

    private void processFriendlyTribeTurn(Tribe tribe) {
        // توسعه بعدی: هر ۵ ترن پیشنهاد مأموریت جدید
    }

    // ─── اعمال رابطه ─────────────────────────────────────────────────────────

    /**
     * تشکیل اتحاد با قبیله.
     * پیش‌نیازها طبق spec:
     * - رابطه ≥ ۷۰ (اصلاح F-04: قبلاً ۵۰ بود)
     * - نه در حال جنگ
     * - رعایت محدودیت‌های همزمانی:
     *   جنگجو با هیچکس؛ کشاورز + کوهستانی باهم ممنوع
     */
    public boolean formAlliance(Tribe targetTribe) {
        if (!targetTribe.canFormAlliance()) return false;

        boolean hasFarmer   = false;
        boolean hasMountain = false;
        boolean hasWarrior  = false;

        for (Hex h : map.getHexes()) {
            if (!(h.getBuilding() instanceof TribeCamp)) continue;
            if (h.getBuilding().isDestroyed()) continue;
            Tribe t = ((TribeCamp) h.getBuilding()).getTribe();
            if (!t.isAllied()) continue;
            if (t.getType() == TribeType.FARMER)     hasFarmer   = true;
            if (t.getType() == TribeType.MOUNTAIN)   hasMountain = true;
            if (t.getType() == TribeType.WARRIOR)    hasWarrior  = true;
        }

        // محدودیت‌های همزمانی طبق spec
        if (hasWarrior) return false;
        if (targetTribe.getType() == TribeType.WARRIOR
                && (hasFarmer || hasMountain)) return false;
        if (targetTribe.getType() == TribeType.FARMER   && hasMountain) return false;
        if (targetTribe.getType() == TribeType.MOUNTAIN && hasFarmer)   return false;

        targetTribe.setAllied(true);
        GameEventDispatcher.fireNotification(
                "🤝 Alliance formed with " + targetTribe.getType().getDisplayName() + "!");
        return true;
    }

    /**
     * ارسال هدیه به قبیله — طبق spec:
     * - 10 غذا/چوب → +2 رابطه
     * - 10 سنگ → +3 رابطه
     * - 5 آهن → +3 رابطه
     * فقط در وضعیت غیر دشمن مجاز است (F-06).
     */
    public boolean sendGift(Tribe tribe, ResourceType resourceType) {
        if (!tribe.canReceiveGift()) return false;

        int requiredAmount;
        int relationGain;

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

        if (!map.getTownHall().getInventory().consumeResource(resourceType, requiredAmount)) {
            return false;
        }

        tribe.addRelationship(relationGain);
        GameEventDispatcher.fireNotification(String.format(
                "🎁 Gift sent to %s! Relation +%d → %s",
                tribe.getType().getDisplayName(),
                relationGain,
                tribe.getStatus()));
        return true;
    }

    /**
     * اعلام جنگ با قبیله.
     * confirmation dialog باید قبل از فراخوانی در View نمایش داده شده باشد.
     * اثر Happiness بر اساس وضعیت قبلی (طبق spec).
     */
    public void declareWar(Tribe tribe) {
        // جریمه happiness بر اساس وضعیت قبلی (باید قبل از reset رابطه محاسبه شود)
        String previousStatus = tribe.getStatus();
        boolean wasAllied   = tribe.isAllied() || tribe.getRelationship() >= 70;
        boolean wasFriendly = tribe.getRelationship() >= 20 && !wasAllied;

        tribe.setAllied(false);
        // رابطه → -100 (بدترین حالت)
        tribe.addRelationship(-200); // بعد از clamp در Tribe.addRelationship → -100

        // جریمه happiness طبق spec:
        // حمله به Friendly: -5 happiness
        // حمله به Allied: -15 happiness
        if (wasAllied) {
            map.getTownHall().addHappiness(-15);
            GameEventDispatcher.fireNotification("⚠️ Alliance broken! -15 Happiness.");
        } else if (wasFriendly) {
            map.getTownHall().addHappiness(-5);
            GameEventDispatcher.fireNotification("⚠️ Friendly tribe attacked! -5 Happiness.");
        }

        GameEventDispatcher.fireNotification(
                "⚔️ War declared with " + tribe.getType().getDisplayName() + "!");
    }

    /**
     * درخواست صلح با قبیله دشمن.
     * هزینه: 30 غذا + 30 چوب + 30 آهن (طبق spec).
     * نتیجه: رابطه -100 → -10، وضعیت Enemy → Displeased.
     */
    public boolean requestPeace(Tribe tribe) {
        if (!tribe.canRequestPeace()) return false;

        if (!map.getTownHall().getInventory().hasEnough(ResourceType.FOOD, 30)
                || !map.getTownHall().getInventory().hasEnough(ResourceType.WOOD, 30)
                || !map.getTownHall().getInventory().hasEnough(ResourceType.IRON, 30)) {
            GameEventDispatcher.fireNotification("❌ Peace requires 30 Food + 30 Wood + 30 Iron!");
            return false;
        }

        map.getTownHall().getInventory().consumeResource(ResourceType.FOOD, 30);
        map.getTownHall().getInventory().consumeResource(ResourceType.WOOD, 30);
        map.getTownHall().getInventory().consumeResource(ResourceType.IRON, 30);

        // -100 → -10: اضافه کردن ۹۰ به رابطه
        tribe.addRelationship(90);
        // ceiling at -10 برای اطمینان
        if (tribe.getRelationship() > -10) {
            tribe.addRelationship(-10 - tribe.getRelationship());
        }

        GameEventDispatcher.fireNotification(
                "🕊️ Peace with " + tribe.getType().getDisplayName() + ". Status: Displeased.");
        return true;
    }

    /**
     * تجارت با قبیله — طبق spec (F-07).
     * نرخ‌ها:
     *   FARMER    → هر منبع → غذا، 75%
     *   MOUNTAIN  → هر منبع → سنگ یا آهن، 75%
     *   COMMERCIAL → هر منبع → هر منبع، 80% — اصلاح F-37
     *   COASTAL   → هر منبع → غذا، 75% — اصلاح F-37
     *   WARRIOR   → (بدون تجارت ترجیحی، طبق spec تجارت ندارد)
     * ۱ تراکنش/ترن — ریست در TradeController.onTurnEnded.
     */
    public boolean tradeWithTribe(TribeCamp camp, ResourceType give, int amount,
                                  ResourceType get) {
        if (camp == null || camp.isDestroyed()) return false;
        Tribe tribe = camp.getTribe();

        if (!tribe.canTrade()) return false;
        if (camp.hasTraded()) return false;

        double rate = getTribeTradeRate(tribe.getType(), get);
        if (rate <= 0) return false;

        if (!map.getTownHall().getInventory().consumeResource(give, amount)) return false;

        int received = (int) Math.floor(amount * rate);
        map.getTownHall().getInventory().addResource(get, received);
        camp.setTraded(true);

        GameEventDispatcher.fireNotification(String.format(
                "💱 Trade with %s: %d %s → %d %s",
                tribe.getType().getDisplayName(),
                amount, give.name(), received, get.name()));
        return true;
    }

    /**
     * نرخ تجارت هر قبیله بر اساس منبع دریافتی — طبق spec.
     * اصلاح F-37: COMMERCIAL(80%) و COASTAL(75%) به جای MERCHANT و NOMAD.
     */
    private double getTribeTradeRate(TribeType type, ResourceType get) {
        return switch (type) {
            case FARMER -> (get == ResourceType.FOOD) ? 0.75 : 0.0;
            case MOUNTAIN -> (get == ResourceType.STONE || get == ResourceType.IRON) ? 0.75 : 0.0;
            case COMMERCIAL -> 0.80;     // اصلاح F-37: هر منبع، ۸۰٪
            case COASTAL -> (get == ResourceType.FOOD) ? 0.75 : 0.0; // اصلاح F-37
            case WARRIOR -> 0.0;         // Warrior تجارت ندارد
        };
    }
}