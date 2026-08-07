package model;

public class TribeCamp extends Building {
    public TribeCamp(int maxHp) {
        super(BuildingType.TRIBE_CAMP.getMaxWorkers());
        this.setMaxHp(maxHp);
        this.hp = maxHp;
    }
    @Override public BuildingType getType() { return BuildingType.TRIBE_CAMP; }
}