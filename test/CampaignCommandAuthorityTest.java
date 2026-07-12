import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampaignCommandAuthorityTest {

    @Test
    void heldBackHullStaysOutWhileReserveHullArrivesLater() throws Exception {
        GameContext ctx = initializedCampaignContext();
        replacePersistentFleet(ctx.campaign,
                ShipRole.FRIGATE,
                ShipRole.CIWS_CORVETTE,
                ShipRole.MINER);
        setName(ctx.campaign, 1, "Commit Frigate");
        setName(ctx.campaign, 2, "Held Back Escort");
        setName(ctx.campaign, 3, "Reserve Miner");

        setCommitment(ctx.campaign, 1, "COMMIT");
        setCommitment(ctx.campaign, 2, "HOLD_BACK");
        setCommitment(ctx.campaign, 3, "RESERVE");

        startSector(ctx, 6);

        assertTrue(hasNamedAllyShip(ctx, "Commit Frigate"));
        assertFalse(hasNamedAllyShip(ctx, "Held Back Escort"));
        assertFalse(hasNamedAllyShip(ctx, "Reserve Miner"));

        CampaignSystem.update(ctx, 24.0);

        assertTrue(hasNamedAllyShip(ctx, "Reserve Miner"), "reserve hull should arrive after tactical contact begins");
        assertFalse(hasNamedAllyShip(ctx, "Held Back Escort"), "held-back hull should remain absent");
    }

    @Test
    void criticalFuelBlocksCourseEngagement() {
        GameContext ctx = initializedCampaignContext();
        ctx.campaign.campaignFuel = 4;
        CampaignSystem.selectCampaignFreeTravelTarget(ctx, 1200.0, 3400.0);

        CampaignSystem.CampaignAction engage = CampaignSystem.campaignVisibleActions(ctx).stream()
                .filter(action -> "ENGAGE_COURSE".equals(action.id))
                .findFirst()
                .orElse(null);

        assertNotNull(engage);
        assertFalse(engage.enabled);
        assertTrue(engage.disabledReason.toLowerCase().contains("fuel"));
    }

    private static GameContext initializedCampaignContext() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 5000, 5000, true, 1234L, false));
        ctx.campaignUnlockProfile = null;
        SpawnSystem.initWorld(ctx);
        return ctx;
    }

    private static void replacePersistentFleet(CampaignSystem.CampaignState st, ShipRole... roles) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("persistentBlueFleet");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) field.get(st);
        entries.clear();
        int slotId = 1;
        for (ShipRole role : roles) {
            entries.add(newPersistentEntry(slotId++, role, role.name().replace('_', ' ')));
        }
    }

    private static Object newPersistentEntry(int slotId, ShipRole role, String name) throws Exception {
        Class<?> entryClass = Class.forName("CampaignSystemModels$PersistentFleetEntry");
        Constructor<?> ctor = entryClass.getDeclaredConstructor(int.class, ShipRole.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(slotId, role, name);
    }

    private static void setCommitment(CampaignSystem.CampaignState st, int slotId, String commitment) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("persistentBlueFleet");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) field.get(st);
        for (Object entry : entries) {
            Field slot = entry.getClass().getDeclaredField("slotId");
            slot.setAccessible(true);
            if (slot.getInt(entry) != slotId) continue;
            Field commit = entry.getClass().getDeclaredField("tacticalCommitmentId");
            commit.setAccessible(true);
            commit.set(entry, commitment);
            return;
        }
        fail("missing persistent fleet slot " + slotId);
    }

    private static void setName(CampaignSystem.CampaignState st, int slotId, String name) throws Exception {
        Field field = CampaignSystem.CampaignState.class.getDeclaredField("persistentBlueFleet");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) field.get(st);
        for (Object entry : entries) {
            Field slot = entry.getClass().getDeclaredField("slotId");
            slot.setAccessible(true);
            if (slot.getInt(entry) != slotId) continue;
            Field entryName = entry.getClass().getDeclaredField("name");
            entryName.setAccessible(true);
            entryName.set(entry, name);
            return;
        }
        fail("missing persistent fleet slot " + slotId);
    }

    private static void startSector(GameContext ctx, int sector) throws Exception {
        Method method = CampaignSystem.class.getDeclaredMethod("startSector", GameContext.class, int.class);
        method.setAccessible(true);
        method.invoke(null, ctx, sector);
    }

    private static boolean hasNamedAllyShip(GameContext ctx, String name) {
        return ctx.ships.stream().anyMatch(ship ->
                ship != null
                        && name.equals(ship.name)
                        && ship.faction != null
                        && ship.faction.teamId() == Faction.ALLY.teamId()
                        && ship.alive
                        && !ship.dying
                        && ship.hp > 0);
    }
}
