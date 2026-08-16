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

        swordsman.setNext(archer);
        archer.setNext(cavalry);
        this.damageChain = swordsman;
    }

    public int executeAttack(List<Unit> attackers, Hex sourceHex, Hex targetHex,
                             boolean isTargetAnimal, boolean isTargetBarbarian,
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
            long swords = validAttackers.stream().filter(u -> u.getType() == UnitType.SWORDSMAN).count();
            long archers = validAttackers.stream().filter(u -> u.getType() == UnitType.ARCHER).count();
            long cavs    = validAttackers.stream().filter(u -> u.getType() == UnitType.CAVALRY).count();
            if (swords > 2 || archers > 2 || cavs > 1) return -1;
        }

        // مصرف ۱ AP از همه مهاجمان
        validAttackers.forEach(u -> u.consumeAP(1));

        // ─── حمله به سازه یا دیوار (Siege) — بدون تاس ──────────────────────────────────
        if (!isTargetAnimal && !isTargetBarbarian) {
            int siegeDmg = validAttackers.stream().mapToInt(Unit::getSiegeDamage).sum();

            int dir = getDirection(sourceHex, targetHex);
            if (dir >= 0 && targetHasWall) {
                // اعمال آسیب مستقیماً به خود دیوار
                targetHex.damageWall((dir + 3) % 6, siegeDmg);
                sourceHex.damageWall(dir, siegeDmg);
                GameEventDispatcher.fireNotification("🧱 Wall took " + siegeDmg + " damage!");
            } else if (targetHex.getBuilding() != null && !targetHex.getBuilding().isDestroyed()) {
                // اعمال آسیب به ساختمان (کمپ، سازه‌های پلیر و ...)
                Building b = targetHex.getBuilding();
                b.takeDamage(siegeDmg);
                GameEventDispatcher.fireNotification("🏰 Structure took " + siegeDmg + " damage!");

                if (b.isDestroyed()) {
                    GameEventDispatcher.fireBuildingDestroyed(targetHex);

                    // اگر هدف، کمپ قبیله بوده باشد، طبق داکیومنت فتح می‌شود
                    if (b instanceof TribeCamp) {
                        TribeCamp camp = (TribeCamp) b;
                        GameEventDispatcher.fireNotification("⛺ " + camp.getTribe().getType().getDisplayName() + " tribe defeated!");
                        targetHex.setInsideBorder(true);
                        map.getTownHall().getInventory().addResource(ResourceType.FOOD, 50);
                        map.getTownHall().getInventory().addResource(ResourceType.WOOD, 50);
                    }

                    // کارگرهای داخل سازه باید به بیرون رانده شوند
                    for (Unit u : map.getUnits()) {
                        if (u instanceof Worker && ((Worker) u).getStationedBuilding() == b) {
                            ((Worker) u).eject(map);
                        }
                    }
                }
            }

            GameEventDispatcher.fireCombatTriggered(
                    new ArrayList<>(), new ArrayList<>(), 0, siegeDmg);
            return siegeDmg;
        }

        // ─── سیستم تاس (Combat against units) ──────────────────────────────────────────────────────────
        int attackerDiceCount = (dist == 2)
                ? 1
                : (int) validAttackers.stream().map(Unit::getType).distinct().count();

        int defenderDiceCount = isTargetBarbarian ? 2 : 1;
        int wallModifier = (dist == 1 && targetHasWall) ? 2 : 0;

        List<Integer> attackerRolls = rollDice(attackerDiceCount, 0);
        List<Integer> defenderRolls = rollDice(defenderDiceCount, wallModifier);

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

        // اعمال دمیج به مهاجمین (از طریق Chain of Responsibility)
        if (attackerTakesDmg > 0) {
            damageChain.handleDamage(validAttackers, attackerTakesDmg);
        }

        // اصلاح باگ: اعمال دمیج به مدافعین (سربازهای دشمن یا خرس‌ها)
        if (defenderTakesDmg > 0) {
            List<Unit> validDefenders = map.getUnits().stream()
                    .filter(u -> u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR()
                            && (isTargetAnimal ? u.getType() == UnitType.BEAR :
                            (u.getType() == UnitType.SWORDSMAN || u.getType() == UnitType.ARCHER || u.getType() == UnitType.CAVALRY)))
                    .collect(Collectors.toList());

            if (isTargetAnimal) {
                // طبق spec خرس مستقیماً کشته می‌شود
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
        Random rand = new Random();
        List<Integer> rolls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int roll = Math.max(1, Math.min(6, rand.nextInt(6) + 1 + modifier));
            rolls.add(roll);
        }
        rolls.sort(Collections.reverseOrder());
        return rolls;
    }
}