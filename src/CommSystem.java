import java.util.List;
import java.util.Locale;

public final class CommSystem {
    private static final double HAIL_CURSOR_RADIUS = 260.0;
    private static final double SUPPORT_ORDER_SECONDS = 20.0;
    private static final double WARN_OFF_ORDER_SECONDS = 14.0;
    private static final double TRADE_COOLDOWN_SECONDS = 12.0;
    private static final double SURRENDER_COOLDOWN_SECONDS = 16.0;
    private static final int TRADE_ORE_BATCH = 40;
    private static final double TRADE_PRICE_BONUS = 1.25;

    private CommSystem() {}

    public static void cycleIntent(GameContext ctx, int dir) {
        if (ctx == null || ctx.ui == null) return;
        UiState.CommIntent current = currentIntent(ctx);
        ctx.ui.commIntent = current.step(dir);
        EventSystem.showBanner(ctx, "COMM INTENT: " + ctx.ui.commIntent.label().toUpperCase(Locale.US), 0.9);
    }

    public static String currentIntentLabel(GameContext ctx) {
        return currentIntent(ctx).label();
    }

    public static void tryHailCurrentContact(GameContext ctx) {
        if (ctx == null || ctx.player == null || ctx.ships == null) return;
        Ship target = resolveHailTarget(ctx);
        if (!isHailable(ctx, target)) {
            EventSystem.showBanner(ctx, "NO CONTACT AVAILABLE TO HAIL", 1.1);
            return;
        }

        UiState.CommIntent intent = currentIntent(ctx);
        CommOutcome outcome = responseFor(ctx, target, intent);
        String speaker = speakerFor(target);
        postHailMessage(ctx, target.faction, speaker, outcome.response(), 8.0);
        if (ctx.ui != null && ctx.ui.voiceCaptionsEnabled) {
            ctx.ui.voiceCaption = speaker + ": " + outcome.response();
            ctx.ui.voiceCaptionT = 2.8;
        }
        String banner = outcome.banner();
        if (banner == null || banner.isBlank()) {
            banner = "HAIL " + intent.label().toUpperCase(Locale.US) + ": " + speaker.toUpperCase(Locale.US);
        }
        EventSystem.showBanner(ctx, banner, 0.9);
    }

    private static UiState.CommIntent currentIntent(GameContext ctx) {
        if (ctx == null || ctx.ui == null || ctx.ui.commIntent == null) {
            return UiState.CommIntent.IDENTIFY;
        }
        return ctx.ui.commIntent;
    }

    private static Ship resolveHailTarget(GameContext ctx) {
        if (ctx == null) return null;
        Ship target = nearestVisibleShipToCursor(ctx, HAIL_CURSOR_RADIUS);
        if (isHailable(ctx, target)) return target;
        if (isHailable(ctx, ctx.lockedTarget)) return ctx.lockedTarget;
        return null;
    }

    private static Ship nearestVisibleShipToCursor(GameContext ctx, double radius) {
        if (ctx == null || ctx.player == null || ctx.ships == null || radius <= 0.0) return null;
        Ship best = null;
        double bestD2 = radius * radius;
        for (Ship ship : ctx.ships) {
            if (!isHailable(ctx, ship)) continue;
            double d2 = GameMath.dist2(ctx.cursorWorldX, ctx.cursorWorldY, ship.x, ship.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = ship;
            }
        }
        return best;
    }

    private static boolean isHailable(GameContext ctx, Ship ship) {
        if (ctx == null || ctx.player == null || ship == null) return false;
        if (ship == ctx.player) return false;
        if (!ship.alive || ship.dying || ship.hp <= 0) return false;
        if (ship.faction == null) return false;
        return TargetingSystem.isDetectableToObserver(ctx.player, ship);
    }

