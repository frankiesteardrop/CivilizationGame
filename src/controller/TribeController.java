package controller;

import model.*;
import model.mission.*;

import java.util.*;
import java.util.stream.Collectors;

public class TribeController implements GameEventListener {

    private final GameMap map;

    private static final int MILITARY_DETECT_RADIUS = 3;
    private static final int GUARD_RADIUS = 3;
    private static final int MIN_INTER_CAMP_DISTANCE = 4;
    private static final int MIN_FROM_TH_DISTANCE = 6;

    public TribeController(GameMap map) {
        this.map = map;
        GameEventDispatcher.addListener(this); // برای ثبت قتل‌ها (مأموریت Warrior)
    }

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

    private Hex findBestHexForTribe(TribeType type, List<Hex> candidates, List<Hex> occupied) {
        TerrainType preferred = getPreferredTerrain(type);
        for (Hex h : candidates)
            if (h.getTerrainType() == preferred && isFarEnoughFromOthers(h, occupied)) return h;
        for (Hex h : candidates)
            if (isFarEnoughFromOthers(h, occupied)) return h;
        return null;
    }

    private boolean isFarEnoughFromOthers(Hex candidate, List<Hex> occupied) {
        for (Hex o : occupied)
            if (map.getHexDistance(candidate.getQ(), candidate.getR(), o.getQ(), o.getR()) < MIN_INTER_CAMP_DISTANCE)
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

    public void processTribesTurn() {
        // ۱. بررسی وضعیت مأموریت‌های فعال برای همه کمپ‌ها
        for (Hex hex : map.getHexes()) {
            if (hex.getBuilding() instanceof TribeCamp camp && !camp.isDestroyed()) {
                checkMissionConditions(camp);
            }
        }

        List<Runnable> deferredActions = new ArrayList<>();

        // ۲. اجرای رفتار اصلی قبایل و کاهش تایمر مأموریت‌ها
        for (Hex hex : map.getHexes()) {
            if (!(hex.getBuilding() instanceof TribeCamp)) continue;
            if (hex.getBuilding().isDestroyed()) continue;

            TribeCamp camp  = (TribeCamp) hex.getBuilding();
            Tribe     tribe = camp.getTribe();

            // بررسی تایمر مأموریت
            Mission m = tribe.getMission();
            if (m != null && (m.getState() == MissionStateEnum.ACTIVE || m.getState() == MissionStateEnum.READY_TO_DELIVER)) {
                m.decrementTurn();
                if (m.getTurnsRemaining() <= 0 && m.getState() != MissionStateEnum.COMPLETED) {
                    m.setState(MissionStateEnum.FAILED);
                    tribe.addRelationship(-10);
                    tribe.setMissionCooldown(5);
                    GameEventDispatcher.fireNotification("❌ Mission for " + tribe.getType().getDisplayName() + " failed (Out of time)!");
                }
            }
            if (tribe.getMissionCooldown() > 0) tribe.decrementMissionCooldown();

            // اعمال رفتار قبیله
            switch (tribe.getStatus()) {
                case "Enemy"      -> processEnemyTribeTurn(camp, hex, deferredActions);
                case "Displeased" -> processDispleasedTribeTurn(tribe, camp, hex);
                case "Allied"     -> processAlliedTribeTurn(tribe);
                case "Friendly"   -> processFriendlyTribeTurn(tribe, camp);
            }
        }

        for (Runnable action : deferredActions) {
            action.run();
        }
    }

    private void processEnemyTribeTurn(TribeCamp camp, Hex campHex, List<Runnable> deferred) {
        int currentCount = camp.getAndIncrementGuardCounter();
        if (currentCount > 0 && currentCount % 3 == 0) {
            int maxGuards = (camp.getTribe().getType() == TribeType.WARRIOR) ? 5 : 3;
            long currentGuards = map.getUnits().stream()
                    .filter(u -> u.isAlive() && u.getType() == UnitType.SWORDSMAN
                            && map.getHexDistance(campHex.getQ(), campHex.getR(), u.getQ(), u.getR()) <= GUARD_RADIUS)
                    .count();

            if (currentGuards < maxGuards) {
                final int campQ = campHex.getQ();
                final int campR = campHex.getR();
                deferred.add(() -> {
                    Hex spawnHex = findNearbyEmptyHex(campQ, campR, GUARD_RADIUS);
                    if (spawnHex != null) {
                        map.addUnit(UnitFactory.createUnit(UnitType.SWORDSMAN, spawnHex.getQ(), spawnHex.getR()));
                        if (campHex.isVisible()) {
                            GameEventDispatcher.fireNotification("⚠️ " + camp.getTribe().getType().getDisplayName() + " tribe is mobilizing guards!");
                        }
                    }
                });
            }
        }
    }

    private void processDispleasedTribeTurn(Tribe tribe, TribeCamp camp, Hex campHex) {
        boolean hasMilitaryNearby = map.getUnits().stream()
                .anyMatch(u -> u.isAlive()
                        && (u.getType() == UnitType.SWORDSMAN || u.getType() == UnitType.ARCHER || u.getType() == UnitType.CAVALRY)
                        && map.getHexDistance(campHex.getQ(), campHex.getR(), u.getQ(), u.getR()) <= MILITARY_DETECT_RADIUS);

        if (hasMilitaryNearby) {
            camp.incrementDispleasedMilTurns();
            if (camp.getDispleasedMilitaryTurns() % 2 == 0) {
                tribe.addRelationship(-1);
                if (campHex.isVisible()) {
                    GameEventDispatcher.fireNotification("😠 " + tribe.getType().getDisplayName() + " tribe is alarmed by your military presence.");
                }
            }
        } else {
            camp.resetDispleasedMilTurns();
        }
    }

    private void processFriendlyTribeTurn(Tribe tribe, TribeCamp camp) {
        if (tribe.getMissionCooldown() > 0) return;

        Mission m = tribe.getMission();
        boolean needsNew = (m == null || m.getState() == MissionStateEnum.COMPLETED ||
                m.getState() == MissionStateEnum.FAILED || m.getState() == MissionStateEnum.CANCELLED);

        if (needsNew) {
            int counter = camp.getAndIncrementMissionCounter();
            if (counter > 0 && counter % 5 == 0) {
                int turns = getInitialTurnsForMission(tribe.getType());
                tribe.setMission(new Mission(turns));
                GameEventDispatcher.fireNotification("📜 " + tribe.getType().getDisplayName() + " tribe has a new mission for you!");
            }
        }
    }

    private void processAlliedTribeTurn(Tribe tribe) {
        switch (tribe.getType()) {
            case FARMER     -> map.getTownHall().getInventory().addResource(ResourceType.FOOD, 5);
            case MOUNTAIN   -> map.getTownHall().getInventory().addResource(ResourceType.STONE, 5);
            case COMMERCIAL -> map.getTownHall().getInventory().addResource(ResourceType.WOOD, 3);
            case COASTAL    -> map.getTownHall().getInventory().addResource(ResourceType.FOOD, 3);
            case WARRIOR    -> { }
        }
    }

    private Hex findNearbyEmptyHex(int centerQ, int centerR, int radius) {
        for (Hex h : map.getHexes()) {
            int dist = map.getHexDistance(centerQ, centerR, h.getQ(), h.getR());
            if (dist > 0 && dist <= radius && h.getTerrainType() != TerrainType.SEA
                    && h.getTerrainType() != TerrainType.MOUNTAIN_RANGE && !map.hasUnitAt(h.getQ(), h.getR())
                    && (h.getBuilding() == null || h.getBuilding().isDestroyed())) {
                return h;
            }
        }
        return null;
    }

    // ─── Mission Logic (منطق State Pattern مأموریت‌ها) ────────────────────────────────

    private int getInitialTurnsForMission(TribeType type) {
        return switch(type) {
            case FARMER -> 5;
            case COMMERCIAL, COASTAL -> 10;
            case WARRIOR -> 8;
            case MOUNTAIN -> 6;
        };
    }

    public void acceptMission(TribeCamp camp) {
        Mission m = camp.getTribe().getMission();
        if(m != null && m.getState() == MissionStateEnum.AVAILABLE) {
            m.setState(MissionStateEnum.ACTIVE);
            GameEventDispatcher.fireNotification("Mission accepted for " + camp.getTribe().getType().getDisplayName());
            checkMissionConditions(camp);
        }
    }

    public void cancelMission(TribeCamp camp) {
        Tribe tribe = camp.getTribe();
        Mission m = tribe.getMission();
        if(m != null && (m.getState() == MissionStateEnum.ACTIVE || m.getState() == MissionStateEnum.READY_TO_DELIVER)) {
            m.setState(MissionStateEnum.CANCELLED);
            tribe.addRelationship(-5);
            GameEventDispatcher.fireNotification("🚫 Mission cancelled. Relations dropped by 5.");
        }
    }

    public void checkMissionConditions(TribeCamp camp) {
        Tribe tribe = camp.getTribe();
        Mission m = tribe.getMission();
        if(m == null) return;
        if(m.getState() != MissionStateEnum.ACTIVE && m.getState() != MissionStateEnum.READY_TO_DELIVER) return;

        boolean completed = false;
        switch(tribe.getType()) {
            case FARMER -> completed = map.getTownHall().getInventory().hasEnough(ResourceType.WOOD, 20) &&
                    map.getTownHall().getInventory().hasEnough(ResourceType.STONE, 10);
            case MOUNTAIN -> completed = map.getTownHall().getInventory().hasEnough(ResourceType.WOOD, 15) &&
                    map.getTownHall().getInventory().hasEnough(ResourceType.IRON, 10);
            case COMMERCIAL -> completed = isRoadConnectedToCamp(camp);
            case WARRIOR -> completed = m.getProgress() >= 2;
            case COASTAL -> completed = hasDockWithinRadius(camp, 4);
        }

        if(completed && m.getState() == MissionStateEnum.ACTIVE) {
            m.setState(MissionStateEnum.READY_TO_DELIVER);
            GameEventDispatcher.fireNotification("✅ Mission for " + tribe.getType().getDisplayName() + " is ready to deliver!");
        } else if (!completed && m.getState() == MissionStateEnum.READY_TO_DELIVER) {
            m.setState(MissionStateEnum.ACTIVE); // در صورتی که بازیکن منابع را پیش از تحویل خرج کند
        }
    }

    public boolean deliverMission(TribeCamp camp) {
        Tribe tribe = camp.getTribe();
        Mission m = tribe.getMission();
        if(m == null || m.getState() != MissionStateEnum.READY_TO_DELIVER) return false;

        Inventory inv = map.getTownHall().getInventory();
        if(tribe.getType() == TribeType.FARMER) {
            inv.consumeResource(ResourceType.WOOD, 20);
            inv.consumeResource(ResourceType.STONE, 10);
            inv.addResource(ResourceType.FOOD, 30);
            tribe.addRelationship(15);
        } else if (tribe.getType() == TribeType.MOUNTAIN) {
            inv.consumeResource(ResourceType.WOOD, 15);
            inv.consumeResource(ResourceType.IRON, 10);
            inv.addResource(ResourceType.STONE, 20);
            tribe.addRelationship(15);
        } else if (tribe.getType() == TribeType.WARRIOR) {
            for(int i=0; i<3; i++) {
                Hex spawn = map.findEmptySpawnHex(camp.getQ(), camp.getR());
                if(spawn != null) map.addUnit(UnitFactory.createUnit(UnitType.SWORDSMAN, spawn.getQ(), spawn.getR()));
            }
            tribe.addRelationship(20);
        } else if (tribe.getType() == TribeType.COMMERCIAL) {
            tribe.setTradeBonus(true);
            tribe.addRelationship(20);
        } else if (tribe.getType() == TribeType.COASTAL) {
            inv.addResource(ResourceType.FOOD, 30);
            map.getTownHall().addDiscountedDock();
        }

        m.setState(MissionStateEnum.COMPLETED);
        return true;
    }

    private Hex getHexOfCamp(TribeCamp camp) {
        for(Hex h : map.getHexes()) {
            if(h.getBuilding() == camp) return h;
        }
        return null;
    }

    private boolean isRoadConnectedToCamp(TribeCamp camp) {
        Hex campHex = getHexOfCamp(camp);
        if(campHex == null) return false;

        Set<Hex> visited = new HashSet<>();
        Queue<Hex> queue = new LinkedList<>();

        for(int i=0; i<6; i++) {
            Hex n = map.getNeighbor(campHex, i);
            if(n != null && n.hasRoad()) {
                queue.add(n);
                visited.add(n);
            }
        }

        while(!queue.isEmpty()) {
            Hex current = queue.poll();
            if(current.getBuilding() != null && !current.getBuilding().isDestroyed() &&
                    !(current.getBuilding() instanceof TribeCamp) && !(current.getBuilding() instanceof TradingPost)) {
                return true;
            }
            for(int i=0; i<6; i++) {
                Hex n = map.getNeighbor(current, i);
                if(n != null && n.hasRoad() && !visited.contains(n)) {
                    visited.add(n);
                    queue.add(n);
                }
            }
        }
        return false;
    }

    private boolean hasDockWithinRadius(TribeCamp camp, int radius) {
        Hex campHex = getHexOfCamp(camp);
        if(campHex == null) return false;

        for(Hex h : map.getHexes()) {
            if(map.getHexDistance(campHex.getQ(), campHex.getR(), h.getQ(), h.getR()) <= radius) {
                if(h.getBuilding() != null && h.getBuilding().getType() == BuildingType.DOCK && !h.getBuilding().isDestroyed()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ─── Listeners (برای مأموریت Warrior) ──────────────────────────────────────

    @Override public void onUnitKilled(Unit unit) {
        if (unit.getType() == UnitType.BEAR || unit.getType() == UnitType.SWORDSMAN || unit.getType() == UnitType.ARCHER) {
            for (Hex h : map.getHexes()) {
                if (h.getBuilding() instanceof TribeCamp camp && camp.getTribe().getType() == TribeType.WARRIOR) {
                    Mission m = camp.getTribe().getMission();
                    if (m != null && m.getState() == MissionStateEnum.ACTIVE) {
                        if (map.getHexDistance(unit.getQ(), unit.getR(), h.getQ(), h.getR()) <= 5) {
                            m.addProgress(1);
                            checkMissionConditions(camp);
                        }
                    }
                }
            }
        }
    }

    @Override public void onResourceChanged(ResourceType type, int newAmount) {}
    @Override public void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) {}
    @Override public void onProductionCompleted(String itemName) {}
    @Override public void onTurnEnded(int newTurn) {}
    @Override public void onStarvationChanged(boolean isStarving) {}
    @Override public void onUnitStateChanged(Unit unit) {}
    @Override public void onBuildingConstructed(Hex hex) {}
    @Override public void onBuildingDestroyed(Hex hex) {}
    @Override public void onBorderExpanded(int centerQ, int centerR) {}

    // ─── Alliance & Trade ──────────────────────────────────────────────────

    public boolean formAlliance(Tribe targetTribe) {
        if (!targetTribe.canFormAlliance()) return false;

        boolean hasFarmer   = false, hasMountain = false, hasWarrior  = false;
        for (Hex h : map.getHexes()) {
            if (h.getBuilding() instanceof TribeCamp camp && !camp.isDestroyed()) {
                Tribe t = camp.getTribe();
                if (!t.isAllied()) continue;
                if (t.getType() == TribeType.FARMER)   hasFarmer   = true;
                if (t.getType() == TribeType.MOUNTAIN) hasMountain = true;
                if (t.getType() == TribeType.WARRIOR)  hasWarrior  = true;
            }
        }
        if (hasWarrior) return false;
        if (targetTribe.getType() == TribeType.WARRIOR && (hasFarmer || hasMountain)) return false;
        if (targetTribe.getType() == TribeType.FARMER   && hasMountain) return false;
        if (targetTribe.getType() == TribeType.MOUNTAIN && hasFarmer)   return false;

        targetTribe.setAllied(true);
        GameEventDispatcher.fireNotification("🤝 Alliance formed with " + targetTribe.getType().getDisplayName() + " Tribe!");
        return true;
    }

    public boolean sendGift(Tribe tribe, ResourceType resourceType) {
        if (!tribe.canReceiveGift()) return false;
        int requiredAmount = (resourceType == ResourceType.IRON) ? 5 : 10;
        int relationGain = (resourceType == ResourceType.STONE || resourceType == ResourceType.IRON) ? 3 : 2;

        if (!map.getTownHall().getInventory().consumeResource(resourceType, requiredAmount)) return false;
        tribe.addRelationship(relationGain);
        return true;
    }

    public void declareWar(Tribe tribe) {
        boolean wasAllied   = tribe.isAllied() || tribe.getRelationship() >= 70;
        boolean wasFriendly = tribe.getRelationship() >= 20 && !wasAllied;

        tribe.setAllied(false);
        tribe.addRelationship(-200);

        if (wasAllied) {
            map.getTownHall().addHappiness(-15);
            GameEventDispatcher.fireNotification("⚠️ Alliance broken! -15 Happiness.");
        } else if (wasFriendly) {
            map.getTownHall().addHappiness(-5);
            GameEventDispatcher.fireNotification("⚠️ Friendly tribe attacked! -5 Happiness.");
        }
        GameEventDispatcher.fireNotification("⚔️ War declared with " + tribe.getType().getDisplayName() + "!");
    }

    public boolean requestPeace(Tribe tribe) {
        if (!tribe.canRequestPeace()) return false;
        Inventory inv = map.getTownHall().getInventory();
        if (!inv.hasEnough(ResourceType.FOOD, 30) || !inv.hasEnough(ResourceType.WOOD, 30) || !inv.hasEnough(ResourceType.IRON, 30)) {
            GameEventDispatcher.fireNotification("❌ Peace requires 30 Food + 30 Wood + 30 Iron!");
            return false;
        }
        inv.consumeResource(ResourceType.FOOD, 30);
        inv.consumeResource(ResourceType.WOOD, 30);
        inv.consumeResource(ResourceType.IRON, 30);

        tribe.addRelationship(90);
        if (tribe.getRelationship() > -10) {
            tribe.addRelationship(-(tribe.getRelationship() + 10));
        }
        GameEventDispatcher.fireNotification("🕊️ Peace with " + tribe.getType().getDisplayName() + ". Status: Displeased.");
        return true;
    }

    public boolean tradeWithTribe(TribeCamp camp, ResourceType give, int amount, ResourceType get) {
        if (camp == null || camp.isDestroyed()) return false;
        Tribe tribe = camp.getTribe();

        if (!tribe.canTrade()) return false;
        if (camp.hasTraded()) return false;

        double rate = getTribeTradeRate(tribe, get);
        if (rate <= 0) return false;

        if (!map.getTownHall().getInventory().consumeResource(give, amount)) return false;
        int received = (int) Math.floor(amount * rate);
        map.getTownHall().getInventory().addResource(get, received);
        camp.setTraded(true);

        GameEventDispatcher.fireNotification(String.format("💱 Trade with %s: %d %s → %d %s",
                tribe.getType().getDisplayName(), amount, give.name(), received, get.name()));
        return true;
    }

    private double getTribeTradeRate(Tribe tribe, ResourceType get) {
        double base = switch (tribe.getType()) {
            case FARMER     -> (get == ResourceType.FOOD) ? 0.75 : 0.0;
            case MOUNTAIN   -> (get == ResourceType.STONE || get == ResourceType.IRON) ? 0.75 : 0.0;
            case COMMERCIAL -> 0.80;
            case COASTAL    -> (get == ResourceType.FOOD) ? 0.75 : 0.0;
            case WARRIOR    -> 0.0;
        };
        // اضافه شدن جایزه مأموریت تجاری
        if (base > 0 && tribe.hasTradeBonus()) base += 0.10;
        return base;
    }
}