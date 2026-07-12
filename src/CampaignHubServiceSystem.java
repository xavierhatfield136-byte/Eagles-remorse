import java.util.Locale;

final class CampaignHubServiceSystem {
    private CampaignHubServiceSystem() {}

    static final class HubServiceQuote {
        final CampaignSystem.HubService service;
        final CampaignSystem.HubProfile profile;
        final ShipRole role;
        final int creditCost;
        final int oreCost;
        final int salvageCost;
        final int supplyCost;
        final int payoutCredits;
        final int selectedOre;
        final int availableOre;
        final int quantity;

        HubServiceQuote(CampaignSystem.HubService service,
                        CampaignSystem.HubProfile profile,
                        ShipRole role,
                        int creditCost,
                        int oreCost,
                        int salvageCost,
                        int supplyCost,
                        int payoutCredits,
                        int selectedOre,
                        int availableOre,
                        int quantity) {
            this.service = service;
            this.profile = profile;
            this.role = role;
            this.creditCost = creditCost;
            this.oreCost = oreCost;
            this.salvageCost = salvageCost;
            this.supplyCost = supplyCost;
            this.payoutCredits = payoutCredits;
            this.selectedOre = selectedOre;
            this.availableOre = availableOre;
            this.quantity = quantity;
        }
    }

