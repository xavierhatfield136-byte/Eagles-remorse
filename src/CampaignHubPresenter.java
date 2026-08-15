import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CampaignHubPresenter {
    private CampaignHubPresenter() {}

    static String selectedHubAlignmentLabel(GameContext ctx) {
        CampaignSystem.CampaignLocation location = CampaignSystem.selectedCampaignLocation(ctx);
        CampaignSystem.HubProfile profile = CampaignSystem.hubProfile(ctx, location);
        return switch (profile.alignment) {
            case GREEN -> "Green Military Hub";
            case YELLOW -> "Yellow Trade Hub";
            case FRONTIER -> "Frontier Support Hub";
        };
    }

    static List<String> selectedHubIdentityLines(GameContext ctx) {
        CampaignSystem.CampaignLocation location = CampaignSystem.selectedCampaignLocation(ctx);
        if (location == null || location.services.isEmpty()) return List.of();
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        CampaignSystem.HubProfile profile = CampaignSystem.hubProfile(ctx, location);
        ArrayList<String> out = new ArrayList<>();
        out.add("Hub Identity: " + selectedHubAlignmentLabel(ctx));
        out.add("Local Danger: " + CampaignSystem.regionalPressureLabel(profile.regionPressure));
        out.add("Service Quality: " + CampaignSystem.routeTempoLabel(
                new CampaignSystem.GalaxyRouteAssessment(0, 0, 0, 0, 0, 0, 0, 150 + profile.quality * 120, 0)));
        if (profile.alignment == CampaignSystem.HubAlignment.GREEN) {
            out.add("Strengths: repair, refit, military escort contracts, intel");
        } else if (profile.alignment == CampaignSystem.HubAlignment.YELLOW) {
            out.add("Strengths: ore trade, cargo economy, recovery crews, industrial throughput");
        } else {
            out.add("Strengths: mixed frontier support under tighter stock and harsher prices");
        }
        out.add("Station State: " + CampaignSystem.stationDamageReadout(location)
                + "  |  services " + CampaignSystem.trimmedOrFallback(location.stationServiceState, "online"));
        out.addAll(CampaignSystem.stationMemoryLines(location, 3));
        CampaignSystem.CampaignForce localSupport = CampaignSystem.nearestCampaignForceForLocation(st, location);
        if (localSupport != null) {
            out.add("Local Forces: " + localSupport.name + "  |  " + CampaignSystem.campaignForceIntentLabel(localSupport.intent)
                    + "  |  readiness " + (int) Math.round(localSupport.readiness));
        }
        CampaignSystem.GalaxySearchGroup hostileThreat = CampaignSystem.trackedHostileThreatForInstallation(ctx, st, location);
        if (hostileThreat != null) {
            out.add("Threat Alert: " + CampaignSystem.contactConfidenceLabel(hostileThreat) + " "
                    + CampaignSystem.trimmedOrFallback(hostileThreat.label, "Hostile patrol")
                    + " near the local defense ring");
            out.add("Hostile Provenance: " + CampaignSystem.hostileThreatOriginLabel(st, hostileThreat));
        } else {
            CampaignSystem.CampaignInstallationThreatCase scriptedThreat =
                    CampaignSystem.scriptedInstallationThreatForLocation(st, location);
            if (scriptedThreat != null) {
                out.add("Threat Alert: Scripted infiltration  " + scriptedThreat.forceName);
                out.add("Hostile Provenance: " + scriptedThreat.origin);
            }
        }
        return out;
    }

    static List<String> hubServicePreviewLines(
            GameContext ctx,
            CampaignSystem.CampaignLocation location,
            CampaignSystem.HubService service) {
        if (location == null || service == null) return List.of("No service selected.");
        CampaignSystem.HubProfile profile = CampaignSystem.hubProfile(ctx, location);
        ArrayList<String> lines = new ArrayList<>();
        lines.add(location.name);
        lines.add(location.detail);
        lines.add(CampaignSystem.selectedHubAlignmentLabelForProfile(profile));
        lines.add("Station State: " + CampaignSystem.stationDamageReadout(location)
                + "  |  " + CampaignSystem.stationMemoryPriceReadout(location, service));
        lines.addAll(CampaignSystem.stationMemoryLines(location, 2));
        lines.add("Station Channel: " + stationConversationPrompt(service));
        CampaignHubServiceSystem.HubServiceQuote quote = CampaignHubServiceSystem.quote(ctx, location, service);
        switch (service) {
            case REPAIR -> {
                lines.add("Request Support");
                lines.add("Station support restores persistent fleet condition and flagship readiness.");
                lines.add("Support may include dock crews, escort coordination, and emergency reinforcements.");
                lines.add("Hub Capability: " + CampaignSystem.installationQuality(profile).name().replace('_', ' ')
                        + "  |  support x" + String.format(Locale.US, "%.2f", profile.supportMul)
                        + "  |  local repair stock " + Math.max(0, location.repairSupplyStockpile));
                lines.add("Recovery Limit: one visit is partial; repeated repairs and ore investment erase heavy attrition.");
                int oreCost = Math.max(15, quote.salvageCost * 25 + quote.supplyCost * 5);
                lines.add("Cost: " + quote.creditCost + " credits  |  " + oreCost + " ore");
            }
            case TRADE -> {
                lines.add("Request Trade");
                lines.add("Market exchange can buy stores or sell salvage/ore for credits.");
                lines.add("Selected ore sale: " + quote.selectedOre + " / " + quote.availableOre
                        + " ore  |  Payout: " + quote.payoutCredits + " credits");
                lines.add("Market Bias: " + (profile.alignment == CampaignSystem.HubAlignment.YELLOW
                        ? "Yellow commerce premium" : "standard frontier exchange"));
            }
            case SHIPYARD -> {
                ShipRole role = quote.role;
                FleetBuildingSystem.HullProfile hull = FleetBuildingSystem.hullProfile(role);
                lines.add("Purchase Ships");
                lines.add("Station hulls are not for sale through campaign shipyards.");
                lines.add("Current Yard Offer: " + role.name());
                lines.add("Seller: " + CampaignSystem.factionBoardName(location.ownerFaction)
                        + "  |  delivered hull keeps seller doctrine and skin.");
                lines.add("Fleet Ore pays player purchases; Yard Ore feeds local faction construction.");
                lines.add("Fleet Ore: " + CampaignSystem.currentCampaignOre(ctx) + "  |  Yard Ore: " + Math.max(0, location.oreStockpile)
                        + "  |  Required Fleet Ore: " + quote.oreCost);
                lines.add("Role: " + hull.battlefieldRole + "  |  Counter: " + hull.counter + "  |  Weakness: " + hull.weakness);
                lines.add("Maintenance: " + hull.budgets.maintenance + "  |  Variant: " + hull.factionVariant);
                lines.add("Silhouette: " + hull.silhouetteCheck);
                lines.add("Cost: " + quote.creditCost + " credits  |  " + quote.oreCost
                        + " Fleet Ore");
            }
            case SUPPLY -> {
                lines.add("Request Replenishment");
                lines.add("Requests delivered fuel, supplies, ammo, and repair stores for the fleet.");
                lines.add("Replenishment Focus: " + (profile.alignment == CampaignSystem.HubAlignment.GREEN
                        ? "military readiness stock" : "civilian industrial cargo"));
            }
            case STRIKE_REARM -> {
                lines.add("Service Strikes");
                lines.add("Rearms torpedoes, readies carrier sorties, and reduces atomic cooldown when active.");
                lines.add("Cost: " + quote.creditCost + " credits  |  " + quote.oreCost + " ore");
            }
            case FUEL -> {
                lines.add("Ore Delivery");
                lines.add("Buy a delivered ore lot from local logistics traffic.");
                lines.add("Ore yield scales with local logistics throughput.");
            }
            case SALVAGE -> {
                lines.add("Sell Ore Lot");
                lines.add("Converts a chunk of fleet ore into credits.");
                lines.add("Fleet ore: " + CampaignSystem.currentCampaignOre(ctx));
            }
            case INTEL -> {
                lines.add("Intel Exchange");
                lines.add("Reveals local hostile contacts and reduces enemy alert.");
                lines.add("Current intel quality: " + CampaignSystem.campaignIntelReadout(ctx));
                lines.add("Green hubs identify more contacts; other hubs mostly confirm threats.");
            }
            case CONTRACTS -> {
                lines.add("Bounty / Job Board");
                lines.add("Creates mission leads, pays a meaningful advance, and points the fleet toward exploration.");
                lines.add("Board entries can reveal hidden objectives and regional opportunities.");
            }
            case REFIT -> {
                lines.add("Refit Docket");
                lines.add("Queues timed refit work against the currently focused persistent hull.");
                lines.add("Green hubs favor warship readiness. Yellow hubs favor logistics hulls and carriers.");
                lines.addAll(CampaignSystem.campaignFleetRefitScreenLines(ctx));
            }
        }
        return lines;
    }

    static String hubServiceActionLabel(GameContext ctx,
                                        CampaignSystem.CampaignLocation location,
                                        CampaignSystem.HubService service) {
        if (service == null) return "SERVICE";
        return switch (service) {
            case REPAIR -> "REQUEST SUPPORT";
            case TRADE -> "REQUEST TRADE";
            case SUPPLY -> "REQUEST REPLENISHMENT";
            default -> service.label.toUpperCase(Locale.US);
        };
    }

    static String hubServiceActionDetail(GameContext ctx,
                                         CampaignSystem.CampaignLocation location,
                                         CampaignSystem.HubService service) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (ctx == null || st == null || location == null || service == null) return "";
        if (!CampaignSystem.stationServiceAvailableFor(location, service)) return "OFFLINE";
        if (!CampaignSystem.isDockedAtSelectedLocation(ctx)) return "APPROACH";
        CampaignSystem.HubProfile profile = CampaignSystem.hubProfile(ctx, location);
        double priceMul = CampaignSystem.hubServicePriceMultiplier(profile, location, service);
        return switch (service) {
            case REPAIR -> "C " + GameContext.scaleCreditEarnings((int) Math.round((80 + CampaignSystem.damagedPersistentFleetCount(ctx, st) * 28) * priceMul));
            case TRADE -> {
                int amount = CampaignSystem.campaignOreSaleAmount(ctx);
                int payout = CampaignSystem.campaignOreSaleCredits(ctx, location, amount);
                yield amount <= 0 ? "NO ORE" : ("SELL " + amount + " ORE  +" + payout + "C");
            }
            case REFIT -> "C " + GameContext.scaleCreditEarnings((int) Math.round(110 * priceMul));
            case SHIPYARD -> "BUILD READY";
            case SUPPLY -> "C " + GameContext.scaleCreditEarnings((int) Math.round(90 * priceMul));
            case STRIKE_REARM -> "C " + CampaignSystem.strikeRearmCreditCost(profile) + " O " + CampaignSystem.strikeRearmOreCost(profile);
            case INTEL -> "C " + GameContext.scaleCreditEarnings((int) Math.round(70 * priceMul));
            case CONTRACTS -> "TAKE ADVANCE";
            case SALVAGE -> "PAYOUT";
            case FUEL -> "C " + GameContext.scaleCreditEarnings((int) Math.round(70 * priceMul));
        };
    }

    static List<String> yardDocketLines(GameContext ctx, int maxCount) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (st == null) return List.of("YARD DOCKET OFFLINE");
        ArrayList<String> out = new ArrayList<>();
        out.add("YARD DOCKET  |  ACTIVE " + st.campaignYardOrders.size());
        for (CampaignSystem.CampaignYardOrder order : st.campaignYardOrders) {
            if (order == null) continue;
            int progress = (int) Math.round(100.0 * (1.0 - order.remainingSeconds / Math.max(1.0, order.totalSeconds)));
            out.add("#" + order.id + " " + order.kind + "  |  " + order.role
                    + "  |  " + order.lane
                    + "  |  " + CampaignSystem.factionBoardName(order.producingFaction)
                    + "  |  " + order.templateName
                    + "  |  " + MathUtil.clamp(progress, 0, 100) + "%"
                    + "  |  ETA " + (int) Math.ceil(order.remainingSeconds) + "s"
                    + "  |  " + order.sourceLabel
                    + yardOrderPauseSuffix(st, order));
            if (out.size() >= Math.max(1, maxCount)) break;
        }
        if (out.size() == 1) out.add("No construction or refit work currently queued.");
        return out;
    }

    static List<String> fleetRefitScreenLines(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        CampaignSystem.PersistentFleetEntry focused = CampaignSystem.campaignFleetFocusEntry(ctx);
        ShipRole role = (focused == null || focused.role == null) ? ShipRole.FRIGATE : focused.role;
        FleetBuildingSystem.HullProfile hull = FleetBuildingSystem.hullProfile(role);
        FleetBuildingSystem.RefitTemplate template = FleetBuildingSystem.standardLoadouts().get(
                Math.floorMod((focused == null) ? 0 : focused.slotId - 1, FleetBuildingSystem.standardLoadouts().size()));
        FleetBuildingSystem.RefitAssessment assessment =
                FleetBuildingSystem.assessRefit(role, template, false, "Blue", "Green");
        ArrayList<String> out = new ArrayList<>();
        out.add("REFIT SCREEN  |  " + role + "  |  TEMPLATE " + template.name);
        out.add("SLOT BUDGETS  |  W" + hull.budgets.weight + " P" + hull.budgets.power
                + " H" + hull.budgets.heat + " C" + hull.budgets.crew + " M" + hull.budgets.maintenance);
        for (FleetBuildingSystem.RefitModule module : template.modules) {
            out.add("MODULE  |  " + module.slot + " " + module.id
                    + "  |  " + module.rarity
                    + "  |  source " + module.industrialSource
                    + (module.capturedTech ? "  |  CAPTURED TECH" : ""));
            out.add("DECISION  |  " + FleetBuildingSystem.damagedModuleDecision(module, 62, true));
        }
        out.add("ASSESSMENT  |  " + (assessment.valid ? "COMPATIBLE" : "BLOCKED")
                + "  |  reliability " + (int) Math.round(assessment.fieldReliability * 100.0) + "%"
                + "  |  " + assessment.refitDays + " work days");
        for (String warning : assessment.warnings) out.add("WARNING  |  " + warning);
        out.add("SAVED LOADOUTS  |  " + FleetBuildingSystem.standardLoadouts().stream()
                .map(saved -> saved.name)
                .collect(java.util.stream.Collectors.joining(", ")));
        if (st != null) out.addAll(yardDocketLines(ctx, 3));
        return out;
    }

    private static String yardOrderPauseSuffix(CampaignSystem.CampaignState st, CampaignSystem.CampaignYardOrder order) {
        CampaignSystem.CampaignLocation yard = CampaignSystem.campaignLocationById(st, order == null ? "" : order.sourceLocationId);
        if (yard == null || yard.destroyed || "destroyed".equalsIgnoreCase(yard.stationDamageState)) {
            return "  |  CANCELED: YARD DESTROYED";
        }
        if (yard.ownerFaction != null && yard.ownerFaction != order.producingFaction) {
            return "  |  PAUSED: YARD CAPTURED BY " + CampaignSystem.factionBoardName(yard.ownerFaction).toUpperCase(Locale.US);
        }
        if ("offline".equalsIgnoreCase(yard.stationServiceState)) return "  |  PAUSED: SERVICES OFFLINE";
        if (CampaignStationMemorySystem.hasMemory(yard, "blockaded")
                || CampaignStationMemorySystem.hasMemory(yard, "under blockade")) {
            return "  |  PAUSED: BLOCKADE";
        }
        if ("damaged".equalsIgnoreCase(yard.stationDamageState)
                || "degraded".equalsIgnoreCase(yard.stationServiceState)) {
            return "  |  DAMAGED: 50% THROUGHPUT";
        }
        return "";
    }

    private static String stationConversationPrompt(CampaignSystem.HubService service) {
        if (service == null) return "Open station channel";
        return switch (service) {
            case REPAIR, REFIT -> "Request support";
            case TRADE, SALVAGE, FUEL -> "Request trade";
            case SUPPLY -> "Request replenishment";
            case STRIKE_REARM -> "Service strike cooldowns";
            case CONTRACTS, INTEL -> "Bounty / job board";
            case SHIPYARD -> "Purchase ships";
        };
    }
}
