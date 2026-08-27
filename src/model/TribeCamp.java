package model;

public class TribeCamp extends Building {

    private final Tribe tribe;
    private boolean hasTradedThisTurn;

    private int guardSpawnTurnCounter;
    private int missionOfferTurnCounter;
    private int displeasedMilitaryTurns;

    private int neutralMilitaryTurns;

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

    public boolean hasTraded()               { return hasTradedThisTurn; }
    public void    setTraded(boolean traded) { this.hasTradedThisTurn = traded; }


    public boolean isDiscovered()               { return discovered; }
    public void    setDiscovered(boolean value) { this.discovered = value; }


    public int  getAndIncrementGuardCounter()    { return guardSpawnTurnCounter++; }
    public void resetGuardCounter()              { guardSpawnTurnCounter = 0; }

    public int  getAndIncrementMissionCounter()  { return missionOfferTurnCounter++; }
    public void resetMissionCounter()            { missionOfferTurnCounter = 0; }

    public int  getDispleasedMilitaryTurns()     { return displeasedMilitaryTurns; }
    public void incrementDispleasedMilTurns()    { displeasedMilitaryTurns++; }
    public void resetDispleasedMilTurns()        { displeasedMilitaryTurns = 0; }


    public int  getNeutralMilitaryTurns()        { return neutralMilitaryTurns; }
    public void incrementNeutralMilTurns()       { neutralMilitaryTurns++; }
    public void resetNeutralMilTurns()           { neutralMilitaryTurns = 0; }
}