package model;

import java.util.LinkedList;
import java.util.Queue;

public class Apothecary extends Building {

    public enum ItemType {
        TELEPORT("Teleport",  20, 10,  0,  0),
        MOBILITY("Mobility",  10,  0,  0,  5),
        COMBAT  ("Combat",     0,  5, 10,  0);

        private final String displayName;
        private final int foodCost;
        private final int stoneCost;
        private final int ironCost;
        private final int woodCost;

        ItemType(String displayName, int foodCost, int stoneCost, int ironCost, int woodCost) {
            this.displayName = displayName;
            this.foodCost    = foodCost;
            this.stoneCost   = stoneCost;
            this.ironCost    = ironCost;
            this.woodCost    = woodCost;
        }

        public String getDisplayName() { return displayName; }
        public int    getFoodCost()    { return foodCost; }
        public int    getStoneCost()   { return stoneCost; }
        public int    getIronCost()    { return ironCost; }
        public int    getWoodCost()    { return woodCost; }


        public static boolean isValid(String name) {
            for (ItemType t : values()) {
                if (t.name().equals(name)) return true;
            }
            return false;
        }
    }

    private final Queue<String> craftingQueue;

    public Apothecary() {
        super(BuildingType.APOTHECARY.getMaxWorkers());
        this.craftingQueue = new LinkedList<>();
    }

    @Override
    public BuildingType getType() { return BuildingType.APOTHECARY; }


    public boolean canQueueItem() {
        return craftingQueue.isEmpty();
    }


    public boolean queueItem(String itemName) {
        if (!canQueueItem()) return false;
        craftingQueue.offer(itemName);
        return true;
    }

    public String advanceCraftingQueue() {
        return craftingQueue.poll();
    }

    public String getCurrentlyCrafting() {
        return craftingQueue.peek();
    }

    public boolean isCraftingQueueEmpty() {
        return craftingQueue.isEmpty();
    }
}