    private static CommOutcome responseFor(GameContext ctx, Ship target, UiState.CommIntent intent) {
        if (ctx == null || target == null) return new CommOutcome("Signal degraded. Repeat.", null);
        Faction playerFaction = (ctx.player == null) ? null : ctx.player.faction;
        boolean friendly = playerFaction != null && target.faction != null && target.faction.isFriendlyTo(playerFaction);
        boolean sameTeam = playerFaction != null && target.faction != null && target.faction.teamId() == playerFaction.teamId();
        double hullFrac = (target.hpMax <= 0) ? 1.0 : (target.hp / (double) target.hpMax);

        if (friendly && sameTeam) {
            return friendlyResponse(ctx, target, hullFrac, intent);
        }
        if (friendly) {
            return alliedResponse(ctx, target, hullFrac, intent);
        }
        if (target.faction == Faction.ENEMY) {
            return hostileResponse(ctx, target, hullFrac, intent);
        }
        return neutralResponse(ctx, target, hullFrac, intent);
    }

    private static CommOutcome friendlyResponse(GameContext ctx, Ship target, double hullFrac, UiState.CommIntent intent) {
        return switch (intent) {
            case IDENTIFY -> outcome(identifyFriendlyResponse(target, hullFrac));
            case STATE_INTENT -> stateIntentOutcome(ctx, target,
                    "Blue command traffic. We are holding this pocket with you and tracking nearby contacts.");
            case REQUEST_SUPPORT -> {
                if (hullFrac < 0.35) yield outcome("We copy your support request, but our hull is in bad shape. We need cover before we can push.");
                applySupportOrder(ctx, target);
                if (target.role != null && (target.role.isCarrierHull() || target.role.isCapitalCombatant())) {
                    yield outcome("Support request received. We can lean into the lane and pressure anything you flush out.",
                            "SUPPORT ACKNOWLEDGED");
                }
                yield outcome("Support request received. We will tighten formation and screen this sector with you.",
                        "ESCORT MOVING TO SUPPORT");
            }
            case REQUEST_TRADE -> friendlyTradeOutcome(ctx, target);
            case WARN_OFF -> {
                applyWarnOffOrder(ctx, target);
                yield outcome("Blue command copies. We will keep our spacing and avoid crossing your firing lane.",
                        "FRIENDLY CONTACT PEELING OFF");
            }
            case DEMAND_SURRENDER -> outcome("Negative. Friendly channel recognized, but we do not answer surrender demands from our own side.");
        };
    }

    private static CommOutcome alliedResponse(GameContext ctx, Ship target, double hullFrac, UiState.CommIntent intent) {
        return switch (intent) {
            case IDENTIFY -> outcome(identifyAlliedResponse(target, hullFrac));
            case STATE_INTENT -> alliedStateIntentOutcome(ctx, target);
            case REQUEST_SUPPORT -> {
                if (hullFrac < 0.45) yield outcome("We hear you, but we are already bleeding. We can screen lightly, not spearhead.");
                applySupportOrder(ctx, target);
                if (target.faction == Faction.TEAM_D) {
                    yield outcome("Yellow flight copies. We can scout ahead and draw the heavier reds onto a bad angle.",
                            "YELLOW SCOUTS MOVING TO SUPPORT");
                }
                if (target.faction == Faction.TEAM_C) {
                    yield outcome("Green channel copies. We can reinforce if the lane stays profitable enough to survive.",
                            "GREEN SUPPORT VECTORING IN");
                }
                yield outcome("Friendly contact copies. We can lend support if the pressure stays manageable.",
                        "SUPPORT ACKNOWLEDGED");
            }
            case REQUEST_TRADE -> alliedTradeOutcome(ctx, target);
            case WARN_OFF -> {
                applyWarnOffOrder(ctx, target);
                if (target.faction == Faction.TEAM_D) {
                    yield outcome("Yellow copies. We will stay loose and leave your engagement lane open.",
                            "YELLOW FLIGHT BREAKING OFF");
                }
                yield outcome("Acknowledged. We will keep clear of your immediate line of fire.",
                        "ALLIED CONTACT PEELING OFF");
            }
            case DEMAND_SURRENDER -> outcome("Friendly alliance channel open. We are not surrendering to an active partner.");
        };
    }

