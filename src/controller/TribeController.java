package controller;

import model.*;
import model.mission.Mission;
import model.state.mission.ActiveMissionState;
import model.state.mission.CompletedFailedState;
import model.trade.TradeStrategy;

import java.util.*;
import java.util.stream.Collectors;

public class TribeController implements GameEventListener {

    private final GameMap map;
    private static final int MIN_INTER_CAMP_DISTANCE = 4;
    private static final int MIN_FROM_TH_DISTANCE    = 6;

    public TribeController(GameMap map) {
        this.map = map;
        GameEventDispatcher.addListener(this);
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
        if (type == TribeType.COASTAL) {
            for (Hex h : candidates) {
                if (isCoastalHex(h) && isFarEnough(h, occupied)) return h;
            }
            for (Hex h : candidates) {
                if (isFarEnough(h, occupied)) return h;
            }
            return null;
        }

        TerrainType preferred = switch (type) {
            case FARMER     -> TerrainType.MEADOW;
            case WARRIOR    -> TerrainType.PLAINS;
            case COMMERCIAL -> TerrainType.PLAINS;
            case MOUNTAIN   -> TerrainType.MOUNTAIN;
            default         -> TerrainType.PLAINS;
        };

        for (Hex h : candidates) {
            if (h.getTerrainType() == preferred && isFarEnough(h, occupied)) return h;
        }
        for (Hex h : candidates) {
            if (isFarEnough(h, occupied)) return h;
        }
        return null;
    }

    private boolean isCoastalHex(Hex h) {
        if (h.getTerrainType() == TerrainType.SEA
                || h.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;
        for (int i = 0; i < 6; i++) {
            Hex neighbor = map.getNeighbor(h, i);
            if (neighbor != null && neighbor.getTerrainType() == TerrainType.SEA) return true;
        }
        return false;
    }

    private boolean isFarEnough(Hex candidate, List<Hex> occupied) {
        for (Hex o : occupied) {
            if (map.getHexDistance(candidate.getQ(), candidate.getR(),
                    o.getQ(), o.getR()) < MIN_INTER_CAMP_DISTANCE) return false;
        }
        return true;
    }

    public void processTribesTurn() {
        for (Hex hex : map.getHexes()) {
            if (hex.getBuilding() instanceof TribeCamp camp && !camp.isDestroyed()) {
                Mission m = camp.getTribe().getMission();
                if (m != null) m.getState().checkConditions(m, camp, map);
            }
        }

        List<Runnable> deferredActions = new ArrayList<>();

        for (Hex hex : map.getHexes()) {
            if (!(hex.getBuilding() instanceof TribeCamp camp) || camp.isDestroyed()) continue;

            Tribe tribe = camp.getTribe();
            Mission m = tribe.getMission();
            if (m != null) m.getState().handleTurn(m, tribe);

            if (tribe.getMissionCooldown() > 0) tribe.decrementMissionCooldown();

            tribe.getState().executeTurnBehavior(tribe, camp, hex, map, deferredActions);
        }

        for (Runnable action : deferredActions) {
            action.run();
        }

        // اجرای هوش مصنوعی گاردهای قبیله پس از تولید و تصمیمات وضعیت‌ها
        processTribeGuardsAI();
    }

    // ─── هوش مصنوعی گاردهای قبیله (اضافه شده در گام ۲) ──────────────────────────

    private void processTribeGuardsAI() {
        // پیدا کردن تمامی نیروهایی که متعلق به قبایل/بربرها هستند
        List<Unit> guards = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.isEnemy())
                .collect(Collectors.toList());

        if (guards.isEmpty()) return;

        UnitController uc = new UnitController();
        CombatController cc = new CombatController(map);

        for (Unit guard : guards) {
            while (guard.getCurrentAP() > 0 && guard.isAlive()) {
                // جستجوی نزدیک‌ترین هدف (نیروی بازیکن) در شعاع ۵ هکسی
                Unit target = findClosestPlayerUnit(guard, 5);
                if (target == null) break; // دشمنی نزدیک نیست، توقف حرکت

                int dist = map.getHexDistance(guard.getQ(), guard.getR(), target.getQ(), target.getR());

                if (dist <= guard.getAttackRange()) {
                    // هدف در برد است -> حمله
                    Hex sourceHex = map.getHexAt(guard.getQ(), guard.getR());
                    Hex targetHex = map.getHexAt(target.getQ(), target.getR());

                    boolean targetHasWall = false;
                    for (int i = 0; i < 6; i++) {
                        if (map.getNeighbor(sourceHex, i) == targetHex) {
                            targetHasWall = sourceHex.hasWall(i);
                            break;
                        }
                    }

                    List<Unit> attackers = Collections.singletonList(guard);
                    cc.executeAttack(attackers, sourceHex, targetHex, false, false, targetHasWall);
                    map.removeDeadUnits();

                    if (!target.isAlive()) {
                        GameEventDispatcher.fireNotification("⚠️ A Tribe Guard has defeated your unit!");
                    }
                } else {
                    // هدف دور است -> حرکت به سمت هدف
                    Hex nextHex = getNextHexTowards(guard, target, uc);
                    if (nextHex != null) {
                        uc.executeMove(guard, nextHex, map);
                    } else {
                        break; // مسیر مسدود است
                    }
                }
            }
        }
    }

    private Unit findClosestPlayerUnit(Unit guard, int radius) {
        return map.getUnits().stream()
                // فقط به یونیت‌های بازیکن (isEnemy = false) و غیر خرس حمله می‌کند
                .filter(u -> u.isAlive() && !u.isEnemy() && u.getType() != UnitType.BEAR)
                .filter(u -> map.getHexDistance(guard.getQ(), guard.getR(), u.getQ(), u.getR()) <= radius)
                .min(Comparator.comparingInt(u -> map.getHexDistance(guard.getQ(), guard.getR(), u.getQ(), u.getR())))
                .orElse(null);
    }

