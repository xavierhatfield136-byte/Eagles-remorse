import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.zip.CRC32;

/**
 * Focused readability/playtest harness for the tactical x-ray room map.
 * Produces deterministic sustained-combat snapshots and objective pass/fail signals.
 */
public final class XrayReadabilityHarness {
    private XrayReadabilityHarness() {}

    private static final int VIEW_W = 1920;
    private static final int VIEW_H = 1080;
    private static final long STEP_NS = 16_666_667L;
    private static final int DEFAULT_TICKS = 3600; // 60s @ 60Hz
    private static final int SAMPLE_EVERY_TICKS = 30;
    private static final int SNAPSHOT_EVERY_SAMPLES = 12;
    private static final Font SYMBOL_FONT = new Font("Consolas", Font.BOLD, 10);
    private static final Font PCT_FONT = new Font("Consolas", Font.PLAIN, 10);

    private static final class Args {
        long seed = 424242L;
        int ticks = DEFAULT_TICKS;
        boolean strict = false;
        Path output = Path.of("build", "reports", "xray_readability_report.json");
        Path snapshotDir = Path.of("build", "reports", "xray_readability_snapshots");

        static Args parse(String[] argv) {
            Args out = new Args();
            if (argv == null) return out;
            for (String arg : argv) {
                if (arg == null || arg.isBlank()) continue;
                if (arg.startsWith("--seed=")) {
                    try { out.seed = Long.parseLong(arg.substring("--seed=".length()).trim()); } catch (Throwable ignored) {}
                } else if (arg.startsWith("--ticks=")) {
                    try { out.ticks = Math.max(600, Integer.parseInt(arg.substring("--ticks=".length()).trim())); } catch (Throwable ignored) {}
                } else if (arg.startsWith("--output=")) {
                    String raw = arg.substring("--output=".length()).trim();
                    if (!raw.isBlank()) out.output = Path.of(raw);
                } else if (arg.startsWith("--snapshot-dir=")) {
                    String raw = arg.substring("--snapshot-dir=".length()).trim();
                    if (!raw.isBlank()) out.snapshotDir = Path.of(raw);
                } else if ("--strict".equalsIgnoreCase(arg)) {
                    out.strict = true;
                }
            }
            return out;
        }
    }

    private static final class Counters {
        int samples = 0;
        int hudSamples = 0;
        int panelVisibleSamples = 0;
        int stateChangeSamples = 0;
        int visualChangeSamples = 0;
        int stateChangedNoVisual = 0;
        int labelOverlapPairs = 0;
        int maxLabelOverlapArea = 0;
        int minRoomArea = Integer.MAX_VALUE;
        int maxRoomArea = 0;
    }

