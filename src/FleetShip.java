import java.util.Locale;

/**
 * A configurable ship used for allies and enemies.
 *
 * Roles:
 * - Small craft: FIGHTER / BOMBER / PD_CRAFT / DRONE
 * - Medium ships: FRIGATE / MISSILE_BOAT / CIWS_CORVETTE / CRUISER
 * - Large ships: BATTLESHIP / DREADNOUGHT / SUPERSHIP / CARRIER / DRONE_CARRIER / TRANSPORT
 * - Structures: BASE / STATIC_TURRET
 *
 * Notes:
 * - CIWS mount counts are standardized by hangar tier when a hull has CIWS.
 */
public class FleetShip extends Ship {
    public FleetShip(ShipRole role, Faction faction, double x, double y) {
        this.role = role;
        this.faction = faction;
        this.x = x;
        this.y = y;
        resetFlightDeckLoadout();

        setup(role);
        conformTurretsToHull();
        standardizeCiwsLoadout();
        nerfGeneralistCiwsLoadout();
        finalizeCapitalLethalityProfile();
        enforceSuperweaponChargeSfxTiming();
        assignDefaultMissileRoles();
        resetFlightDeckLoadout();
        applyCustomFlightDeckLoadout();
        applyStrikeCraftDurabilityAndDamageRebalance();
        applyCarrierWingCapacityRebalance();
    }

    public FleetBuildingSystem.HullProfile hullProfile() {
        return FleetBuildingSystem.hullProfile(role);
    }

    public String battlefieldIdentityCard() {
        return FleetBuildingSystem.battleCard(role);
    }

    private void enforceSuperweaponChargeSfxTiming() {
        if (!hasSuperweapon) return;
        superweaponChargeTime = SUPERWEAPON_CHARGE_SFX_SECONDS;
    }

    private void applyStrikeCraftDurabilityAndDamageRebalance() {
        if (role != ShipRole.FIGHTER && role != ShipRole.BOMBER && role != ShipRole.DRONE) return;
        hpMax = Math.max(1, hpMax * 2);
        hp = hpMax;
        if (shieldMax > 0.0) {
            shieldMax *= 2.0;
            shield = shieldMax;
        }
        for (Turret turret : turrets) {
            if (turret == null) continue;
            turret.damage = Math.max(1, turret.damage * 2);
        }
    }

    private void applyCarrierWingCapacityRebalance() {
        if (!isCarrier || maxFighters <= 0) return;
        maxFighters = Math.max(1, (int) Math.ceil(maxFighters * 0.5));
    }

    private void assignDefaultMissileRoles() {
        if (turrets == null || turrets.isEmpty()) return;
        int missileCount = 0;
        for (Turret t : turrets) {
            if (t != null && t.kind == Turret.Kind.MISSILE) missileCount++;
        }
        if (missileCount <= 0) return;

        int missileIdx = 0;
        for (Turret t : turrets) {
            if (t == null || t.kind != Turret.Kind.MISSILE) continue;
            t.missileRole = defaultMissileRoleForSlot(role, faction, missileIdx, missileCount);
            missileIdx++;
        }
    }

    private static Turret.MissileRole defaultMissileRoleForSlot(ShipRole role, Faction faction, int missileIdx, int missileCount) {
        if (role == null) return Turret.MissileRole.ANTI_MEDIUM;
        if (faction == Faction.TEAM_C) {
            // Green reads best as photon/torpedo salvos: bias to heavier missiles by default.
            if (missileCount >= 2 && missileIdx == 0) return Turret.MissileRole.ANTI_MEDIUM;
            return Turret.MissileRole.ANTI_HEAVY;
        }
        if (role == ShipRole.CARRIER || role == ShipRole.DRONE_CARRIER) {
            return Turret.MissileRole.ANTI_LIGHT;
        }
        if (role == ShipRole.CIWS_CORVETTE || role == ShipRole.PD_CRAFT) {
            return Turret.MissileRole.INTERCEPT;
        }
        if (role == ShipRole.MISSILE_BOAT
                || role == ShipRole.LIGHT_CRUISER
                || role == ShipRole.MEDIUM_CRUISER
                || role == ShipRole.CRUISER
                || role == ShipRole.ARTILLERY_SHIP
                || role == ShipRole.STEALTH_SHIP
                || role == ShipRole.BATTLECRUISER
                || role == ShipRole.BATTLESHIP
                || role == ShipRole.DREADNOUGHT
                || role == ShipRole.SUPERSHIP
                || role.isTitanOrMothership()) {
            return Turret.MissileRole.ANTI_HEAVY;
        }
        return Turret.MissileRole.ANTI_MEDIUM;
    }

    private void standardizeCiwsLoadout() {
        if (!hasCIWS) return;
        if (role == ShipRole.BASE || role == ShipRole.STATIC_TURRET) return;
        ciwsQuality = 1.0;
        ciwsPelletsPerBurst = dedicatedPointDefenseRole(role)
                ? Math.max(ciwsPelletsPerBurst, switch (SpawnSystem.requiredHangarTierForRole(role)) {
                    case 0 -> 2;
                    case 1 -> 3;
                    case 2 -> 5;
                    default -> 8;
                })
                : 1;
    }

    private static boolean dedicatedPointDefenseRole(ShipRole role) {
        return role == ShipRole.PD_CRAFT || role == ShipRole.CIWS_CORVETTE;
    }

    private void nerfGeneralistCiwsLoadout() {
        if (!hasCIWS) return;
        if (role == ShipRole.BASE || role == ShipRole.STATIC_TURRET) return;
        if (dedicatedPointDefenseRole(role)) return;

        double rangeClamp = switch (role) {
            case PATROL, PICKET -> 205.0;
            case FRIGATE, MISSILE_BOAT, STEALTH_SHIP -> 190.0;
            case FIGHTER, BOMBER, DRONE, ARTILLERY_SHIP -> 165.0;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, BATTLECRUISER -> 180.0;
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP -> 170.0;
            case CARRIER, DRONE_CARRIER, MINER, HAULER, TRANSPORT -> 175.0;
            default -> role != null && role.isTitanOrMothership() ? 185.0 : 180.0;
        };
        double cooldownFloor = switch (role) {
            case PATROL, PICKET -> 0.18;
            case FRIGATE, MISSILE_BOAT, STEALTH_SHIP -> 0.20;
            case FIGHTER, BOMBER, DRONE, ARTILLERY_SHIP -> 0.22;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, BATTLECRUISER -> 0.22;
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP -> 0.24;
            case CARRIER, DRONE_CARRIER, MINER, HAULER, TRANSPORT -> 0.23;
            default -> role != null && role.isTitanOrMothership() ? 0.26 : 0.22;
        };
        double qualityClamp = switch (role) {
            case PATROL, PICKET -> 0.24;
            case FRIGATE, MISSILE_BOAT, STEALTH_SHIP -> 0.16;
            case FIGHTER, BOMBER, DRONE, ARTILLERY_SHIP -> 0.10;
            case LIGHT_CRUISER, MEDIUM_CRUISER, CRUISER, BATTLECRUISER -> 0.14;
            case BATTLESHIP, DREADNOUGHT, SUPERSHIP -> 0.10;
            case CARRIER, DRONE_CARRIER, MINER, HAULER, TRANSPORT -> 0.12;
            default -> role != null && role.isTitanOrMothership() ? 0.12 : 0.14;
        };

        ciwsQuality = Math.min(ciwsQuality, qualityClamp);
        ciwsRange = Math.min(ciwsRange, rangeClamp);
        ciwsCooldown = Math.max(ciwsCooldown, cooldownFloor);
        ciwsPelletsPerBurst = 1;
        ciwsPelletDamage = 1;
        ciwsPelletLife = Math.min(ciwsPelletLife, role != null && role.isTitanOrMothership() ? 14 : 16);
        ciwsPelletSpeed = Math.min(ciwsPelletSpeed, role != null && role.isTitanOrMothership() ? 860.0 : 900.0);
        ciwsPelletRadius = Math.min(ciwsPelletRadius, 1.8);
    }

    private void finalizeCapitalLethalityProfile() {
        if (!role.isTitanOrMothership()) return;

        double gunCooldownMul = switch (role) {
            case ARTILLERY_TITAN -> 0.80;
            case VANGUARD_TITAN -> 0.84;
            case HYPERWEAPON_TITAN -> 1.08;
            case ELITE_SUPERSHIP_COMMAND_TITAN, ELITE_REINFORCEMENTS_TITAN, MOTHERSHIP -> 0.90;
            default -> 0.92;
        };
        double missileCooldownMul = switch (role) {
            case INTERDICTION_TITAN, FLEET_TELEPORTER_TITAN -> 0.84;
            case VANGUARD_TITAN -> 0.80;
            case HYPERWEAPON_TITAN -> 1.14;
            case MOTHERSHIP -> 0.88;
            default -> 0.90;
        };
        int gunDamageBonus = switch (role) {
            case ARTILLERY_TITAN, VANGUARD_TITAN, MOTHERSHIP -> 2;
            case HYPERWEAPON_TITAN -> 0;
            default -> 1;
        };
        int missileDamageBonus = switch (role) {
            case VANGUARD_TITAN, ARTILLERY_TITAN -> 3;
            case MOTHERSHIP -> 3;
            case HYPERWEAPON_TITAN -> 0;
            default -> 2;
        };

        for (Turret turret : turrets) {
            if (turret == null) continue;
            if (turret.kind == Turret.Kind.GUN) {
                turret.cooldown = Math.max(0.10, turret.cooldown * gunCooldownMul);
                turret.damage = Math.max(1, turret.damage + gunDamageBonus);
                turret.bulletSpeed = Math.max(720.0, turret.bulletSpeed * 1.06);
                turret.bulletLife += (role == ShipRole.ARTILLERY_TITAN) ? 44 : 18;
            } else if (turret.kind == Turret.Kind.MISSILE) {
                turret.cooldown = Math.max(0.60, turret.cooldown * missileCooldownMul);
                turret.damage = Math.max(2, turret.damage + missileDamageBonus);
                turret.missileSpeed = Math.max(260.0, turret.missileSpeed * 1.08);
                turret.missileLife += 24;
                turret.missileTurnRate *= 1.06;
            }
        }

        if (hasCIWS) {
            ciwsRange += role.isMothership() ? 36.0 : 18.0;
            ciwsCooldown = Math.max(0.055, ciwsCooldown * 0.94);
            ciwsPelletDamage = Math.max(ciwsPelletDamage, role.isMothership() ? 2 : 1);
        }

        switch (role) {
            case TRANSPORT_TITAN -> {
                cargoMax = Math.max(cargoMax, 1400);
                shieldRegen += 0.30;
            }
            case BULWARK_TITAN -> {
                shieldMax += 26.0;
                shield = shieldMax;
            }
            case CARRIER_SUPPORT_TITAN -> {
                maxFighters = Math.max(maxFighters, 24);
                ciwsRange += 18.0;
            }
            case VANGUARD_TITAN -> desiredSpeed += 22.0;
            case COMMAND_INTEL_TITAN -> ciwsRange += 12.0;
            case ARTILLERY_TITAN -> {
                superweaponChargeTime = Math.max(2.4, superweaponChargeTime * 0.82);
                superweaponCooldown = Math.max(13.5, superweaponCooldown * 0.78);
                superweaponDamage += 46;
                superweaponRadius += 6.0;
                superweaponMaxHits += 8;
            }
            case SHIELD_BASTION_TITAN -> {
                shieldMax += 34.0;
                shield = shieldMax;
                shieldRegen += 0.65;
            }
            case FLEET_TELEPORTER_TITAN -> desiredSpeed += 8.0;
            case MOBILE_STATION_TITAN -> maxFighters = Math.max(maxFighters, 14);
            case HYPERWEAPON_TITAN -> {
                desiredSpeed = Math.max(30.0, desiredSpeed - 6.0);
                ciwsRange = Math.max(260.0, ciwsRange - 44.0);
                superweaponChargeTime = Math.max(3.0, superweaponChargeTime * 0.88);
                superweaponCooldown = Math.max(18.0, superweaponCooldown * 0.90);
                superweaponDamage += 10;
                superweaponBeamDamageScale += 0.10;
            }
            case MOTHERSHIP -> {
                maxFighters = Math.max(maxFighters, 20);
                shieldMax += 48.0;
                shield = shieldMax;
                shieldRegen += 0.45;
            }
            default -> {
            }
        }
    }

