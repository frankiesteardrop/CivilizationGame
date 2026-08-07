package controller;

import model.*;
import java.util.*;
import java.util.stream.Collectors;

public class CombatController {

    private final GameMap map;
    private final DamageHandler damageChain;

    public CombatController(GameMap map) {
        this.map = map;

        // اتصال زنجیره مسئولیت (شمشیرزن -> کماندار -> سواره‌نظام)
        DamageHandler swordsman = new SwordsmanDamageHandler();
        DamageHandler archer = new ArcherDamageHandler();
        DamageHandler cavalry = new CavalryDamageHandler();

        swordsman.setNext(archer);
        archer.setNext(cavalry);
        this.damageChain = swordsman;
    }

    public int executeAttack(List<Unit> attackers, Hex sourceHex, Hex targetHex, boolean isTargetAnimal, boolean isTargetBarbarian, boolean targetHasWall) {
        int dist = map.getHexDistance(sourceHex.getQ(), sourceHex.getR(), targetHex.getQ(), targetHex.getR());
        if (dist > 2 || dist < 1) return -1;

        // فقط یونیت‌هایی که بردشان می‌رسد اجازه حمله دارند (مثلا شمشیرزن از فاصله 2 خط می‌خورد)
        List<Unit> validAttackers = attackers.stream().filter(u -> u.getAttackRange() >= dist && u.isAlive()).collect(Collectors.toList());
        if (validAttackers.isEmpty()) return -1;

        if (validAttackers.stream().anyMatch(u -> u.getCurrentAP() < 1)) return -1;

        // بررسی سقف ظرفیت هکس تهاجمی
        long swords = validAttackers.stream().filter(u -> u.getType() == UnitType.SWORDSMAN).count();
        long archers = validAttackers.stream().filter(u -> u.getType() == UnitType.ARCHER).count();
        long cavs = validAttackers.stream().filter(u -> u.getType() == UnitType.CAVALRY).count();
        if (swords > 2 || archers > 2 || cavs > 1) return -1;

        // مصرف 1 AP
        validAttackers.forEach(u -> u.consumeAP(1));

        // اگر مدافعی نیست، مستقیماً آسیب به سازه وارد می‌شود (Siege)
        if (!isTargetAnimal && !isTargetBarbarian) {
            int siegeDmg = validAttackers.stream().mapToInt(Unit::getSiegeDamage).sum();
            return siegeDmg; // این مقدار به HP کمپ، دیوار یا TownHall کسر خواهد شد
        }

        // سیستم تاس
        int attackerDiceCount = (int) validAttackers.stream().map(Unit::getType).distinct().count();
        int defenderDiceCount = isTargetBarbarian ? 2 : 1;
        int wallPenalty = targetHasWall ? 2 : 0;

        List<Integer> attackerRolls = rollDice(attackerDiceCount, 0);
        List<Integer> defenderRolls = rollDice(defenderDiceCount, wallPenalty);

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

        return defenderTakesDmg; // مقدار ضربه به دشمن برگردانده می‌شود
    }

    private List<Integer> rollDice(int count, int modifier) {
        Random rand = new Random();
        List<Integer> rolls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int roll = rand.nextInt(6) + 1 + modifier;
            rolls.add(Math.min(roll, 6)); // سقف تاس 6 است
        }
        rolls.sort(Collections.reverseOrder());
        return rolls;
    }
}