    private static CommOutcome hostileResponse(GameContext ctx, Ship target, double hullFrac, UiState.CommIntent intent) {
        double playerDist = (ctx.player == null) ? Double.POSITIVE_INFINITY : Math.hypot(target.x - ctx.player.x, target.y - ctx.player.y);
        return switch (intent) {
            case IDENTIFY -> outcome(identifyHostileResponse(target, hullFrac, playerDist));
            case STATE_INTENT -> hostileStateIntentOutcome(ctx, target);
            case REQUEST_SUPPORT -> outcome("Your request is noted and refused. Try calling someone who does not want your flagship broken apart.");
            case REQUEST_TRADE -> {
                if (target.role == ShipRole.TRANSPORT || target.role == ShipRole.HAULER || target.role == ShipRole.MINER) {
                    yield outcome("Red logistics channel denies trade while under hostile contact.");
                }
                yield outcome("No trade. Only terms of engagement.");
            }
            case WARN_OFF -> hostileWarnOutcome(target, hullFrac);
            case DEMAND_SURRENDER -> surrenderOutcome(ctx, target, hullFrac);
        };
    }

    private static CommOutcome neutralResponse(GameContext ctx, Ship target, double hullFrac, UiState.CommIntent intent) {
        return switch (intent) {
            case IDENTIFY -> outcome(identifyNeutralResponse(target, hullFrac));
            case STATE_INTENT -> stateIntentOutcome(ctx, target,
                    isTradeCapable(target)
                            ? "Local civilian channel. We are trying to stay solvent and stay out of military crossfire."
                            : "Unknown local contact. We are passing through and would prefer not to get entangled.");
            case REQUEST_SUPPORT -> {
                if (target.role == ShipRole.TRANSPORT || target.role == ShipRole.HAULER || target.role == ShipRole.MINER) {
                    yield outcome("Negative. Civilian hull. We are not equipped to enter a fleet action.");
                }
                yield outcome("Support request denied. This hull is not signing onto your fight.");
            }
            case REQUEST_TRADE -> neutralTradeOutcome(ctx, target);
            case WARN_OFF -> {
                applyWarnOffOrder(ctx, target);
                if (hullFrac < 0.55) yield outcome("Understood. We are damaged already and happy to clear the lane.",
                        "NEUTRAL CONTACT WITHDRAWING");
                yield outcome("Acknowledged. We will keep distance and drift clear of the fighting.",
                        "NEUTRAL CONTACT CLEARING LANE");
            }
            case DEMAND_SURRENDER -> outcome("Civilian or neutral traffic rejects surrender demand. We are not your combat prize.");
        };
    }

    private static CommOutcome friendlyTradeOutcome(GameContext ctx, Ship target) {
        if (!isTradeCapable(target)) {
            return outcome("We are fleet-local, not a merchant hull. No trade ledger available on this channel.");
        }
        return executeTrade(ctx, target,
                "Trade channel is open. Bring salvage or ore when the shooting eases.",
                "Trade complete. Clearing cargo and crediting your account now.");
    }

    private static CommOutcome alliedTradeOutcome(GameContext ctx, Ship target) {
        if (isTradeCapable(target) || target.faction == Faction.TEAM_C) {
            return executeTrade(ctx, target,
                    "Trade window available. Keep your weapons cool and we can talk terms.",
                    "Terms accepted. We are moving credits over and keeping the lane clear.");
        }
        return outcome("No trade inventory on this hull. We are a combat contact, not a broker.");
    }

    private static CommOutcome neutralTradeOutcome(GameContext ctx, Ship target) {
        if (!isTradeCapable(target)) {
            return outcome("No cargo exchange available on this signal.");
        }
        return executeTrade(ctx, target,
                "Trade channel open. Keep your escorts steady and we can do business.",
                "Exchange complete. Stay clear of any incoming firing lane.");
    }

