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
            // آپدیت معماری MVC: اطلاع به لایه View بابت تغییر وضعیت (کسر شارژ)
            GameEventDispatcher.fireUnitStateChanged(this);
        }

        // ایمن‌سازی فرآیند حذف (Consume)
        if (charges == 0 && this.isAlive()) {
            this.kill();
        }
    }
}