    public static void main(String[] argv) throws Exception {
        Args args = Args.parse(argv);
        Files.createDirectories(args.snapshotDir);
        Path outputParent = args.output.getParent();
        if (outputParent != null) Files.createDirectories(outputParent);

        Method drawHud = Renderer.class.getDeclaredMethod(
                "drawLockedTargetXrayHud",
                Graphics2D.class, GameContext.class, Player.class, Ship.class, boolean.class, int.class, int.class
        );
        drawHud.setAccessible(true);

        Method stackLayout = Renderer.class.getDeclaredMethod(
                "computeXrayStackLayout",
                Player.class, Ship.class, boolean.class, int.class, int.class
        );
        stackLayout.setAccessible(true);

        Method mapRect = Renderer.class.getDeclaredMethod(
                "xrayMapRect",
                int.class, int.class, int.class, int.class
        );
        mapRect.setAccessible(true);

        Method roomPoly = Renderer.class.getDeclaredMethod(
                "xrayRoomPolygon",
                int.class, int.class, int.class, int.class, double[].class, double[].class
        );
        roomPoly.setAccessible(true);

        GameConfig cfg = new GameConfig(GameMode.RESOURCE_RUSH, 5000, 5000, true, args.seed, false);
        GameContext ctx = new GameContext(cfg);
        SpawnSystem.initWorld(ctx);
        if (ctx.player == null) {
            System.err.println("[xray-readability] player_missing");
            if (args.strict) System.exit(2);
            return;
        }

        Player player = ctx.player;
        Random rng = new Random(args.seed ^ 0x1A2B3C4D5E6FL);
        GameSimulationRuntime runtime = new GameSimulationRuntime(ctx);
        long now = System.nanoTime();

        Counters c = new Counters();
        long prevHash = 0L;
        long prevStateHash = 0L;
        int snapIndex = 0;
        List<String> snapshotFiles = new ArrayList<>();

        for (int tick = 1; tick <= args.ticks; tick++) {
            // Keep sustained-combat pressure deterministic.
            if ((tick % 75) == 0) applyLocalizedDamage(player, rng);
            if ((tick % 120) == 0) ensureLockedTarget(ctx, player, rng);
            if ((tick % 150) == 0 && ctx.lockedTarget != null && ctx.lockedTarget.alive) {
                applyLocalizedDamage(ctx.lockedTarget, rng);
            }
            if ((tick % 180) == 0) {
                SpawnSystem.spawnEnemy(ctx, ShipRole.FRIGATE, player.x + 300 + rng.nextDouble() * 180.0, player.y - 100 + rng.nextDouble() * 200.0);
            }

            ensureLockedTarget(ctx, player, rng);
            now += STEP_NS;
            runtime.advanceFrame(now, new InputSnapshot(false, false, false, false, false, 0, 0), VIEW_W, VIEW_H, 1.0);

            if ((tick % SAMPLE_EVERY_TICKS) != 0) continue;
            c.samples++;

            BufferedImage frame = new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = frame.createGraphics();
            g2.setClip(0, 0, VIEW_W, VIEW_H);
            try {
                drawHud.invoke(null, g2, ctx, player, ctx.lockedTarget, false, VIEW_W, VIEW_H);
            } finally {
                g2.dispose();
            }

            Object layoutObj = stackLayout.invoke(null, player, ctx.lockedTarget, false, VIEW_W, VIEW_H);
            if (layoutObj == null) continue;
            c.hudSamples++;
            Rectangle panelRect = extractPlayerPanelRect(layoutObj);
            Rectangle xr = (Rectangle) mapRect.invoke(null, panelRect.x, panelRect.y, panelRect.width, panelRect.height);
            int alphaPixels = alphaPixels(frame, panelRect);
            if (alphaPixels > 1200) c.panelVisibleSamples++;

            LabelStats ls = evaluateLabelLayout(player, xr, roomPoly);
            c.labelOverlapPairs = Math.max(c.labelOverlapPairs, ls.overlapPairs);
            c.maxLabelOverlapArea = Math.max(c.maxLabelOverlapArea, ls.maxOverlapArea);
            c.minRoomArea = Math.min(c.minRoomArea, ls.minRoomArea);
            c.maxRoomArea = Math.max(c.maxRoomArea, ls.maxRoomArea);

            long frameHash = crc32(frame);
            long stateHash = roomStateHash(player);
            boolean stateChanged = (c.samples > 1) && (stateHash != prevStateHash);
            boolean visualChanged = (c.samples > 1) && (frameHash != prevHash);
            if (stateChanged) {
                c.stateChangeSamples++;
                if (!visualChanged) c.stateChangedNoVisual++;
            }
            if (visualChanged) c.visualChangeSamples++;

            prevHash = frameHash;
            prevStateHash = stateHash;

            if (snapIndex < 8 && (snapIndex == 0 || visualChanged || (c.samples % SNAPSHOT_EVERY_SAMPLES) == 0)) {
                Path out = args.snapshotDir.resolve(String.format(Locale.US, "xray_readability_%02d.png", snapIndex + 1));
                ImageIO.write(frame, "png", out.toFile());
                snapshotFiles.add(out.toString().replace('\\', '/'));

                Rectangle stackRect = extractStackRect(layoutObj);
                Rectangle expanded = new Rectangle(
                        Math.max(0, stackRect.x - 12),
                        Math.max(0, stackRect.y - 12),
                        Math.min(frame.getWidth() - Math.max(0, stackRect.x - 12), stackRect.width + 24),
                        Math.min(frame.getHeight() - Math.max(0, stackRect.y - 12), stackRect.height + 24)
                );
                BufferedImage crop = frame.getSubimage(expanded.x, expanded.y, expanded.width, expanded.height);
                Path cropOut = args.snapshotDir.resolve(String.format(Locale.US, "xray_readability_%02d_crop.png", snapIndex + 1));
                ImageIO.write(crop, "png", cropOut.toFile());
                snapIndex++;
            }
        }

        boolean passPanelVisible = c.hudSamples > 0 && c.panelVisibleSamples == c.hudSamples;
        boolean passRealtime = c.stateChangeSamples >= 6 && c.stateChangedNoVisual <= 1;
        boolean passLabelLayout = c.labelOverlapPairs <= 8 && c.maxLabelOverlapArea <= 360 && c.minRoomArea >= 150;
        boolean pass = passPanelVisible && passRealtime && passLabelLayout;

        String json = buildJson(args, c, passPanelVisible, passRealtime, passLabelLayout, pass, snapshotFiles);
        Files.writeString(args.output, json, StandardCharsets.UTF_8);

        System.out.println("[xray-readability] wrote " + args.output.toAbsolutePath());
        System.out.println("[xray-readability] snapshots " + args.snapshotDir.toAbsolutePath());
        System.out.println("[xray-readability] samples=" + c.samples
                + " hudSamples=" + c.hudSamples
                + " panelVisible=" + c.panelVisibleSamples
                + " stateChanges=" + c.stateChangeSamples
                + " visualChanges=" + c.visualChangeSamples
                + " lagged=" + c.stateChangedNoVisual);
        System.out.println("[xray-readability] label overlapPairs=" + c.labelOverlapPairs
                + " maxOverlapArea=" + c.maxLabelOverlapArea
                + " minRoomArea=" + c.minRoomArea);
        System.out.println("[xray-readability] pass panelVisible=" + passPanelVisible
                + " realtime=" + passRealtime
                + " labelLayout=" + passLabelLayout
                + " overall=" + pass);

        if (args.strict && !pass) System.exit(2);
    }

