package model;

/**
 * کمپ قبیله بی‌طرف.
 *
 * F-25: دو counter برای رفتار per-turn:
 *   guardSpawnTurnCounter  → هر ۳ ترن برای قبیله دشمن
 *   missionOfferTurnCounter → هر ۵ ترن برای قبیله دوستانه
 */
public class TribeCamp extends Building {

    private final Tribe tribe;
    private boolean hasTradedThisTurn;

    // F-25: counter های رفتار per-turn قبیله
    private int guardSpawnTurnCounter;    // برای Enemy: spawn guard هر ۳ ترن
    private int missionOfferTurnCounter;  // برای Friendly: mission offer هر ۵ ترن
    private int displeasedMilitaryTurns;  // برای Displeased: ترن‌های حضور نظامی

    public TribeCamp(TribeType type) {
        super(BuildingType.TRIBE_CAMP.getMaxWorkers());
        this.tribe                  = new Tribe(type);
        this.hasTradedThisTurn      = false;
        this.guardSpawnTurnCounter  = 0;
        this.missionOfferTurnCounter = 0;
        this.displeasedMilitaryTurns = 0;
        this.setMaxHp(type.getMaxHp());
        this.hp = type.getMaxHp();
    }

    @Override
    public BuildingType getType() { return BuildingType.TRIBE_CAMP; }

    public Tribe getTribe() { return tribe; }

    // ─── Trade flag ───────────────────────────────────────────────────────────
    public boolean hasTraded()               { return hasTradedThisTurn; }
    public void    setTraded(boolean traded) { this.hasTradedThisTurn = traded; }

    // ─── F-25: Counter accessors ──────────────────────────────────────────────

    /** بازگرداندن و افزایش counter گارد (برای Enemy). */
    public int  getAndIncrementGuardCounter()    { return guardSpawnTurnCounter++; }
    public void resetGuardCounter()              { guardSpawnTurnCounter = 0; }

    /** بازگرداندن و افزایش counter مأموریت (برای Friendly). */
    public int  getAndIncrementMissionCounter()  { return missionOfferTurnCounter++; }
    public void resetMissionCounter()            { missionOfferTurnCounter = 0; }

    /** شمارش ترن‌های حضور نظامی در نزدیکی (برای Displeased). */
    public int  getDispleasedMilitaryTurns()     { return displeasedMilitaryTurns; }
    public void incrementDispleasedMilTurns()    { displeasedMilitaryTurns++; }
    public void resetDispleasedMilTurns()        { displeasedMilitaryTurns = 0; }
}