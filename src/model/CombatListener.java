package model;
import java.util.List;
public interface CombatListener { void onCombatTriggered(List<Integer> attackerDice, List<Integer> defenderDice, int atkDmg, int defDmg); }