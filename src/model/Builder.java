package model;

public class Builder extends Unit {
    private int charges;

    public Builder(int q, int r) {

        super(q, r, UnitType.BUILDER);
        this.charges = GameConfig.BUILDER_INITIAL_CHARGES;
    }

    public int getCharges() { return charges; }

    public void useCharge() {
        if (charges > 0) {
            charges--;
        }
        if (charges == 0) {
            this.kill();
        }
    }
}