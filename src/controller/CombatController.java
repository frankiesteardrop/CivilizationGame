package controller;

import model.*;
import java.util.*;
import java.util.stream.Collectors;

public class CombatController {

    private final GameMap map;
    private final DamageHandler damageChain;

    public CombatController(GameMap map) {
        this.map = map;

        DamageHandler swordsman =
                new SwordsmanDamageHandler();

        DamageHandler archer =
                new ArcherDamageHandler();

        DamageHandler cavalry =
                new CavalryDamageHandler();

        DamageHandler catapult =
                new CatapultDamageHandler();

        DamageHandler civilian =
                new CivilianDamageHandler();

        swordsman.setNext(archer);
        archer.setNext(cavalry);
        cavalry.setNext(catapult);
        catapult.setNext(civilian);

        this.damageChain = swordsman;
    }

    public CombatResult executeAttack(
            List<Unit> attackers,
            Hex sourceHex,
            Hex targetHex,
            boolean isSiegeAttack,
            boolean isTargetAnimal,
            boolean targetHasWall) {

        if (attackers == null
                || sourceHex == null
                || targetHex == null) {
            return null;
        }

        int dist =
                map.getHexDistance(
                        sourceHex.getQ(),
                        sourceHex.getR(),
                        targetHex.getQ(),
                        targetHex.getR()
                );

        if (dist > 2 || dist < 1) {
            return null;
        }


        List<Unit> militaryCandidates =
                attackers.stream()
                        .filter(Unit::isAlive)
                        .filter(this::isMilitaryUnit)
                        .collect(Collectors.toList());

        if (militaryCandidates.isEmpty()) {
            return null;
        }


        List<Unit> validAttackers =
                militaryCandidates.stream()
                        .filter(u ->
                                isValidAttackerForDistance(
                                        u,
                                        dist
                                )
                        )
                        .collect(Collectors.toList());

        if (validAttackers.isEmpty()) {
            return null;
        }


        if (validAttackers.stream()
                .anyMatch(u ->
                        u.getCurrentAP() < 1)) {

            return null;
        }

        /*
         * Existing close-combat composition limit.
         */
        if (dist == 1) {

            long swords =
                    validAttackers.stream()
                            .filter(u ->
                                    u.getType()
                                            == UnitType.SWORDSMAN)
                            .count();

            long archers =
                    validAttackers.stream()
                            .filter(u ->
                                    u.getType()
                                            == UnitType.ARCHER)
                            .count();

            long cavs =
                    validAttackers.stream()
                            .filter(u ->
                                    u.getType()
                                            == UnitType.CAVALRY)
                            .count();

            long catapults =
                    validAttackers.stream()
                            .filter(u ->
                                    u.getType()
                                            == UnitType.CATAPULT)
                            .count();

            if (swords > 2
                    || archers > 2
                    || cavs > 1
                    || catapults > 1) {
                return null;
            }
        }

        for (Unit attacker :
                validAttackers) {

            if (!attacker.consumeAP(1)) {
                return null;
            }
        }

        List<Unit> validDefenders =
                map.getUnits()
                        .stream()
                        .filter(u ->
                                u.isAlive()
                                        && u.getQ()
                                        == targetHex.getQ()
                                        && u.getR()
                                        == targetHex.getR()
                                        && (
                                        isTargetAnimal
                                                ? u.getType()
                                                == UnitType.BEAR
                                                : (
                                                u.getType()
                                                        == UnitType.SWORDSMAN
                                                        || u.getType()
                                                        == UnitType.ARCHER
                                                        || u.getType()
                                                        == UnitType.CAVALRY
                                                        || u.getType()
                                                        == UnitType.CATAPULT
                                                        || u.getType()
                                                        == UnitType.WORKER
                                                        || u.getType()
                                                        == UnitType.BUILDER
                                                        || u.getType()
                                                        == UnitType.EXPLORER
                                                        || u.getType()
                                                        == UnitType.BORDER_EXPANDER
                                        )
                                )
                        )
                        .collect(Collectors.toList());

        validDefenders.forEach(
                u -> u.consumeAP(1)
        );

        boolean isPlayerAttacking =
                !validAttackers.isEmpty()
                        && !validAttackers
                        .get(0)
                        .isEnemy();

        if (isPlayerAttacking) {

            for (Unit def :
                    validDefenders) {

                if (def.getOwnerTribe() != null) {

                    Tribe t =
                            def.getOwnerTribe();

                    if (!t.getState()
                            .getName()
                            .equals("Enemy")) {

                        t.setAllied(false);
                        t.addRelationship(-200);

                        GameEventDispatcher.fireNotification(
                                "⚔️ You attacked a Tribe unit! War declared automatically."
                        );
                    }
                }
            }
        }

        if (isSiegeAttack) {

            int siegeDmg =
                    handleSiegeAttack(
                            validAttackers,
                            sourceHex,
                            targetHex,
                            targetHasWall
                    );

            return new CombatResult(
                    siegeDmg,
                    0,
                    0,
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        }

        if (validDefenders.isEmpty()) {

            return new CombatResult(
                    0,
                    0,
                    0,
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        }

        int attackerDiceCount =
                (dist == 2)
                        ? 1
                        : (int) validAttackers.stream()
                        .map(Unit::getType)
                        .distinct()
                        .count();

        int defenderDiceCount =
                isTargetAnimal
                        ? 1
                        : 2;

        int wallModifier =
                (dist == 1 && targetHasWall)
                        ? 2
                        : 0;

        List<Integer> attackerRolls =
                rollDice(
                        attackerDiceCount,
                        0
                );

        int totalCombatBuffs =
                validAttackers.stream()
                        .mapToInt(
                                Unit::getTemporaryCombatDiceBonus
                        )
                        .sum();

        for (int i = 0;
             i < totalCombatBuffs
                     && i < attackerRolls.size();
             i++) {

            attackerRolls.set(
                    i,
                    attackerRolls.get(i) + 1
            );
        }

        attackerRolls.sort(
                Collections.reverseOrder()
        );

        List<Integer> defenderRolls =
                rollDice(
                        defenderDiceCount,
                        wallModifier
                );

        int attackerTakesDmg = 0;
        int defenderTakesDmg = 0;

        int pairs =
                Math.min(
                        attackerRolls.size(),
                        defenderRolls.size()
                );

        for (int i = 0; i < pairs; i++) {

            if (attackerRolls.get(i)
                    > defenderRolls.get(i)) {

                defenderTakesDmg++;

            } else {

                attackerTakesDmg++;
            }
        }

        if (attackerTakesDmg > 0) {

            List<Unit> aliveAttackers =
                    validAttackers.stream()
                            .filter(Unit::isAlive)
                            .collect(Collectors.toList());

            if (!aliveAttackers.isEmpty()) {

                damageChain.handleDamage(
                        aliveAttackers,
                        attackerTakesDmg
                );
            }
        }

        if (defenderTakesDmg > 0) {

            if (isTargetAnimal) {

                for (Unit bear :
                        validDefenders) {

                    if (defenderTakesDmg > 0
                            && bear.isAlive()) {

                        bear.kill();
                        defenderTakesDmg--;
                    }
                }

            } else {

                List<Unit> aliveDefenders =
                        validDefenders.stream()
                                .filter(Unit::isAlive)
                                .collect(Collectors.toList());

                if (!aliveDefenders.isEmpty()) {

                    damageChain.handleDamage(
                            aliveDefenders,
                            defenderTakesDmg
                    );
                }
            }
        }

        map.removeDeadUnits();

        GameEventDispatcher.fireCombatTriggered(
                attackerRolls,
                defenderRolls,
                attackerTakesDmg,
                defenderTakesDmg
        );

        return new CombatResult(
                0,
                defenderTakesDmg,
                attackerTakesDmg,
                attackerRolls,
                defenderRolls
        );
    }

    private boolean isMilitaryUnit(Unit unit) {

        if (unit == null) {
            return false;
        }

        return switch (unit.getType()) {

            case SWORDSMAN,
                 ARCHER,
                 CAVALRY,
                 CATAPULT -> true;

            default -> false;
        };
    }

    private boolean isValidAttackerForDistance(
            Unit unit,
            int distance) {

        if (!isMilitaryUnit(unit)) {
            return false;
        }

        if (!unit.isAlive()) {
            return false;
        }

        if (unit.getAttackRange() < distance) {
            return false;
        }

        if (distance == 2) {

            return unit.getType()
                    == UnitType.ARCHER
                    || unit.getType()
                    == UnitType.CATAPULT;
        }

        return distance == 1;
    }

    private int handleSiegeAttack(
            List<Unit> attackers,
            Hex sourceHex,
            Hex targetHex,
            boolean targetHasWall) {

        int siegeDmg =
                attackers.stream()
                        .mapToInt(
                                Unit::getSiegeDamage
                        )
                        .sum();

        int dir =
                getDirection(
                        sourceHex,
                        targetHex
                );

        if (dir >= 0 && targetHasWall) {

            targetHex.damageWall(
                    (dir + 3) % 6,
                    siegeDmg
            );

            sourceHex.damageWall(
                    dir,
                    siegeDmg
            );

            if (!targetHex.hasWall(
                    (dir + 3) % 6)
                    || !sourceHex.hasWall(dir)) {

                targetHex.setWall(
                        (dir + 3) % 6,
                        false,
                        0
                );

                sourceHex.setWall(
                        dir,
                        false,
                        0
                );

                GameEventDispatcher.fireNotification(
                        "🧱 Wall destroyed!"
                );

            } else {

                GameEventDispatcher.fireNotification(
                        "🧱 Wall took "
                                + siegeDmg
                                + " damage!"
                );
            }

        } else if (targetHex.getBuilding() != null
                && !targetHex.getBuilding().isDestroyed()) {

            Building b =
                    targetHex.getBuilding();

            if (b instanceof TribeCamp camp) {

                if (!camp.getTribe()
                        .getState()
                        .getName()
                        .equals("Enemy")) {

                    camp.getTribe().setAllied(false);

                    camp.getTribe()
                            .addRelationship(-200);

                    GameEventDispatcher.fireNotification(
                            "⚔️ You attacked a Tribe Camp! War declared automatically."
                    );
                }
            }

            b.takeDamage(siegeDmg);

            GameEventDispatcher.fireNotification(
                    "🏰 Structure took "
                            + siegeDmg
                            + " damage!"
            );

            if (b.isDestroyed()) {

                if (b instanceof TribeCamp camp) {

                    targetHex.setInsideBorder(true);
                    targetHex.setExplored(true);

                    for (int i = 0; i < 6; i++) {

                        Hex neighbor =
                                map.getNeighbor(
                                        targetHex,
                                        i
                                );

                        if (neighbor != null
                                && neighbor.getTerrainType()
                                != TerrainType.SEA
                                && neighbor.getTerrainType()
                                != TerrainType.MOUNTAIN_RANGE) {

                            neighbor.setInsideBorder(true);
                            neighbor.setExplored(true);
                        }
                    }

                    targetHex.setBuilding(
                            BuildingFactory.createBuilding(
                                    BuildingType.OUTPOST
                            )
                    );

                    GameEventDispatcher.fireBorderExpanded(
                            targetHex.getQ(),
                            targetHex.getR()
                    );

                    camp.getTribe()
                            .getType()
                            .grantLoot(
                                    map,
                                    targetHex
                            );

                } else {

                    targetHex.setBuilding(
                            null
                    );
                }

                GameEventDispatcher.fireBuildingDestroyed(
                        targetHex
                );
            }
        }

        return siegeDmg;
    }

    private int getDirection(
            Hex source,
            Hex target) {

        for (int i = 0; i < 6; i++) {

            if (map.getNeighbor(
                    source,
                    i
            ) == target) {

                return i;
            }
        }

        return -1;
    }

    private List<Integer> rollDice(
            int count,
            int modifier) {

        Random rand =
                map.getRandom();

        List<Integer> rolls =
                new ArrayList<>();

        for (int i = 0;
             i < count;
             i++) {

            int roll =
                    Math.max(
                            1,
                            Math.min(
                                    6,
                                    rand.nextInt(6)
                                            + 1
                                            + modifier
                            )
                    );

            rolls.add(roll);
        }

        rolls.sort(
                Collections.reverseOrder()
        );

        return rolls;
    }
}