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
        standardizeCiwsLoadout();
        resetFlightDeckLoadout();
    }

    private void standardizeCiwsLoadout() {
        if (!hasCIWS) return;
        if (role == ShipRole.BASE || role == ShipRole.STATIC_TURRET) return;
        ciwsQuality = 1.0;
        ciwsPelletsPerBurst = switch (SpawnSystem.requiredHangarTierForRole(role)) {
            case 0 -> 2;
            case 1 -> 3;
            case 2 -> 5;
            default -> 8;
        };
    }

    private void setup(ShipRole role) {
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
                cloakActive = true;
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
                if (faction == Faction.ENEMY) name = "Enemy Supership";
                else if (faction == Faction.TEAM_C) name = "Team C Supership";
                else if (faction == Faction.TEAM_D) name = "Team D Supership";
                else name = "Supership";

                radius = 52;
                hpMax = 170;
                hp = hpMax;

                shieldMax = 120;
                shield = shieldMax;
                shieldRegen = 4.2;
                shieldActive = true;

                desiredSpeed = 52;
                bountyValue = 1600;

                Turret a1 = new Turret(Turret.Kind.GUN, 38, -18);
                a1.cooldown = 0.36;
                a1.damage = 6;
                a1.bulletSpeed = 1080;
                a1.bulletLife = 320;
                a1.primary = true;
                a1.radius = 12;
                a1.barrelLen = 30;
                addTurret(a1);

                Turret a2 = new Turret(Turret.Kind.GUN, 38, 18);
                a2.cooldown = 0.36;
                a2.damage = 6;
                a2.bulletSpeed = 1080;
                a2.bulletLife = 320;
                a2.primary = true;
                a2.radius = 12;
                a2.barrelLen = 30;
                addTurret(a2);

                Turret s1 = new Turret(Turret.Kind.GUN, 14, -24);
                s1.cooldown = 0.30;
                s1.damage = 4;
                s1.bulletSpeed = 980;
                s1.bulletLife = 280;
                s1.primary = true;
                s1.radius = 10;
                s1.barrelLen = 24;
                addTurret(s1);

                Turret s2 = new Turret(Turret.Kind.GUN, 14, 24);
                s2.cooldown = 0.30;
                s2.damage = 4;
                s2.bulletSpeed = 980;
                s2.bulletLife = 280;
                s2.primary = true;
                s2.radius = 10;
                s2.barrelLen = 24;
                addTurret(s2);

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
                    case TEAM_D -> SuperweaponPattern.MISSILE_BARRAGE;
                    default -> SuperweaponPattern.PULSE_BARRAGE;
                };

                if (superweaponPattern == SuperweaponPattern.DIRECT_BEAM) {
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
                if (faction == Faction.ENEMY) name = "Base (ENEMY)";
                else if (faction == Faction.TEAM_C) name = "Base (TEAM C)";
                else if (faction == Faction.TEAM_D) name = "Base (TEAM D)";
                else name = "Base (ALLY)";
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
        } else if (role == ShipRole.CARRIER || role == ShipRole.DRONE_CARRIER) {
            cooldownMul = 1.16;
            damageMul = 0.90;
            speedMul = 0.93;
            turnMul = 0.91;
            lifeMul = 0.93;
        } else if (role == ShipRole.BATTLECRUISER || role == ShipRole.BATTLESHIP
                || role == ShipRole.DREADNOUGHT || role == ShipRole.SUPERSHIP) {
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
}
