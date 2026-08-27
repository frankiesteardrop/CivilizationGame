package model;

public class Bazaar extends Building {

    private boolean hasTradedThisTurn;
    private int level;

    public Bazaar() {
        super(BuildingType.BAZAAR.getMaxWorkers());
        this.hasTradedThisTurn = false;
        this.level = 1;
    }

    @Override
    public BuildingType getType() {
        return BuildingType.BAZAAR;
    }

    public boolean hasTraded() {
        return hasTradedThisTurn;
    }

    public void setTraded(boolean traded) {
        this.hasTradedThisTurn = traded;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        if (level >= 1 && level <= 3) {
            this.level = level;
        }
    }

    public boolean canUpgrade() {
        return level < 3;
    }

    public void upgrade() {
        if (canUpgrade()) {
            level++;
        }
    }
}