    private void setup(ShipRole role) {
        radius = RoleStats.get(role).radius;
        switch (role) {

            // -----------------------
            // Scout / escort line
            // -----------------------
            case PATROL -> {
                name = (faction == Faction.ENEMY ? "Enemy Patrol" : "Patrol");
                radius = 14;
                hpMax = 10;
                hp = hpMax;

                shieldMax = 8;
                shield = shieldMax;
                shieldRegen = 1.2;
                shieldActive = true;

                desiredSpeed = 200;
                bountyValue = 70;

                Turret gun = new Turret(Turret.Kind.GUN, 11, 0);
                gun.cooldown = 0.10;
                gun.damage = 1;
                gun.bulletSpeed = 860;
                gun.bulletLife = 100;
                gun.primary = true;
                gun.radius = 5.5;
                gun.barrelLen = 14;
                addTurret(gun);

                // light CIWS
                hasCIWS = true;
                ciwsQuality = 0.30;
                ciwsRange = 210;
                ciwsCooldown = 0.12;
                ciwsPelletsPerBurst = 2;
            }

            case PICKET -> {
                name = (faction == Faction.ENEMY ? "Enemy Picket" : "Picket");
                radius = 16;
                hpMax = 14;
                hp = hpMax;

                shieldMax = 12;
                shield = shieldMax;
                shieldRegen = 1.5;
                shieldActive = true;

                desiredSpeed = 175;
                bountyValue = 90;

                Turret longGun = new Turret(Turret.Kind.GUN, 13, 0);
                longGun.cooldown = 0.22;
                longGun.damage = 3;
                longGun.bulletSpeed = 980;
                longGun.bulletLife = 220;
                longGun.primary = true;
                longGun.radius = 6;
                longGun.barrelLen = 18;
                addTurret(longGun);

                // better CIWS than patrol, worse than corvette
                hasCIWS = true;
                ciwsQuality = 0.62;
                ciwsRange = 270;
                ciwsCooldown = 0.075;
                ciwsPelletsPerBurst = 3;
                ciwsPelletSpeed = 980;
                ciwsPelletLife = 18;
            }

            case STEALTH_SHIP -> {
                name = (faction == Faction.ENEMY ? "Enemy Stealth" : "Stealth Ship");
                radius = 15;
                hpMax = 12;
                hp = hpMax;

                shieldMax = 10;
                shield = shieldMax;
                shieldRegen = 1.4;
                shieldActive = true;

                desiredSpeed = 235;
                bountyValue = 140;

                // stealth properties
                isStealth = true;
                signature = 0.35;
                revealTimer = 0;
                cloakEnabled = true;
                cloakActive = false;
                cloakControlMode = CloakControlMode.CHARGE;
                cloakEnergyMax = 20.0;
                cloakEnergy = cloakEnergyMax;
                cloakDrainPerSec = 1.05;
                cloakRechargePerSec = 1.20;
                cloakMinEnergyToEngage = 0.8;
                cloakSignature = 0.07;

                Turret burstGun = new Turret(Turret.Kind.GUN, 11, 0);
                // Battleship-class main battery punch on stealth hull.
                burstGun.cooldown = 0.34;
                burstGun.damage = 4;
                burstGun.bulletSpeed = 980;
                burstGun.bulletLife = 270;
                burstGun.primary = true;
                burstGun.radius = 7.0;
                burstGun.barrelLen = 20;
                addTurret(burstGun);

                // small missile rack for surprise strikes
                Turret rack = new Turret(Turret.Kind.MISSILE, 5, 0);
                rack.cooldown = 0.95;
                rack.damage = 5;
                rack.missileSpeed = 295;
                rack.missileTurnRate = Math.toRadians(280);
                rack.missileLife = 250;
                rack.primary = false;
                rack.radius = 8;
                rack.barrelLen = 12;
                addTurret(rack);

                // weak CIWS
                hasCIWS = true;
                ciwsQuality = 0.15;
                ciwsRange = 180;
                ciwsCooldown = 0.17;
                ciwsPelletsPerBurst = 1;
            }

            // -----------------------
            // Small craft
            // -----------------------
            case FIGHTER -> {
                name = (faction == Faction.ENEMY ? "Enemy Fighter" : "Fighter");
                radius = 12;
                hpMax = 1;
                hp = hpMax;

                shieldMax = 1;
                shield = shieldMax;
                shieldRegen = 0.0;
                shieldActive = true;

                desiredSpeed = 255;
                bountyValue = 35;

                Turret gun = new Turret(Turret.Kind.GUN, 10, 0);
                gun.cooldown = 0.09;
                gun.damage = 1;
                gun.bulletSpeed = 900;
                gun.bulletLife = 100;
                gun.primary = true;
                gun.radius = 5;
                gun.barrelLen = 14;
                addTurret(gun);
            }

            case BOMBER -> {
                name = (faction == Faction.ENEMY ? "Enemy Bomber" : "Bomber");
                radius = 14;
                hpMax = 1;
                hp = hpMax;

                shieldMax = 1;
                shield = shieldMax;
                shieldRegen = 0.0;
                shieldActive = true;

                desiredSpeed = 215;
                bountyValue = 55;

                Turret gun = new Turret(Turret.Kind.GUN, 10, 0);
                gun.cooldown = 0.20;
                gun.damage = 1;
                gun.bulletSpeed = 820;
                gun.bulletLife = 90;
                gun.primary = true;
                addTurret(gun);

                Turret rack = new Turret(Turret.Kind.MISSILE, 4, 0);
                rack.cooldown = 0.90;
                rack.damage = 1;
                rack.missileSpeed = 280;
                rack.missileTurnRate = Math.toRadians(250);
                rack.missileLife = 260;
                rack.primary = false;
                rack.radius = 7;
                rack.barrelLen = 12;
                addTurret(rack);

                // weak CIWS
                hasCIWS = true;
                ciwsQuality = 0.20;
                ciwsRange = 200;
                ciwsCooldown = 0.16;
                ciwsPelletsPerBurst = 1;
            }

            case PD_CRAFT -> {
                name = (faction == Faction.ENEMY ? "Enemy PD Escort Frigate" : "PD Escort Frigate");
                // Frigate-sized carrier escort with exceptional anti-missile coverage.
                radius = 18;
                hpMax = 18;
                hp = hpMax;

                shieldMax = 16;
                shield = shieldMax;
                shieldRegen = 2.0;
                shieldActive = true;

                desiredSpeed = 170;
                bountyValue = 105;

                Turret fore = new Turret(Turret.Kind.GUN, 13, 0);
                fore.cooldown = 0.14;
                fore.damage = 1;
                fore.bulletSpeed = 820;
                fore.bulletLife = 120;
                fore.primary = true;
                addTurret(fore);

                Turret aft = new Turret(Turret.Kind.GUN, -10, 0);
                aft.cooldown = 0.20;
                aft.damage = 1;
                aft.bulletSpeed = 760;
                aft.bulletLife = 110;
                aft.primary = true;
                addTurret(aft);

                // Near-corvette CIWS quality tuned for escort duty.
                hasCIWS = true;
                ciwsQuality = 0.96;
                ciwsRange = 330;
                ciwsCooldown = 0.050;
                ciwsPelletsPerBurst = 6;
                ciwsPelletSpeed = 1010;
                ciwsPelletLife = 20;
                ciwsPelletRadius = 1.8;
            }

            case DRONE -> {
                name = (faction == Faction.ENEMY ? "Enemy Missile Drone" : "Missile Drone");
                radius = 11;
                hpMax = 1;
                hp = hpMax;

                shieldMax = 1;
                shield = shieldMax;
                shieldRegen = 0.0;
                shieldActive = true;

                desiredSpeed = 285;
                bountyValue = 38;

                Turret noseGun = new Turret(Turret.Kind.GUN, 8, 0);
                noseGun.cooldown = 0.18;
                noseGun.damage = 1;
                noseGun.bulletSpeed = 840;
                noseGun.bulletLife = 85;
                noseGun.primary = true;
                noseGun.radius = 4.8;
                noseGun.barrelLen = 11;
                addTurret(noseGun);

                Turret rackL = new Turret(Turret.Kind.MISSILE, 4, -3);
                rackL.cooldown = 0.72;
                rackL.damage = 4;
                rackL.missileSpeed = 305;
                rackL.missileTurnRate = Math.toRadians(310);
                rackL.missileLife = 260;
                rackL.primary = false;
                rackL.radius = 7;
                rackL.barrelLen = 11;
                addTurret(rackL);

                Turret rackR = new Turret(Turret.Kind.MISSILE, 4, 3);
                rackR.cooldown = 0.72;
                rackR.damage = 4;
                rackR.missileSpeed = 305;
                rackR.missileTurnRate = Math.toRadians(310);
                rackR.missileLife = 260;
                rackR.primary = false;
                rackR.radius = 7;
                rackR.barrelLen = 11;
                addTurret(rackR);

                hasCIWS = true;
                ciwsQuality = 0.18;
                ciwsRange = 180;
                ciwsCooldown = 0.17;
                ciwsPelletsPerBurst = 1;
            }

            // -----------------------
            // Medium ships
            // -----------------------
            case FRIGATE -> {
                name = (faction == Faction.ENEMY ? "Enemy Frigate" : "Frigate");
                radius = 18;
                hpMax = 18;
                hp = hpMax;

                shieldMax = 18;
                shield = shieldMax;
                shieldRegen = 2.0;
                shieldActive = true;

                desiredSpeed = 150;
                bountyValue = 90;

                Turret foreGun = new Turret(Turret.Kind.GUN, 14, 0);
                foreGun.cooldown = 0.13;
                foreGun.damage = 1;
                foreGun.bulletSpeed = 780;
                foreGun.bulletLife = 120;
                foreGun.primary = true;
                addTurret(foreGun);

                Turret aftGun = new Turret(Turret.Kind.GUN, -10, 0);
                aftGun.cooldown = 0.20;
                aftGun.damage = 1;
                aftGun.bulletSpeed = 720;
                aftGun.bulletLife = 110;
                aftGun.primary = true;
                aftGun.barrelLen = 12;
                addTurret(aftGun);

                Turret rack = new Turret(Turret.Kind.MISSILE, 6, 8);
                rack.cooldown = 0.98;
                rack.damage = 3;
                rack.missileSpeed = 255;
                rack.missileTurnRate = Math.toRadians(220);
                rack.missileLife = 220;
                rack.primary = false;
                rack.radius = 7;
                rack.barrelLen = 10;
                addTurret(rack);

                // weak CIWS
                hasCIWS = true;
                ciwsQuality = 0.25;
                ciwsRange = 210;
                ciwsCooldown = 0.13;
                ciwsPelletsPerBurst = 1;
                ciwsPelletSpeed = 900;
            }

            case ARTILLERY_SHIP -> {
                name = (faction == Faction.ENEMY ? "Enemy Artillery Ship" : "Artillery Ship");
                radius = 17;
                hpMax = 10;
                hp = hpMax;

                shieldMax = 8;
                shield = shieldMax;
                shieldRegen = 1.0;
                shieldActive = true;

                desiredSpeed = 126;
                bountyValue = 118;

                // Single battleship-grade spinal gun on a fragile early-game hull.
                Turret spinalGun = new Turret(Turret.Kind.GUN, 16, 0);
                spinalGun.cooldown = 0.34;
                spinalGun.damage = 4;
                spinalGun.bulletSpeed = 980;
                spinalGun.bulletLife = 270;
                spinalGun.primary = true;
                spinalGun.radius = 8.5;
                spinalGun.barrelLen = 21;
                addTurret(spinalGun);

                hasCIWS = true;
                ciwsQuality = 0.08;
                ciwsRange = 165;
                ciwsCooldown = 0.19;
                ciwsPelletsPerBurst = 1;
            }

            case CIWS_CORVETTE -> {
                name = (faction == Faction.ENEMY ? "Enemy CIWS Corvette" : "CIWS Corvette");
                radius = 16;
                hpMax = 14;
                hp = hpMax;

                shieldMax = 10;
                shield = shieldMax;
                shieldRegen = 1.6;
                shieldActive = true;

                desiredSpeed = 175;
                bountyValue = 95;

                Turret g1 = new Turret(Turret.Kind.GUN, 12, -6);
                g1.cooldown = 0.12;
                g1.primary = true;
                g1.bulletSpeed = 800;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 12, 6);
                g2.cooldown = 0.12;
                g2.primary = true;
                g2.bulletSpeed = 800;
                addTurret(g2);

                // BEST CIWS SHIP
                hasCIWS = true;
                ciwsQuality = 0.97;
                ciwsRange = 340;
                ciwsCooldown = 0.045;
                ciwsPelletsPerBurst = 7;
                ciwsPelletSpeed = 1020;
                ciwsPelletLife = 19;
                ciwsPelletRadius = 1.8;
            }

            case MISSILE_BOAT -> {
                name = (faction == Faction.ENEMY ? "Enemy Missile Boat" : "Missile Boat");
                radius = 20;
                hpMax = 14;
                hp = hpMax;

                shieldMax = 10;
                shield = shieldMax;
                shieldRegen = 1.5;
                shieldActive = true;

                desiredSpeed = 125;
                bountyValue = 105;

                Turret gunL = new Turret(Turret.Kind.GUN, 10, -8);
                gunL.cooldown = 0.24;
                gunL.damage = 1;
                gunL.bulletSpeed = 700;
                gunL.primary = true;
                addTurret(gunL);

                Turret gunR = new Turret(Turret.Kind.GUN, 10, 8);
                gunR.cooldown = 0.24;
                gunR.damage = 1;
                gunR.bulletSpeed = 700;
                gunR.primary = true;
                addTurret(gunR);

                Turret tubes1 = new Turret(Turret.Kind.MISSILE, 6, -4);
                tubes1.cooldown = 0.70;
                tubes1.damage = 5;
                tubes1.missileSpeed = 285;
                tubes1.missileTurnRate = Math.toRadians(255);
                tubes1.missileLife = 300;
                tubes1.primary = false;
                tubes1.radius = 8;
                addTurret(tubes1);

                Turret tubes2 = new Turret(Turret.Kind.MISSILE, 6, 4);
                tubes2.cooldown = 0.70;
                tubes2.damage = 5;
                tubes2.missileSpeed = 285;
                tubes2.missileTurnRate = Math.toRadians(255);
                tubes2.missileLife = 300;
                tubes2.primary = false;
                tubes2.radius = 8;
                addTurret(tubes2);

                // very weak CIWS
                hasCIWS = true;
                ciwsQuality = 0.12;
                ciwsRange = 170;
                ciwsCooldown = 0.17;
                ciwsPelletsPerBurst = 1;
            }

            case LIGHT_CRUISER -> {
                name = (faction == Faction.ENEMY ? "Enemy Light Cruiser" : "Light Cruiser");
                radius = 23;
                hpMax = 28;
                hp = hpMax;

                shieldMax = 22;
                shield = shieldMax;
                shieldRegen = 2.2;
                shieldActive = true;

                desiredSpeed = 120;
                bountyValue = 150;

                Turret g1 = new Turret(Turret.Kind.GUN, 16, -9);
                g1.cooldown = 0.17;
                g1.damage = 2;
                g1.bulletSpeed = 870;
                g1.bulletLife = 170;
                g1.primary = true;
                g1.radius = 7;
                g1.barrelLen = 18;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 16, 9);
                g2.cooldown = 0.17;
                g2.damage = 2;
                g2.bulletSpeed = 870;
                g2.bulletLife = 170;
                g2.primary = true;
                g2.radius = 7;
                g2.barrelLen = 18;
                addTurret(g2);

                Turret rear = new Turret(Turret.Kind.GUN, -12, 0);
                rear.cooldown = 0.21;
                rear.damage = 1;
                rear.bulletSpeed = 810;
                rear.bulletLife = 155;
                rear.primary = true;
                rear.radius = 6;
                rear.barrelLen = 14;
                addTurret(rear);

                Turret mb = new Turret(Turret.Kind.MISSILE, 5, 0);
                mb.cooldown = 1.15;
                mb.damage = 5;
                mb.primary = false;
                mb.missileSpeed = 275;
                mb.missileTurnRate = Math.toRadians(205);
                mb.missileLife = 270;
                mb.radius = 9;
                mb.barrelLen = 14;
                addTurret(mb);

                hasCIWS = true;
                ciwsQuality = 0.22;
                ciwsRange = 235;
                ciwsCooldown = 0.14;
                ciwsPelletsPerBurst = 1;
            }

