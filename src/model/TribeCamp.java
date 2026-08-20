package model;

/**
 * کمپ قبیله بی‌طرف.
 *
 * F-25: دو counter برای رفتار per-turn:
 *   guardSpawnTurnCounter   → هر ۳ ترن برای قبیله دشمن
 *   missionOfferTurnCounter → هر ۵ ترن برای قبیله دوستانه
 *
 * I7 (گام ۵): counter جدید برای NeutralState:
 *   neutralMilitaryTurns → ترن‌های متوالی حضور نظامی در محدوده ممنوعه
 *
 * M4 (گام ۷): فلگ جداگانه discovered برای تمایز:
 *   - hex.isExplored(): آیا hex قبلاً دیده شده (برای FoW rendering)
 *   - camp.isDiscovered(): آیا قبیله توسط یک یونیت کشف شده (برای interaction)
 *   طبق spec، فقط وقتی یک یونیت (نه ساختمان) به شعاع دید کمپ برسد،
 *   قبیله «کشف‌شده» محسوب می‌شود و پنل تعامل باز می‌شود.
 */
public class TribeCamp extends Building {

    private final Tribe tribe;
    private boolean hasTradedThisTurn;

    // F-25: counter های رفتار per-turn قبیله
    private int guardSpawnTurnCounter;     // Enemy: spawn guard هر ۳ ترن
    private int missionOfferTurnCounter;   // Friendly: mission offer هر ۵ ترن
    private int displeasedMilitaryTurns;   // Displeased: ترن‌های حضور نظامی

    // I7 (گام ۵): counter برای NeutralState
    private int neutralMilitaryTurns;

    // M4 (گام ۷): فلگ کشف قبیله — فقط توسط یونیت‌ها (نه ساختمان) set می‌شود
    private boolean discovered;

    public TribeCamp(TribeType type) {
        super(BuildingType.TRIBE_CAMP.getMaxWorkers());
        this.tribe                   = new Tribe(type);
        this.hasTradedThisTurn       = false;
        this.guardSpawnTurnCounter   = 0;
        this.missionOfferTurnCounter = 0;
        this.displeasedMilitaryTurns = 0;
        this.neutralMilitaryTurns    = 0;
        this.discovered              = false;
        this.setMaxHp(type.getMaxHp());
        this.hp = type.getMaxHp();
    }

    @Override
    public BuildingType getType() { return BuildingType.TRIBE_CAMP; }

    public Tribe getTribe() { return tribe; }

    // ─── Trade flag ───────────────────────────────────────────────────────────
    public boolean hasTraded()               { return hasTradedThisTurn; }
    public void    setTraded(boolean traded) { this.hasTradedThisTurn = traded; }

    // ─── M4: Discovery flag ───────────────────────────────────────────────────

    /**
     * آیا این قبیله توسط یک یونیت بازیکن کشف شده است؟
     * کشف‌شده = کمپ اصلی قبیله حداقل یک بار در شعاع دید یک یونیت قرار گرفته.
     * ساختمان‌ها این flag را set نمی‌کنند — فقط یونیت‌ها.
     */
    public boolean isDiscovered()               { return discovered; }
    public void    setDiscovered(boolean value) { this.discovered = value; }

    // ─── F-25: Counter accessors ──────────────────────────────────────────────

    public int  getAndIncrementGuardCounter()    { return guardSpawnTurnCounter++; }
    public void resetGuardCounter()              { guardSpawnTurnCounter = 0; }

    public int  getAndIncrementMissionCounter()  { return missionOfferTurnCounter++; }
    public void resetMissionCounter()            { missionOfferTurnCounter = 0; }

    public int  getDispleasedMilitaryTurns()     { return displeasedMilitaryTurns; }
    public void incrementDispleasedMilTurns()    { displeasedMilitaryTurns++; }
    public void resetDispleasedMilTurns()        { displeasedMilitaryTurns = 0; }

    // ─── I7 (گام ۵): Counter برای NeutralState ───────────────────────────────

    public int  getNeutralMilitaryTurns()        { return neutralMilitaryTurns; }
    public void incrementNeutralMilTurns()       { neutralMilitaryTurns++; }
    public void resetNeutralMilTurns()           { neutralMilitaryTurns = 0; }
}