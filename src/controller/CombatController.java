package controller;

import model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class CombatController {

    private final GameMap map;
    private final DamageHandler damageChain;

    public CombatController(GameMap map) {
        this.map = map;

        DamageHandler swordsman = new SwordsmanDamageHandler();
        DamageHandler archer    = new ArcherDamageHandler();
        DamageHandler cavalry   = new CavalryDamageHandler();
        DamageHandler civilian  = new CivilianDamageHandler();

        swordsman.setNext(archer);
        archer.setNext(cavalry);
        cavalry.setNext(civilian);
        this.damageChain = swordsman;
    }

    public int executeAttack(List<Unit> attackers, Hex sourceHex, Hex targetHex,
                             boolean isSiegeAttack, boolean isTargetAnimal,
                             boolean targetHasWall) {

        int dist = map.getHexDistance(sourceHex.getQ(), sourceHex.getR(),
                targetHex.getQ(), targetHex.getR());
        if (dist > 2 || dist < 1) return -1;

        List<Unit> validAttackers = attackers.stream()
                .filter(u -> u.getAttackRange() >= dist && u.isAlive())
                .collect(Collectors.toList());

        if (dist == 2) {
            validAttackers = validAttackers.stream()
                    .filter(u -> u.getType() == UnitType.ARCHER)
                    .collect(Collectors.toList());
        }

        if (validAttackers.isEmpty()) return -1;
        if (validAttackers.stream().anyMatch(u -> u.getCurrentAP() < 1)) return -1;

        if (dist == 1) {
            long swords  = validAttackers.stream().filter(u -> u.getType() == UnitType.SWORDSMAN).count();
            long archers = validAttackers.stream().filter(u -> u.getType() == UnitType.ARCHER).count();
            long cavs    = validAttackers.stream().filter(u -> u.getType() == UnitType.CAVALRY).count();
            if (swords > 2 || archers > 2 || cavs > 1) return -1;
        }

        validAttackers.forEach(u -> u.consumeAP(1));

        List<Unit> validDefenders = map.getUnits().stream()
                .filter(u -> u.isAlive()
                        && u.getQ() == targetHex.getQ()
                        && u.getR() == targetHex.getR()
                        && (isTargetAnimal ? u.getType() == UnitType.BEAR
                        : (u.getType() == UnitType.SWORDSMAN
                        || u.getType() == UnitType.ARCHER
                        || u.getType() == UnitType.CAVALRY
                        || u.getType() == UnitType.WORKER
                        || u.getType() == UnitType.BUILDER
                        || u.getType() == UnitType.EXPLORER
                        || u.getType() == UnitType.BORDER_EXPANDER)))
                .collect(Collectors.toList());

        validDefenders.forEach(u -> u.consumeAP(1));

        // اصلاح نهایی: اعلام جنگ خودکار در صورت حمله بازیکن به یونیت‌های قبیله
        boolean isPlayerAttacking = !validAttackers.isEmpty() && !validAttackers.get(0).isEnemy();
        if (isPlayerAttacking) {
            for (Unit def : validDefenders) {
                if (def.getOwnerTribe() != null) {
                    Tribe t = def.getOwnerTribe();
                    if (!t.getState().getName().equals("Enemy")) {
                        t.setAllied(false);
                        t.addRelationship(-200);
                        GameEventDispatcher.fireNotification("⚔️ You attacked a Tribe unit! War declared automatically.");
                    }
                }
            }
        }

        if (isSiegeAttack) {
            int siegeDmg = validAttackers.stream().mapToInt(Unit::getSiegeDamage).sum();

            int dir = getDirection(sourceHex, targetHex);
            if (dir >= 0 && targetHasWall) {
                targetHex.damageWall((dir + 3) % 6, siegeDmg);
                sourceHex.damageWall(dir, siegeDmg);

                if (!targetHex.hasWall((dir + 3) % 6) || !sourceHex.hasWall(dir)) {
                    targetHex.setWall((dir + 3) % 6, false, 0);
                    sourceHex.setWall(dir, false, 0);
                    GameEventDispatcher.fireNotification("🧱 Wall destroyed!");
                } else {
                    GameEventDispatcher.fireNotification("🧱 Wall took " + siegeDmg + " damage!");
                }

            } else if (targetHex.getBuilding() != null && !targetHex.getBuilding().isDestroyed()) {
                Building b = targetHex.getBuilding();

                // اعلام جنگ خودکار در صورت حمله غیرمستقیم به کمپ قبیله
                if (b instanceof TribeCamp camp) {
                    if (!camp.getTribe().getState().getName().equals("Enemy")) {
                        camp.getTribe().setAllied(false);
                        camp.getTribe().addRelationship(-200);
                        GameEventDispatcher.fireNotification("⚔️ You attacked a Tribe Camp! War declared automatically.");
                    }
                }

                b.takeDamage(siegeDmg);
                GameEventDispatcher.fireNotification("🏰 Structure took " + siegeDmg + " damage!");

                if (b.isDestroyed()) {
                    if (b instanceof TribeCamp camp) {
                        targetHex.setInsideBorder(true);
                        targetHex.setExplored(true);

                        for (int i = 0; i < 6; i++) {
                            Hex neighbor = map.getNeighbor(targetHex, i);
                            if (neighbor != null
                                    && neighbor.getTerrainType() != TerrainType.SEA
                                    && neighbor.getTerrainType() != TerrainType.MOUNTAIN_RANGE) {
                                neighbor.setInsideBorder(true);
                                neighbor.setExplored(true);
                            }
                        }

                        targetHex.setBuilding(BuildingFactory.createBuilding(BuildingType.OUTPOST));
                        GameEventDispatcher.fireBorderExpanded(targetHex.getQ(), targetHex.getR());
                        camp.getTribe().getType().grantLoot(map, targetHex);
                    } else {
                        targetHex.setBuilding(null);
                    }

                    GameEventDispatcher.fireBuildingDestroyed(targetHex);
                }
            }

            GameEventDispatcher.fireCombatTriggered(new ArrayList<>(), new ArrayList<>(), 0, siegeDmg);
            map.removeDeadUnits();
            return siegeDmg;
        }

        int attackerDiceCount = (dist == 2) ? 1
                : (int) validAttackers.stream().map(Unit::getType).distinct().count();

        int defenderDiceCount = isTargetAnimal ? 1 : 2;
        int wallModifier      = (dist == 1 && targetHasWall) ? 2 : 0;

        List<Integer> attackerRolls  = rollDice(attackerDiceCount, 0);
        List<Integer> defenderRolls  = rollDice(defenderDiceCount, wallModifier);

        int attackerTakesDmg = 0;
        int defenderTakesDmg = 0;

        int pairs = Math.min(attackerRolls.size(), defenderRolls.size());
        for (int i = 0; i < pairs; i++) {
            if (attackerRolls.get(i) > defenderRolls.get(i)) {
                defenderTakesDmg++;
            } else {
                attackerTakesDmg++;
            }
        }

        if (attackerTakesDmg > 0) {
            damageChain.handleDamage(validAttackers, attackerTakesDmg);
        }

        if (defenderTakesDmg > 0) {
            if (isTargetAnimal) {
                for (Unit bear : validDefenders) {
                    if (defenderTakesDmg > 0) {
                        bear.kill();
                        defenderTakesDmg--;
                    }
                }
            } else {
                damageChain.handleDamage(validDefenders, defenderTakesDmg);
            }
        }

        map.removeDeadUnits();

        GameEventDispatcher.fireCombatTriggered(
                attackerRolls, defenderRolls, attackerTakesDmg, defenderTakesDmg);
        return defenderTakesDmg;
    }

    private int getDirection(Hex source, Hex target) {
        for (int i = 0; i < 6; i++) {
            if (map.getNeighbor(source, i) == target) return i;
        }
        return -1;
    }

    private List<Integer> rollDice(int count, int modifier) {
        Random rand = map.getRandom();
        List<Integer> rolls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int roll = Math.max(1, Math.min(6, rand.nextInt(6) + 1 + modifier));
            rolls.add(roll);
        }
        rolls.sort(Collections.reverseOrder());
        return rolls;
    }
}