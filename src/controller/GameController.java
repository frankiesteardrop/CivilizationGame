package controller;

import model.*;
import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * کنترلر مرکزی بازی. تمامی منطق‌های تجاری، ساخت‌وساز، حرکت و ارتقا در این لایه پردازش می‌شوند.
 * این کلاس پل ارتباطی ایمن بین View و Model است.
 */
public class GameController {
    private final GameMap gameMap;

    public GameController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public GameMap getGameMap() {
        return gameMap;
    }

    public void endTurn() {
        gameMap.nextTurn();
    }

    public boolean canMove(Unit unit, Hex targetHex, Component parentView) {
        if (unit instanceof Worker && ((Worker) unit).isStationed()) {
            JOptionPane.showMessageDialog(parentView, "Worker is stationed. Leave facility first!");
            return false;
        }

        int dq = targetHex.getQ() - unit.getQ();
        int dr = targetHex.getR() - unit.getR();
        boolean isNeighbor = (Math.abs(dq) <= 1 && Math.abs(dr) <= 1 && Math.abs(dq + dr) <= 1) && !(dq == 0 && dr == 0);

        if (!isNeighbor) {
            JOptionPane.showMessageDialog(parentView, "Movement is only allowed to adjacent hexes!");
            return false;
        }

        int cost = targetHex.getTerrainType().getMovementCost();
        if (unit.getCurrentAP() < cost) {
            JOptionPane.showMessageDialog(parentView, "Not enough Action Points (AP)! Need " + cost);
            return false;
        }
        return true;
    }

    // منطق ارتقای انبار
    public void handleWarehouseUpgrade() {
        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();
        if (th.getWarehouseUpgradeLevel() < 2 && inv.consumeResource(ResourceType.WOOD, 100) && inv.consumeResource(ResourceType.STONE, 50)) {
            th.upgradeWarehouse();
        }
    }

    // منطق آنلاک تکنولوژی‌ها
    public void unlockTech(String techType) {
        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();
        switch (techType) {
            case "STONE_MINE":
                if (!th.isStoneMineUnlocked() && inv.consumeResource(ResourceType.WOOD, 50)) th.setStoneMineUnlocked(true);
                break;
            case "IRON_MINE":
                if (!th.isIronMineUnlocked() && inv.consumeResource(ResourceType.WOOD, 100) && inv.consumeResource(ResourceType.STONE, 50)) th.setIronMineUnlocked(true);
                break;
            case "PROF_TOOLS":
                if (!th.isProfessionalToolsUnlocked() && inv.consumeResource(ResourceType.WOOD, 100) && inv.consumeResource(ResourceType.STONE, 100) && inv.consumeResource(ResourceType.IRON, 50)) th.setProfessionalToolsUnlocked(true);
                break;
            case "SETTLEMENT":
                if (!th.isSettlementUnlocked() && inv.consumeResource(ResourceType.WOOD, 200) && inv.consumeResource(ResourceType.STONE, 100)) th.setSettlementUnlocked(true);
                break;
        }
    }

    public boolean checkTerrainForBuilding(BuildingType type, Hex hex) {
        TownHall th = gameMap.getTownHall();
        switch (type) {
            case LUMBER_MILL: return hex.getTerrainType() == TerrainType.FOREST;
            case FARM: return hex.getTerrainType() == TerrainType.MEADOW && hex.getResourceType() == ResourceType.FOOD;
            case STABLE: return hex.getTerrainType() == TerrainType.PLAINS && hex.getResourceType() == ResourceType.FOOD;
            case STONE_MINE: return th.isStoneMineUnlocked() && hex.getTerrainType() == TerrainType.MOUNTAIN && hex.getResourceType() == ResourceType.STONE;
            case IRON_MINE: return th.isIronMineUnlocked() && hex.getTerrainType() == TerrainType.MOUNTAIN && hex.getResourceType() == ResourceType.IRON;
            case SETTLEMENT: return th.isSettlementUnlocked() && hex.getResourceType() == ResourceType.NONE && hex.isInsideBorder();
            default: return false;
        }
    }

    public void buildStructure(Builder builder, BuildingType type, Hex hex) {
        Inventory inv = gameMap.getTownHall().getInventory();
        if (inv.consumeResource(ResourceType.WOOD, type.getWoodCost()) &&
                inv.consumeResource(ResourceType.STONE, type.getStoneCost()) &&
                inv.consumeResource(ResourceType.IRON, type.getIronCost())) {

            builder.consumeAP(type.getApCost());
            builder.useCharge();

            // استفاده از چندریختی ساخته شده در گام قبل
            switch (type) {
                case LUMBER_MILL: hex.setBuilding(new LumberMill()); break;
                case STONE_MINE: hex.setBuilding(new StoneMine()); break;
                case IRON_MINE: hex.setBuilding(new IronMine()); break;
                case FARM: hex.setBuilding(new Farm()); break;
                case STABLE: hex.setBuilding(new Stable()); break;
                case SETTLEMENT: hex.setBuilding(new Settlement()); break;
            }
        }
    }
}