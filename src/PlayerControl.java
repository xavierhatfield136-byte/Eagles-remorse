import java.awt.event.*;

public class PlayerControl implements KeyListener, MouseMotionListener {

    private final Player player;

    private boolean up, down, left, right;
    private boolean boost;
    private double mouseX, mouseY;

    public PlayerControl(Player player) {
        this.player = player;
    }

    public InputSnapshot snapshot() {
        return new InputSnapshot(up, down, left, right, boost, mouseX, mouseY);
    }

    public void update(double dt) {
        if (player == null) return;
        // Keep legacy control path aligned with runtime input handling.
        double speed = Math.max(55.0, player.desiredSpeed);

        double vx = 0, vy = 0;
        if (up) vy -= speed;
        if (down) vy += speed;
        if (left) vx -= speed;
        if (right) vx += speed;

        if (vx != 0 && vy != 0) {
            double inv = 1.0 / Math.sqrt(2);
            vx *= inv;
            vy *= inv;
        }

        player.vx = vx * dt;
        player.vy = vy * dt;
    }

    public void updateAim(double worldX, double worldY) {
        if (player == null) return;
        double dx = worldX - player.x;
        double dy = worldY - player.y;
        player.angle = Math.atan2(dy, dx);
    }

    public double getMouseX() { return mouseX; }
    public double getMouseY() { return mouseY; }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W -> up = true;
            case KeyEvent.VK_S -> down = true;
            case KeyEvent.VK_A -> left = true;
            case KeyEvent.VK_D -> right = true;
            case KeyEvent.VK_SHIFT -> boost = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W -> up = false;
            case KeyEvent.VK_S -> down = false;
            case KeyEvent.VK_A -> left = false;
            case KeyEvent.VK_D -> right = false;
            case KeyEvent.VK_SHIFT -> boost = false;
        }
    }

    @Override public void keyTyped(KeyEvent e) {}
}