    private static CommOutcome hostileWarnOutcome(Ship target, double hullFrac) {
        if (target == null) return outcome("We are not yielding the lane. Move or burn.");
        if ((target.role == ShipRole.PATROL || target.role == ShipRole.PICKET || target.isSmallCraft()) && hullFrac < 0.42) {
            target.aiCommittedTargetId = -1;
            target.aiTargetCommitTimer = 0.0;
            return outcome("Warning received. We are breaking contact for now, but not leaving the sector to you.",
                    "HOSTILE SCOUT BREAKING CONTACT");
        }
        if (target.role == ShipRole.PATROL || target.role == ShipRole.PICKET || target.isSmallCraft()) {
            return outcome("Warning rejected. We can shadow you all day if our drive holds.");
        }
        return outcome("We are not yielding the lane. Move or burn.");
    }

    private static CommOutcome alliedStateIntentOutcome(GameContext ctx, Ship target) {
        String base = "Friendly contact acknowledges. We are staying mobile inside this sector.";
        if (target == null) return outcome(base);
        if (target.faction == Faction.TEAM_C) {
            base = "Green traffic here. We are protecting our margin and keeping one eye on red patrol routes.";
        } else if (target.faction == Faction.TEAM_D) {
            base = "Yellow flight here. We are probing weak points and trying not to get pinned by heavier red hulls.";
        }
        return stateIntentOutcome(ctx, target, base);
    }

    private static CommOutcome hostileStateIntentOutcome(GameContext ctx, Ship target) {
        if (ctx == null || target == null) return outcome("Red channel received. Our intent is to pin you until the strike group closes.");
        if (target.role == ShipRole.PATROL || target.role == ShipRole.PICKET || target.isSmallCraft()) {
            Ship support = nearestHostileToTarget(ctx, target, 2400.0);
            if (isAliveHostileTo(target, support)) {
                return outcome("Red scout screen. We are painting your trail and feeding heavier hulls a cleaner vector. Command hull "
                                + speakerFor(support) + " is angling in behind us.",
                        "HOSTILE INTENT DECLARED");
            }
            return outcome("Red scout screen. We are painting your trail and feeding heavier hulls a cleaner vector.",
                    "HOSTILE INTENT DECLARED");
        }
        if (target.role == ShipRole.BASE || target.role == ShipRole.STATIC_TURRET) {
            return outcome("Hostile control node here. Our intent is simple: deny you this sector.");
        }
        return outcome("Red channel received. Our intent is to pin you until the strike group closes.");
    }

    private static CommOutcome stateIntentOutcome(GameContext ctx, Ship target, String baseReply) {
        Ship intel = nearestHostileToTarget(ctx, target, 2200.0);
        if (!isAliveHostileTo(target, intel)) {
            return outcome(baseReply);
        }
        if (ctx != null) {
            ctx.lockedTarget = intel;
        }
        applySupportOrder(ctx, target);
        return outcome(baseReply + " We are reading hostile contact " + speakerFor(intel) + " close to our lane.",
                "INTEL SHARED: " + speakerFor(intel).toUpperCase(Locale.US));
    }