    private Hex getNextHexTowards(Unit guard, Unit target, UnitController uc) {
        Hex bestHex = null;
        int minTargetDist = map.getHexDistance(guard.getQ(), guard.getR(), target.getQ(), target.getR());

        Hex currentHex = map.getHexAt(guard.getQ(), guard.getR());
        if (currentHex == null) return null;

        for (int i = 0; i < 6; i++) {
            Hex neighbor = map.getNeighbor(currentHex, i);
            if (neighbor != null && uc.canMove(guard, neighbor, map)) {
                int dist = map.getHexDistance(neighbor.getQ(), neighbor.getR(), target.getQ(), target.getR());
                if (dist < minTargetDist) {
                    minTargetDist = dist;
                    bestHex = neighbor;
                }
            }
        }
        return bestHex;
    }

    // ─── تعاملات و دیپلماسی ─────────────────────────────────────────────────

    public void acceptMission(TribeCamp camp) {
        Mission m = camp.getTribe().getMission();
        if (m != null && m.getState().canAccept()) {
            m.setState(new ActiveMissionState());
            GameEventDispatcher.fireNotification(
                    "Mission accepted for " + camp.getTribe().getType().getDisplayName());
            m.getState().checkConditions(m, camp, map);
        }
    }

    public void cancelMission(TribeCamp camp) {
        Tribe tribe = camp.getTribe();
        Mission m = tribe.getMission();
        if (m != null
                && (m.getState().getDisplayName().equals("Active")
                ||  m.getState().getDisplayName().equals("Ready to Deliver"))) {
            m.setState(new CompletedFailedState("Cancelled"));
            tribe.addRelationship(-5);
            GameEventDispatcher.fireNotification("🚫 Mission cancelled. Relations dropped by 5.");
        }
    }

    public boolean deliverMission(TribeCamp camp) {
        Mission m = camp.getTribe().getMission();
        if (m == null || !m.getState().canDeliver()) return false;
        return m.getState().deliver(m, camp, map);
    }

    public boolean formAlliance(Tribe targetTribe) {
        if (!targetTribe.canFormAlliance()) return false;

        if (targetTribe.getMissionCooldown() > 0) return false;

        boolean hasFarmer = false, hasMountain = false, hasWarrior = false;
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
        if (targetTribe.getType() == TribeType.FARMER  && hasMountain) return false;
        if (targetTribe.getType() == TribeType.MOUNTAIN && hasFarmer)  return false;

        targetTribe.setAllied(true);
        GameEventDispatcher.fireNotification(
                "🤝 Alliance formed with " + targetTribe.getType().getDisplayName() + " Tribe!");
        return true;
    }

    public boolean sendGift(Tribe tribe, ResourceType resourceType, int amount) {
        if (!tribe.canReceiveGift()) return false;
        if (amount <= 0) return false;

        int unitSize = (resourceType == ResourceType.IRON) ? 5 : 10;
        if (amount < unitSize) return false;

        if (!map.getTownHall().getInventory().hasEnough(resourceType, amount)) return false;

        int relationGain;
        if (resourceType == ResourceType.IRON) {
            relationGain = (amount / 5) * 3;
        } else if (resourceType == ResourceType.STONE) {
            relationGain = (amount / 10) * 3;
        } else {
            relationGain = (amount / 10) * 2;
        }

        if (relationGain <= 0) return false;

        map.getTownHall().getInventory().consumeResource(resourceType, amount);
        tribe.addRelationship(relationGain);

        GameEventDispatcher.fireNotification(String.format(
                "🎁 Gift sent: %d %s → +%d relation with %s",
                amount, resourceType.name(), relationGain, tribe.getType().getDisplayName()));
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
        GameEventDispatcher.fireNotification(
                "⚔️ War declared with " + tribe.getType().getDisplayName() + "!");
    }

    public boolean requestPeace(Tribe tribe) {
        if (!tribe.canRequestPeace()) return false;
        Inventory inv = map.getTownHall().getInventory();
        if (!inv.hasEnough(ResourceType.FOOD, 30)
                || !inv.hasEnough(ResourceType.WOOD, 30)
                || !inv.hasEnough(ResourceType.IRON, 30)) {
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
        GameEventDispatcher.fireNotification(
                "🕊️ Peace with " + tribe.getType().getDisplayName() + ". Status: Displeased.");
        return true;
    }

    public boolean tradeWithTribe(TribeCamp camp, ResourceType give, int amount, ResourceType get) {
        if (camp == null || camp.isDestroyed()) return false;
        Tribe tribe = camp.getTribe();

        if (!tribe.canTrade() || camp.hasTraded()) return false;

        TradeStrategy strategy = tribe.getType().getTradeStrategy();
        int received = strategy.calculateReceivedAmount(amount, get, tribe.hasTradeBonus());
        if (received <= 0) return false;

        if (!map.getTownHall().getInventory().consumeResource(give, amount)) return false;

        map.getTownHall().getInventory().addResource(get, received);
        camp.setTraded(true);

        GameEventDispatcher.fireNotification(String.format("💱 Trade with %s: %d %s → %d %s",
                tribe.getType().getDisplayName(), amount, give.name(), received, get.name()));
        return true;
    }

    // ─── Listeners ───────────────────────────────────────────────────────────

    @Override
    public void onUnitKilled(Unit unit) {
        for (Hex h : map.getHexes()) {
            if (h.getBuilding() instanceof TribeCamp camp) {
                Mission m = camp.getTribe().getMission();
                if (m != null) m.getState().onUnitKilled(m, camp, unit, map);
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
    @Override public void onNotification(String message) {}
}