            case MEDIUM_CRUISER -> {
                name = (faction == Faction.ENEMY ? "Enemy Medium Cruiser" : "Medium Cruiser");
                radius = 27;
                hpMax = 38;
                hp = hpMax;

                shieldMax = 28;
                shield = shieldMax;
                shieldRegen = 2.6;
                shieldActive = true;

                desiredSpeed = 108;
                bountyValue = 190;

                Turret g1 = new Turret(Turret.Kind.GUN, 19, -11);
                g1.cooldown = 0.17;
                g1.damage = 2;
                g1.bulletSpeed = 900;
                g1.bulletLife = 180;
                g1.primary = true;
                g1.radius = 7;
                g1.barrelLen = 18;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 19, 11);
                g2.cooldown = 0.17;
                g2.damage = 2;
                g2.bulletSpeed = 900;
                g2.bulletLife = 180;
                g2.primary = true;
                g2.radius = 7;
                g2.barrelLen = 18;
                addTurret(g2);

                Turret rear = new Turret(Turret.Kind.GUN, -15, 0);
                rear.cooldown = 0.20;
                rear.damage = 1;
                rear.bulletSpeed = 830;
                rear.bulletLife = 165;
                rear.primary = true;
                rear.radius = 6;
                rear.barrelLen = 14;
                addTurret(rear);

                Turret mb = new Turret(Turret.Kind.MISSILE, 6, 0);
                mb.cooldown = 1.05;
                mb.damage = 5;
                mb.primary = false;
                mb.missileSpeed = 280;
                mb.missileTurnRate = Math.toRadians(210);
                mb.missileLife = 285;
                mb.radius = 9;
                mb.barrelLen = 14;
                addTurret(mb);

