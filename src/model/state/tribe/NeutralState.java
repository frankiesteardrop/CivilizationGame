package model.state.tribe;

import model.GameMap;
import model.Hex;
import model.Tribe;
import model.TribeCamp;
import java.util.List;

public class NeutralState implements TribeState {

    @Override
    public String getName() { return "Neutral"; }

    @Override
    public boolean canTrade() { return false; }

    @Override
    public boolean canReceiveGift() { return true; }

    @Override
    public boolean canFormAlliance() { return false; }

    @Override
    public boolean canRequestPeace() { return false; }

    // اصلاح گام سوم
    @Override public boolean isHostile() { return false; }
    @Override public boolean canDeclareWar() { return true; }

    @Override
    public void executeTurnBehavior(Tribe tribe, TribeCamp camp, Hex campHex, GameMap map, List<Runnable> deferredActions) {
        // قبیله‌ی خنثی رفتار تهاجمی یا دوستانه خاصی در پایان نوبت ندارد
    }
}