    private static void ensureLockedTarget(GameContext ctx, Player player, Random rng) {
        if (ctx == null || player == null) return;
        Ship current = ctx.lockedTarget;
        if (current != null && current.alive && !current.dying && current.hp > 0 && current.faction != null
                && !current.faction.isFriendlyTo(player.faction)) {
            return;
        }
        Ship best = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        for (Ship s : ctx.ships) {
            if (s == null || !s.alive || s.dying || s.hp <= 0) continue;
            if (s.faction == null || s.faction.isFriendlyTo(player.faction)) continue;
            double dx = s.x - player.x;
            double dy = s.y - player.y;
            double d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = s;
            }
        }
        if (best == null) {
            double sx = player.x + 280.0 + rng.nextDouble() * 160.0;
            double sy = player.y - 120.0 + rng.nextDouble() * 240.0;
            SpawnSystem.spawnEnemy(ctx, ShipRole.FRIGATE, sx, sy);
            for (int i = ctx.ships.size() - 1; i >= 0; i--) {
                Ship s = ctx.ships.get(i);
                if (s != null && s.alive && s.faction != null && !s.faction.isFriendlyTo(player.faction)) {
                    best = s;
                    break;
                }
            }
        }
        ctx.lockedTarget = best;
    }

    private static void applyLocalizedDamage(Ship ship, Random rng) {
        if (ship == null || !ship.alive || ship.dying || ship.hp <= 0) return;
        List<ShipRoomLayout.RoomDef> defs = ShipRoomLayout.profileFor(ship.role);
        if (defs == null || defs.isEmpty()) return;
        ShipRoomLayout.RoomDef room = defs.get(rng.nextInt(defs.size()));
        if (room == null || room.id == null) return;
        double nx = avg(room.xs);
        double ny = avg(room.ys);
        double wx = ship.x + nx * ship.radius;
        double wy = ship.y + ny * ship.radius;
        int dmg = 2 + rng.nextInt(5);
        ship.takeDamage(dmg, wx, wy, 0.0, 0.0);
    }

    private static double avg(double[] arr) {
        if (arr == null || arr.length == 0) return 0.0;
        double sum = 0.0;
        for (double v : arr) sum += v;
        return sum / arr.length;
    }

    private static Rectangle extractPlayerPanelRect(Object layoutObj) throws Exception {
        Class<?> c = layoutObj.getClass();
        Field fx = c.getDeclaredField("panelX");
        Field fw = c.getDeclaredField("panelW");
        Field fy = c.getDeclaredField("playerY");
        Field fh = c.getDeclaredField("playerH");
        fx.setAccessible(true);
        fw.setAccessible(true);
        fy.setAccessible(true);
        fh.setAccessible(true);
        return new Rectangle(
                fx.getInt(layoutObj),
                fy.getInt(layoutObj),
                fw.getInt(layoutObj),
                fh.getInt(layoutObj)
        );
    }

    private static Rectangle extractStackRect(Object layoutObj) throws Exception {
        Class<?> c = layoutObj.getClass();
        Field fx = c.getDeclaredField("panelX");
        Field fw = c.getDeclaredField("panelW");
        Field ftx = c.getDeclaredField("targetX");
        Field fpy = c.getDeclaredField("playerY");
        Field fph = c.getDeclaredField("playerH");
        Field fty = c.getDeclaredField("targetY");
        Field fth = c.getDeclaredField("targetH");
        Field ftv = c.getDeclaredField("targetVisible");
        fx.setAccessible(true);
        fw.setAccessible(true);
        ftx.setAccessible(true);
        fpy.setAccessible(true);
        fph.setAccessible(true);
        fty.setAccessible(true);
        fth.setAccessible(true);
        ftv.setAccessible(true);

        int x = fx.getInt(layoutObj);
        int w = fw.getInt(layoutObj);
        int tx = ftx.getInt(layoutObj);
        int py = fpy.getInt(layoutObj);
        int ph = fph.getInt(layoutObj);
        int ty = fty.getInt(layoutObj);
        int th = fth.getInt(layoutObj);
        boolean tv = ftv.getBoolean(layoutObj);

        int x0 = x;
        int x1 = x + w;
        int y0 = py;
        int y1 = py + ph;
        if (tv && th > 0) {
            x0 = Math.min(x0, tx);
            x1 = Math.max(x1, tx + w);
            y0 = Math.min(y0, ty);
            y1 = Math.max(y1, ty + th);
        }
        return new Rectangle(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
    }

    private static int alphaPixels(BufferedImage image, Rectangle rect) {
        int x0 = Math.max(0, rect.x);
        int y0 = Math.max(0, rect.y);
        int x1 = Math.min(image.getWidth(), rect.x + rect.width);
        int y1 = Math.min(image.getHeight(), rect.y + rect.height);
        int count = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int a = (image.getRGB(x, y) >>> 24) & 0xFF;
                if (a > 8) count++;
            }
        }
        return count;
    }

    private record LabelStats(int overlapPairs, int maxOverlapArea, int minRoomArea, int maxRoomArea) {}

    private static LabelStats evaluateLabelLayout(Player player, Rectangle mapRect, Method roomPoly) throws Exception {
        BufferedImage tmp = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = tmp.createGraphics();
        g2.setFont(SYMBOL_FONT);
        FontMetrics symFm = g2.getFontMetrics();
        g2.setFont(PCT_FONT);
        FontMetrics pctFm = g2.getFontMetrics();
        g2.dispose();

        List<LabelRect> labelRects = new ArrayList<>();
        List<ShipRoomLayout.RoomDef> defs = ShipRoomLayout.profileFor(player.role);
        int minArea = Integer.MAX_VALUE;
        int maxArea = 0;
        int roomIndex = 0;

        for (ShipRoomLayout.RoomDef room : defs) {
            if (room == null || room.id == null) continue;
            Polygon p = (Polygon) roomPoly.invoke(null, mapRect.x, mapRect.y, mapRect.width, mapRect.height, room.xs, room.ys);
            if (p == null || p.npoints < 3) continue;
            Rectangle b = p.getBounds();
            minArea = Math.min(minArea, Math.max(0, b.width * b.height));
            maxArea = Math.max(maxArea, Math.max(0, b.width * b.height));
            int cx = (int) Math.round(b.getCenterX());
            int cy = (int) Math.round(b.getCenterY());

            String sym = symbolFor(room.id);
            int sw = symFm.stringWidth(sym);
            int sh = symFm.getAscent();
            int sx = cx - sw / 2 - 4;
            int sy = cy - 14 - sh;
            labelRects.add(new LabelRect(roomIndex, new Rectangle(sx, sy, sw + 8, sh + 5)));

            int pctVal = MathUtil.clamp((int) Math.round(player.roomHealthFraction(room.id) * 100.0), 0, 100);
            String pct = pctVal + "%";
            int pw = pctFm.stringWidth(pct);
            int ph = pctFm.getAscent();
            int px = cx - pw / 2;
            int py = cy + 12 - ph;
            labelRects.add(new LabelRect(roomIndex, new Rectangle(px, py, pw, ph + 2)));
            roomIndex++;
        }

        int overlapPairs = 0;
        int maxOverlap = 0;
        for (int i = 0; i < labelRects.size(); i++) {
            LabelRect la = labelRects.get(i);
            for (int j = i + 1; j < labelRects.size(); j++) {
                LabelRect lb = labelRects.get(j);
                if (la.roomIndex == lb.roomIndex) continue;
                Rectangle inter = la.rect.intersection(lb.rect);
                if (inter.isEmpty()) continue;
                overlapPairs++;
                maxOverlap = Math.max(maxOverlap, inter.width * inter.height);
            }
        }

        if (minArea == Integer.MAX_VALUE) minArea = 0;
        return new LabelStats(overlapPairs, maxOverlap, minArea, maxArea);
    }

    private static String symbolFor(ShipRoomLayout.RoomId roomId) {
        return ShipRoomLayout.symbol(roomId);
    }

    private record LabelRect(int roomIndex, Rectangle rect) {}

    private static long crc32(BufferedImage image) {
        CRC32 crc = new CRC32();
        int w = image.getWidth();
        int h = image.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                crc.update((argb >>> 24) & 0xFF);
                crc.update((argb >>> 16) & 0xFF);
                crc.update((argb >>> 8) & 0xFF);
                crc.update(argb & 0xFF);
            }
        }
        return crc.getValue();
    }

    private static long roomStateHash(Player player) {
        long h = 1469598103934665603L;
        List<Ship.RoomStatus> rooms = new ArrayList<>(player.roomStatusSnapshot());
        rooms.sort(Comparator.comparingInt(a -> (a.roomId == null) ? Integer.MAX_VALUE : a.roomId.ordinal()));
        for (Ship.RoomStatus rs : rooms) {
            if (rs == null || rs.roomId == null) continue;
            double frac = (rs.hpMax <= 1e-9) ? 1.0 : (rs.hp / rs.hpMax);
            int hpPct = MathUtil.clamp((int) Math.round(frac * 100.0), 0, 100);
            int firePct = MathUtil.clamp((int) Math.round(rs.fireIntensity * 100.0), 0, 260);
            h ^= (rs.roomId.ordinal() * 131 + hpPct * 17 + firePct);
            h *= 1099511628211L;
        }
        return h;
    }

    private static String buildJson(Args args,
                                    Counters c,
                                    boolean passPanelVisible,
                                    boolean passRealtime,
                                    boolean passLabelLayout,
                                    boolean pass,
                                    List<String> snapshotFiles) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n");
        sb.append("  \"scenario\": \"xray_readability_sustained_combat\",\n");
        sb.append("  \"seed\": ").append(args.seed).append(",\n");
        sb.append("  \"ticks\": ").append(args.ticks).append(",\n");
        sb.append("  \"sampleEveryTicks\": ").append(SAMPLE_EVERY_TICKS).append(",\n");
        sb.append("  \"samples\": ").append(c.samples).append(",\n");
        sb.append("  \"hudSamples\": ").append(c.hudSamples).append(",\n");
        sb.append("  \"panelVisibleSamples\": ").append(c.panelVisibleSamples).append(",\n");
        sb.append("  \"stateChangeSamples\": ").append(c.stateChangeSamples).append(",\n");
        sb.append("  \"visualChangeSamples\": ").append(c.visualChangeSamples).append(",\n");
        sb.append("  \"stateChangedNoVisualSamples\": ").append(c.stateChangedNoVisual).append(",\n");
        sb.append("  \"labelOverlapPairs\": ").append(c.labelOverlapPairs).append(",\n");
        sb.append("  \"maxLabelOverlapArea\": ").append(c.maxLabelOverlapArea).append(",\n");
        sb.append("  \"minRoomArea\": ").append(c.minRoomArea).append(",\n");
        sb.append("  \"maxRoomArea\": ").append(c.maxRoomArea).append(",\n");
        sb.append("  \"passPanelVisible\": ").append(passPanelVisible).append(",\n");
        sb.append("  \"passRealtime\": ").append(passRealtime).append(",\n");
        sb.append("  \"passLabelLayout\": ").append(passLabelLayout).append(",\n");
        sb.append("  \"pass\": ").append(pass).append(",\n");
        sb.append("  \"snapshotFiles\": [\n");
        for (int i = 0; i < snapshotFiles.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append("    ").append(q(snapshotFiles.get(i)));
        }
        sb.append("\n  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String q(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
