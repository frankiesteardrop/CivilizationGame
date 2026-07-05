package model;

public class Explorer extends Unit {

    public Explorer(int q, int r) {
        // [اصلاح شد]: ارسال نوع یونیت (UnitType.EXPLORER) به جای اعداد هاردکد شده
        super(q, r, UnitType.EXPLORER);
    }
}