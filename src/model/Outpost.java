package model;

public class Outpost extends Building {
    public Outpost() {
        super(BuildingType.OUTPOST.getMaxWorkers());
        this.setMaxHp(BuildingType.OUTPOST.getMaxHp());
        this.hp = BuildingType.OUTPOST.getMaxHp();
    }

    @Override
    public BuildingType getType() {
        return BuildingType.OUTPOST;
    }
}