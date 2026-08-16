package controller;

import model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TribeController {

    private final GameMap map;

    // شعاع تشخیص حضور نظامی برای Displeased behavior
    private static final int MILITARY_DETECT_RADIUS = 3;
    // شعاع حضور گارد حول کمپ
    private static final int GUARD_RADIUS = 3;
    // فاصله حداقل بین کمپ‌ها هنگام spawn
    private static final int MIN_INTER_CAMP_DISTANCE = 4;
    // فاصله حداقل از TH بازیکن
    private static final int MIN_FROM_TH_DISTANCE = 6;

    public TribeController(GameMap map) {
        this.map = map;
    }

    // ─── Spawn ───────────────────────────────────────────────────────────────

    /**
     * F-01: ایجاد ۵ کمپ قبیله با نوع‌های مختلف روی نقشه.
     * هر نوع از TribeType یک نمونه دریافت می‌کند.
     */
    public void spawnInitialTribes() {
        List<Hex> candidates = map.getHexes().stream()
                .filter(h -> h.getTerrainType() != TerrainType.SEA
                        && h.getTerrainType() != TerrainType.MOUNTAIN_RANGE)
                .filter(h -> h.getBuilding() == null)
                .filter(h -> map.getHexDistance(
                        map.getTownHall().getQ(), map.getTownHall().getR(),
                        h.getQ(), h.getR()) >= MIN_FROM_TH_DISTANCE)
                .collect(Collectors.toList());

        Collections.shuffle(candidates);

        List<Hex> spawnedLocations = new ArrayList<>();
        for (TribeType type : TribeType.values()) {
            Hex bestHex = findBestHexForTribe(type, candidates, spawnedLocations);
            if (bestHex == null) continue;
            bestHex.setBuilding(new TribeCamp(type));
            spawnedLocations.add(bestHex);
            candidates.remove(bestHex);
        }
    }

    private Hex findBestHexForTribe(TribeType type, List<Hex> candidates,
                                    List<Hex> occupied) {
        TerrainType preferred = getPreferredTerrain(type);
        for (Hex h : candidates)
            if (h.getTerrainType() == preferred && isFarEnoughFromOthers(h, occupied)) return h;
        for (Hex h : candidates)
            if (isFarEnoughFromOthers(h, occupied)) return h;
        return null;
    }

    private boolean isFarEnoughFromOthers(Hex candidate, List<Hex> occupied) {
        for (Hex o : occupied)
            if (map.getHexDistance(candidate.getQ(), candidate.getR(),
                    o.getQ(), o.getR()) < MIN_INTER_CAMP_DISTANCE)
                return false;
        return true;
    }

    private TerrainType getPreferredTerrain(TribeType type) {
        return switch (type) {
            case FARMER     -> TerrainType.MEADOW;
            case WARRIOR    -> TerrainType.PLAINS;
            case MOUNTAIN   -> TerrainType.MOUNTAIN;
            case COMMERCIAL -> TerrainType.PLAINS;
            case COASTAL    -> TerrainType.PLAINS;
        };
    }

    // ─── Per-Turn Processing ─────────────────────────────────────────────────

    /**
     * رفتار نوبتی قبایل.
     *
     * F-32: برای جلوگیری از ConcurrentModificationException در حین spawn،
     * تمام عملیات تغییر map در یک deferredActions list جمع‌آوری و
     * بعد از پایان iteration اجرا می‌شوند.
     *
     * F-25: اولویت‌بندی طبق spec:
     *   Enemy     → دفاع + spawn guard هر ۳ ترن
     *   Displeased → کاهش رابطه اگر نظامی نزدیک باشد
     *   Friendly   → پیشنهاد مأموریت هر ۵ ترن
     *   Allied     → bonus دائمی هر ترن
     */
    public void processTribesTurn() {
        // F-32: defer list برای جلوگیری از ConcurrentModification
        List<Runnable> deferredActions = new ArrayList<>();

        for (Hex hex : map.getHexes()) {
            if (!(hex.getBuilding() instanceof TribeCamp)) continue;
            if (hex.getBuilding().isDestroyed()) continue;

            TribeCamp camp  = (TribeCamp) hex.getBuilding();
            Tribe     tribe = camp.getTribe();

            switch (tribe.getStatus()) {
                case "Enemy"      -> processEnemyTribeTurn(camp, hex, deferredActions);
                case "Displeased" -> processDispleasedTribeTurn(tribe, camp, hex);
                case "Allied"     -> processAlliedTribeTurn(tribe);
                case "Friendly"   -> processFriendlyTribeTurn(tribe, camp);
                // Neutral: بدون رفتار فعال
            }
        }

        // F-32: اجرای spawn actions بعد از اتمام iteration روی hexes
        for (Runnable action : deferredActions) {
            action.run();
        }
    }

    /**
     * F-25 — رفتار دشمن:
     * هر ۳ ترن، اگر تعداد گارد حول کمپ کمتر از حداکثر باشد، یک گارد spawn می‌شود.
     * حداکثر: Warrior → 5 گارد؛ بقیه → 3 گارد.
     */
    private void processEnemyTribeTurn(TribeCamp camp, Hex campHex,
                                       List<Runnable> deferred) {
        int currentCount = camp.getAndIncrementGuardCounter();

        // هر ۳ ترن (counter: 0, 3, 6, 9, ...)
        if (currentCount > 0 && currentCount % 3 == 0) {
            int maxGuards = (camp.getTribe().getType() == TribeType.WARRIOR) ? 5 : 3;

            // شمارش guard های موجود در شعاع GUARD_RADIUS از کمپ
            long currentGuards = map.getUnits().stream()
                    .filter(u -> u.isAlive()
                            && u.getType() == UnitType.SWORDSMAN // اصلاح: تغییر خرس به نیروی پیاده برای جلوگیری از تداخل با هوش مصنوعی بلایای طبیعی
                            && map.getHexDistance(campHex.getQ(), campHex.getR(),
                            u.getQ(), u.getR()) <= GUARD_RADIUS)
                    .count();

            if (currentGuards < maxGuards) {
                // F-32: spawn در deferred list برای thread-safety
                final int campQ = campHex.getQ();
                final int campR = campHex.getR();
                deferred.add(() -> {
                    Hex spawnHex = findNearbyEmptyHex(campQ, campR, GUARD_RADIUS);
                    if (spawnHex != null) {
                        // اصلاح: استفاده از UnitFactory برای ساخت Swordsman به عنوان نگهبان
                        map.addUnit(UnitFactory.createUnit(UnitType.SWORDSMAN, spawnHex.getQ(), spawnHex.getR()));
                        if (campHex.isVisible()) {
                            GameEventDispatcher.fireNotification(
                                    "⚠️ " + camp.getTribe().getType().getDisplayName()
                                            + " tribe is mobilizing guards!");
                        }
                    }
                });
            }
        }
    }

    /**
     * F-25 — رفتار ناراضی (Displeased):
     * اگر یونیت نظامی بازیکن در شعاع MILITARY_DETECT_RADIUS باشد،
     * رابطه هر ترن -1 کاهش می‌یابد (کاهش سریع‌تر از حالت عادی).
     */
    private void processDispleasedTribeTurn(Tribe tribe, TribeCamp camp, Hex campHex) {
        boolean hasMilitaryNearby = map.getUnits().stream()
                .anyMatch(u -> u.isAlive()
                        && (u.getType() == UnitType.SWORDSMAN
                        || u.getType() == UnitType.ARCHER
                        || u.getType() == UnitType.CAVALRY)
                        && map.getHexDistance(campHex.getQ(), campHex.getR(),
                        u.getQ(), u.getR()) <= MILITARY_DETECT_RADIUS);

        if (hasMilitaryNearby) {
            camp.incrementDispleasedMilTurns();
            // رابطه هر ۲ ترن -1 (نه هر ترن تا بیش از حد تند نشود)
            if (camp.getDispleasedMilitaryTurns() % 2 == 0) {
                tribe.addRelationship(-1);
                if (campHex.isVisible()) {
                    GameEventDispatcher.fireNotification(
                            "😠 " + tribe.getType().getDisplayName()
                                    + " tribe is alarmed by your military presence.");
                }
            }
        } else {
            camp.resetDispleasedMilTurns();
        }
    }

    /**
     * F-25 — رفتار دوستانه:
     * هر ۵ ترن پیشنهاد مأموریت جدید می‌دهد (notification در HUD).
     */
    private void processFriendlyTribeTurn(Tribe tribe, TribeCamp camp) {
        int counter = camp.getAndIncrementMissionCounter();

        // هر ۵ ترن (counter: 5, 10, 15, ...)
        if (counter > 0 && counter % 5 == 0) {
            GameEventDispatcher.fireNotification(
                    "📜 " + tribe.getType().getDisplayName()
                            + " tribe has a new mission for you! Visit their camp.");
        }
    }

    /**
     * F-25 — رفتار متحد:
     * bonus دائمی هر ترن اعمال می‌شود.
     *
     * F-26 (جزئی): COMMERCIAL و COASTAL bonus اعمال می‌شوند.
     * نرخ تجاری COMMERCIAL توسط flag در TradeController (گام ۱۳) پیاده می‌شود.
     */
    private void processAlliedTribeTurn(Tribe tribe) {
        switch (tribe.getType()) {
            case FARMER     -> map.getTownHall().getInventory()
                    .addResource(ResourceType.FOOD, 5);
            case MOUNTAIN   -> map.getTownHall().getInventory()
                    .addResource(ResourceType.STONE, 5);
            case COMMERCIAL ->
                // F-26: اصلاح — bonus تجاری از طریق flag در TradeController
                // نرخ +10% تجارت در گام ۱۳ پیاده می‌شود.
                // فعلاً: +3 چوب/ترن به عنوان نماد
                    map.getTownHall().getInventory()
                            .addResource(ResourceType.WOOD, 3);
            case COASTAL    ->
                // F-26: اصلاح — bonus ماهیگیری
                // Dock production bonus در گام ۱۳ از طریق EconomyController flag پیاده می‌شود
                    map.getTownHall().getInventory()
                            .addResource(ResourceType.FOOD, 3);
            case WARRIOR    -> {
                // Attack bonus نزدیک کمپ — در CombatController اعمال می‌شود
            }
        }
    }

    /**
     * پیدا کردن یک هکس خالی در شعاع مشخص از مرکز.
     * برای spawn guard و سایر موارد استفاده می‌شود.
     */
    private Hex findNearbyEmptyHex(int centerQ, int centerR, int radius) {
        for (Hex h : map.getHexes()) {
            int dist = map.getHexDistance(centerQ, centerR, h.getQ(), h.getR());
            if (dist > 0 && dist <= radius
                    && h.getTerrainType() != TerrainType.SEA
                    && h.getTerrainType() != TerrainType.MOUNTAIN_RANGE
                    && !map.hasUnitAt(h.getQ(), h.getR())
                    && (h.getBuilding() == null || h.getBuilding().isDestroyed())) {
                return h;
            }
        }
        return null;
    }

    // ─── Alliance ─────────────────────────────────────────────────────────────

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
            if (t.getType() == TribeType.FARMER)   hasFarmer   = true;
            if (t.getType() == TribeType.MOUNTAIN)  hasMountain = true;
            if (t.getType() == TribeType.WARRIOR)   hasWarrior  = true;
        }

        if (hasWarrior) return false;
        if (targetTribe.getType() == TribeType.WARRIOR && (hasFarmer || hasMountain)) return false;
        if (targetTribe.getType() == TribeType.FARMER   && hasMountain) return false;
        if (targetTribe.getType() == TribeType.MOUNTAIN && hasFarmer)   return false;

        targetTribe.setAllied(true);
        GameEventDispatcher.fireNotification(
                "🤝 Alliance formed with " + targetTribe.getType().getDisplayName() + " Tribe!");
        return true;
    }

    // ─── Gift ─────────────────────────────────────────────────────────────────

    public boolean sendGift(Tribe tribe, ResourceType resourceType) {
        if (!tribe.canReceiveGift()) return false;

        int requiredAmount;
        int relationGain;

        if (resourceType == ResourceType.IRON) {
            requiredAmount = 5;  relationGain = 3;
        } else if (resourceType == ResourceType.STONE) {
            requiredAmount = 10; relationGain = 3;
        } else if (resourceType == ResourceType.FOOD || resourceType == ResourceType.WOOD) {
            requiredAmount = 10; relationGain = 2;
        } else {
            return false;
        }

        if (!map.getTownHall().getInventory().consumeResource(resourceType, requiredAmount))
            return false;

        tribe.addRelationship(relationGain); // F-31: notification در addRelationship آتش می‌شود
        return true;
    }

    // ─── War / Peace ──────────────────────────────────────────────────────────

    public void declareWar(Tribe tribe) {
        boolean wasAllied   = tribe.isAllied() || tribe.getRelationship() >= 70;
        boolean wasFriendly = tribe.getRelationship() >= 20 && !wasAllied;

        tribe.setAllied(false);
        tribe.addRelationship(-200); // → -100 بعد از clamp در addRelationship

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

        tribe.addRelationship(90); // -100 → -10
        // ceiling at -10
        if (tribe.getRelationship() > -10) {
            int excess = tribe.getRelationship() + 10;
            tribe.addRelationship(-excess);
        }

        GameEventDispatcher.fireNotification(
                "🕊️ Peace with " + tribe.getType().getDisplayName() + ". Status: Displeased.");
        return true;
    }

    // ─── Trade ────────────────────────────────────────────────────────────────

    public boolean tradeWithTribe(TribeCamp camp, ResourceType give,
                                  int amount, ResourceType get) {
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

    private double getTribeTradeRate(TribeType type, ResourceType get) {
        return switch (type) {
            case FARMER     -> (get == ResourceType.FOOD) ? 0.75 : 0.0;
            case MOUNTAIN   -> (get == ResourceType.STONE || get == ResourceType.IRON) ? 0.75 : 0.0;
            case COMMERCIAL -> 0.80;
            case COASTAL    -> (get == ResourceType.FOOD) ? 0.75 : 0.0;
            case WARRIOR    -> 0.0;
        };
    }
}