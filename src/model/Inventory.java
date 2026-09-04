package model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds a player's resources (Food, Wood, Stone, Iron), locked-resource
 * bookkeeping for pending trades (B12), and their consumable item inventory (B11).
 *
 * <p>Capacity is set at construction to the GameConfig defaults and updated
 * whenever the owning Town Hall upgrades.
 */
public class Inventory {

    // ─── Resources ────────────────────────────────────────────────────────────

    /** Current stored amount per resource type. */
    private final EnumMap<ResourceType, Integer> resources = new EnumMap<>(ResourceType.class);

    /** Maximum storable amount per resource type (scales with TH level). */
    private final EnumMap<ResourceType, Integer> capacities = new EnumMap<>(ResourceType.class);

    /**
     * Resources reserved for pending trade offers.
     * Locked resources count toward getResourceAmount() but cannot be spent
     * by economy, upkeep, training, or building operations. (B12)
     */
    private final EnumMap<ResourceType, Integer> locked = new EnumMap<>(ResourceType.class);

    // ─── Items ─────────────────────────────────────────────────────────────────

    /**
     * Consumable item inventory produced by the Apothecary.
     * Key = item name (matches {@link Apothecary.ItemType#name()}),
     * value = quantity held. (B11, B27)
     */
    private final Map<String, Integer> items = new HashMap<>();

    // ─── Constructor ──────────────────────────────────────────────────────────

    public Inventory() {
        for (ResourceType type : ResourceType.values()) {
            if (type == ResourceType.NONE) continue;
            resources.put(type, 0);
            locked.put(type, 0);
        }
        // Default capacities (overridden when Town Hall upgrades)
        capacities.put(ResourceType.FOOD,  GameConfig.DEFAULT_FOOD_CAPACITY);
        capacities.put(ResourceType.WOOD,  GameConfig.DEFAULT_WOOD_CAPACITY);
        capacities.put(ResourceType.STONE, GameConfig.DEFAULT_STONE_CAPACITY);
        capacities.put(ResourceType.IRON,  GameConfig.DEFAULT_IRON_CAPACITY);
    }

    // ─── Resource Access ──────────────────────────────────────────────────────

    /**
     * Returns the total stored amount of the given resource (including locked portion).
     */
    public int getResourceAmount(ResourceType type) {
        if (type == ResourceType.NONE) return 0;
        return resources.getOrDefault(type, 0);
    }

    /** Returns the storage capacity for the given resource type. */
    public int getCapacity(ResourceType type) {
        if (type == ResourceType.NONE) return 0;
        return capacities.getOrDefault(type, 0);
    }

    /**
     * Sets a new storage capacity. If current stored amount exceeds the new
     * capacity it is silently trimmed. Called by TownHall on upgrade.
     */
    public void setCapacity(ResourceType type, int newCapacity) {
        if (type == ResourceType.NONE || newCapacity < 0) return;
        capacities.put(type, newCapacity);
        int current = resources.getOrDefault(type, 0);
        if (current > newCapacity) resources.put(type, newCapacity);
    }

    /**
     * Adds {@code amount} of the given resource, capped at capacity.
     * Ignores non-positive amounts and NONE type.
     */
    public void addResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;
        int current = resources.getOrDefault(type, 0);
        int cap     = capacities.getOrDefault(type, 0);
        resources.put(type, Math.min(current + amount, cap));
    }

    /**
     * Consumes {@code amount} from the unlocked portion of the resource.
     *
     * @return true if the operation succeeded; false if insufficient unlocked resources
     */
    public boolean consumeResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;
        if (!hasEnough(type, amount)) return false;
        resources.put(type, resources.getOrDefault(type, 0) - amount);
        return true;
    }

    /**
     * Returns true if the player has at least {@code amount} of <em>unlocked</em>
     * (freely available) resources of the given type.
     */
    public boolean hasEnough(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;
        return getUnlocked(type) >= amount;
    }

    /**
     * Returns the unlocked (freely spendable) amount of the given resource.
     * This is total − locked.
     */
    private int getUnlocked(ResourceType type) {
        int total      = resources.getOrDefault(type, 0);
        int lockedAmt  = locked.getOrDefault(type, 0);
        return Math.max(0, total - lockedAmt);
    }

    // ─── Resource Locking (B12 — Trade) ──────────────────────────────────────

    /**
     * Reserves {@code amount} of the resource for a pending trade offer so it
     * cannot be spent elsewhere while the offer is open.
     *
     * @return true if successfully locked; false if insufficient unlocked resources
     */
    public boolean lockResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;
        if (getUnlocked(type) < amount) return false;
        locked.put(type, locked.getOrDefault(type, 0) + amount);
        return true;
    }

    /**
     * Releases a previously applied lock (trade offer rejected or cancelled).
     * Silently clamps to zero if more is released than was locked.
     */
    public void unlockResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;
        locked.put(type, Math.max(0, locked.getOrDefault(type, 0) - amount));
    }

    /**
     * Removes locked resources from the inventory when a trade offer is accepted.
     * Both the total stock and the lock counter are decremented.
     */
    public void consumeLockedResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;
        int currentLocked = locked.getOrDefault(type, 0);
        int toRemove      = Math.min(currentLocked, amount);
        locked.put(type, currentLocked - toRemove);
        resources.put(type, Math.max(0, resources.getOrDefault(type, 0) - toRemove));
    }

    // ─── Items (B11 Apothecary / B27 getItems) ────────────────────────────────

    /**
     * Adds {@code quantity} of the named item to the player's item inventory.
     * Item names match {@link Apothecary.ItemType#name()}: "TELEPORT", "MOBILITY", "COMBAT".
     */
    public void addItem(String itemName, int quantity) {
        if (itemName == null || quantity <= 0) return;
        items.merge(itemName, quantity, Integer::sum);
    }

    /**
     * Consumes one unit of the named item.
     *
     * @return true if the item was present and consumed; false if not available
     */
    public boolean consumeItem(String itemName) {
        if (itemName == null) return false;
        int qty = items.getOrDefault(itemName, 0);
        if (qty <= 0) return false;
        if (qty == 1) items.remove(itemName);
        else          items.put(itemName, qty - 1);
        return true;
    }

    /**
     * Returns a read-only snapshot of the player's item inventory. (B27)
     *
     * <p>Keys are item names (e.g. "TELEPORT"); values are quantities.
     * Used by the UI to display available items and by the server for validation.
     */
    public Map<String, Integer> getItems() {
        return Collections.unmodifiableMap(items);
    }

    /**
     * Returns true if the player holds at least one of the named item.
     */
    public boolean hasItem(String itemName) {
        return items.getOrDefault(itemName, 0) > 0;
    }
}