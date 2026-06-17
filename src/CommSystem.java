import app.config.GameMode;
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
        if (ctx.ui != null) {
            String panelTitle = (outcome.banner() == null || outcome.banner().isBlank())
                    ? "COMM " + intent.label().toUpperCase(Locale.US)
                    : outcome.banner();
            ctx.ui.showCommResult(panelTitle, outcome.response(), target.id, 4.5);
        }
        applyFactionMemoryFromHail(ctx, target, intent, outcome);
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
        return TargetingSystem.isDetectableToObserver(ctx, ctx.player, ship);
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
        CommandState.CommFactionMemory memory = memoryFor(ctx, target == null ? null : target.faction);
        return switch (intent) {
            case IDENTIFY -> outcome(identifyFriendlyResponse(target, hullFrac));
            case STATE_INTENT -> stateIntentOutcome(ctx, target,
                    "Blue command traffic. We are holding this pocket with you and tracking nearby contacts.");
            case REQUEST_SUPPORT -> {
                if (hullFrac < 0.35) yield outcome("We copy your support request, but our hull is in bad shape. We need cover before we can push.");
                applySupportOrder(ctx, target);
                CommOutcome vector = objectiveSupportOutcome(ctx, target,
                        "Blue command copies. We are pushing toward ",
                        "SUPPORT VECTOR LOCKED");
                if (vector != null) yield vector;
                if (target.role != null && (target.role.isCarrierHull() || target.role.isCapitalCombatant())) {
                    yield outcome("Support request received. We can lean into the lane and pressure anything you flush out.",
                            "SUPPORT ACKNOWLEDGED");
                }
                if (memory.cooperation > 0.24) {
                    yield outcome("Support request received. We are warping back toward your screen and will focus your marked target.",
                            "SUPPORT VECTOR LOCKED");
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
        CommandState.CommFactionMemory memory = memoryFor(ctx, target == null ? null : target.faction);
        return switch (intent) {
            case IDENTIFY -> outcome(identifyAlliedResponse(target, hullFrac));
            case STATE_INTENT -> alliedStateIntentOutcome(ctx, target);
            case REQUEST_SUPPORT -> {
                if (hullFrac < 0.45) yield outcome("We hear you, but we are already bleeding. We can screen lightly, not spearhead.");
                applySupportOrder(ctx, target);
                CommOutcome vector = objectiveSupportOutcome(ctx, target,
                        (target.faction == Faction.TEAM_D)
                                ? "Yellow flight copies. We are vectoring on "
                                : "Green channel copies. We are vectoring on ",
                        (target.faction == Faction.TEAM_D)
                                ? "YELLOW SUPPORT VECTOR LOCKED"
                                : "GREEN SUPPORT VECTOR LOCKED");
                if (vector != null && memory.cooperation > 0.10) yield vector;
                if (target.faction == Faction.TEAM_D) {
                    if (memory.cooperation > 0.18) {
                        yield outcome("Yellow flight copies. We are warping toward your hull, taking escort posture, and marking your target for the squadron.",
                                "YELLOW SUPPORT WARPING IN");
                    }
                    yield outcome("Yellow flight copies. We can scout ahead and draw the heavier reds onto a bad angle.",
                            "YELLOW SCOUTS MOVING TO SUPPORT");
                }
                if (target.faction == Faction.TEAM_C) {
                    if (memory.cooperation > 0.18) {
                        yield outcome("Green channel copies. We are vectoring a support wing onto your position and will pressure whatever you have painted.",
                                "GREEN SUPPORT VECTOR LOCKED");
                    }
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
            case WARN_OFF -> hostileWarnOutcome(ctx, target, hullFrac);
            case DEMAND_SURRENDER -> surrenderOutcome(ctx, target, hullFrac);
        };
    }

    private static CommOutcome neutralResponse(GameContext ctx, Ship target, double hullFrac, UiState.CommIntent intent) {
        CommandState.CommFactionMemory memory = memoryFor(ctx, target == null ? null : target.faction);
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
                if (memory.trust > 0.14) {
                    yield outcome("Acknowledged. We will clear the lane and stay off your weapon line until this burns past.",
                            "NEUTRAL CONTACT CLEARING LANE");
                }
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
        openTradeMenu(ctx, target, TradeCounterparty.FRIENDLY);
        return outcome("Trade channel open. Select a ledger item from the trade menu.",
                "TRADE MENU OPEN");
    }

    private static CommOutcome alliedTradeOutcome(GameContext ctx, Ship target) {
        if (isTradeCapable(target) || target.faction == Faction.TEAM_C) {
            openTradeMenu(ctx, target, TradeCounterparty.ALLIED);
            return outcome("Trade channel open. Select a ledger item from the trade menu.",
                    "TRADE MENU OPEN");
        }
        return outcome("No trade inventory on this hull. We are a combat contact, not a broker.");
    }

    private static CommOutcome neutralTradeOutcome(GameContext ctx, Ship target) {
        if (!isTradeCapable(target)) {
            return outcome("No cargo exchange available on this signal.");
        }
        CommandState.CommFactionMemory memory = memoryFor(ctx, target == null ? null : target.faction);
        boolean underFire = isUnderFirePressure(ctx, target, 820.0);
        if (underFire && memory.trust < 0.12) {
            return outcome("Negative. We are under fire and not opening our holds for an unknown warship right now.",
                    "TRADE REFUSED UNDER FIRE");
        }
        openTradeMenu(ctx, target, underFire ? TradeCounterparty.NEUTRAL_UNDER_FIRE : TradeCounterparty.NEUTRAL);
        return outcome("Trade channel open. Select a ledger item from the trade menu.",
                "TRADE MENU OPEN");
    }

    private enum TradeCounterparty {
        FRIENDLY,
        ALLIED,
        NEUTRAL,
        NEUTRAL_UNDER_FIRE
    }

    private static void openTradeMenu(GameContext ctx, Ship target, TradeCounterparty counterparty) {
        if (ctx == null || ctx.ui == null || target == null) return;
        List<UiState.CommTradeOption> options = tradeOptionsFor(ctx, target, counterparty);
        String speaker = speakerFor(target);
        String body = switch (counterparty) {
            case FRIENDLY -> "Blue logistics desk. Choose what you want moved through this channel.";
            case ALLIED -> "Allied broker channel. Pick cargo sale, paid intel, or a contract if this hull can offer one.";
            case NEUTRAL_UNDER_FIRE -> "Hazard pricing is active while fire crosses the lane.";
            case NEUTRAL -> "Civilian exchange window. Select the deal before the channel closes.";
        };
        ctx.ui.showCommTradeMenu(target.id, "REQUEST TRADE: " + speaker, body, options);
    }

    private static List<UiState.CommTradeOption> tradeOptionsFor(GameContext ctx, Ship target, TradeCounterparty counterparty) {
        java.util.ArrayList<UiState.CommTradeOption> options = new java.util.ArrayList<>();
        boolean coolingDown = commActionCoolingDown(ctx, target);
        int cargo = (ctx == null || ctx.player == null) ? 0 : Math.max(0, ctx.player.cargo);
        double payoutMul = tradePayoutMulFor(ctx, target, counterparty);
        int expectedCredits = expectedOreSaleCredits(ctx, Math.min(TRADE_ORE_BATCH, cargo), payoutMul);
        options.add(tradeOption("SELL_ORE",
                "Sell ore cargo",
                cargo > 0
                        ? "Sell up to " + TRADE_ORE_BATCH + " ore for about " + expectedCredits + " credits."
                        : "No ore is currently in your holds.",
                !coolingDown && cargo > 0));
        if (ctx != null && ctx.config != null && ctx.config.mode == GameMode.CAMPAIGN_OPS) {
            int intelCost = intelCostFor(target, counterparty);
            options.add(tradeOption("BUY_INTEL",
                    "Buy route intel",
                    "Purchase local vectors and contact intel for " + intelCost + " credits.",
                    !coolingDown && cargo <= 0 && ctx.credits >= intelCost && bestIntelSalePackage(ctx, target) != null));
        }
        if (counterparty == TradeCounterparty.ALLIED && canOfferContractHire(ctx, target)) {
            int contractCost = contractHireCreditCost(target.role);
            options.add(tradeOption("HIRE_ESCORT",
                    "Hire escort",
                    contractCost > 0
                            ? "Pay " + contractCost + " credits to bring this hull into your squad."
                            : "This hull has no contract ledger.",
                    !coolingDown && contractCost > 0 && ctx != null && ctx.credits >= contractCost));
        }
        options.add(tradeOption("CANCEL", "Close channel", "Leave the trade menu without exchanging cargo.", true));
        return options;
    }

    private static UiState.CommTradeOption tradeOption(String id, String label, String detail, boolean enabled) {
        UiState.CommTradeOption option = new UiState.CommTradeOption();
        option.id = id;
        option.label = label;
        option.detail = detail;
        option.enabled = enabled;
        return option;
    }

    public static boolean chooseTradeMenuOption(GameContext ctx, int optionIndex) {
        if (ctx == null || ctx.ui == null || !ctx.ui.commTradeMenu.active) return false;
        if (optionIndex < 0 || optionIndex >= ctx.ui.commTradeMenu.options.size()) return false;
        UiState.CommTradeOption option = ctx.ui.commTradeMenu.options.get(optionIndex);
        if (option == null || !option.enabled) {
            EventSystem.showBanner(ctx, "TRADE OPTION UNAVAILABLE", 0.9);
            return true;
        }
        Ship target = shipById(ctx, ctx.ui.commTradeMenu.targetId);
        String optionId = option.id == null ? "" : option.id.trim();
        if ("CANCEL".equals(optionId)) {
            ctx.ui.clearCommTradeMenu();
            EventSystem.showBanner(ctx, "TRADE CHANNEL CLOSED", 0.8);
            return true;
        }
        if (target == null || !isHailable(ctx, target)) {
            ctx.ui.clearCommTradeMenu();
            EventSystem.showBanner(ctx, "TRADE CONTACT LOST", 1.1);
            return true;
        }

        CommOutcome outcome = executeTradeMenuOption(ctx, target, optionId);
        if (outcome == null) {
            outcome = outcome("That ledger item is not available on this channel right now.",
                    "TRADE OPTION UNAVAILABLE");
        }
        ctx.ui.clearCommTradeMenu();
        String speaker = speakerFor(target);
        postHailMessage(ctx, target.faction, speaker, outcome.response(), 8.0);
        ctx.ui.showCommResult((outcome.banner() == null || outcome.banner().isBlank()) ? "TRADE RESULT" : outcome.banner(),
                outcome.response(), target.id, 4.5);
        if (ctx.ui.voiceCaptionsEnabled) {
            ctx.ui.voiceCaption = speaker + ": " + outcome.response();
            ctx.ui.voiceCaptionT = 2.8;
        }
        if (outcome.banner() != null && !outcome.banner().isBlank()) {
            EventSystem.showBanner(ctx, outcome.banner(), 0.9);
        }
        return true;
    }

    private static CommOutcome executeTradeMenuOption(GameContext ctx, Ship target, String optionId) {
        TradeCounterparty counterparty = counterpartyFor(ctx, target);
        return switch (optionId) {
            case "SELL_ORE" -> executeTrade(ctx, target,
                    tradeBaseReply(counterparty),
                    tradeSuccessReply(counterparty),
                    tradePayoutMulFor(ctx, target, counterparty));
            case "BUY_INTEL" -> intelTradeOutcome(ctx, target,
                    intelCostFor(target, counterparty),
                    intelOfferReply(counterparty),
                    intelSuccessBanner(target, counterparty));
            case "HIRE_ESCORT" -> alliedContractHireOutcome(ctx, target);
            default -> outcome("Trade channel closed.");
        };
    }

    private static TradeCounterparty counterpartyFor(GameContext ctx, Ship target) {
        if (ctx == null || ctx.player == null || target == null || target.faction == null) return TradeCounterparty.NEUTRAL;
        boolean friendly = target.faction.isFriendlyTo(ctx.player.faction);
        boolean sameTeam = target.faction.teamId() == ctx.player.faction.teamId();
        if (friendly && sameTeam) return TradeCounterparty.FRIENDLY;
        if (friendly) return TradeCounterparty.ALLIED;
        return isUnderFirePressure(ctx, target, 820.0) ? TradeCounterparty.NEUTRAL_UNDER_FIRE : TradeCounterparty.NEUTRAL;
    }

    private static Ship shipById(GameContext ctx, int id) {
        if (ctx == null || ctx.ships == null || id <= 0) return null;
        for (Ship ship : ctx.ships) {
            if (ship != null && ship.id == id) return ship;
        }
        return null;
    }

    private static int expectedOreSaleCredits(GameContext ctx, int moved, double payoutMul) {
        if (ctx == null || moved <= 0) return 0;
        double priceMul = ctx.orePriceMul * ctx.orePriceBaseMul * CampaignSystem.oreCreditMul(ctx)
                * TRADE_PRICE_BONUS * MathUtil.clamp(payoutMul, 0.55, 1.25);
        int baseCredits = (int) Math.round(moved * GameContext.ORE_PRICE * priceMul);
        return GameContext.scaleCreditEarnings(baseCredits);
    }

    private static double tradePayoutMulFor(GameContext ctx, Ship target, TradeCounterparty counterparty) {
        if (counterparty == TradeCounterparty.NEUTRAL || counterparty == TradeCounterparty.NEUTRAL_UNDER_FIRE) {
            CommandState.CommFactionMemory memory = memoryFor(ctx, target == null ? null : target.faction);
            return counterparty == TradeCounterparty.NEUTRAL_UNDER_FIRE
                    ? MathUtil.clamp(0.72 + memory.trust * 0.35, 0.72, 0.92)
                    : MathUtil.clamp(1.0 + memory.trust * 0.10, 1.0, 1.10);
        }
        return 1.0;
    }

    private static int intelCostFor(Ship target, TradeCounterparty counterparty) {
        return switch (counterparty) {
            case FRIENDLY -> 120;
            case ALLIED -> (target != null && target.faction == Faction.TEAM_D) ? 180 : 160;
            case NEUTRAL_UNDER_FIRE -> 220;
            case NEUTRAL -> 180;
        };
    }

    private static String tradeBaseReply(TradeCounterparty counterparty) {
        return switch (counterparty) {
            case FRIENDLY -> "Trade channel is open. Bring salvage or ore when the shooting eases.";
            case ALLIED -> "Trade window available. Keep your weapons cool and we can talk terms.";
            case NEUTRAL_UNDER_FIRE -> "Trade channel open, but hazard premiums apply while rounds are still crossing the lane.";
            case NEUTRAL -> "Trade channel open. Keep your escorts steady and we can do business.";
        };
    }

    private static String tradeSuccessReply(TradeCounterparty counterparty) {
        return switch (counterparty) {
            case FRIENDLY -> "Trade complete. Clearing cargo and crediting your account now.";
            case ALLIED -> "Terms accepted. We are moving credits over and keeping the lane clear.";
            case NEUTRAL_UNDER_FIRE -> "Exchange complete. We kept our margin and you kept the lane from collapsing.";
            case NEUTRAL -> "Exchange complete. Stay clear of any incoming firing lane.";
        };
    }

    private static String intelOfferReply(TradeCounterparty counterparty) {
        return switch (counterparty) {
            case FRIENDLY -> "Blue logistics can sell you a quick intel package if you need route data more than ore settlement.";
            case ALLIED -> "We can move a paid intel packet if you need target vectors more than ore settlement.";
            case NEUTRAL_UNDER_FIRE -> "We can sell you a hazard-priced intel packet if you need vectors more than cargo exchange.";
            case NEUTRAL -> "We can sell you route and contact intel if that is more useful than moving ore right now.";
        };
    }

    private static String intelSuccessBanner(Ship target, TradeCounterparty counterparty) {
        return switch (counterparty) {
            case FRIENDLY -> "BLUE INTEL PACKAGE SOLD";
            case ALLIED -> (target != null && target.faction == Faction.TEAM_D)
                    ? "YELLOW INTEL PACKAGE SOLD"
                    : "GREEN INTEL PACKAGE SOLD";
            case NEUTRAL_UNDER_FIRE -> "HAZARD INTEL PACKAGE SOLD";
            case NEUTRAL -> "INTEL PACKAGE SOLD";
        };
    }

    private static CommOutcome alliedContractHireOutcome(GameContext ctx, Ship target) {
        if (!canOfferContractHire(ctx, target)) return null;
        if (commActionCoolingDown(ctx, target)) {
            return outcome("Contract channel is still settling from the last exchange. Call again in a few seconds.",
                    "CONTRACT CHANNEL COOLDOWN");
        }
        CommandState.CommFactionMemory memory = memoryFor(ctx, target.faction);
        if (isUnderFirePressure(ctx, target, 760.0) && memory.cooperation < 0.10) {
            return outcome("Negative. We are too busy surviving this lane to sign onto a new contract right now.",
                    "CONTRACT REFUSED UNDER FIRE");
        }
        if (target.hpMax > 0 && (target.hp / (double) target.hpMax) < 0.32) {
            return outcome("Negative. This hull is too damaged to promise reliable escort service until we patch it together.",
                    "CONTRACT REFUSED DAMAGED HULL");
        }

        int contractCost = contractHireCreditCost(target.role);
        if (contractCost <= 0) {
            return outcome("No contract ledger is available for this hull class.");
        }
        if (ctx.credits < contractCost) {
            return outcome("We can join your squad for " + contractCost + " credits, but your account is light.",
                    "CONTRACT COST " + contractCost + "C");
        }

        ctx.credits -= contractCost;
        convertShipToContractEscort(ctx, target);
        putCommActionCooldown(ctx, target, TRADE_COOLDOWN_SECONDS * 1.5);
        String speaker = speakerFor(target);
        return outcome("Contract accepted. " + speaker + " is joining your squad and will screen your flagship now.",
                "CONTRACT ACCEPTED -" + contractCost + "C");
    }

    private static CommOutcome intelTradeOutcome(GameContext ctx, Ship target, int creditCost,
                                                 String offerReply, String successBanner) {
        if (ctx == null || ctx.player == null || target == null) return null;
        if (ctx.config == null || ctx.config.mode != GameMode.CAMPAIGN_OPS) return null;
        if (ctx.player.cargo > 0) return null;
        if (commActionCoolingDown(ctx, target)) {
            return outcome("Trade channel is still settling from the last exchange. Call back in a few seconds.",
                    "TRADE CHANNEL COOLDOWN");
        }
        IntelSalePackage intel = bestIntelSalePackage(ctx, target);
        if (intel == null) {
            return outcome(offerReply + " We have nothing actionable to sell you on this channel right now.");
        }
        if (ctx.credits < Math.max(0, creditCost)) {
            return outcome(offerReply + " Price is " + creditCost + " credits and your ledger is short.",
                    "INTEL COST " + creditCost + "C");
        }
        ctx.credits -= Math.max(0, creditCost);
        pushIntelPing(ctx, intel.x, intel.y, teamCodeFor(target));
        EventSystem.showWorldCallout(ctx, intel.x, intel.y, intel.label, intel.color, 2.9);
        putCommActionCooldown(ctx, target, TRADE_COOLDOWN_SECONDS);
        return outcome("Intel package sold for " + creditCost + " credits. Vectoring you toward " + intel.label + ". "
                        + intel.subtitle,
                successBanner + " -" + creditCost + "C");
    }

    private static CommOutcome hostileWarnOutcome(GameContext ctx, Ship target, double hullFrac) {
        if (target == null) return outcome("We are not yielding the lane. Move or burn.");
        boolean pressured = isUnderFirePressure(ctx, target, 760.0);
        if ((target.role == ShipRole.PATROL || target.role == ShipRole.PICKET || target.isSmallCraft())
                && (hullFrac < 0.58 || pressured)) {
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
            CampaignSystem.noteYellowStateIntentNeutrality(ctx, target);
            DiscoveryIntelHint discoveryHint = nearestDiscoveryHint(ctx, target, 2600.0);
            if (discoveryHint != null) {
                pushIntelPing(ctx, discoveryHint.x, discoveryHint.y, teamCodeFor(target));
                EventSystem.showWorldCallout(ctx, discoveryHint.x, discoveryHint.y, discoveryHint.label, new java.awt.Color(150, 220, 255), 2.6);
                return outcome(baseReply + " We are reading a weak discovery pocket near " + discoveryHint.label + ".",
                        "DISCOVERY POCKET REVEALED");
            }
            ObjectiveIntelHint objectiveHint = highestPriorityObjectiveHint(ctx, target, 4200.0);
            if (objectiveHint != null) {
                pushIntelPing(ctx, objectiveHint.x, objectiveHint.y, teamCodeFor(target));
                EventSystem.showWorldCallout(ctx, objectiveHint.x, objectiveHint.y, objectiveHint.label,
                        objectiveHint.color, 2.6);
                return outcome(baseReply + " Primary traffic says push toward " + objectiveHint.label + ". " + objectiveHint.subtitle,
                        objectiveHint.banner);
            }
            MissionSectionHint reserveHint = reserveSectionHint(ctx);
            if (reserveHint != null) {
                pushIntelPing(ctx, reserveHint.x, reserveHint.y, teamCodeFor(target));
                EventSystem.showWorldCallout(ctx, reserveHint.x, reserveHint.y, reserveHint.label, new java.awt.Color(255, 204, 132), 2.4);
                return outcome(baseReply + " We are seeing reserve traffic building near " + reserveHint.label + ".",
                        "RESERVE VECTOR HINT");
            }
            return outcome(baseReply);
        }
        if (ctx != null) {
            ctx.lockedTarget = intel;
        }
        pushIntelPing(ctx, intel.x, intel.y, teamCodeFor(target));
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

        CommandState.CommFactionMemory memory = memoryFor(ctx, target.faction);
        boolean pressured = isUnderFirePressure(ctx, target, 700.0);
        if (canForceRetreatViaSurrender(target, hullFrac) || ((target.isSmallCraft() || target.role == ShipRole.PATROL || target.role == ShipRole.PICKET) && pressured && memory.fear > 0.10)) {
            applyWarnOffOrder(ctx, target);
            putCommActionCooldown(ctx, target, SURRENDER_COOLDOWN_SECONDS);
            return outcome("Negative. We are hurt, not finished, but we are breaking away from your guns.",
                    "PANIC RETREAT TRIGGERED");
        }

        if (canAcceptSurrender(ctx, target, hullFrac)) {
            convertShipToPlayerSide(ctx, target);
            putCommActionCooldown(ctx, target, SURRENDER_COOLDOWN_SECONDS);
            return outcome("We are done. Cutting drives and yielding the hull. Do not fire on us as we cross over.",
                    "SURRENDER ACCEPTED");
        }

        if ((hullFrac < 0.24 || memory.fear > 0.26) && !target.isSmallCraft() && target.role != ShipRole.BASE && target.role != ShipRole.STATIC_TURRET) {
            target.applyTemporaryDisable(5.0);
            putCommActionCooldown(ctx, target, SURRENDER_COOLDOWN_SECONDS * 0.85);
            return outcome("We are killing weapons power and trying to drift out of this lane. Do not press closer.",
                    "WEAPON SHUTDOWN");
        }

        if ((hullFrac < 0.32 || memory.fear > 0.18) && target.role != ShipRole.BASE && target.role != ShipRole.STATIC_TURRET) {
            applyTemporaryCeasefire(ctx, target, 4.5);
            putCommActionCooldown(ctx, target, SURRENDER_COOLDOWN_SECONDS * 0.75);
            return outcome("Temporary ceasefire. We are cooling weapons and falling off the line for a few seconds.",
                    "TEMPORARY CEASEFIRE");
        }

        if (hullFrac < 0.14) return outcome("Red hull failing. We are dumping telemetry and going dead. Take the wreck if you earn it.");
        if (target.role == ShipRole.PATROL || target.role == ShipRole.PICKET || target.isSmallCraft()) {
            return outcome("Scout element refuses surrender. Catch us before the main line arrives.");
        }
        return outcome("Demand rejected. If you want surrender, break our guns first.");
    }

    private static void applySupportOrder(GameContext ctx, Ship target) {
        if (ctx == null || ctx.command == null || target == null) return;
        CampaignSystem.noteAmbientSupportRequest(ctx, target);
        GameContext.FleetCommand cmd = (target.role == ShipRole.BASE || target.role == ShipRole.STATIC_TURRET)
                ? GameContext.FleetCommand.DEFEND
                : GameContext.FleetCommand.ESCORT;
        ctx.command.shipFleetCommandOverrides.put(target.id, cmd);
        double duration = SUPPORT_ORDER_SECONDS + memoryFor(ctx, target.faction).cooperation * 10.0;
        ctx.command.shipFleetCommandOverrideTimers.put(target.id, duration);
        Ship playerTarget = (ctx.lockedTarget != null && isAliveHostileTo(target, ctx.lockedTarget)) ? ctx.lockedTarget : null;
        if (cmd == GameContext.FleetCommand.ESCORT && ctx.player != null) {
            target.escortAnchorId = ctx.player.id;
        }
        if (playerTarget != null) {
            target.aiCommittedTargetId = playerTarget.id;
            target.aiTargetCommitTimer = Math.max(target.aiTargetCommitTimer, Math.min(duration, 10.0));
            if (target.faction != null) {
                ctx.command.fleetSharedTargets.put(target.faction, playerTarget);
            }
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
        return executeTrade(ctx, target, baseReply, successReply, 1.0);
    }

    private static CommOutcome executeTrade(GameContext ctx, Ship target, String baseReply, String successReply, double payoutMul) {
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
        double priceMul = ctx.orePriceMul * ctx.orePriceBaseMul * CampaignSystem.oreCreditMul(ctx)
                * TRADE_PRICE_BONUS * MathUtil.clamp(payoutMul, 0.55, 1.25);
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
        return hullFrac < 0.34 && (target.isSmallCraft() || target.role == ShipRole.PATROL || target.role == ShipRole.PICKET);
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

    private static CommOutcome objectiveSupportOutcome(GameContext ctx, Ship target, String prefix, String bannerFallback) {
        ObjectiveIntelHint hint = highestPriorityObjectiveHint(ctx, target, 4200.0);
        if (hint == null) return null;
        pushIntelPing(ctx, hint.x, hint.y, teamCodeFor(target));
        EventSystem.showWorldCallout(ctx, hint.x, hint.y, hint.label, hint.color, 2.8);
        String response = prefix + hint.label + " and will hold pressure there.";
        if (hint.subtitle != null && !hint.subtitle.isBlank()) {
            response += " " + hint.subtitle;
        }
        String banner = (hint.banner == null || hint.banner.isBlank()) ? bannerFallback : hint.banner;
        return outcome(response, banner);
    }

    private static ObjectiveIntelHint highestPriorityObjectiveHint(GameContext ctx, Ship source, double maxDist) {
        if (ctx == null || source == null) return null;
        List<CampaignSystem.CampaignObjectiveMarker> markers = CampaignSystem.activeObjectiveMarkers(ctx);
        if (markers.isEmpty()) return null;
        CampaignSystem.CampaignObjectiveMarker best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double limit = Math.max(240.0, maxDist);
        for (CampaignSystem.CampaignObjectiveMarker marker : markers) {
            if (marker == null) continue;
            double dist = Math.hypot(marker.x - source.x, marker.y - source.y);
            if (dist > limit) continue;
            double score = marker.priority - dist / 180.0;
            if (best == null || score > bestScore) {
                best = marker;
                bestScore = score;
            }
        }
        if (best == null) return null;
        return objectiveHintForMarker(best);
    }

    private static IntelSalePackage bestIntelSalePackage(GameContext ctx, Ship source) {
        if (ctx == null || source == null) return null;
        ObjectiveIntelHint objective = highestPriorityObjectiveHint(ctx, source, 5200.0);
        if (objective != null) {
            return new IntelSalePackage(objective.label, objective.subtitle, objective.x, objective.y, objective.color);
        }
        DiscoveryIntelHint discovery = nearestDiscoveryHint(ctx, source, 4200.0);
        if (discovery != null) {
            return new IntelSalePackage(discovery.label,
                    "Weak signal pocket. Could be salvage, anomaly debris, or a side-route advantage.",
                    discovery.x, discovery.y,
                    new java.awt.Color(150, 220, 255));
        }
        MissionSectionHint reserve = reserveSectionHint(ctx);
        if (reserve != null) {
            return new IntelSalePackage(reserve.label,
                    "Reserve traffic is massing here. Expect reinforcements or a more dangerous route.",
                    reserve.x, reserve.y,
                    new java.awt.Color(255, 204, 132));
        }
        return null;
    }

    private static ObjectiveIntelHint objectiveHintForMarker(CampaignSystem.CampaignObjectiveMarker marker) {
        if (marker == null) return null;
        return switch (marker.type) {
            case DESTROY_TARGET -> new ObjectiveIntelHint(marker.label,
                    "Marked kill remains live on the board.",
                    marker.x, marker.y,
                    "MARKED TARGET VECTORED",
                    new java.awt.Color(255, 146, 146));
            case ESCORT_TARGET -> new ObjectiveIntelHint(marker.label,
                    "Keep the escort inside your screen or the run collapses.",
                    marker.x, marker.y,
                    "ESCORT TARGET VECTORED",
                    new java.awt.Color(196, 240, 176));
            case PROTECTED_ASSET -> new ObjectiveIntelHint(marker.label,
                    "This asset still needs cover to keep the mission alive.",
                    marker.x, marker.y,
                    "PROTECTED ASSET VECTORED",
                    new java.awt.Color(188, 228, 255));
            case CAPTURE_ZONE -> new ObjectiveIntelHint(marker.label,
                    "Clear the defenders and hold the zone to progress.",
                    marker.x, marker.y,
                    "CAPTURE ZONE VECTORED",
                    new java.awt.Color(140, 224, 196));
            case BOSS_TARGET -> new ObjectiveIntelHint(marker.label,
                    "The command hull is still the decisive break point.",
                    marker.x, marker.y,
                    "BOSS TARGET VECTORED",
                    new java.awt.Color(255, 186, 132));
            case NEXT_ROUTE, PRIMARY_OBJECTIVE -> new ObjectiveIntelHint(marker.label,
                    "That pocket is the route that advances the mission.",
                    marker.x, marker.y,
                    "MISSION ROUTE VECTORED",
                    new java.awt.Color(150, 220, 255));
            case OPTIONAL_OBJECTIVE -> new ObjectiveIntelHint(marker.label,
                    "Optional contact if you have time to peel off.",
                    marker.x, marker.y,
                    "OPTIONAL CONTACT VECTORED",
                    new java.awt.Color(214, 204, 132));
        };
    }

    private static boolean canOfferContractHire(GameContext ctx, Ship target) {
        if (ctx == null || ctx.player == null || target == null || target.role == null || target.faction == null) return false;
        if (ctx.config == null || ctx.config.mode != GameMode.CAMPAIGN_OPS) return false;
        if (target.faction != Faction.TEAM_C && target.faction != Faction.TEAM_D) return false;
        if (!target.alive || target.dying || target.hp <= 0) return false;
        return switch (target.role) {
            case BASE, STATIC_TURRET, MOTHERSHIP,
                    TRANSPORT_TITAN, BULWARK_TITAN, CARRIER_SUPPORT_TITAN, VANGUARD_TITAN,
                    INTERDICTION_TITAN, COMMAND_INTEL_TITAN, BOARDING_RECOVERY_TITAN,
                    ARTILLERY_TITAN, SHIELD_BASTION_TITAN, FLEET_TELEPORTER_TITAN,
                    ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN,
                    MOBILE_STATION_TITAN, HYPERWEAPON_TITAN -> false;
            default -> true;
        };
    }

    private static int contractHireCreditCost(ShipRole role) {
        int baseCost = CampaignSystem.marketCreditCostForRole(role);
        if (baseCost <= 0) return 0;
        return Math.max(120, (int) Math.round(baseCost * 1.5));
    }

    private static void convertShipToPlayerSide(GameContext ctx, Ship target) {
        if (ctx == null || ctx.player == null || target == null) return;
        Faction newFaction = Faction.forTeamId(ctx.player.faction.teamId());
        target.faction = newFaction;
        target.clearSurrenderState();
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

    private static void convertShipToContractEscort(GameContext ctx, Ship target) {
        if (ctx == null || ctx.player == null || target == null) return;
        target.faction = Faction.forTeamId(ctx.player.faction.teamId());
        target.clearSurrenderState();
        target.aiCommittedTargetId = -1;
        target.aiTargetCommitTimer = 0.0;
        target.cancelBattlefieldWarp();
        target.reveal(3.0);
        target.crewOrder = Ship.CrewOrder.ENGINEERING;
        target.minerHomeBase = ctx.player;
        target.escortAnchorId = ctx.player.id;
        if (target.name != null && !target.name.startsWith("Contract ")) {
            target.name = "Contract " + target.name;
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

    private static void applyTemporaryCeasefire(GameContext ctx, Ship target, double seconds) {
        if (ctx == null || target == null) return;
        target.applyTemporaryDisable(Math.max(2.0, seconds));
        applyWarnOffOrder(ctx, target);
        ctx.command.shipCommCeasefireTimers.put(target.id, Math.max(2.0, seconds));
    }

    private static boolean isUnderFirePressure(GameContext ctx, Ship target, double radius) {
        if (ctx == null || target == null || target.faction == null) return false;
        double hullFrac = (target.hpMax <= 0) ? 1.0 : (target.hp / (double) target.hpMax);
        if (hullFrac < 0.62) return true;
        if (target.totalFireIntensity() >= 1.2 || target.activeFireRoomCount() > 0) return true;
        int hostiles = 0;
        double rr2 = Math.max(260.0, radius) * Math.max(260.0, radius);
        for (Ship ship : ctx.ships) {
            if (ship == null || ship == target) continue;
            if (!ship.alive || ship.dying || ship.hp <= 0) continue;
            if (ship.faction == null || target.faction.isFriendlyTo(ship.faction)) continue;
            if (GameMath.dist2(target.x, target.y, ship.x, ship.y) <= rr2) {
                hostiles++;
                if (hostiles >= 2) return true;
            }
        }
        return false;
    }

    private static void applyFactionMemoryFromHail(GameContext ctx, Ship target, UiState.CommIntent intent, CommOutcome outcome) {
        if (ctx == null || target == null || target.faction == null || intent == null || outcome == null) return;
        CommandState.CommFactionMemory memory = memoryFor(ctx, target.faction);
        switch (intent) {
            case IDENTIFY -> memory.trust += 0.03;
            case STATE_INTENT -> {
                memory.trust += 0.04;
                memory.cooperation += 0.04;
            }
            case REQUEST_SUPPORT -> {
                if (outcome.banner() != null && (outcome.banner().contains("SUPPORT") || outcome.banner().contains("ESCORT"))) {
                    memory.cooperation += 0.10;
                } else {
                    memory.cooperation -= 0.02;
                }
            }
            case REQUEST_TRADE -> {
                if (outcome.banner() != null && outcome.banner().startsWith("TRADE COMPLETE")) {
                    memory.trust += 0.08;
                    memory.cooperation += 0.05;
                } else if (outcome.banner() != null && outcome.banner().startsWith("CONTRACT ACCEPTED")) {
                    memory.trust += 0.12;
                    memory.cooperation += 0.14;
                } else if (outcome.banner() != null && outcome.banner().contains("REFUSED")) {
                    memory.trust -= 0.03;
                }
            }
            case WARN_OFF -> {
                memory.fear += 0.10;
                memory.trust += 0.02;
            }
            case DEMAND_SURRENDER -> {
                memory.fear += 0.18;
                memory.trust -= 0.04;
            }
        }
        memory.trust = MathUtil.clamp(memory.trust, -0.35, 0.95);
        memory.fear = MathUtil.clamp(memory.fear, 0.0, 0.95);
        memory.cooperation = MathUtil.clamp(memory.cooperation, -0.20, 0.95);
    }

    private static CommandState.CommFactionMemory memoryFor(GameContext ctx, Faction faction) {
        CommandState.CommFactionMemory fallback = new CommandState.CommFactionMemory();
        if (ctx == null || ctx.command == null || faction == null) return fallback;
        return ctx.command.commFactionMemory.computeIfAbsent(faction, key -> new CommandState.CommFactionMemory());
    }

    private static DiscoveryIntelHint nearestDiscoveryHint(GameContext ctx, Ship source, double maxDist) {
        if (ctx == null || source == null) return null;
        CampaignSystem.DiscoverySignalSite site =
                CampaignSystem.nearestDiscoverySignalSite(ctx, source.x, source.y, maxDist);
        if (site == null) return null;
        return new DiscoveryIntelHint(site.label, site.x, site.y);
    }

    private static MissionSectionHint reserveSectionHint(GameContext ctx) {
        if (ctx == null) return null;
        double[] point = CampaignSystem.reserveSectionPoint(ctx);
        String label = CampaignSystem.reserveSectionLabel(ctx);
        if (point == null || label == null || label.isBlank()) return null;
        return new MissionSectionHint(label, point[0], point[1]);
    }

    private static void pushIntelPing(GameContext ctx, double x, double y, int factionCode) {
        if (ctx == null || ctx.ui == null) return;
        ctx.ui.mapPings.add(new Renderer.MapPing(x, y, 6.0, factionCode));
        while (ctx.ui.mapPings.size() > 14) {
            ctx.ui.mapPings.remove(0);
        }
    }

    private static int teamCodeFor(Ship target) {
        if (target == null || target.faction == null) return 0;
        return switch (target.faction.teamId()) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            default -> 0;
        };
    }

    private static CommOutcome outcome(String response) {
        return new CommOutcome(response, null);
    }

    private static CommOutcome outcome(String response, String banner) {
        return new CommOutcome(response, banner);
    }

    private record CommOutcome(String response, String banner) {}
    private record DiscoveryIntelHint(String label, double x, double y) {}
    private record MissionSectionHint(String label, double x, double y) {}
    private record ObjectiveIntelHint(String label, String subtitle, double x, double y,
                                      String banner, java.awt.Color color) {}
    private record IntelSalePackage(String label, String subtitle, double x, double y, java.awt.Color color) {}
}
