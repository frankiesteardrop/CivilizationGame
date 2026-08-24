package model;
public interface BuildingListener {
    void onBuildingConstructed(Hex hex);
    void onBuildingDestroyed(Hex hex);
}