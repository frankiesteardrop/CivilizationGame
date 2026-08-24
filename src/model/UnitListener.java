package model;
public interface UnitListener {
    void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR);
    void onUnitKilled(Unit unit);
    void onUnitStateChanged(Unit unit);
}