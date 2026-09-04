package model;

/**
 * Catapult (منجنیق) — long-range siege weapon.
 *
 * <p>Spec properties:
 * <ul>
 *   <li>Can attack structures and units at range 2.</li>
 *   <li>Against buildings: deals 20 fixed damage (no dice system).</li>
 *   <li>Against units: participates in dice combat but is vulnerable in melee.</li>
 *   <li>Movement: slow (maxAP = 2), high food consumption.</li>
 * </ul>
 */
public class Catapult extends Unit {

    public Catapult(int q, int r) {
        super(q, r, UnitType.CATAPULT);
    }
}