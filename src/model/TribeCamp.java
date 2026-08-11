package model;

public class TribeCamp extends Building {

    private final Tribe tribe;
    private boolean hasTradedThisTurn;

    /**
     * ساخت کمپ قبیله بر اساس نوع — HP متناسب با نوع قبیله (طبق spec).
     */
    public TribeCamp(TribeType type) {
        super(BuildingType.TRIBE_CAMP.getMaxWorkers());
        this.tribe            = new Tribe(type);
        this.hasTradedThisTurn = false;
        this.setMaxHp(type.getMaxHp());
        this.hp = type.getMaxHp();
    }

    @Override
    public BuildingType getType() { return BuildingType.TRIBE_CAMP; }

    public Tribe getTribe() { return tribe; }

    // Trade flag — ریست در TradeController.onTurnEnded
    public boolean hasTraded()               { return hasTradedThisTurn; }
    public void    setTraded(boolean traded) { this.hasTradedThisTurn = traded; }
}