    private static CommOutcome surrenderOutcome(GameContext ctx, Ship target, double hullFrac) {
        if (ctx == null || target == null) {
            return outcome("Demand rejected. If you want surrender, break our guns first.");
        }
        if (commActionCoolingDown(ctx, target)) {
            return outcome("This contact already answered your surrender demand. Press the advantage or let them go.",
                    "SURRENDER CHANNEL COOLDOWN");
        }

        if (canForceRetreatViaSurrender(target, hullFrac)) {
            applyWarnOffOrder(ctx, target);
            putCommActionCooldown(ctx, target, SURRENDER_COOLDOWN_SECONDS);
            return outcome("Negative. We are hurt, not finished, but we are breaking away from your guns.",
                    "HOSTILE WITHDRAWAL FORCED");
        }

        if (canAcceptSurrender(ctx, target, hullFrac)) {
            convertShipToPlayerSide(ctx, target);
            putCommActionCooldown(ctx, target, SURRENDER_COOLDOWN_SECONDS);
            return outcome("We are done. Cutting drives and yielding the hull. Do not fire on us as we cross over.",
                    "SURRENDER ACCEPTED");
        }

        if (hullFrac < 0.14) return outcome("Red hull failing. We are dumping telemetry and going dead. Take the wreck if you earn it.");
        if (target.role == ShipRole.PATROL || target.role == ShipRole.PICKET || target.isSmallCraft()) {
            return outcome("Scout element refuses surrender. Catch us before the main line arrives.");
        }
        return outcome("Demand rejected. If you want surrender, break our guns first.");
    }

    private static void applySupportOrder(GameContext ctx, Ship target) {
        if (ctx == null || ctx.command == null || target == null) return;
        GameContext.FleetCommand cmd = (target.role == ShipRole.BASE || target.role == ShipRole.STATIC_TURRET)
                ? GameContext.FleetCommand.DEFEND
                : GameContext.FleetCommand.ESCORT;
        ctx.command.shipFleetCommandOverrides.put(target.id, cmd);
        ctx.command.shipFleetCommandOverrideTimers.put(target.id, SUPPORT_ORDER_SECONDS);
        Ship playerTarget = (ctx.lockedTarget != null && isAliveHostileTo(target, ctx.lockedTarget)) ? ctx.lockedTarget : null;
        if (playerTarget != null) {
            target.aiCommittedTargetId = playerTarget.id;
            target.aiTargetCommitTimer = Math.max(target.aiTargetCommitTimer, Math.min(SUPPORT_ORDER_SECONDS, 9.0));
        }
    }

    private static void applyWarnOffOrder(GameContext ctx, Ship target) {
        if (ctx == null || ctx.command == null || target == null) return;
        if (target.role == ShipRole.BASE || target.role == ShipRole.STATIC_TURRET) return;
        ctx.command.shipFleetCommandOverrides.put(target.id, GameContext.FleetCommand.RETREAT);
        ctx.command.shipFleetCommandOverrideTimers.put(target.id, WARN_OFF_ORDER_SECONDS);
        target.aiCommittedTargetId = -1;
        target.aiTargetCommitTimer = 0.0;
        target.crewOrder = Ship.CrewOrder.DAMAGE_CONTROL;
    }

    private static CommOutcome executeTrade(GameContext ctx, Ship target, String baseReply, String successReply) {
        if (ctx == null || ctx.player == null || target == null) return outcome(baseReply);
        if (commActionCoolingDown(ctx, target)) {
            return outcome("Trade channel is still settling from the last exchange. Call back in a few seconds.",
                    "TRADE CHANNEL COOLDOWN");
        }
        if (ctx.player.cargo <= 0) {
            return outcome(baseReply + " We are reading no ore in your holds.");
        }

        int moved = Math.min(TRADE_ORE_BATCH, Math.max(0, ctx.player.cargo));
        if (moved <= 0) return outcome(baseReply);
        ctx.player.cargo = Math.max(0, ctx.player.cargo - moved);
        double priceMul = ctx.orePriceMul * ctx.orePriceBaseMul * CampaignSystem.oreCreditMul(ctx) * TRADE_PRICE_BONUS;
        int baseCredits = (int) Math.round(moved * GameContext.ORE_PRICE * priceMul);
        ctx.credits += GameContext.scaleCreditEarnings(baseCredits);
        putCommActionCooldown(ctx, target, TRADE_COOLDOWN_SECONDS);
        return outcome(successReply + " Purchased " + moved + " ore.",
                "TRADE COMPLETE +" + GameContext.scaleCreditEarnings(baseCredits) + "C");
    }

