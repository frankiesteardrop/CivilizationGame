package model;

public class StoneMine extends Building {
    public StoneMine() { super(BuildingType.STONE_MINE.getMaxWorkers()); }

    @Override
    public BuildingType getType() { return BuildingType.STONE_MINE; }

    @Override
    public int calculateProduction(TownHall townHall) {
        if (townHall != null && townHall.isProfessionalToolsUnlocked()) {
            int boostedPerWorker = (getType().getBaseProduction() * 3) / 2;
            return getStationedWorkers() * boostedPerWorker;
        }
        return super.calculateProduction(townHall);
    }
}