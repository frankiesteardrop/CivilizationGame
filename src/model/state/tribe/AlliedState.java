package model.state.tribe;

import model.GameMap;
import model.Hex;
import model.ResourceType;
import model.Tribe;
import model.TribeCamp;
import java.util.List;

public class AlliedState implements TribeState {

    @Override
    public String getName() { return "Allied"; }

    @Override
    public boolean canTrade() { return true; }

    @Override
    public boolean canReceiveGift() { return true; }

    @Override
    public boolean canFormAlliance() { return false; }

    @Override
    public boolean canRequestPeace() { return false; }

    @Override public boolean isHostile() { return false; }
    @Override public boolean canDeclareWar() { return true; }

    @Override
    public void executeTurnBehavior(Tribe tribe, TribeCamp camp, Hex campHex, GameMap map, List<Runnable> deferredActions) {
        switch (tribe.getType()) {
            case FARMER -> map.getTownHall().getInventory().addResource(ResourceType.FOOD, 5);
            case MOUNTAIN -> map.getTownHall().getInventory().addResource(ResourceType.STONE, 5);
            case COMMERCIAL -> map.getTownHall().getInventory().addResource(ResourceType.WOOD, 3);
            case COASTAL -> map.getTownHall().getInventory().addResource(ResourceType.FOOD, 3);
            case WARRIOR -> {
            }
        }
    }
}