    private static boolean commActionCoolingDown(GameContext ctx, Ship target) {
        if (ctx == null || ctx.command == null || target == null || ctx.command.shipCommActionCooldowns == null) return false;
        return ctx.command.shipCommActionCooldowns.getOrDefault(target.id, 0.0) > 0.0;
    }

    private static void putCommActionCooldown(GameContext ctx, Ship target, double seconds) {
        if (ctx == null || ctx.command == null || target == null || ctx.command.shipCommActionCooldowns == null) return;
        ctx.command.shipCommActionCooldowns.put(target.id, Math.max(0.2, seconds));
    }

    private static boolean isAliveHostileTo(Ship source, Ship target) {
        if (source == null || target == null) return false;
        if (!target.alive || target.dying || target.hp <= 0) return false;
        if (source.faction == null || target.faction == null) return false;
        return !source.faction.isFriendlyTo(target.faction);
    }

    private static Ship nearestHostileToTarget(GameContext ctx, Ship source, double maxDist) {
        if (ctx == null || source == null || source.faction == null) return null;
        Ship best = null;
        double bestD2 = Math.max(1.0, maxDist) * Math.max(1.0, maxDist);
        for (Ship ship : ctx.ships) {
            if (ship == null || ship == source) continue;
            if (!ship.alive || ship.dying || ship.hp <= 0) continue;
            if (ship.faction == null || source.faction.isFriendlyTo(ship.faction)) continue;
            if (!TargetingSystem.isDetectableToObserver(source, ship)) continue;
            double d2 = GameMath.dist2(source.x, source.y, ship.x, ship.y);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = ship;
            }
        }
        return best;
    }

    private static boolean canForceRetreatViaSurrender(Ship target, double hullFrac) {
        if (target == null) return false;
        if (target.role == ShipRole.BASE || target.role == ShipRole.STATIC_TURRET) return false;
        return hullFrac < 0.30 && (target.isSmallCraft() || target.role == ShipRole.PATROL || target.role == ShipRole.PICKET);
    }

    private static boolean canAcceptSurrender(GameContext ctx, Ship target, double hullFrac) {
        if (ctx == null || ctx.player == null || target == null) return false;
        if (target.role == ShipRole.BASE || target.role == ShipRole.STATIC_TURRET) return false;
        if (target.role != null && target.role.isCapitalCombatant()) return false;
        boolean supportHull = target.role == ShipRole.TRANSPORT || target.role == ShipRole.HAULER || target.role == ShipRole.MINER;
        boolean lightCombat = target.isSmallCraft() || target.role == ShipRole.PATROL || target.role == ShipRole.PICKET || target.role == ShipRole.FRIGATE;
        if (!supportHull && !lightCombat) return false;
        return hullFrac < 0.18;
    }

    private static void convertShipToPlayerSide(GameContext ctx, Ship target) {
        if (ctx == null || ctx.player == null || target == null) return;
        Faction newFaction = Faction.forTeamId(ctx.player.faction.teamId());
        target.faction = newFaction;
        target.aiCommittedTargetId = -1;
        target.aiTargetCommitTimer = 0.0;
        target.cancelBattlefieldWarp();
        target.reveal(3.0);
        target.crewOrder = Ship.CrewOrder.ENGINEERING;
        target.minerHomeBase = ctx.player;
        target.escortAnchorId = ctx.player.id;
        if (target.name != null && !target.name.startsWith("Defector ")) {
            target.name = "Defector " + target.name;
        }
        applySupportOrder(ctx, target);
    }

    private static String identifyFriendlyResponse(Ship target, double hullFrac) {
        if (hullFrac < 0.35) return "Taking damage. We can still answer, but we need cover.";
        if (target.role == ShipRole.MINER || target.role == ShipRole.HAULER || target.role == ShipRole.TRANSPORT) {
            return "Local traffic acknowledges. Staying on station and keeping the lane moving.";
        }
        if (target.role == ShipRole.CARRIER || target.role == ShipRole.DRONE_CARRIER) {
            return "Flight control copies. Deck crews are standing by.";
        }
        if (target.role == ShipRole.BASE || target.role == ShipRole.STATIC_TURRET) {
            return "Station control online. Docking and local defense channels remain open.";
        }
        return "Copy your signal. Holding local posture and watching the sector.";
    }

    private static String identifyAlliedResponse(Ship target, double hullFrac) {
        if (target.faction == Faction.TEAM_C) {
            if (hullFrac < 0.45) return "Green broker screen here. We are hurt, but we can still trade fire for you.";
            return "Green traffic acknowledges. We can share the lane if you keep the pressure off our hulls.";
        }
        if (target.faction == Faction.TEAM_D) {
            if (hullFrac < 0.45) return "Yellow liberation channel received. We are pulling survivors out under fire.";
            return "Yellow flight copies. Keep moving and the larger red elements may lose the trail.";
        }
        return "Friendly contact acknowledges. We are local and reading you clearly.";
    }

    private static String identifyHostileResponse(Ship target, double hullFrac, double playerDist) {
        if (hullFrac < 0.22) return "Red channel. We are not breaking. Finish it if you can.";
        if (target.role == ShipRole.PATROL || target.role == ShipRole.PICKET || target.isSmallCraft()) {
            return playerDist < 1100.0
                    ? "Scout contact confirmed. Main force is moving on your signature."
                    : "Red scout screen. We have your sector, but not a firing solution yet.";
        }
        if (target.role == ShipRole.BASE || target.role == ShipRole.STATIC_TURRET) {
            return "Hostile station control. Leave the sector or be cut apart where you drift.";
        }
        if (target.role != null && target.role.isCapitalCombatant()) {
            return "Red command hull acknowledges. Hold still and this ends quickly for you.";
        }
        return "Red contact received. Keep talking if it helps you stay calm.";
    }

    private static String identifyNeutralResponse(Ship target, double hullFrac) {
        if (target.role == ShipRole.TRANSPORT || target.role == ShipRole.HAULER) {
            return hullFrac < 0.55
                    ? "Civilian band open. We are damaged and trying to clear the lane."
                    : "Civilian traffic acknowledges. We are not looking for a fight.";
        }
        if (target.role == ShipRole.MINER) {
            return "Prospector channel here. Ore is thin and everyone is jumpy.";
        }
        return "Unknown local contact acknowledges. Signal quality is rough but readable.";
    }

    private static boolean isTradeCapable(Ship target) {
        if (target == null || target.role == null) return false;
        return switch (target.role) {
            case TRANSPORT, HAULER, MINER, BASE -> true;
            default -> target.faction == Faction.TEAM_C;
        };
    }

    private static String speakerFor(Ship target) {
        if (target == null) return "CONTACT";
        String name = (target.name == null || target.name.isBlank()) ? null : target.name.trim();
        if (name != null) return name;
        String faction = (target.faction == null) ? "LOCAL" : target.faction.teamName().toUpperCase(Locale.US);
        String role = (target.role == null) ? "CONTACT" : target.role.name().replace('_', ' ');
        return faction + " " + role;
    }

    private static void postHailMessage(GameContext ctx, Faction faction, String channel, String text, double ttl) {
        if (ctx == null) return;
        List<GameContext.FleetCommMessage> log = ctx.fleetCommLog;
        if (log.size() >= 8) {
            log.remove(0);
        }
        log.add(new GameContext.FleetCommMessage(faction, channel, text, ttl, true));
    }

    private static CommOutcome outcome(String response) {
        return new CommOutcome(response, null);
    }

    private static CommOutcome outcome(String response, String banner) {
        return new CommOutcome(response, banner);
    }

    private record CommOutcome(String response, String banner) {}
}
