public final class InputSnapshot {
    public final boolean up;
    public final boolean down;
    public final boolean left;
    public final boolean right;
    public final boolean boost;
    public final double mouseX;
    public final double mouseY;

    public InputSnapshot(boolean up, boolean down, boolean left, boolean right, boolean boost,
                         double mouseX, double mouseY) {
        this.up = up;
        this.down = down;
        this.left = left;
        this.right = right;
        this.boost = boost;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }
}
