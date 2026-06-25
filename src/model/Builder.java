package model;

public class Builder extends Unit {
    private int charges;

    public Builder(int q, int r) {
        // اختصاص مقادیر: AP = 3، مصرف غذا = 2، شعاع دید = 1
        super(q, r, 3, 2, 1);
        this.charges = 3; // هر بیلدر ۳ بار قابلیت ساخت دارد
    }

    public int getCharges() { return charges; }

    public void useCharge() {
        if (charges > 0) {
            charges--;
        }
        if (charges == 0) {
            this.kill(); // با صفر شدن شارژ، یونیت از بین می‌رود
        }
    }
}