                hasCIWS = true;
                ciwsQuality = 0.24;
                ciwsRange = 250;
                ciwsCooldown = 0.13;
                ciwsPelletsPerBurst = 1;
            }

            case CRUISER -> {
                name = (faction == Faction.ENEMY ? "Enemy Guided Missile Cruiser" : "Guided Missile Cruiser");
                radius = 27;
                hpMax = 36;
                hp = hpMax;

                shieldMax = 30;
                shield = shieldMax;
                shieldRegen = 2.7;
                shieldActive = true;

                desiredSpeed = 104;
                bountyValue = 230;

                Turret g1 = new Turret(Turret.Kind.GUN, 17, -10);
                g1.cooldown = 0.19;
                g1.damage = 2;
                g1.bulletSpeed = 900;
                g1.bulletLife = 185;
                g1.primary = true;
                g1.radius = 7;
                g1.barrelLen = 17;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 17, 10);
                g2.cooldown = 0.19;
                g2.damage = 2;
                g2.bulletSpeed = 900;
                g2.bulletLife = 185;
                g2.primary = true;
                g2.radius = 7;
                g2.barrelLen = 17;
                addTurret(g2);

                Turret g3 = new Turret(Turret.Kind.GUN, -8, -12);
                g3.cooldown = 0.23;
                g3.damage = 1;
                g3.bulletSpeed = 860;
                g3.bulletLife = 170;
                g3.primary = true;
                g3.radius = 6;
                g3.barrelLen = 14;
                addTurret(g3);

                Turret g4 = new Turret(Turret.Kind.GUN, -8, 12);
                g4.cooldown = 0.23;
                g4.damage = 1;
                g4.bulletSpeed = 860;
                g4.bulletLife = 170;
                g4.primary = true;
                g4.radius = 6;
                g4.barrelLen = 14;
                addTurret(g4);

                Turret m1 = new Turret(Turret.Kind.MISSILE, 7, -8);
                m1.cooldown = 0.74;
                m1.damage = 5;
                m1.primary = false;
                m1.missileSpeed = 300;
                m1.missileTurnRate = Math.toRadians(235);
                m1.missileLife = 320;
                m1.radius = 8;
                addTurret(m1);

                Turret m2 = new Turret(Turret.Kind.MISSILE, 7, 8);
                m2.cooldown = 0.74;
                m2.damage = 5;
                m2.primary = false;
                m2.missileSpeed = 300;
                m2.missileTurnRate = Math.toRadians(235);
                m2.missileLife = 320;
                m2.radius = 8;
                addTurret(m2);

                Turret m3 = new Turret(Turret.Kind.MISSILE, -2, -5);
                m3.cooldown = 0.98;
                m3.damage = 6;
                m3.primary = false;
                m3.missileSpeed = 290;
                m3.missileTurnRate = Math.toRadians(220);
                m3.missileLife = 340;
                m3.radius = 8;
                addTurret(m3);

                Turret m4 = new Turret(Turret.Kind.MISSILE, -2, 5);
                m4.cooldown = 0.98;
                m4.damage = 6;
                m4.primary = false;
                m4.missileSpeed = 290;
                m4.missileTurnRate = Math.toRadians(220);
                m4.missileLife = 340;
                m4.radius = 8;
                addTurret(m4);

                hasCIWS = true;
                ciwsQuality = 0.26;
                ciwsRange = 255;
                ciwsCooldown = 0.13;
                ciwsPelletsPerBurst = 1;
            }

            case BATTLECRUISER -> {
                name = (faction == Faction.ENEMY ? "Enemy Battlecruiser" : "Battlecruiser");
                radius = 32;
                hpMax = 58;
                hp = hpMax;

                shieldMax = 38;
                shield = shieldMax;
                shieldRegen = 3.0;
                shieldActive = true;

                desiredSpeed = 92; // faster than battleship
                bountyValue = 380;

                Turret g1 = new Turret(Turret.Kind.GUN, 24, -13);
                g1.cooldown = 0.22;
                g1.damage = 3;
                g1.bulletSpeed = 970;
                g1.bulletLife = 190;
                g1.primary = true;
                g1.radius = 9;
                g1.barrelLen = 22;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 24, 13);
                g2.cooldown = 0.22;
                g2.damage = 3;
                g2.bulletSpeed = 970;
                g2.bulletLife = 190;
                g2.primary = true;
                g2.radius = 9;
                g2.barrelLen = 22;
                addTurret(g2);

                Turret g3 = new Turret(Turret.Kind.GUN, 4, -16);
                g3.cooldown = 0.24;
                g3.damage = 2;
                g3.bulletSpeed = 930;
                g3.bulletLife = 175;
                g3.primary = true;
                g3.radius = 8;
                g3.barrelLen = 20;
                addTurret(g3);

                Turret g4 = new Turret(Turret.Kind.GUN, 4, 16);
                g4.cooldown = 0.24;
                g4.damage = 2;
                g4.bulletSpeed = 930;
                g4.bulletLife = 175;
                g4.primary = true;
                g4.radius = 8;
                g4.barrelLen = 20;
                addTurret(g4);

                Turret g5 = new Turret(Turret.Kind.GUN, -12, -12);
                g5.cooldown = 0.27;
                g5.damage = 2;
                g5.bulletSpeed = 900;
                g5.bulletLife = 175;
                g5.primary = true;
                g5.radius = 7;
                g5.barrelLen = 18;
                addTurret(g5);

                Turret g6 = new Turret(Turret.Kind.GUN, -12, 12);
                g6.cooldown = 0.27;
                g6.damage = 2;
                g6.bulletSpeed = 900;
                g6.bulletLife = 175;
                g6.primary = true;
                g6.radius = 7;
                g6.barrelLen = 18;
                addTurret(g6);

                Turret mb = new Turret(Turret.Kind.MISSILE, 0, 0);
                mb.cooldown = 1.10;
                mb.damage = 6;
                mb.primary = false;
                mb.missileSpeed = 295;
                mb.missileTurnRate = Math.toRadians(215);
                mb.missileLife = 270;
                mb.radius = 10;
                mb.barrelLen = 16;
                addTurret(mb);

                hasCIWS = true;
                ciwsQuality = 0.24;
                ciwsRange = 265;
                ciwsCooldown = 0.14;
                ciwsPelletsPerBurst = 1;
            }

            // -----------------------
            // Large ships
            // -----------------------
            case BATTLESHIP -> {
                name = (faction == Faction.ENEMY ? "Enemy Battleship" : "Battleship");
                radius = 36;
                hpMax = 70;
                hp = hpMax;

                shieldMax = 45;
                shield = shieldMax;
                shieldRegen = 3.0;
                shieldActive = true;

                desiredSpeed = 75;
                bountyValue = 420;

                Turret g1 = new Turret(Turret.Kind.GUN, 26, -14);
                g1.cooldown = 0.34;
                g1.damage = 4;
                g1.bulletSpeed = 980;
                g1.bulletLife = 270;
                g1.primary = true;
                g1.radius = 9;
                g1.barrelLen = 22;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 26, 14);
                g2.cooldown = 0.34;
                g2.damage = 4;
                g2.bulletSpeed = 980;
                g2.bulletLife = 270;
                g2.primary = true;
                g2.radius = 9;
                g2.barrelLen = 22;
                addTurret(g2);

                Turret g3 = new Turret(Turret.Kind.GUN, 4, -18);
                g3.cooldown = 0.36;
                g3.damage = 2;
                g3.bulletSpeed = 920;
                g3.bulletLife = 240;
                g3.primary = true;
                g3.radius = 8;
                g3.barrelLen = 20;
                addTurret(g3);

                Turret g4 = new Turret(Turret.Kind.GUN, 4, 18);
                g4.cooldown = 0.36;
                g4.damage = 2;
                g4.bulletSpeed = 920;
                g4.bulletLife = 240;
                g4.primary = true;
                g4.radius = 8;
                g4.barrelLen = 20;
                addTurret(g4);

                Turret g5 = new Turret(Turret.Kind.GUN, -14, -14);
                g5.cooldown = 0.40;
                g5.damage = 2;
                g5.bulletSpeed = 900;
                g5.bulletLife = 225;
                g5.primary = true;
                g5.radius = 8;
                g5.barrelLen = 18;
                addTurret(g5);

                Turret g6 = new Turret(Turret.Kind.GUN, -14, 14);
                g6.cooldown = 0.40;
                g6.damage = 2;
                g6.bulletSpeed = 900;
                g6.bulletLife = 225;
                g6.primary = true;
                g6.radius = 8;
                g6.barrelLen = 18;
                addTurret(g6);

                Turret mb = new Turret(Turret.Kind.MISSILE, -2, 0);
                mb.cooldown = 1.50;
                mb.damage = 7;
                mb.primary = false;
                mb.missileSpeed = 280;
                mb.missileTurnRate = Math.toRadians(180);
                mb.missileLife = 320;
                mb.radius = 10;
                mb.barrelLen = 16;
                addTurret(mb);

                hasCIWS = true;
                ciwsQuality = 0.18;
                ciwsRange = 250;
                ciwsCooldown = 0.15;
                ciwsPelletsPerBurst = 1;
            }

            case DREADNOUGHT -> {
                name = (faction == Faction.ENEMY ? "Enemy Dreadnought" : "Dreadnought");
                radius = 44;
                hpMax = 110;
                hp = hpMax;

                shieldMax = 70;
                shield = shieldMax;
                shieldRegen = 3.6;
                shieldActive = true;

                desiredSpeed = 62;
                bountyValue = 850;

                // Heavy batteries
                Turret a1 = new Turret(Turret.Kind.GUN, 32, -18);
                a1.cooldown = 0.38;
                a1.damage = 5;
                a1.bulletSpeed = 1020;
                a1.bulletLife = 300;
                a1.primary = true;
                a1.radius = 11;
                a1.barrelLen = 26;
                addTurret(a1);

                Turret a2 = new Turret(Turret.Kind.GUN, 32, 18);
                a2.cooldown = 0.38;
                a2.damage = 5;
                a2.bulletSpeed = 1020;
                a2.bulletLife = 300;
                a2.primary = true;
                a2.radius = 11;
                a2.barrelLen = 26;
                addTurret(a2);

                // Secondary guns
                Turret s1 = new Turret(Turret.Kind.GUN, 10, -22);
                s1.cooldown = 0.34;
                s1.damage = 3;
                s1.bulletSpeed = 960;
                s1.bulletLife = 255;
                s1.primary = true;
                s1.radius = 9;
                s1.barrelLen = 22;
                addTurret(s1);

                Turret s2 = new Turret(Turret.Kind.GUN, 10, 22);
                s2.cooldown = 0.34;
                s2.damage = 3;
                s2.bulletSpeed = 960;
                s2.bulletLife = 255;
                s2.primary = true;
                s2.radius = 9;
                s2.barrelLen = 22;
                addTurret(s2);

                Turret s3 = new Turret(Turret.Kind.GUN, -10, -18);
                s3.cooldown = 0.36;
                s3.damage = 3;
                s3.bulletSpeed = 940;
                s3.bulletLife = 245;
                s3.primary = true;
                s3.radius = 8;
                s3.barrelLen = 20;
                addTurret(s3);

                Turret s4 = new Turret(Turret.Kind.GUN, -10, 18);
                s4.cooldown = 0.36;
                s4.damage = 3;
                s4.bulletSpeed = 940;
                s4.bulletLife = 245;
                s4.primary = true;
                s4.radius = 8;
                s4.barrelLen = 20;
                addTurret(s4);

                // Missile bank
                Turret mb = new Turret(Turret.Kind.MISSILE, 0, 0);
                mb.cooldown = 1.55;
                mb.damage = 9;
                mb.primary = false;
                mb.missileSpeed = 300;
                mb.missileTurnRate = Math.toRadians(175);
                mb.missileLife = 380;
                mb.radius = 12;
                mb.barrelLen = 18;
                addTurret(mb);

                hasCIWS = true;
                ciwsQuality = 0.26;
                ciwsRange = 285;
                ciwsCooldown = 0.14;
                ciwsPelletsPerBurst = 2;
            }

            case SUPERSHIP -> {
                if (faction == Faction.ENEMY) name = "Red Supership";
                else if (faction == Faction.TEAM_C) name = "Green Supership";
                else if (faction != null && faction.isYellowLineage()) name = faction.teamName() + " Supership";
                else name = "Blue Supership";

                radius = 52;
                hpMax = 170;
                hp = hpMax;

                shieldMax = 120;
                shield = shieldMax;
                shieldRegen = 4.2;
                shieldActive = true;

                desiredSpeed = 52;
                bountyValue = 1600;

                addHullCenterGunTurret(0.72, 0.36, 6, 1080, 320, true, 12, 30);
                addHullCenterGunTurret(0.56, 0.34, 6, 1060, 312, true, 12, 28);
                addHullCenterGunTurret(0.42, 0.30, 4, 980, 280, true, 10, 24);
                addHullCenterGunTurret(0.28, 0.32, 4, 960, 265, true, 9, 22);

                Turret mb = new Turret(Turret.Kind.MISSILE, 2, 0);
                mb.cooldown = 1.65;
                mb.damage = 11;
                mb.primary = false;
                mb.missileSpeed = 320;
                mb.missileTurnRate = Math.toRadians(185);
                mb.missileLife = 420;
                mb.radius = 13;
                mb.barrelLen = 20;
                addTurret(mb);

                hasSuperweapon = true;
                superweaponChargeTime = 3.4;
                superweaponCooldown = 26.0;
                superweaponDamage = 96;
                superweaponSpeed = 1700.0;
                superweaponLife = 190;
                superweaponRadius = 14.0;
                superweaponMaxHits = 26;
                superweaponBeamDuration = 1.15;
                superweaponBeamTickInterval = 0.11;
                superweaponBeamDamageScale = 0.36;
                superweaponPattern = switch (faction) {
                    case ENEMY -> SuperweaponPattern.KINETIC_SLUG;
                    case TEAM_C -> SuperweaponPattern.DIRECT_BEAM;
                    case TEAM_D, BRIGHT_YELLOW, DARK_YELLOW -> SuperweaponPattern.MISSILE_BARRAGE;
                    default -> SuperweaponPattern.DESTABILIZER_PULSE;
                };

                if (superweaponPattern == SuperweaponPattern.DESTABILIZER_PULSE) {
                    superweaponChargeTime = 2.9;
                    superweaponCooldown = 19.0;
                    superweaponDamage = 108;
                    superweaponSpeed = 1500.0;
                    superweaponLife = 188;
                    superweaponRadius = 16.5;
                    superweaponBeamDuration = 0.0;
                } else if (superweaponPattern == SuperweaponPattern.DIRECT_BEAM) {
                    superweaponChargeTime = 3.0;
                    superweaponCooldown = 24.0;
                    superweaponDamage = 110;
                    superweaponBeamDuration = 1.45;
                } else if (superweaponPattern == SuperweaponPattern.KINETIC_SLUG) {
                    superweaponChargeTime = 3.3;
                    superweaponCooldown = 23.0;
                    superweaponDamage = 130;
                    superweaponBeamDuration = 0.0;
                } else if (superweaponPattern == SuperweaponPattern.MISSILE_BARRAGE) {
                    superweaponChargeTime = 2.8;
                    superweaponCooldown = 25.0;
                    superweaponDamage = 84;
                    superweaponBeamDuration = 1.20;
                    superweaponBeamTickInterval = 0.15;
                }

                hasCIWS = true;
                ciwsQuality = 0.45;
                ciwsRange = 320;
                ciwsCooldown = 0.11;
                ciwsPelletsPerBurst = 3;
            }

            case TRANSPORT_TITAN -> {
                name = factionCapitalName("Transport Titan");

                addHullGunPair(0.57, 0.47, 0.28, 3, 900, 230, true, 10, 22);
                addHullGunPair(0.39, 0.62, 0.36, 2, 820, 195, true, 8.5, 18);
                addHullMissilePair(0.50, 0.28, 1.55, 7, 300, 185, 360, 11, 18);

                hasCIWS = true;
                ciwsQuality = 0.64;
                ciwsRange = 360;
                ciwsCooldown = 0.10;

                configureRepairAura(560, 5.8, 16.0);
            }

            case BULWARK_TITAN -> {
                name = factionCapitalName("Bulwark Titan");

                addHullGunPair(0.60, 0.56, 0.34, 7, 1080, 335, true, 13, 32);
                addHullGunPair(0.44, 0.68, 0.30, 5, 980, 290, true, 11, 25);
                addHullGunPair(0.27, 0.57, 0.36, 4, 920, 250, true, 9.5, 21);
                addHullCenterMissileTurret(0.46, 1.70, 10, 315, 180, 430, 12, 20);

                hasCIWS = true;
                ciwsQuality = 0.82;
                ciwsRange = 395;
                ciwsCooldown = 0.08;

                configureRepairAura(360, 0.8, 14.5);
            }

            case CARRIER_SUPPORT_TITAN -> {
                name = factionCapitalName("Carrier Support Titan");

                addHullGunPair(0.54, 0.60, 0.24, 3, 900, 215, true, 9.5, 20);
                addHullGunPair(0.36, 0.48, 0.28, 2, 860, 195, true, 8.0, 18);
                addHullMissilePair(0.53, 0.24, 1.35, 6, 300, 195, 320, 10.5, 17);
                addHullMissilePair(0.34, 0.44, 1.60, 7, 295, 185, 335, 10.5, 17);

                hasCIWS = true;
                ciwsQuality = 0.70;
                ciwsRange = 370;
                ciwsCooldown = 0.09;

                configureRepairAura(500, 4.0, 14.0);
                configureCarrierSuite(1.5, 24, 6.8, 10);
                setFlightDeckRole(0, ShipRole.FIGHTER);
                setFlightDeckRole(1, ShipRole.BOMBER);
                setFlightDeckRole(2, ShipRole.FIGHTER);
                setFlightDeckRole(3, ShipRole.BOMBER);
                setFlightDeckRole(4, ShipRole.DRONE);
            }

            case VANGUARD_TITAN -> {
                name = factionCapitalName("Vanguard Titan");

                addHullGunPair(0.44, 0.72, 0.27, 7, 1120, 340, true, 12, 30);
                addHullGunPair(0.28, 0.86, 0.24, 5, 1020, 290, true, 10, 24);
                addHullMissilePair(0.54, 0.58, 1.10, 9, 350, 245, 360, 11, 18);
                addHullCenterMissileTurret(0.20, 1.55, 7, 320, 205, 320, 10.0, 17);

                hasCIWS = true;
                ciwsQuality = 0.66;
                ciwsRange = 350;
                ciwsCooldown = 0.09;
            }

            case INTERDICTION_TITAN -> {
                name = factionCapitalName("Interdiction Titan");

                addHullGunPair(0.56, 0.46, 0.29, 5, 1000, 295, true, 11, 27);
                addHullGunPair(0.38, 0.60, 0.32, 4, 940, 250, true, 9.5, 22);
                addHullMissilePair(0.54, 0.24, 1.15, 7, 320, 255, 360, 10.5, 18);
                addHullCenterMissileTurret(0.33, 1.55, 8, 300, 220, 390, 11.5, 18);

                hasCIWS = true;
                ciwsQuality = 0.68;
                ciwsRange = 390;
                ciwsCooldown = 0.09;
            }

            case COMMAND_INTEL_TITAN -> {
                name = factionCapitalName("Command / Intel Titan");

                addHullGunPair(0.62, 0.38, 0.27, 4, 980, 280, true, 10.5, 24);
                addHullGunPair(0.42, 0.42, 0.25, 3, 920, 225, true, 9, 20);
                addHullMissilePair(0.40, 0.20, 1.45, 7, 305, 205, 355, 10.5, 17);

                hasCIWS = true;
                ciwsQuality = 0.74;
                ciwsRange = 390;
                ciwsCooldown = 0.09;

                configureRepairAura(360, 1.0, 8.0);
            }

            case BOARDING_RECOVERY_TITAN -> {
                name = factionCapitalName("Boarding / Recovery Titan");

                addHullGunPair(0.58, 0.44, 0.30, 6, 1020, 300, true, 11.5, 28);
                addHullGunPair(0.40, 0.58, 0.29, 4, 950, 255, true, 9.5, 22);
                addHullMissilePair(0.52, 0.22, 1.20, 7, 315, 245, 350, 10.5, 18);

                hasCIWS = true;
                ciwsQuality = 0.72;
                ciwsRange = 370;
                ciwsCooldown = 0.09;

                configureRepairAura(420, 2.8, 10.5);
                configureCarrierSuite(1.8, 14, 8.0, 8);
                setFlightDeckRole(0, ShipRole.BOMBER);
                setFlightDeckRole(1, ShipRole.BOMBER);
                setFlightDeckRole(2, ShipRole.FIGHTER);
                setFlightDeckRole(3, ShipRole.BOMBER);
                setFlightDeckRole(4, ShipRole.BOMBER);
            }

            case ARTILLERY_TITAN -> {
                name = factionCapitalName("Artillery Titan");

                addHullGunPair(0.70, 0.34, 0.38, 8, 1220, 390, true, 13.5, 34);
                addHullGunPair(0.49, 0.50, 0.34, 5, 1040, 320, true, 10.5, 26);
                addHullGunPair(0.31, 0.66, 0.42, 5, 980, 340, true, 10.0, 24);
                addHullMissilePair(0.46, 0.20, 1.65, 8, 310, 190, 400, 11.0, 19);
                addHullCenterMissileTurret(0.24, 2.00, 10, 295, 165, 430, 11.5, 20);

                hasCIWS = true;
                ciwsQuality = 0.60;
                ciwsRange = 340;
                ciwsCooldown = 0.10;

                SuperweaponPattern artilleryPattern = (faction == Faction.ALLY || faction == Faction.PLAYER)
                        ? SuperweaponPattern.DESTABILIZER_PULSE
                        : resolveTitanSuperweaponPattern();
                int artillerySuperweaponDamage = (faction == Faction.ENEMY) ? 164 : 140;
                configureSuperweapon(artilleryPattern, 2.8, 18.0, artillerySuperweaponDamage, 1500.0, 190, 22.0, 24,
                        0.0, 0.12, 0.30);
            }

            case SHIELD_BASTION_TITAN -> {
                name = factionCapitalName("Shield Bastion Titan");

                addHullGunPair(0.24, 0.88, 0.31, 5, 1020, 305, true, 11.5, 28);
                addHullGunPair(0.40, 0.76, 0.34, 4, 940, 260, true, 9.5, 22);
                addHullMissilePair(0.58, 0.62, 1.50, 7, 300, 205, 360, 10.5, 18);

                hasCIWS = true;
                ciwsQuality = 0.86;
                ciwsRange = 410;
                ciwsCooldown = 0.08;

                configureRepairAura(620, 1.8, 22.0);
            }

            case FLEET_TELEPORTER_TITAN -> {
                name = factionCapitalName("Fleet Teleporter Titan");

                addHullGunPair(0.24, 0.84, 0.28, 5, 1010, 300, true, 11, 26);
                addHullGunPair(0.42, 0.72, 0.30, 4, 940, 250, true, 9.5, 22);
                addHullMissilePairDirect(0.68, 0.56, 1.20, 8, 335, 260, 355, 11.0, 18);

                hasCIWS = true;
                ciwsQuality = 0.70;
                ciwsRange = 370;
                ciwsCooldown = 0.09;
            }

            case ELITE_SUPERSHIP_COMMAND_TITAN -> {
                name = factionCapitalName("Elite Supership Command Titan");

                addHullGunPair(0.24, 0.88, 0.33, 7, 1100, 340, true, 12.5, 32);
                addHullGunPair(0.42, 0.76, 0.31, 5, 990, 285, true, 10.5, 25);
                addHullCenterMissileTurret(0.58, 1.55, 10, 320, 205, 420, 12.0, 20);

                hasCIWS = true;
                ciwsQuality = 0.78;
                ciwsRange = 385;
                ciwsCooldown = 0.08;
            }

            case ELITE_REINFORCEMENTS_TITAN -> {
                name = factionCapitalName("Elite Reinforcements Titan");

                addHullGunPair(0.24, 0.88, 0.33, 7, 1100, 340, true, 12.5, 32);
                addHullGunPair(0.42, 0.76, 0.31, 5, 990, 285, true, 10.5, 25);
                addHullCenterMissileTurret(0.58, 1.55, 10, 320, 205, 420, 12.0, 20);

                hasCIWS = true;
                ciwsQuality = 0.78;
                ciwsRange = 385;
                ciwsCooldown = 0.08;
            }

            case MOBILE_STATION_TITAN -> {
                name = factionCapitalName("Mobile Station Titan");

                addHullGunPair(0.24, 0.84, 0.27, 4, 930, 245, true, 10.0, 22);
                addHullGunPair(0.40, 0.72, 0.34, 3, 860, 220, true, 9.0, 19);
                addHullMissilePairDirect(0.58, 0.56, 1.50, 7, 295, 195, 350, 10.5, 17);

                hasCIWS = true;
                ciwsQuality = 0.80;
                ciwsRange = 405;
                ciwsCooldown = 0.08;

                configureRepairAura(500, 4.4, 18.0);
                configureCarrierSuite(3.4, 12, 11.0, 8);
            }

            case HYPERWEAPON_TITAN -> {
                name = factionCapitalName("Hyperweapon Titan");

                addHullGunPairDirect(0.22, 0.88, 0.44, 4, 980, 255, true, 10.5, 24);
                addHullGunPairDirect(0.50, 0.72, 0.62, 2, 900, 215, true, 8.0, 18);
                if (faction == Faction.ENEMY) {
                    applyRedHyperweaponKineticBatteryProfile();
                }

                hasCIWS = true;
                ciwsQuality = 0.56;
                ciwsRange = 305;
                ciwsCooldown = 0.11;

                SuperweaponPattern hyperweaponPattern = resolveTitanSuperweaponPattern();
                if (hyperweaponPattern == SuperweaponPattern.KINETIC_SHOTGUN) {
                    configureSuperweapon(hyperweaponPattern, 4.0, 24.0, 34, 3600.0, 42, 7.0, 2,
                            1.25, 0.055, 0.55);
                } else if (hyperweaponPattern == SuperweaponPattern.KINETIC_SLUG) {
                    configureSuperweapon(hyperweaponPattern, 4.2, 28.0, 104, 1460.0, 210, 22.0, 12,
                            0.0, 0.12, 0.0);
                } else if (hyperweaponPattern == SuperweaponPattern.DIRECT_BEAM) {
                    configureSuperweapon(hyperweaponPattern, 4.1, 26.0, 164, 2140.0, 220, 28.0, 24,
                            1.18, 0.12, 0.54);
                } else if (hyperweaponPattern == SuperweaponPattern.MISSILE_BARRAGE) {
                    configureSuperweapon(hyperweaponPattern, 4.4, 30.0, 72, 820.0, 280, 24.0, 1,
                            0.0, 0.16, 0.0);
                } else {
                    configureSuperweapon(hyperweaponPattern, 4.0, 24.0, 152, 1760.0, 220, 24.0, 24,
                            0.0, 0.12, 0.0);
                }
            }

            case MOTHERSHIP -> {
                name = factionCapitalName("Mothership");

                addHullGunPair(0.24, 0.90, 0.34, 8, 1120, 360, true, 13.0, 34);
                addHullGunPair(0.42, 0.80, 0.30, 6, 1020, 320, true, 11.5, 28);
                addHullGunPairDirect(0.60, 0.62, 0.34, 5, 960, 275, true, 10.5, 24);
                addHullMissilePairDirect(0.72, 0.48, 1.45, 9, 320, 210, 420, 12.0, 20);

                hasCIWS = true;
                ciwsQuality = 0.92;
                ciwsRange = 455;
                ciwsCooldown = 0.07;

                configureRepairAura(560, 5.0, 22.0);
                configureCarrierSuite(2.3, 18, 8.5, 12);
            }

            case CARRIER -> {
                name = (faction == Faction.ENEMY ? "Enemy Carrier" : "Carrier");
                radius = 34;
                hpMax = 50;
                hp = hpMax;

                shieldMax = 34;
                shield = shieldMax;
                shieldRegen = 3.0;
                shieldActive = true;

                desiredSpeed = 90;
                bountyValue = 360;

                Turret leftGun = new Turret(Turret.Kind.GUN, 14, -18);
                leftGun.cooldown = 0.22;
                leftGun.damage = 1;
                leftGun.bulletSpeed = 780;
                leftGun.bulletLife = 150;
                leftGun.primary = true;
                addTurret(leftGun);

                Turret rightGun = new Turret(Turret.Kind.GUN, 14, 18);
                rightGun.cooldown = 0.22;
                rightGun.damage = 1;
                rightGun.bulletSpeed = 780;
                rightGun.bulletLife = 150;
                rightGun.primary = true;
                addTurret(rightGun);

                Turret rearGun = new Turret(Turret.Kind.GUN, -14, 0);
                rearGun.cooldown = 0.25;
                rearGun.damage = 1;
                rearGun.bulletSpeed = 760;
                rearGun.bulletLife = 150;
                rearGun.primary = true;
                addTurret(rearGun);

                Turret portGun = new Turret(Turret.Kind.GUN, 0, -14);
                portGun.cooldown = 0.24;
                portGun.damage = 1;
                portGun.bulletSpeed = 760;
                portGun.bulletLife = 150;
                portGun.primary = true;
                addTurret(portGun);

                Turret starboardGun = new Turret(Turret.Kind.GUN, 0, 14);
                starboardGun.cooldown = 0.24;
                starboardGun.damage = 1;
                starboardGun.bulletSpeed = 760;
                starboardGun.bulletLife = 150;
                starboardGun.primary = true;
                addTurret(starboardGun);

                Turret missiles = new Turret(Turret.Kind.MISSILE, 6, 0);
                missiles.cooldown = 1.45;
                missiles.damage = 4;
                missiles.primary = false;
                missiles.missileSpeed = 265;
                missiles.missileTurnRate = Math.toRadians(195);
                missiles.missileLife = 270;
                addTurret(missiles);

                hasCIWS = true;
                ciwsQuality = 0.18;
                ciwsRange = 245;
                ciwsCooldown = 0.16;
                ciwsPelletsPerBurst = 1;

                isCarrier = true;
                fighterLaunchCooldown = 4.2;
                maxFighters = 10;
                baseSpawnCooldown = 18.0;
                maxDefenders = 5;
            }

            case DRONE_CARRIER -> {
                name = (faction == Faction.ENEMY ? "Enemy Drone Carrier" : "Drone Carrier");
                radius = 32;
                hpMax = 44;
                hp = hpMax;

                shieldMax = 28;
                shield = shieldMax;
                shieldRegen = 2.6;
                shieldActive = true;

                desiredSpeed = 96;
                bountyValue = 340;

                Turret g1 = new Turret(Turret.Kind.GUN, 14, -14);
                g1.cooldown = 0.16;
                g1.bulletSpeed = 820;
                g1.bulletLife = 140;
                g1.primary = true;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 14, 14);
                g2.cooldown = 0.16;
                g2.bulletSpeed = 820;
                g2.bulletLife = 140;
                g2.primary = true;
                addTurret(g2);

                Turret g3 = new Turret(Turret.Kind.GUN, -2, -12);
                g3.cooldown = 0.18;
                g3.damage = 1;
                g3.bulletSpeed = 800;
                g3.bulletLife = 140;
                g3.primary = true;
                addTurret(g3);

                Turret g4 = new Turret(Turret.Kind.GUN, -2, 12);
                g4.cooldown = 0.18;
                g4.damage = 1;
                g4.bulletSpeed = 800;
                g4.bulletLife = 140;
                g4.primary = true;
                addTurret(g4);

                Turret missiles = new Turret(Turret.Kind.MISSILE, 6, 0);
                missiles.cooldown = 1.30;
                missiles.damage = 4;
                missiles.primary = false;
                missiles.missileSpeed = 270;
                missiles.missileTurnRate = Math.toRadians(215);
                missiles.missileLife = 250;
                addTurret(missiles);

                hasCIWS = true;
                ciwsQuality = 0.16;
                ciwsRange = 235;
                ciwsCooldown = 0.17;
                ciwsPelletsPerBurst = 1;

                isCarrier = true;
                fighterLaunchCooldown = 3.6;
                maxFighters = 10;
                baseSpawnCooldown = 16.0;
                maxDefenders = 4;
            }

            case TRANSPORT -> {
                name = (faction == Faction.ENEMY ? "Enemy Combat Support Transport" : "Combat Support Transport");
                radius = 24;
                hpMax = 26;
                hp = hpMax;

                shieldMax = 18;
                shield = shieldMax;
                shieldRegen = 2.0;
                shieldActive = true;

                desiredSpeed = 110;
                bountyValue = 150;

                Turret gun = new Turret(Turret.Kind.GUN, 14, 0);
                gun.cooldown = 0.28;
                gun.damage = 1;
                gun.bulletSpeed = 740;
                gun.bulletLife = 115;
                gun.primary = true;
                addTurret(gun);

                hasCIWS = true;
                ciwsQuality = 0.14;
                ciwsRange = 190;
                ciwsCooldown = 0.18;
                ciwsPelletsPerBurst = 1;

                // Support aura (used by Main AI)
                repairRange = 300;
                repairHullPerSec = 1.8;
                repairShieldPerSec = 6.8;
            }

            // -----------------------
            // Economy / logistics
            // -----------------------

            case MINER -> {
                name = (faction == Faction.ENEMY ? "Enemy Miner" : "Miner");
                radius = 18;
                hpMax = 16;
                hp = hpMax;

                shieldMax = 10;
                shield = shieldMax;
                shieldRegen = 1.5;
                shieldActive = true;

                desiredSpeed = 125;
                bountyValue = 120;

                // Resource loop
                cargoMax = 180;
                miningRate = 22.0;
                miningRange = 64.0;

                // Light defensive gun
                Turret gun = new Turret(Turret.Kind.GUN, 12, 0);
                gun.cooldown = 0.26;
                gun.damage = 1;
                gun.bulletSpeed = 740;
                gun.bulletLife = 110;
                gun.primary = true;
                gun.radius = 5.5;
                gun.barrelLen = 14;
                addTurret(gun);

                hasCIWS = true;
                ciwsQuality = 0.20;
                ciwsRange = 210;
                ciwsCooldown = 0.16;
                ciwsPelletsPerBurst = 1;
            }

            case HAULER -> {
                name = (faction == Faction.ENEMY ? "Enemy Hauler" : "Hauler");
                radius = 22;
                hpMax = 22;
                hp = hpMax;

                shieldMax = 14;
                shield = shieldMax;
                shieldRegen = 1.8;
                shieldActive = true;

                desiredSpeed = 115;
                bountyValue = 140;

                cargoMax = 420;
                miningRate = 0.0;
                miningRange = 0.0;

                // Basic gun
                Turret gun = new Turret(Turret.Kind.GUN, 14, 0);
                gun.cooldown = 0.30;
                gun.damage = 1;
                gun.bulletSpeed = 720;
                gun.bulletLife = 110;
                gun.primary = true;
                addTurret(gun);

                hasCIWS = true;
                ciwsQuality = 0.14;
                ciwsRange = 190;
                ciwsCooldown = 0.18;
                ciwsPelletsPerBurst = 1;
            }

            // -----------------------
            // Structures
            // -----------------------
            case BASE -> {
                String teamLabel = (faction == null) ? "UNKNOWN" : faction.teamName().toUpperCase(Locale.US);
                name = "Base (" + teamLabel + ")";
                radius = 60;
                hpMax = 240;
                hp = hpMax;

                shieldMax = 190;
                shield = shieldMax;
                shieldRegen = 7.0;
                shieldActive = true;

                desiredSpeed = 0;
                bountyValue = 900;

                isBase = true;
                baseOwner = faction;
                captureProgress = (faction == Faction.ENEMY) ? 0.0 : 1.0;
                captureRadius = 380;
                captureTime = 12.0;

                baseSpawnCooldown = 9.0;
                maxDefenders = 10;

                repairRange = 340;
                repairHullPerSec = 2.0;
                repairShieldPerSec = 10.0;

                hasCIWS = true;
                ciwsQuality = 0.18;
                ciwsRange = 270;
                ciwsCooldown = 0.18;
                ciwsPelletsPerBurst = 1;

                Turret g1 = new Turret(Turret.Kind.GUN, 22, -22);
                g1.cooldown = 0.22;
                g1.damage = 2;
                g1.bulletSpeed = 860;
                g1.bulletLife = 165;
                g1.primary = true;
                g1.radius = 8;
                g1.barrelLen = 18;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 22, 22);
                g2.cooldown = 0.22;
                g2.damage = 2;
                g2.bulletSpeed = 860;
                g2.bulletLife = 165;
                g2.primary = true;
                g2.radius = 8;
                g2.barrelLen = 18;
                addTurret(g2);

                Turret g3 = new Turret(Turret.Kind.GUN, -22, -22);
                g3.cooldown = 0.24;
                g3.damage = 2;
                g3.bulletSpeed = 860;
                g3.bulletLife = 165;
                g3.primary = true;
                g3.radius = 8;
                g3.barrelLen = 18;
                addTurret(g3);

                Turret g4 = new Turret(Turret.Kind.GUN, -22, 22);
                g4.cooldown = 0.24;
                g4.damage = 2;
                g4.bulletSpeed = 860;
                g4.bulletLife = 165;
                g4.primary = true;
                g4.radius = 8;
                g4.barrelLen = 18;
                addTurret(g4);

                Turret mb = new Turret(Turret.Kind.MISSILE, 0, 0);
                mb.cooldown = 1.45;
                mb.damage = 5;
                mb.primary = false;
                mb.missileSpeed = 260;
                mb.missileTurnRate = Math.toRadians(200);
                mb.missileLife = 260;
                mb.radius = 10;
                mb.barrelLen = 16;
                addTurret(mb);
            }

            case STATIC_TURRET -> {
                name = (faction == Faction.ENEMY ? "Enemy Turret" : "Turret");
                radius = 16;
                hpMax = 22;
                hp = hpMax;

                shieldMax = 10;
                shield = shieldMax;
                shieldRegen = 1.5;
                shieldActive = true;

                desiredSpeed = 0;
                bountyValue = 120;

                Turret g = new Turret(Turret.Kind.GUN, 10, 0);
                g.cooldown = 0.18;
                g.damage = 2;
                g.bulletSpeed = 900;
                g.bulletLife = 200;
                g.primary = true;
                g.radius = 7;
                g.barrelLen = 18;
                addTurret(g);

                Turret m = new Turret(Turret.Kind.MISSILE, 2, 0);
                m.cooldown = 1.9;
                m.damage = 4;
                m.primary = false;
                m.missileSpeed = 250;
                m.missileTurnRate = Math.toRadians(185);
                m.missileLife = 230;
                addTurret(m);

                hasCIWS = false;
            }
        }

        // Apply baseline core stats from the single source of truth.
        // (Keeps tuning centralized in RoleStats.)
        RoleStats.applyCore(this, role);
        rebalanceEnemyMissileLoadout();
        desiredSpeedBase = Math.max(0.0, desiredSpeed);
        configureStrikeCraftMunitions();
        resetInternalSystems();
    }

    private void rebalanceEnemyMissileLoadout() {
        if (faction == null) return;
        if (faction.teamId() == Faction.ALLY.teamId()) return;
        if (turrets == null || turrets.isEmpty()) return;

        double cooldownMul = 1.10;
        double damageMul = 0.92;
        double speedMul = 0.95;
        double turnMul = 0.93;
        double lifeMul = 0.95;

        if (role == ShipRole.MISSILE_BOAT || role == ShipRole.BOMBER
                || role == ShipRole.STEALTH_SHIP || role == ShipRole.CRUISER) {
            cooldownMul = 1.04;
            damageMul = 0.96;
            speedMul = 0.97;
            turnMul = 0.96;
            lifeMul = 0.98;
        } else if (role == ShipRole.CARRIER || role == ShipRole.DRONE_CARRIER
                || role == ShipRole.CARRIER_SUPPORT_TITAN || role == ShipRole.MOBILE_STATION_TITAN
                || role == ShipRole.MOTHERSHIP) {
            cooldownMul = 1.16;
            damageMul = 0.90;
            speedMul = 0.93;
            turnMul = 0.91;
            lifeMul = 0.93;
        } else if (role == ShipRole.BATTLECRUISER || role == ShipRole.BATTLESHIP
                || role == ShipRole.DREADNOUGHT || role == ShipRole.SUPERSHIP
                || role.isTitanOrMothership()) {
            cooldownMul = 1.12;
            damageMul = 0.94;
            speedMul = 0.95;
            turnMul = 0.92;
            lifeMul = 0.95;
        }

        for (Turret t : turrets) {
            if (t == null || t.kind != Turret.Kind.MISSILE) continue;
            t.cooldown = t.cooldown * cooldownMul;
            t.damage = Math.max(1, (int) Math.round(t.damage * damageMul));
            t.missileSpeed = t.missileSpeed * speedMul;
            t.missileTurnRate = t.missileTurnRate * turnMul;
            t.missileLife = Math.max(1, (int) Math.round(t.missileLife * lifeMul));
        }
    }

    private void conformTurretsToHull() {
        if (turrets.isEmpty()) return;

        java.util.List<Turret> adjusted = new java.util.ArrayList<>(turrets.size());
        boolean changed = false;
        for (Turret turret : turrets) {
            Turret resolved = conformTurretToHull(turret);
            adjusted.add(resolved);
            changed |= resolved != turret;
        }
        if (!changed) return;

        turrets.clear();
        for (Turret turret : adjusted) {
            addTurret(turret);
        }
    }

    private Turret conformTurretToHull(Turret turret) {
        if (turret == null) return null;
        double footprint = Math.max(2.0, turret.radius * 0.55);
        if (isTurretMountOnHull(turret.localX, turret.localY, footprint)) return turret;

        double lo = 0.0;
        double hi = 1.0;
        for (int i = 0; i < 24; i++) {
            double t = 0.5 * (lo + hi);
            double tx = turret.localX * t;
            double ty = turret.localY * t;
            if (isTurretMountOnHull(tx, ty, footprint)) lo = t;
            else hi = t;
        }

        double scale = Math.max(0.16, lo);
        if (scale >= 0.995) return turret;
        return copyTurret(turret, turret.localX * scale, turret.localY * scale);
    }

    private boolean isTurretMountOnHull(double localX, double localY, double footprint) {
        HullGeometry.ImpactSample center = HullGeometry.sampleImpact(this, x + localX, y + localY);
        if (center != null && center.onHull) return true;

        double probe = Math.max(1.5, footprint);
        HullGeometry.ImpactSample left = HullGeometry.sampleImpact(this, x + localX, y + localY - probe);
        HullGeometry.ImpactSample right = HullGeometry.sampleImpact(this, x + localX, y + localY + probe);
        HullGeometry.ImpactSample fore = HullGeometry.sampleImpact(this, x + localX + probe, y + localY);
        HullGeometry.ImpactSample aft = HullGeometry.sampleImpact(this, x + localX - probe, y + localY);
        return (left != null && left.onHull)
                || (right != null && right.onHull)
                || (fore != null && fore.onHull)
                || (aft != null && aft.onHull);
    }

    private Turret copyTurret(Turret src, double localX, double localY) {
        Turret copy = new Turret(src.kind, localX, localY);
        copy.angle = src.angle;
        copy.turnRate = src.turnRate;
        copy.cooldown = src.cooldown;
        copy.damage = src.damage;
        copy.bulletSpeed = src.bulletSpeed;
        copy.bulletLife = src.bulletLife;
        copy.missileSpeed = src.missileSpeed;
        copy.missileTurnRate = src.missileTurnRate;
        copy.missileLife = src.missileLife;
        copy.missileRole = src.missileRole;
        copy.enablesDamageGrowth = src.enablesDamageGrowth;
        copy.radius = src.radius;
        copy.barrelLen = src.barrelLen;
        copy.primary = src.primary;
        return copy;
    }

    private String factionCapitalName(String hullName) {
        String prefix = switch (faction) {
            case ENEMY -> "Red";
            case TEAM_C -> "Green";
            case TEAM_D -> "Yellow";
            case BRIGHT_YELLOW -> "Bright Yellow";
            case DARK_YELLOW -> "Dark Orange-Yellow";
            default -> "Blue";
        };
        return prefix + " " + hullName;
    }

    private Turret addGunTurret(double localX, double localY, double cooldown, int damage,
                                double bulletSpeed, int bulletLife, boolean primary,
                                double turretRadius, double barrelLen) {
        Turret gun = new Turret(Turret.Kind.GUN, localX, localY);
        gun.cooldown = cooldown;
        gun.damage = damage;
        gun.bulletSpeed = bulletSpeed;
        gun.bulletLife = bulletLife;
        gun.primary = primary;
        gun.radius = turretRadius;
        gun.barrelLen = barrelLen;
        addTurret(gun);
        return gun;
    }

    private Turret addMissileTurret(double localX, double localY, double cooldown, int damage,
                                    double missileSpeed, double missileTurnRateDeg, int missileLife,
                                    double turretRadius, double barrelLen) {
        Turret rack = new Turret(Turret.Kind.MISSILE, localX, localY);
        rack.cooldown = cooldown;
        rack.damage = damage;
        rack.primary = false;
        rack.missileSpeed = missileSpeed;
        rack.missileTurnRate = Math.toRadians(missileTurnRateDeg);
        rack.missileLife = missileLife;
        rack.radius = turretRadius;
        rack.barrelLen = barrelLen;
        addTurret(rack);
        return rack;
    }

    private void addHullGunPair(double alongFrac, double lateralFrac, double cooldown, int damage,
                                double bulletSpeed, int bulletLife, boolean primary,
                                double turretRadius, double barrelLen) {
        double[] upper = hullMount(alongFrac, lateralFrac, true);
        double[] lower = hullMount(alongFrac, lateralFrac, false);
        addGunTurret(upper[0], upper[1], cooldown, damage, bulletSpeed, bulletLife, primary, turretRadius, barrelLen);
        addGunTurret(lower[0], lower[1], cooldown, damage, bulletSpeed, bulletLife, primary, turretRadius, barrelLen);
    }

    private void addHullGunPairDirect(double alongFrac, double lateralFrac, double cooldown, int damage,
                                      double bulletSpeed, int bulletLife, boolean primary,
                                      double turretRadius, double barrelLen) {
        double[] upper = hullMountExact(alongFrac, lateralFrac, true);
        double[] lower = hullMountExact(alongFrac, lateralFrac, false);
        addGunTurret(upper[0], upper[1], cooldown, damage, bulletSpeed, bulletLife, primary, turretRadius, barrelLen);
        addGunTurret(lower[0], lower[1], cooldown, damage, bulletSpeed, bulletLife, primary, turretRadius, barrelLen);
    }

    private Turret addHullCenterGunTurret(double alongFrac, double cooldown, int damage,
                                          double bulletSpeed, int bulletLife, boolean primary,
                                          double turretRadius, double barrelLen) {
        double[] center = hullCenterMount(alongFrac);
        return addGunTurret(center[0], center[1], cooldown, damage, bulletSpeed, bulletLife, primary,
                turretRadius, barrelLen);
    }

    private void addHullMissilePair(double alongFrac, double lateralFrac, double cooldown, int damage,
                                    double missileSpeed, double missileTurnRateDeg, int missileLife,
                                    double turretRadius, double barrelLen) {
        double[] upper = hullMount(alongFrac, lateralFrac, true);
        double[] lower = hullMount(alongFrac, lateralFrac, false);
        addMissileTurret(upper[0], upper[1], cooldown, damage, missileSpeed, missileTurnRateDeg, missileLife,
                turretRadius, barrelLen);
        addMissileTurret(lower[0], lower[1], cooldown, damage, missileSpeed, missileTurnRateDeg, missileLife,
                turretRadius, barrelLen);
    }

    private void addHullMissilePairDirect(double alongFrac, double lateralFrac, double cooldown, int damage,
                                          double missileSpeed, double missileTurnRateDeg, int missileLife,
                                          double turretRadius, double barrelLen) {
        double[] upper = hullMountExact(alongFrac, lateralFrac, true);
        double[] lower = hullMountExact(alongFrac, lateralFrac, false);
        addMissileTurret(upper[0], upper[1], cooldown, damage, missileSpeed, missileTurnRateDeg, missileLife,
                turretRadius, barrelLen);
        addMissileTurret(lower[0], lower[1], cooldown, damage, missileSpeed, missileTurnRateDeg, missileLife,
                turretRadius, barrelLen);
    }

    private Turret addHullCenterMissileTurret(double alongFrac, double cooldown, int damage,
                                              double missileSpeed, double missileTurnRateDeg, int missileLife,
                                              double turretRadius, double barrelLen) {
        double[] center = hullCenterMount(alongFrac);
        return addMissileTurret(center[0], center[1], cooldown, damage, missileSpeed, missileTurnRateDeg, missileLife,
                turretRadius, barrelLen);
    }

    private double[] hullCenterMount(double alongFrac) {
        HullMountColumn column = hullMountColumn(alongFrac);
        return new double[]{column.localX, 0.0};
    }

    private double[] hullMount(double alongFrac, double lateralFrac, boolean upper) {
        HullMountColumn column = hullMountColumn(alongFrac, lateralFrac);
        return hullMountFromColumn(column, lateralFrac, upper);
    }

    private double[] hullMountExact(double alongFrac, double lateralFrac, boolean upper) {
        HullMountColumn column = hullMountColumn(alongFrac);
        return hullMountFromColumn(column, lateralFrac, upper);
    }

    private double[] hullMountFromColumn(HullMountColumn column, double lateralFrac, boolean upper) {
        double frac = MathUtil.clamp(lateralFrac, 0.0, 0.92);
        double safetyInset = Math.max(1.25, column.thickness * 0.06);
        double symmetricHalfSpan = Math.max(2.0,
                column.balancedHalfSpan - safetyInset);
        double edgeFrac = MathUtil.clamp(0.66 + frac * 0.28, 0.66, 0.92);
        double offset = symmetricHalfSpan * edgeFrac;
        double y = upper ? -offset : offset;
        return new double[]{column.localX, y};
    }

    private HullMountColumn hullMountColumn(double alongFrac) {
        java.awt.Polygon hull = ShipHullSilhouette.hullPolygon(role, radius, faction);
        if (hull == null || hull.npoints < 3) {
            double fallbackX = MathUtil.clamp(alongFrac, 0.0, 1.0) * radius * 1.8 - radius * 0.9;
            return new HullMountColumn(fallbackX, 0.0, Math.max(6.0, radius * 0.42));
        }

        java.awt.Rectangle bounds = hull.getBounds();
        double minX = bounds.getMinX();
        double maxX = bounds.getMaxX();
        double targetX = minX + MathUtil.clamp(alongFrac, 0.0, 1.0) * Math.max(1.0, maxX - minX);

        HullMountColumn column = sampleHullMountColumn(hull, targetX);
        if (column != null) return column;

        double[] offsets = new double[]{-2.5, 2.5, -5.0, 5.0, -8.0, 8.0, -12.0, 12.0};
        for (double offset : offsets) {
            column = sampleHullMountColumn(hull, targetX + offset);
            if (column != null) return column;
        }

        double fallbackX = MathUtil.clamp(targetX, minX, maxX);
        double fallbackHalfSpan = Math.max(6.0, bounds.getHeight() * 0.18);
        return new HullMountColumn(fallbackX, -fallbackHalfSpan, fallbackHalfSpan);
    }

    private HullMountColumn hullMountColumn(double alongFrac, double lateralFrac) {
        java.awt.Polygon hull = ShipHullSilhouette.hullPolygon(role, radius, faction);
        if (hull == null || hull.npoints < 3) {
            return hullMountColumn(alongFrac);
        }

        java.awt.Rectangle bounds = hull.getBounds();
        double minX = bounds.getMinX();
        double maxX = bounds.getMaxX();
        double targetX = minX + MathUtil.clamp(alongFrac, 0.0, 1.0) * Math.max(1.0, maxX - minX);

        HullMountColumn primary = hullMountColumn(alongFrac);
        double frac = MathUtil.clamp(lateralFrac, 0.0, 0.92);
        double desiredBalancedHalfSpan = Math.max(6.0, radius * (0.20 + frac * 0.14));
        if (primary.balancedHalfSpan >= desiredBalancedHalfSpan) return primary;

        HullMountColumn best = primary;
        double bestScore = mountColumnScore(primary, 0.0, desiredBalancedHalfSpan);
        double searchRange = Math.max(14.0, radius * 0.74);
        for (double offset = -searchRange; offset <= searchRange; offset += 2.0) {
            if (Math.abs(offset) < 1.0) continue;
            HullMountColumn candidate = sampleHullMountColumn(hull, targetX + offset);
            if (candidate == null) continue;
            double score = mountColumnScore(candidate, Math.abs(offset), desiredBalancedHalfSpan);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private double mountColumnScore(HullMountColumn column, double offsetDistance, double desiredBalancedHalfSpan) {
        if (column == null) return Double.NEGATIVE_INFINITY;
        double balancedCoverage = Math.min(column.balancedHalfSpan, desiredBalancedHalfSpan) * 2.8;
        double excessCoverage = Math.max(0.0, column.balancedHalfSpan - desiredBalancedHalfSpan) * 0.8;
        double thicknessBonus = column.thickness * 0.08;
        double distancePenalty = offsetDistance * 0.32;
        return balancedCoverage + excessCoverage + thicknessBonus - distancePenalty;
    }

    private HullMountColumn sampleHullMountColumn(java.awt.Polygon hull, double sampleX) {
        if (hull == null || hull.npoints < 3) return null;
        java.util.ArrayList<Double> ys = new java.util.ArrayList<>();
        final double eps = 1e-6;
        for (int i = 0, j = hull.npoints - 1; i < hull.npoints; j = i++) {
            double ax = hull.xpoints[j];
            double ay = hull.ypoints[j];
            double bx = hull.xpoints[i];
            double by = hull.ypoints[i];
            double min = Math.min(ax, bx);
            double max = Math.max(ax, bx);
            if (sampleX < min - eps || sampleX > max + eps) continue;

            if (Math.abs(ax - bx) <= eps) {
                if (Math.abs(sampleX - ax) <= 0.75) {
                    ys.add(ay);
                    ys.add(by);
                }
                continue;
            }

            double t = (sampleX - ax) / (bx - ax);
            if (t < -eps || t > 1.0 + eps) continue;
            ys.add(ay + (by - ay) * t);
        }
        if (ys.size() < 2) return null;
        java.util.Collections.sort(ys);
        double top = ys.get(0);
        double bottom = ys.get(ys.size() - 1);
        if (bottom - top < 2.0) return null;
        return new HullMountColumn(sampleX, top, bottom);
    }

    private static final class HullMountColumn {
        final double localX;
        final double topY;
        final double bottomY;
        final double thickness;
        final double balancedHalfSpan;

        HullMountColumn(double localX, double topY, double bottomY) {
            this.localX = localX;
            this.topY = Math.min(topY, bottomY);
            this.bottomY = Math.max(topY, bottomY);
            this.thickness = Math.max(0.0, this.bottomY - this.topY);
            this.balancedHalfSpan = Math.min(Math.abs(this.topY), Math.abs(this.bottomY));
        }
    }

    private void configureRepairAura(double range, double hullPerSec, double shieldPerSec) {
        repairRange = range;
        repairHullPerSec = hullPerSec;
        repairShieldPerSec = shieldPerSec;
    }

    private void configureCarrierSuite(double launchCooldown, int fighters, double spawnCooldown, int defenders) {
        isCarrier = true;
        fighterLaunchCooldown = launchCooldown;
        maxFighters = fighters;
        baseSpawnCooldown = spawnCooldown;
        maxDefenders = defenders;
    }

    private void applyCustomFlightDeckLoadout() {
        switch (role) {
            case CARRIER_SUPPORT_TITAN -> {
                setFlightDeckRole(0, ShipRole.FIGHTER);
                setFlightDeckRole(1, ShipRole.BOMBER);
                setFlightDeckRole(2, ShipRole.FIGHTER);
                setFlightDeckRole(3, ShipRole.BOMBER);
                setFlightDeckRole(4, ShipRole.DRONE);
            }
            case BOARDING_RECOVERY_TITAN -> {
                setFlightDeckRole(0, ShipRole.BOMBER);
                setFlightDeckRole(1, ShipRole.BOMBER);
                setFlightDeckRole(2, ShipRole.FIGHTER);
                setFlightDeckRole(3, ShipRole.BOMBER);
                setFlightDeckRole(4, ShipRole.BOMBER);
            }
            default -> {
            }
        }
    }

    private void configureSuperweapon(SuperweaponPattern pattern,
                                      double chargeTime,
                                      double cooldown,
                                      int damage,
                                      double speed,
                                      int life,
                                      double shotRadius,
                                      int maxHits,
                                      double beamDuration,
                                      double beamTickInterval,
                                      double beamDamageScale) {
        hasSuperweapon = true;
        superweaponPattern = (pattern == null) ? SuperweaponPattern.DESTABILIZER_PULSE : pattern;
        superweaponChargeTime = chargeTime;
        superweaponCooldown = cooldown;
        superweaponDamage = damage;
        superweaponSpeed = speed;
        superweaponLife = life;
        superweaponRadius = shotRadius;
        superweaponMaxHits = maxHits;
        superweaponBeamDuration = beamDuration;
        superweaponBeamTickInterval = beamTickInterval;
        superweaponBeamDamageScale = beamDamageScale;
    }

    private SuperweaponPattern resolveTitanSuperweaponPattern() {
        return switch (faction) {
            case ENEMY -> role == ShipRole.HYPERWEAPON_TITAN
                    ? SuperweaponPattern.KINETIC_SHOTGUN
                    : SuperweaponPattern.KINETIC_SLUG;
            case TEAM_C -> SuperweaponPattern.DIRECT_BEAM;
            case TEAM_D, BRIGHT_YELLOW, DARK_YELLOW -> SuperweaponPattern.MISSILE_BARRAGE;
            default -> SuperweaponPattern.DESTABILIZER_PULSE;
        };
    }

    private void applyRedHyperweaponKineticBatteryProfile() {
        for (Turret turret : turrets) {
            if (turret == null || turret.kind != Turret.Kind.GUN) continue;
            turret.cooldown = Math.max(0.045, turret.cooldown * 0.30);
            turret.bulletSpeed = Math.max(2600.0, turret.bulletSpeed * 2.75);
            turret.bulletLife = Math.max(34, (int) Math.round(turret.bulletLife * 0.54));
            turret.damage = Math.max(1, (int) Math.round(turret.damage * 0.72));
            turret.turnRate *= 1.35;
        }
    }
}
