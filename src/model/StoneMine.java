package model;

public class StoneMine extends Building {
    public StoneMine() { super(BuildingType.STONE_MINE.getMaxWorkers()); }

    @Override
    public BuildingType getType() { return BuildingType.STONE_MINE; }

    // اصلاح گام اول: اعمال اصل کپسوله‌سازی و Information Expert
    @Override
    public int calculateProduction(TownHall townHall) {
        int baseProduction = super.calculateProduction(townHall);
        if (townHall != null && townHall.isProfessionalToolsUnlocked()) {
            return (int) (baseProduction * 1.5);
        }
        return baseProduction;
    }
}