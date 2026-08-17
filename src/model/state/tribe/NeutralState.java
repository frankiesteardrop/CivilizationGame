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
    public boolean canTrade() { return false; } // تجارت نیاز به رابطه دوستانه یا متحد دارد

    @Override
    public boolean canReceiveGift() { return true; } // در حالت خنثی می‌توان هدیه داد

    @Override
    public boolean canFormAlliance() { return false; }

    @Override
    public boolean canRequestPeace() { return false; }

    @Override
    public void executeTurnBehavior(Tribe tribe, TribeCamp camp, Hex campHex, GameMap map, List<Runnable> deferredActions) {
        // قبیله‌ی خنثی رفتار تهاجمی یا دوستانه خاصی در پایان نوبت ندارد
        // فقط در محدوده‌ی خود باقی می‌ماند و کار خاصی نمی‌کند.
    }
}