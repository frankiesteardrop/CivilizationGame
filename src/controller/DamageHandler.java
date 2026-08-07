package controller;

import model.Unit;
import java.util.List;

public abstract class DamageHandler {
    protected DamageHandler next;

    public void setNext(DamageHandler next) {
        this.next = next;
    }

    public abstract void handleDamage(List<Unit> units, int damageAmount);
}