package model;

public class StoneMine extends Building {
    public StoneMine() { super(BuildingType.STONE_MINE.getMaxWorkers()); }

    @Override
    public BuildingType getType() { return BuildingType.STONE_MINE; }

    @Override
    public int calculateProduction(TownHall townHall) {
        int baseProduction = super.calculateProduction(townHall);
        if (townHall != null && townHall.isProfessionalToolsUnlocked()) {
            // [گام حل باگ ۱۶]: استفاده از Math.round برای جلوگیری از خطای خاموش Truncation
            return (int) Math.round(baseProduction * 1.5);
        }
        return baseProduction;
    }
}