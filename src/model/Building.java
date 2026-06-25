package model;

public class Building {
    private BuildingType type;
    private int stationedWorkers;
    private int failedUpkeepTurns;

    public Building(BuildingType type) {
        this.type = type;
        this.stationedWorkers = 0;
        this.failedUpkeepTurns = 0;
    }

    public BuildingType getType() { return type; }
    public int getStationedWorkers() { return stationedWorkers; }
    public int getFailedUpkeepTurns() { return failedUpkeepTurns; }

    public void addWorker() {
        if (stationedWorkers < type.getMaxWorkers()) {
            stationedWorkers++;
        }
    }

    public void removeWorker() {
        if (stationedWorkers > 0) {
            stationedWorkers--;
        }
    }

    public void registerFailedUpkeep() { failedUpkeepTurns++; }
    public void resetFailedUpkeep() { failedUpkeepTurns = 0; }

    public boolean isDestroyed() {
        return failedUpkeepTurns >= 3; // طبق داک: خرابی پس از ۳ ترن ناتوانی در نگهداری
    }

    // محاسبه تولید کل این ساختمان در یک ترن
    public int calculateProduction() {
        return stationedWorkers * type.getBaseProduction();
    }
}