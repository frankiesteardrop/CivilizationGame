package model;

public class Bazaar extends Building {

    private boolean hasTradedThisTurn;
    // [M1] Fix: اضافه شدن فیلد سطح (Level) برای حفظ State در Model
    private int level;

    public Bazaar() {
        super(BuildingType.BAZAAR.getMaxWorkers());
        this.hasTradedThisTurn = false;
        this.level = 1; // مقدار اولیه بازار
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

    // [M1] Fix: متدهای مدیریت کپسوله‌شده‌ی سطح بازار
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