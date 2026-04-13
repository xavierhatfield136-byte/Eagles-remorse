/** Simple per-base upgrade tracking (kept separate so GamePanel stays small). */
public class BaseUpgrades {
    public int hullLv = 0;
    public int shieldLv = 0;
    public int turretLv = 0;
    public int miningLv = 0;
    public int hangarLv = 0;

    public BaseUpgrades bindTo(Ship ship) {
        if (ship != null) ship.stationUpgrades = this;
        return this;
    }
}
