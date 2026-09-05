package model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds a player's resources, locked-resource bookkeeping for pending trades (B12),
 * and their consumable item inventory (B11/B27).
 */
public class Inventory {

    // ─── Resources ────────────────────────────────────────────────────────────

    private final EnumMap<ResourceType, Integer> resources  = new EnumMap<>(ResourceType.class);
    private final EnumMap<ResourceType, Integer> capacities = new EnumMap<>(ResourceType.class);

    /**
     * Resources reserved for pending trade offers (B12).
     * Locked resources count toward getResourceAmount() but cannot be freely spent.
     */
    private final EnumMap<ResourceType, Integer> locked = new EnumMap<>(ResourceType.class);

    // ─── Items ────────────────────────────────────────────────────────────────

    /** Consumable item inventory produced by the Apothecary (B11/B27). */
    private final Map<String, Integer> items = new HashMap<>();

    // ─── Constructor ──────────────────────────────────────────────────────────

    public Inventory() {
        for (ResourceType type : ResourceType.values()) {
            if (type == ResourceType.NONE) continue;
            resources.put(type, 0);
            locked.put(type, 0);
        }
        capacities.put(ResourceType.FOOD,  GameConfig.DEFAULT_FOOD_CAPACITY);
        capacities.put(ResourceType.WOOD,  GameConfig.DEFAULT_WOOD_CAPACITY);
        capacities.put(ResourceType.STONE, GameConfig.DEFAULT_STONE_CAPACITY);
        capacities.put(ResourceType.IRON,  GameConfig.DEFAULT_IRON_CAPACITY);
    }

    // ─── Starting Resources ───────────────────────────────────────────────────

    /** Sets the initial resources granted to a player at game start. */
    public void applyStartingResources() {
        addResource(ResourceType.FOOD,  GameConfig.STARTING_FOOD);
        addResource(ResourceType.WOOD,  GameConfig.STARTING_WOOD);
        addResource(ResourceType.STONE, GameConfig.STARTING_STONE);
        addResource(ResourceType.IRON,  GameConfig.STARTING_IRON);
    }

    // ─── TownHall Upgrade Capacity Methods (compile fix) ─────────────────────

    /**
     * Expands storage capacity to the Level-2 Town Hall cap.
     * Called by {@link TownHall#upgradeLevel()} when upgrading to level 2.
     */
    public void upgradeToLevel2() {
        setCapacity(ResourceType.FOOD,  GameConfig.TH_UPGRADE2_CAPACITY);
        setCapacity(ResourceType.WOOD,  GameConfig.TH_UPGRADE2_CAPACITY);
        setCapacity(ResourceType.STONE, GameConfig.TH_UPGRADE2_CAPACITY);
        setCapacity(ResourceType.IRON,  GameConfig.TH_UPGRADE2_CAPACITY);
    }

    /**
     * Expands storage capacity to the Level-3 Town Hall cap.
     * Called by {@link TownHall#upgradeLevel()} when upgrading to level 3.
     */
    public void upgradeToLevel3() {
        setCapacity(ResourceType.FOOD,  GameConfig.TH_UPGRADE3_CAPACITY);
        setCapacity(ResourceType.WOOD,  GameConfig.TH_UPGRADE3_CAPACITY);
        setCapacity(ResourceType.STONE, GameConfig.TH_UPGRADE3_CAPACITY);
        setCapacity(ResourceType.IRON,  GameConfig.TH_UPGRADE3_CAPACITY);
    }

    // ─── Resource Access ──────────────────────────────────────────────────────

    public int getResourceAmount(ResourceType type) {
        if (type == ResourceType.NONE) return 0;
        return resources.getOrDefault(type, 0);
    }

    public int getCapacity(ResourceType type) {
        if (type == ResourceType.NONE) return 0;
        return capacities.getOrDefault(type, 0);
    }

    public void setCapacity(ResourceType type, int newCapacity) {
        if (type == ResourceType.NONE || newCapacity < 0) return;
        capacities.put(type, newCapacity);
        int current = resources.getOrDefault(type, 0);
        if (current > newCapacity) resources.put(type, newCapacity);
    }

    public void addResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;
        int current = resources.getOrDefault(type, 0);
        int cap     = capacities.getOrDefault(type, 0);
        resources.put(type, Math.min(current + amount, cap));
    }

    /**
     * Consumes {@code amount} from the unlocked portion of the resource.
     * @return true if sufficient unlocked resources were available and consumed
     */
    public boolean consumeResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;
        if (!hasEnough(type, amount)) return false;
        resources.put(type, resources.getOrDefault(type, 0) - amount);
        return true;
    }

    /**
     * Returns true if the player has at least {@code amount} of freely available
     * (unlocked) resources of the given type.
     */
    public boolean hasEnough(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;
        return getUnlocked(type) >= amount;
    }

    private int getUnlocked(ResourceType type) {
        return Math.max(0, resources.getOrDefault(type, 0)
                - locked.getOrDefault(type, 0));
    }

    // ─── Resource Locking — Trade (B12/B32) ──────────────────────────────────

    /**
     * Reserves resources for a pending trade offer (B12).
     * @return true if successfully locked; false if insufficient unlocked resources
     */
    public boolean lockResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;
        if (getUnlocked(type) < amount) return false;
        locked.merge(type, amount, Integer::sum);
        return true;
    }

    /** Releases a previously applied lock (offer rejected or cancelled). */
    public void unlockResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;
        locked.put(type, Math.max(0, locked.getOrDefault(type, 0) - amount));
    }

    /** Removes locked resources when a trade offer is accepted. */
    public void consumeLockedResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;
        int toRemove = Math.min(locked.getOrDefault(type, 0), amount);
        locked.put(type, locked.getOrDefault(type, 0) - toRemove);
        resources.put(type, Math.max(0, resources.getOrDefault(type, 0) - toRemove));
    }

    // ─── Items — Apothecary (B11/B27) ─────────────────────────────────────────

    public void addItem(String itemName, int quantity) {
        if (itemName == null || quantity <= 0) return;
        items.merge(itemName, quantity, Integer::sum);
    }

    public boolean consumeItem(String itemName) {
        if (itemName == null) return false;
        int qty = items.getOrDefault(itemName, 0);
        if (qty <= 0) return false;
        if (qty == 1) items.remove(itemName);
        else          items.put(itemName, qty - 1);
        return true;
    }

    /**
     * Returns a read-only snapshot of the consumable item inventory. (B27)
     * Keys = item names ("TELEPORT", "MOBILITY", "COMBAT"); values = quantities.
     */
    public Map<String, Integer> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public boolean hasItem(String itemName) {
        return items.getOrDefault(itemName, 0) > 0;
    }
}