    static HubServiceQuote quote(GameContext ctx,
                                 CampaignSystem.CampaignLocation location,
                                 CampaignSystem.HubService service) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        CampaignSystem.HubProfile profile = CampaignSystem.hubProfile(ctx, location);
        double priceMul = CampaignSystem.hubServicePriceMultiplier(profile, location, service);
        ShipRole role = null;
        int creditCost = 0;
        int oreCost = 0;
        int salvageCost = 0;
        int supplyCost = 0;
        int payoutCredits = 0;
        int selectedOre = CampaignSystem.campaignOreSaleAmount(ctx);
        int availableOre = CampaignSystem.currentCampaignOre(ctx);
        int quantity = 0;
        if (service != null) {
            switch (service) {
                case REPAIR -> {
                    int damagedShips = CampaignSystem.damagedPersistentFleetCount(ctx, st);
                    creditCost = GameContext.scaleCreditEarnings((int) Math.round((95 + damagedShips * 36) * priceMul));
                    salvageCost = Math.max(0, (int) Math.round(Math.max(0, damagedShips) * 3 / profile.supportMul));
                    supplyCost = Math.max(2, (int) Math.round((6 + damagedShips * 3) / profile.supportMul));
                    quantity = damagedShips;
                }
                case TRADE -> {
                    payoutCredits = CampaignSystem.campaignOreSaleCredits(ctx, location, selectedOre);
                    if (selectedOre <= 0 || availableOre <= 0) {
                        creditCost = GameContext.scaleCreditEarnings((int) Math.round(140 * priceMul));
                        salvageCost = Math.max(2, (int) Math.round(4 / profile.logisticsMul));
                    }
                }
                case SHIPYARD -> {
                    role = CampaignSystem.shipyardOfferRole(location, profile);
                    creditCost = GameContext.scaleCreditEarnings((int) Math.round(CampaignSystem.shipyardOfferCreditCost(role) * priceMul));
                    oreCost = CampaignSystem.campaignHubShipyardOreCost(role, creditCost, profile);
                    salvageCost = CampaignSystem.campaignHubShipyardSalvageCost(role, profile);
                }
                case SUPPLY -> creditCost = GameContext.scaleCreditEarnings((int) Math.round(90 * priceMul));
                case STRIKE_REARM -> {
                    creditCost = CampaignSystem.strikeRearmCreditCost(profile);
                    oreCost = CampaignSystem.strikeRearmOreCost(profile);
                    supplyCost = Math.max(3, (int) Math.round(5 / profile.supportMul));
                }
                case REFIT -> {
                    creditCost = GameContext.scaleCreditEarnings((int) Math.round(110 * priceMul));
                    salvageCost = Math.max(1, (int) Math.round(3 / profile.supportMul));
                }
                case INTEL -> creditCost = GameContext.scaleCreditEarnings((int) Math.round(70 * priceMul));
                case CONTRACTS -> {
                    payoutCredits = GameContext.scaleCreditReward((int) Math.round(
                            (profile.alignment == CampaignSystem.HubAlignment.GREEN ? 135 : 105) * profile.quality));
                    quantity = profile.alignment == CampaignSystem.HubAlignment.GREEN ? 6 : 3;
                }
                case SALVAGE -> {
                    quantity = st == null ? 0 : Math.min(st.campaignSalvage, 16);
                    payoutCredits = GameContext.scaleCreditReward((int) Math.round(quantity * 11 * profile.tradeMul));
                }
                case FUEL -> creditCost = GameContext.scaleCreditEarnings((int) Math.round(70 * priceMul));
            }
        }
        return new HubServiceQuote(service, profile, role, creditCost, oreCost, salvageCost, supplyCost,
                payoutCredits, selectedOre, availableOre, quantity);
    }

    static boolean stationServiceAvailableFor(CampaignSystem.CampaignLocation location, CampaignSystem.HubService service) {
        if (location == null || service == null) return false;
        if (location.destroyed || "destroyed".equalsIgnoreCase(location.stationDamageState)) return false;
        if (location.ownerFaction == Faction.ENEMY || "restricted".equalsIgnoreCase(location.stationServiceState)) return false;
        if ("offline".equalsIgnoreCase(location.stationServiceState)) {
            return service == CampaignSystem.HubService.INTEL || service == CampaignSystem.HubService.CONTRACTS;
        }
        return true;
    }

    static String stationServiceUnavailableReason(CampaignSystem.CampaignLocation location, CampaignSystem.HubService service) {
        String name = location == null ? "station" : CampaignSystem.trimmedOrFallback(location.name, "station");
        if (location == null || service == null) return "station service unavailable";
        if (location.destroyed || "destroyed".equalsIgnoreCase(location.stationDamageState)) {
            return name + " is destroyed and cannot provide " + service.label;
        }
        if (location.ownerFaction == Faction.ENEMY || "restricted".equalsIgnoreCase(location.stationServiceState)) {
            return name + " is under hostile occupation";
        }
        if ("offline".equalsIgnoreCase(location.stationServiceState)) {
            return name + " services are offline after station damage";
        }
        return name + " cannot provide " + service.label + " right now";
    }

    static boolean openSelectedHubService(GameContext ctx, CampaignSystem.HubService service) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (ctx == null || st == null || ctx.ui == null || service == null) return false;
        CampaignSystem.CampaignLocation location = CampaignSystem.selectedCampaignLocation(ctx);
        if (location == null || !location.services.contains(service)) return false;
        if (!CampaignSystem.isDockedAtSelectedLocation(ctx)) {
            EventSystem.showBanner(ctx, "MOVE INTO DOCKING RANGE BEFORE USING HUB SERVICES", 1.3);
            return false;
        }
        ctx.ui.showCampaignHubMenu(location.id, service.name());
        EventSystem.showBanner(ctx, service.label.toUpperCase(Locale.US) + " - " + location.name.toUpperCase(Locale.US), 1.0);
        return true;
    }

    static boolean executeSelectedHubService(GameContext ctx, String serviceId) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (ctx == null || st == null || serviceId == null || serviceId.isBlank()) return false;
        CampaignSystem.HubService service = CampaignSystem.hubServiceById(serviceId);
        CampaignSystem.CampaignLocation location = CampaignSystem.selectedCampaignLocation(ctx);
        if (service == null || location == null || !location.services.contains(service)) return false;
        if (!CampaignSystem.isDockedAtSelectedLocation(ctx)) {
            EventSystem.showBanner(ctx, "MOVE INTO DOCKING RANGE BEFORE USING HUB SERVICES", 1.3);
            return false;
        }
        return CampaignSystem.performHubService(ctx, st, location, service);
    }

    static void closeHubServiceMenu(GameContext ctx) {
        if (ctx == null || ctx.ui == null) return;
        ctx.ui.clearCampaignHubMenu();
    }

    static boolean confirmSelectedHubService(GameContext ctx) {
        CampaignSystem.CampaignState st = CampaignSystem.state(ctx);
        if (ctx == null || st == null || ctx.ui == null || !ctx.ui.campaignHubMenu.active) return false;
        CampaignSystem.CampaignLocation location = CampaignSystem.campaignLocationById(st, ctx.ui.campaignHubMenu.locationId);
        CampaignSystem.HubService service = CampaignSystem.hubServiceById(ctx.ui.campaignHubMenu.serviceId);
        if (location == null || service == null) {
            ctx.ui.clearCampaignHubMenu();
            return false;
        }
        boolean result = CampaignSystem.performHubService(ctx, st, location, service);
        ctx.ui.clearCampaignHubMenu();
        return result;
    }
}
