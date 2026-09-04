package model;

import java.util.LinkedList;
import java.util.Queue;

/**
 * The Apothecary building (عطاری) — crafts consumable items for units.
 *
 * <p>Requirements: must be built on a PLAINS hex; requires Town Hall Level 2.
 *
 * <p>Crafting: one item at a time; each item takes exactly 1 turn to complete.
 * Completed items are automatically delivered to the owning player's inventory.
 *
 * <p>Available items and their resource costs:
 * <ul>
 *   <li>{@link ItemType#TELEPORT}  — 20 Food + 10 Stone</li>
 *   <li>{@link ItemType#MOBILITY}  — 10 Food + 5 Wood</li>
 *   <li>{@link ItemType#COMBAT}    — 10 Iron + 5 Stone</li>
 * </ul>
 */
public class Apothecary extends Building {

    /**
     * All craftable item types. Resource costs follow the principle that
     * powerful effects cost rarer resources (Iron for Combat, Food for Mobility).
     */
    public enum ItemType {
        TELEPORT("Teleport",  20, 10,  0,  0),  // Food, Stone, Iron, Wood
        MOBILITY("Mobility",  10,  0,  0,  5),  // Food, Stone, Iron, Wood
        COMBAT  ("Combat",     0,  5, 10,  0);  // Food, Stone, Iron, Wood

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

        /**
         * Validates that the given item name is a known ItemType.
         * Used on the server to reject spoofed crafting requests.
         */
        public static boolean isValid(String name) {
            for (ItemType t : values()) {
                if (t.name().equals(name)) return true;
            }
            return false;
        }
    }

    /**
     * Independent crafting queue — holds the name of the item currently
     * being crafted. Capacity: 1 (matches the spec: "independent queue").
     */
    private final Queue<String> craftingQueue;

    public Apothecary() {
        super(BuildingType.APOTHECARY.getMaxWorkers());
        this.craftingQueue = new LinkedList<>();
    }

    @Override
    public BuildingType getType() { return BuildingType.APOTHECARY; }

    // ─── Queue Management ─────────────────────────────────────────────────────

    /** Returns true if a crafting order can currently be accepted. */
    public boolean canQueueItem() {
        return craftingQueue.isEmpty();
    }

    /**
     * Adds an item to the crafting queue. Fails silently if the queue is busy.
     *
     * @param itemName the {@link ItemType#name()} of the item to craft
     * @return true if the item was queued, false if the queue is already occupied
     */
    public boolean queueItem(String itemName) {
        if (!canQueueItem()) return false;
        craftingQueue.offer(itemName);
        return true;
    }

    /**
     * Advances the crafting queue by one turn.
     * Since each item takes exactly 1 turn, this always returns the completed item
     * (or null if the queue was empty).
     *
     * @return the name of the completed item, or null if nothing was queued
     */
    public String advanceCraftingQueue() {
        return craftingQueue.poll();
    }

    /** Returns the name of the item currently being crafted, or null. */
    public String getCurrentlyCrafting() {
        return craftingQueue.peek();
    }

    /** Returns true if no item is currently queued. */
    public boolean isCraftingQueueEmpty() {
        return craftingQueue.isEmpty();
    }
}