import app.config.GameConfig;
import app.config.GameMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AISystemSmallCraftRangeTest {

    @Test
    void campaignLocalAlliesDoNotJoinPlayerFormationGroup() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 4200, 2600, true, 140L, false));
        ctx.campaign = new CampaignSystem.CampaignState();
        ctx.campaign.enabled = true;
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1200.0, 1300.0);
        ctx.player.faction = Faction.ALLY;

        FleetShip playerEscort = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 1020.0, 1450.0);
        playerEscort.minerHomeBase = ctx.player;
        FleetShip localLeader = new FleetShip(ShipRole.BATTLECRUISER, Faction.ALLY, 2100.0, 1300.0);
        FleetShip localEscort = new FleetShip(ShipRole.PICKET, Faction.ALLY, 2180.0, 1400.0);
        FleetShip coalitionEscort = new FleetShip(ShipRole.FRIGATE, Faction.TEAM_C, 1120.0, 1180.0);
        coalitionEscort.minerHomeBase = ctx.player;

        ctx.ships.clear();
        ctx.ships.add(ctx.player);
        ctx.ships.add(playerEscort);
        ctx.ships.add(localLeader);
        ctx.ships.add(localEscort);
        ctx.ships.add(coalitionEscort);
        ctx.entityQuery.rebuild(ctx);

        Method buildFleetState = AISystem.class.getDeclaredMethod("buildFleetState", GameContext.class, double.class);
        buildFleetState.setAccessible(true);
        Object fleetState = buildFleetState.invoke(null, ctx, 1.0 / 60.0);
        Field membersField = fleetState.getClass().getDeclaredField("members");
        membersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, List<Ship>> members = (Map<Integer, List<Ship>>) membersField.get(fleetState);

        List<Ship> playerGroup = members.get(Faction.ALLY.teamId());
        assertTrue(playerGroup != null && playerGroup.contains(ctx.player));
        assertTrue(playerGroup.contains(playerEscort), "player-owned persistent escorts should stay in the Mothership formation");
        assertFalse(playerGroup.contains(localLeader), "local friendly fleets should keep a separate formation anchor");
        assertFalse(playerGroup.contains(localEscort), "local friendly escorts should not consume player formation slots");
        assertFalse(playerGroup.contains(coalitionEscort), "coalition-faction hulls should not become player escorts just because they are friendly");
        assertNull(coalitionEscort.minerHomeBase, "stale coalition anchors should be stripped as soon as campaign fleet grouping sees them");
        assertTrue(members.values().stream().anyMatch(group -> group.contains(coalitionEscort)),
                "coalition-faction hulls should keep their own local command group");
    }

    @Test
    void campaignCoalitionCiviliansDetachFromPlayerButRosterShipsStay() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CAMPAIGN_OPS, 4200, 2600, true, 141L, false));
        ctx.campaign = new CampaignSystem.CampaignState();
        ctx.campaign.enabled = true;
        ctx.player = new Player(ShipRole.MOTHERSHIP, 1200.0, 1300.0);
        ctx.player.faction = Faction.ALLY;

        FleetShip greenMiner = new FleetShip(ShipRole.MINER, Faction.TEAM_C, 1120.0, 1180.0);
        greenMiner.minerHomeBase = ctx.player;
        greenMiner.escortAnchorId = ctx.player.id;
        FleetShip yellowHauler = new FleetShip(ShipRole.HAULER, Faction.TEAM_D, 1160.0, 1210.0);
        yellowHauler.minerHomeBase = ctx.player;
        yellowHauler.escortAnchorId = ctx.player.id;
        FleetShip blueRoster = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 1020.0, 1450.0);
        blueRoster.minerHomeBase = ctx.player;
        blueRoster.escortAnchorId = ctx.player.id;
        ctx.campaign.shipCampaignProvenance.put(blueRoster.id, CampaignShipProvenance.PLAYER_ROSTER);

        ctx.ships.clear();
        ctx.ships.add(ctx.player);
        ctx.ships.add(greenMiner);
        ctx.ships.add(yellowHauler);
        ctx.ships.add(blueRoster);

        CampaignSystem.sanitizeCoalitionCivilianPlayerAnchors(ctx, ctx.campaign);

        assertNull(greenMiner.minerHomeBase, "Green civilian traffic should not follow the player indefinitely");
        assertEquals(-1, greenMiner.escortAnchorId);
        assertNull(yellowHauler.minerHomeBase, "Yellow haulers should return to their own faction flow instead of becoming ghost escorts");
        assertEquals(-1, yellowHauler.escortAnchorId);
        assertEquals(ctx.player, blueRoster.minerHomeBase, "persistent Blue roster ships should keep their formation anchor");
        assertEquals(ctx.player.id, blueRoster.escortAnchorId);
    }

    @Test
    void ciwsDogfightRangeUsesPracticalPelletReach() {
        FleetShip fighter = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 0.0, 0.0);
        FleetShip hostile = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 280.0, 0.0);
        Turret gun = fighter.turrets.getFirst();

        double baseGunRange = 720.0 * 0.82;
        double allowed = AISystem.effectiveGunRangeForTarget(fighter, gun, hostile, baseGunRange);

        assertTrue(allowed < 300.0, "fighter anti-fighter fire should be clamped well inside generic gun range");
        assertTrue(allowed < baseGunRange, "CIWS-style dogfight fire should not use the coarse shared gun range");
    }

    @Test
    void standardGunRangeRemainsUnchangedAgainstLargerTargets() {
        FleetShip fighter = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 0.0, 0.0);
        FleetShip frigate = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 500.0, 0.0);
        Turret gun = fighter.turrets.getFirst();

        double baseGunRange = 720.0 * 0.82;
        double allowed = AISystem.effectiveGunRangeForTarget(fighter, gun, frigate, baseGunRange);

        assertEquals(baseGunRange, allowed, 0.001, "non-dogfight gun engagements should keep their normal range gate");
    }

    @Test
    void sustainedEngagementRangeUsesSensorContactForCapitals() {
        FleetShip mothership = new FleetShip(ShipRole.MOTHERSHIP, Faction.ALLY, 0.0, 0.0);
        FleetShip enemyCruiser = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 1000.0, 0.0);

        double sustainedRange = AISystem.sustainedEngagementRangeForTarget(null, mothership, enemyCruiser);
        double baseGunRange = 720.0;
        double practicalGunRange = AISystem.effectivePrimaryGunRangeAgainstTarget(mothership, enemyCruiser, baseGunRange);

        assertTrue(TargetingSystem.isDetectableToObserver(mothership, enemyCruiser),
                "the hostile should be inside the mothership's sensor envelope");
        assertTrue(sustainedRange >= 1000.0,
                "sensor contact should keep the target in the sustained engagement envelope");
        assertTrue(sustainedRange > practicalGunRange,
                "the sustained engagement envelope should extend beyond the coarse gun range gate");
    }

    @Test
    void blueEscortGunAuthorityUsesMothershipSensorContact() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 3200, 1800, true, 47L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 0.0, 900.0);
        ctx.player.faction = Faction.ALLY;
        FleetShip escort = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 900.0);
        FleetShip enemyCruiser = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 1700.0, 900.0);
        Turret gun = escort.turrets.getFirst();

        double oldCoarseGunRange = 460.0 * 1.04;
        double authorized = AISystem.gunFireAuthorityRange(ctx, escort, gun, enemyCruiser, oldCoarseGunRange);

        assertTrue(oldCoarseGunRange < 600.0, "test setup should stay outside the old escort gun gate");
        assertTrue(TargetingSystem.isDetectableToObserver(ctx, ctx.player, enemyCruiser),
                "the mothership should have the target on sensors");
        assertTrue(authorized >= 1700.0,
                "blue escorts should inherit mothership sensor fire authority instead of waiting for point-blank range");
    }

    @Test
    void playerFleetShipsProsecuteKnownHostilesAtTwentyFiveHundredUnits() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 6200, 2600, true, 51L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 0.0, 1300.0);
        ctx.player.faction = Faction.ALLY;
        FleetShip escort = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 1300.0);
        FleetShip enemyCruiser = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 2500.0, 1300.0);
        Turret gun = escort.turrets.getFirst();

        double sustained = AISystem.sustainedEngagementRangeForTarget(ctx, escort, enemyCruiser);
        double authorized = AISystem.gunFireAuthorityRange(ctx, escort, gun, enemyCruiser, 460.0);

        assertTrue(sustained >= 2500.0,
                "player-fleet ships should prosecute known hostiles out to 2,500 units");
        assertTrue(authorized >= 2500.0,
                "gun fire authority should match the player-fleet prosecution envelope");
    }

    @Test
    void redShipsProsecuteThreatsAtTwentyFiveHundredUnits() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 6200, 2600, true, 52L, false));
        FleetShip redCruiser = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 0.0, 1300.0);
        FleetShip blueFrigate = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 2500.0, 1300.0);
        Turret gun = redCruiser.turrets.getFirst();

        double sustained = AISystem.sustainedEngagementRangeForTarget(ctx, redCruiser, blueFrigate);
        double authorized = AISystem.gunFireAuthorityRange(ctx, redCruiser, gun, blueFrigate, 460.0);

        assertTrue(sustained >= 2500.0,
                "Red ships should prosecute hostile contacts out to 2,500 units");
        assertTrue(authorized >= 2500.0,
                "Red kinetic fire authority should open at the prosecution envelope instead of waiting near 1,000 units");
    }

    @Test
    void redHyperweaponStartsChargingImmediatelyAtLineTarget() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 3600, 1800, true, 53L, false));
        FleetShip redHyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.ENEMY, 0.0, 900.0);
        FleetShip blueCruiser = new FleetShip(ShipRole.CRUISER, Faction.ALLY, 900.0, 900.0);
        ctx.ships.clear();
        ctx.ships.add(redHyperweapon);
        ctx.ships.add(blueCruiser);
        ctx.entityQuery.rebuild(ctx);

        assertTrue(redHyperweapon.superweaponChargeTime > 0.0, "test expects a charge-up hyperweapon");

        invokeFireIfAble(ctx, redHyperweapon, blueCruiser, GameContext.DT, 900.0);

        assertTrue(redHyperweapon.isSuperweaponCharging(),
                "red hyperweapon AI should start charging as soon as it sees a line-or-larger target");
    }

    @Test
    void greenDirectBeamSuperweaponStartsAtUniversalRange() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 5000, 2200, true, 55L, false));
        FleetShip greenHyperweapon = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.TEAM_C, 0.0, 1100.0);
        FleetShip blueCruiser = new FleetShip(ShipRole.CRUISER, Faction.ALLY, 1950.0, 1100.0);
        ctx.ships.clear();
        ctx.ships.add(greenHyperweapon);
        ctx.ships.add(blueCruiser);
        ctx.entityQuery.rebuild(ctx);

        invokeFireIfAble(ctx, greenHyperweapon, blueCruiser, GameContext.DT, 1950.0);

        assertTrue(greenHyperweapon.isSuperweaponCharging() || ctx.projectiles.stream()
                        .anyMatch(projectile -> projectile.sourceShipId == greenHyperweapon.id),
                "direct-beam hyperweapons should begin prosecution at the shared 2,000m special-weapon range");
    }

    @Test
    void missileLaunchersFireHalfRateButDoubleDamage() throws Exception {
        FleetShip shooter = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 0.0, 0.0);
        FleetShip target = new FleetShip(ShipRole.CRUISER, Faction.ALLY, 400.0, 0.0);
        Turret launcher = new Turret(Turret.Kind.MISSILE, 0.0, 0.0);
        launcher.cooldown = 2.0;
        launcher.damage = 5;
        launcher.missileRole = Turret.MissileRole.ANTI_MEDIUM;
        launcher.setReady();
        double baselineCycle = shooter.weaponCycleRateMultiplier() * shooter.missileCycleRateMultiplier();
        double oldReload = launcher.cooldown / Math.max(0.20, baselineCycle);
        double expectedReload = Math.max(launcher.cooldown / Math.max(0.20,
                baselineCycle * Turret.GLOBAL_MISSILE_LAUNCH_RATE_MULT), Ship.MISSILE_MIN_RELOAD_SECONDS);

        Projectile shot = launcher.fire(shooter, target, GameContext.DT);
        Missile missile = assertInstanceOf(Missile.class, shot);

        Field coolLeftField = Turret.class.getDeclaredField("coolLeft");
        coolLeftField.setAccessible(true);
        double actualReload = coolLeftField.getDouble(launcher);
        assertEquals(expectedReload, actualReload, 1e-6,
                "global missile cadence should halve launcher firing rate");
        assertTrue(actualReload >= oldReload * 1.95,
                "missile reload should be roughly doubled after existing ship modifiers are applied");
        assertTrue(missile.damage >= 15,
                "global missile damage should be doubled before the existing missile damage doctrine is applied");
    }

    @Test
    void antiLightMissilesProxyFuseNearSmallCraft() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 2200, 1600, true, 56L, false));
        FleetShip fighter = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 900.0, 800.0);
        Missile missile = new Missile(0.0, 0.0, 0.0, fighter, GameContext.DT,
                260.0, Math.toRadians(300.0), 12, 80, 5.0, Faction.ENEMY);
        missile.role = Turret.MissileRole.ANTI_LIGHT;
        double directImpactDistance = missile.radius + HullGeometry.broadPhaseRadius(fighter);
        missile.x = fighter.x + directImpactDistance + 10.0;
        missile.y = fighter.y;

        int hpBefore = fighter.hp;
        double shieldBefore = fighter.shield;
        ctx.ships.clear();
        ctx.ships.add(fighter);
        ctx.projectiles.clear();
        ctx.projectiles.add(missile);
        ctx.entityQuery.rebuild(ctx);

        CollisionSystem.handleProjectilesVsShips(ctx, ctx.projectiles, ctx.ships);

        assertFalse(missile.alive, "anti-light missiles should detonate close to small craft instead of requiring a pixel-perfect hit");
    }

    @Test
    void superweaponDoctrineSelectsLargestLineOrLargerTargetAcrossFactions() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 4200, 2200, true, 54L, false));
        FleetShip shooter = new FleetShip(ShipRole.HYPERWEAPON_TITAN, Faction.TEAM_D, 0.0, 1100.0);
        FleetShip smallCraft = new FleetShip(ShipRole.BOMBER, Faction.ALLY, 360.0, 1100.0);
        FleetShip lineShip = new FleetShip(ShipRole.CRUISER, Faction.ALLY, 720.0, 1100.0);
        FleetShip capital = new FleetShip(ShipRole.DREADNOUGHT, Faction.ALLY, 980.0, 1100.0);
        ctx.ships.clear();
        ctx.ships.add(shooter);
        ctx.ships.add(smallCraft);
        ctx.ships.add(lineShip);
        ctx.ships.add(capital);
        ctx.entityQuery.rebuild(ctx);

        Ship selected = invokeSelectLargestSuperweaponTarget(ctx, shooter, 2200.0);

        assertNotNull(selected);
        assertEquals(capital.id, selected.id,
                "superweapon doctrine should ignore small craft and pick the largest line-or-larger target");
    }

    @Test
    void blueEscortFiresBeyondOldGunGateWhenMothershipHasContact() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 3200, 1800, true, 48L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 0.0, 900.0);
        ctx.player.faction = Faction.ALLY;
        FleetShip escort = new FleetShip(ShipRole.FRIGATE, Faction.ALLY, 0.0, 900.0);
        FleetShip enemyCruiser = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 1700.0, 900.0);
        ctx.ships.clear();
        ctx.ships.add(ctx.player);
        ctx.ships.add(escort);
        ctx.ships.add(enemyCruiser);
        ctx.entityQuery.rebuild(ctx);

        int shots = invokeFireIfAble(ctx, escort, enemyCruiser, GameContext.DT, 1700.0);
        assertTrue(shots > 0);
        assertTrue(ctx.projectiles.stream().anyMatch(projectile -> projectile.sourceShipId == escort.id),
                "blue escort should engage using mothership-grade contact authority");
    }

    @Test
    void missileShipsOpenAtStandoffBeforeUsingSensorAuthorizedGuns() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 3600, 1800, true, 49L, false));
        ctx.player = new Player(ShipRole.MOTHERSHIP, 0.0, 900.0);
        ctx.player.faction = Faction.ALLY;
        FleetShip missileBoat = new FleetShip(ShipRole.MISSILE_BOAT, Faction.ALLY, 0.0, 900.0);
        FleetShip enemyCruiser = new FleetShip(ShipRole.CRUISER, Faction.ENEMY, 1700.0, 900.0);
        ctx.ships.clear();
        ctx.ships.add(ctx.player);
        ctx.ships.add(missileBoat);
        ctx.ships.add(enemyCruiser);
        ctx.entityQuery.rebuild(ctx);

        invokeCommitToTarget(missileBoat, enemyCruiser, 5.0);
        assertTrue(missileBoat.aiMissileStandoffTimer > 0.0);

        int openingShots = invokeFireIfAble(ctx, missileBoat, enemyCruiser, GameContext.DT, 1700.0);
        assertTrue(openingShots > 0);
        assertTrue(ctx.projectiles.stream()
                        .filter(projectile -> projectile.sourceShipId == missileBoat.id)
                        .allMatch(projectile -> projectile instanceof Missile),
                "opening standoff should spend missile racks without starting long-range gunfire");

        ctx.projectiles.clear();
        missileBoat.aiMissileStandoffTimer = 0.0;
        missileBoat.aiMissileStandoffTargetId = -1;
        for (Turret turret : missileBoat.turrets) turret.setReady();

        int followUpShots = invokeFireIfAble(ctx, missileBoat, enemyCruiser, GameContext.DT, 1700.0);
        assertTrue(followUpShots > 0);
        assertTrue(ctx.projectiles.stream()
                        .filter(projectile -> projectile.sourceShipId == missileBoat.id)
                        .anyMatch(projectile -> !(projectile instanceof Missile)),
                "after the opening standoff expires, sensor-authorized guns should join the engagement");
    }

    @Test
    void fighterOnlyTeamsDoNotFormUpAroundAFighterFlagship() throws Exception {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 2400, 1800, true, 44L, false));
        FleetShip friendlyOne = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 400.0, 900.0);
        FleetShip friendlyTwo = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 430.0, 940.0);
        FleetShip hostile = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 610.0, 905.0);
        ctx.ships.clear();
        ctx.ships.add(friendlyOne);
        ctx.ships.add(friendlyTwo);
        ctx.ships.add(hostile);
        ctx.entityQuery.rebuild(ctx);

        AISystem.update(ctx, 1.0 / 60.0);

        assertFalse(ctx.command.fleetCommandShips.containsKey(Faction.ALLY),
                "fighter-only teams should not elect a fighter as fleet command anchor");
        assertTrue(friendlyOne.aiCommittedTargetId == hostile.id || friendlyTwo.aiCommittedTargetId == hostile.id,
                "at least one friendly fighter should commit to the hostile instead of circling a friendly fighter");
        assertTrue(Math.hypot(friendlyOne.vx, friendlyOne.vy) > 0.0001
                        || Math.hypot(friendlyTwo.vx, friendlyTwo.vy) > 0.0001,
                "friendly fighters should keep moving without a friendly capital ship");
    }

    @Test
    void fighterCommitsToHostileSmallCraftBeforeLargerTargets() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 2600, 1800, true, 45L, false));
        FleetShip fighter = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 500.0, 900.0);
        FleetShip bomber = new FleetShip(ShipRole.BOMBER, Faction.ENEMY, 760.0, 900.0);
        FleetShip frigate = new FleetShip(ShipRole.FRIGATE, Faction.ENEMY, 650.0, 900.0);
        ctx.ships.clear();
        ctx.ships.add(fighter);
        ctx.ships.add(bomber);
        ctx.ships.add(frigate);
        ctx.entityQuery.rebuild(ctx);

        AISystem.update(ctx, 1.0 / 60.0);

        assertEquals(bomber.id, fighter.aiCommittedTargetId,
                "fighters should aggressively intercept hostile small craft before heavier hulls");
    }

    @Test
    void opposingFightersFireOrRepositionInsteadOfOrbitingIndefinitely() {
        GameContext ctx = new GameContext(new GameConfig(GameMode.CUSTOM_BATTLES, 2600, 1800, true, 46L, false));
        FleetShip blue = new FleetShip(ShipRole.FIGHTER, Faction.ALLY, 900.0, 900.0);
        FleetShip red = new FleetShip(ShipRole.FIGHTER, Faction.ENEMY, 1180.0, 900.0);
        ctx.ships.clear();
        ctx.ships.add(blue);
        ctx.ships.add(red);
        ctx.entityQuery.rebuild(ctx);

        double initialDistance = Math.hypot(blue.x - red.x, blue.y - red.y);
        int blueHp = blue.hp;
        int redHp = red.hp;
        double blueShield = blue.shield;
        double redShield = red.shield;

        for (int i = 0; i < 8 * 60; i++) {
            AISystem.update(ctx, 1.0 / 60.0);
            for (Ship ship : ctx.ships) ship.update(1.0 / 60.0);
            ctx.entityQuery.rebuild(ctx);
        }

        double finalDistance = Math.hypot(blue.x - red.x, blue.y - red.y);
        boolean damageExchanged = blue.hp < blueHp || red.hp < redHp || blue.shield < blueShield || red.shield < redShield;
        boolean deliberateReposition = Math.abs(finalDistance - initialDistance) >= 48.0
                && (blue.aiCommittedTargetId == red.id || red.aiCommittedTargetId == blue.id);
        assertTrue(damageExchanged || deliberateReposition,
                "opposing fighters must fire, disengage, or deliberately reposition instead of orbiting indefinitely");
    }

    private static void invokeCommitToTarget(Ship seeker, Ship target, double duration) throws Exception {
        Method method = AISystem.class.getDeclaredMethod("commitToTarget", Ship.class, Ship.class, double.class);
        method.setAccessible(true);
        method.invoke(null, seeker, target, duration);
    }

    private static int invokeFireIfAble(GameContext ctx, Ship shooter, Ship target, double dt, double dist) throws Exception {
        Method method = AISystem.class.getDeclaredMethod(
                "fireIfAble", GameContext.class, Ship.class, Ship.class, double.class, double.class);
        method.setAccessible(true);
        Object result = method.invoke(null, ctx, shooter, target, dt, dist);
        assertNotNull(result);
        return (int) result;
    }

    private static Ship invokeSelectLargestSuperweaponTarget(GameContext ctx, Ship shooter, double range) throws Exception {
        Method method = AISystem.class.getDeclaredMethod(
                "selectLargestSuperweaponTarget", GameContext.class, Ship.class, double.class);
        method.setAccessible(true);
        Object result = method.invoke(null, ctx, shooter, range);
        return (Ship) result;
    }
}
