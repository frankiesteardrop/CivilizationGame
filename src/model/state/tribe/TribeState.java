package model.state.tribe;
import model.GameMap;
import model.Hex;
import model.Tribe;
import model.TribeCamp;
import java.util.List;

public interface TribeState {
    String getName();
    boolean canTrade();
    boolean canReceiveGift();
    boolean canFormAlliance();
    boolean canRequestPeace();

    // اصلاح کلیدی گام سوم: اضافه شدن متدهای اختصاصی برای جلوگیری از String comparison
    boolean isHostile();
    boolean canDeclareWar();

    void executeTurnBehavior(Tribe tribe, TribeCamp camp, Hex campHex, GameMap map, List<Runnable> deferredActions);
}