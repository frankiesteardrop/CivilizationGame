package model;

public class IronMine extends Building {
    public IronMine() { super(BuildingType.IRON_MINE.getMaxWorkers()); }

    @Override
    public BuildingType getType() { return BuildingType.IRON_MINE; }

    @Override
    public int calculateProduction(TownHall townHall) {
        if (townHall != null && townHall.isProfessionalToolsUnlocked()) {
            int boostedPerWorker = (getType().getBaseProduction() * 3) / 2;
            return getStationedWorkers() * boostedPerWorker;
        }
        return super.calculateProduction(townHall);
    }
}