package model;

public class IronMine extends Building {
    public IronMine() { super(BuildingType.IRON_MINE.getMaxWorkers()); }

    @Override
    public BuildingType getType() { return BuildingType.IRON_MINE; }

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