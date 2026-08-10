package controller;

import model.*;
import java.util.*;
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

        // فیلتر یونیت‌هایی که برد کافی دارند
        List<Unit> validAttackers = attackers.stream()
                .filter(u -> u.getAttackRange() >= dist && u.isAlive())
                .collect(Collectors.toList());

        // F-13: از فاصله ۲، فقط Archer مجاز به حمله است
        if (dist == 2) {
            validAttackers = validAttackers.stream()
                    .filter(u -> u.getType() == UnitType.ARCHER)
                    .collect(Collectors.toList());
        }

        if (validAttackers.isEmpty()) return -1;
        if (validAttackers.stream().anyMatch(u -> u.getCurrentAP() < 1)) return -1;

        // بررسی سقف ظرفیت هکس مهاجم — فقط برای حمله نزدیک (dist == 1)
        if (dist == 1) {
            long swords = validAttackers.stream().filter(u -> u.getType() == UnitType.SWORDSMAN).count();
            long archers = validAttackers.stream().filter(u -> u.getType() == UnitType.ARCHER).count();
            long cavs    = validAttackers.stream().filter(u -> u.getType() == UnitType.CAVALRY).count();
            if (swords > 2 || archers > 2 || cavs > 1) return -1;
        }

        // مصرف ۱ AP از همه مهاجمان
        validAttackers.forEach(u -> u.consumeAP(1));

        // ─── حمله به سازه (Siege) — بدون تاس ──────────────────────────────────
        if (!isTargetAnimal && !isTargetBarbarian) {
            int siegeDmg = validAttackers.stream().mapToInt(Unit::getSiegeDamage).sum();
            GameEventDispatcher.fireCombatTriggered(
                    new ArrayList<>(), new ArrayList<>(), 0, siegeDmg);
            return siegeDmg;
        }

        // ─── سیستم تاس ──────────────────────────────────────────────────────────
        // F-13: از فاصله ۲ فقط ۱ تاس تهاجمی — صرف‌نظر از تعداد Archerها
        int attackerDiceCount = (dist == 2)
                ? 1
                : (int) validAttackers.stream().map(Unit::getType).distinct().count();

        int defenderDiceCount = isTargetBarbarian ? 2 : 1;

        // دیوار فقط برای حمله نزدیک (dist == 1) اثر دارد
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

        if (attackerTakesDmg > 0) {
            damageChain.handleDamage(validAttackers, attackerTakesDmg);
        }

        GameEventDispatcher.fireCombatTriggered(
                attackerRolls, defenderRolls, attackerTakesDmg, defenderTakesDmg);

        return defenderTakesDmg;
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