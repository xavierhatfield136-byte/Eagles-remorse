/**
 * A configurable ship used for allies and enemies.
 *
 * Roles:
 * - Small craft: FIGHTER / BOMBER / PD_CRAFT / DRONE
 * - Medium ships: FRIGATE / MISSILE_BOAT / CIWS_CORVETTE / CRUISER
 * - Large ships: BATTLESHIP / CARRIER / DRONE_CARRIER / TRANSPORT
 * - Structures: BASE / STATIC_TURRET
 *
 * Notes:
 * - CIWS quality is intentionally strong only on CIWS_CORVETTE (and PD_CRAFT).
 * - Most other ships have weak CIWS (or none) to keep the corvette valuable.
 */
public class FleetShip extends Ship {

    public FleetShip(ShipRole role, Faction faction, double x, double y) {
        this.role = role;
        this.faction = faction;
        this.x = x;
        this.y = y;

        setup(role);
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
                gun.cooldown = 0.12;
                gun.damage = 1;
                gun.bulletSpeed = 820;
                gun.bulletLife = 110;
                gun.primary = true;
                gun.radius = 5.5;
                gun.barrelLen = 14;
                addTurret(gun);

                // light CIWS
                hasCIWS = true;
                ciwsQuality = 0.35;
                ciwsRange = 220;
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
                longGun.cooldown = 0.18;
                longGun.damage = 2;
                longGun.bulletSpeed = 900;
                longGun.bulletLife = 165;
                longGun.primary = true;
                longGun.radius = 6;
                longGun.barrelLen = 18;
                addTurret(longGun);

                // better CIWS than patrol, worse than corvette
                hasCIWS = true;
                ciwsQuality = 0.70;
                ciwsRange = 280;
                ciwsCooldown = 0.07;
                ciwsPelletsPerBurst = 4;
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

                Turret burstGun = new Turret(Turret.Kind.GUN, 11, 0);
                burstGun.cooldown = 0.10;
                burstGun.damage = 1;
                burstGun.bulletSpeed = 860;
                burstGun.bulletLife = 120;
                burstGun.primary = true;
                burstGun.radius = 5.5;
                burstGun.barrelLen = 16;
                addTurret(burstGun);

                // small missile rack for surprise strikes
                Turret rack = new Turret(Turret.Kind.MISSILE, 5, 0);
                rack.cooldown = 1.15;
                rack.damage = 4;
                rack.missileSpeed = 270;
                rack.missileTurnRate = Math.toRadians(260);
                rack.missileLife = 230;
                rack.primary = false;
                rack.radius = 8;
                rack.barrelLen = 12;
                addTurret(rack);

                // weak CIWS
                hasCIWS = true;
                ciwsQuality = 0.20;
                ciwsRange = 200;
                ciwsCooldown = 0.16;
                ciwsPelletsPerBurst = 1;
            }

            // -----------------------
            // Small craft
            // -----------------------
            case FIGHTER -> {
                name = (faction == Faction.ENEMY ? "Enemy Fighter" : "Fighter");
                radius = 12;
                hpMax = 6;
                hp = hpMax;

                shieldMax = 0;
                shield = 0;
                shieldActive = false;

                desiredSpeed = 255;
                bountyValue = 35;

                Turret gun = new Turret(Turret.Kind.GUN, 10, 0);
                gun.cooldown = 0.10;
                gun.damage = 1;
                gun.bulletSpeed = 860;
                gun.bulletLife = 95;
                gun.primary = true;
                gun.radius = 5;
                gun.barrelLen = 14;
                addTurret(gun);
            }

            case BOMBER -> {
                name = (faction == Faction.ENEMY ? "Enemy Bomber" : "Bomber");
                radius = 14;
                hpMax = 8;
                hp = hpMax;

                shieldMax = 6;
                shield = shieldMax;
                shieldRegen = 0.8;
                shieldActive = true;

                desiredSpeed = 215;
                bountyValue = 55;

                Turret gun = new Turret(Turret.Kind.GUN, 10, 0);
                gun.cooldown = 0.16;
                gun.damage = 1;
                gun.bulletSpeed = 820;
                gun.bulletLife = 90;
                gun.primary = true;
                addTurret(gun);

                Turret rack = new Turret(Turret.Kind.MISSILE, 4, 0);
                rack.cooldown = 0.95;
                rack.damage = 4;
                rack.missileSpeed = 270;
                rack.missileTurnRate = Math.toRadians(260);
                rack.missileLife = 240;
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
                name = (faction == Faction.ENEMY ? "Enemy PD Craft" : "PD Craft");
                radius = 13;
                hpMax = 7;
                hp = hpMax;

                shieldMax = 5;
                shield = shieldMax;
                shieldRegen = 0.9;
                shieldActive = true;

                desiredSpeed = 245;
                bountyValue = 50;

                Turret gun = new Turret(Turret.Kind.GUN, 9, 0);
                gun.cooldown = 0.14;
                gun.damage = 1;
                gun.bulletSpeed = 820;
                gun.bulletLife = 85;
                gun.primary = true;
                addTurret(gun);

                // Good CIWS (but still slightly worse than dedicated corvette)
                hasCIWS = true;
                ciwsQuality = 0.92;
                ciwsRange = 300;
                ciwsCooldown = 0.055;
                ciwsPelletsPerBurst = 5;
                ciwsPelletSpeed = 980;
                ciwsPelletLife = 18;
                ciwsPelletRadius = 1.8;
            }

            case DRONE -> {
                name = (faction == Faction.ENEMY ? "Enemy Drone" : "Drone");
                radius = 10;
                hpMax = 4;
                hp = hpMax;

                shieldMax = 0;
                shield = 0;
                shieldActive = false;

                desiredSpeed = 290;
                bountyValue = 18;

                Turret gun = new Turret(Turret.Kind.GUN, 8, 0);
                gun.cooldown = 0.12;
                gun.damage = 1;
                gun.bulletSpeed = 820;
                gun.bulletLife = 75;
                gun.primary = true;
                gun.radius = 4.6;
                gun.barrelLen = 12;
                addTurret(gun);
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
                foreGun.cooldown = 0.14;
                foreGun.damage = 1;
                foreGun.bulletSpeed = 780;
                foreGun.bulletLife = 120;
                foreGun.primary = true;
                addTurret(foreGun);

                Turret aftGun = new Turret(Turret.Kind.GUN, -10, 0);
                aftGun.cooldown = 0.22;
                aftGun.damage = 1;
                aftGun.bulletSpeed = 720;
                aftGun.bulletLife = 110;
                aftGun.primary = true;
                aftGun.barrelLen = 12;
                addTurret(aftGun);

                Turret rack = new Turret(Turret.Kind.MISSILE, 6, 8);
                rack.cooldown = 1.05;
                rack.damage = 3;
                rack.missileSpeed = 245;
                rack.missileTurnRate = Math.toRadians(220);
                rack.missileLife = 205;
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
                g1.cooldown = 0.16;
                g1.primary = true;
                g1.bulletSpeed = 760;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 12, 6);
                g2.cooldown = 0.16;
                g2.primary = true;
                g2.bulletSpeed = 760;
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
                gunL.cooldown = 0.18;
                gunL.damage = 1;
                gunL.bulletSpeed = 740;
                gunL.primary = true;
                addTurret(gunL);

                Turret gunR = new Turret(Turret.Kind.GUN, 10, 8);
                gunR.cooldown = 0.18;
                gunR.damage = 1;
                gunR.bulletSpeed = 740;
                gunR.primary = true;
                addTurret(gunR);

                Turret tubes1 = new Turret(Turret.Kind.MISSILE, 6, -4);
                tubes1.cooldown = 0.80;
                tubes1.damage = 4;
                tubes1.missileSpeed = 260;
                tubes1.missileTurnRate = Math.toRadians(260);
                tubes1.missileLife = 230;
                tubes1.primary = false;
                tubes1.radius = 8;
                addTurret(tubes1);

                Turret tubes2 = new Turret(Turret.Kind.MISSILE, 6, 4);
                tubes2.cooldown = 0.80;
                tubes2.damage = 4;
                tubes2.missileSpeed = 260;
                tubes2.missileTurnRate = Math.toRadians(260);
                tubes2.missileLife = 230;
                tubes2.primary = false;
                tubes2.radius = 8;
                addTurret(tubes2);

                // very weak CIWS
                hasCIWS = true;
                ciwsQuality = 0.18;
                ciwsRange = 190;
                ciwsCooldown = 0.16;
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
                g1.cooldown = 0.19;
                g1.damage = 2;
                g1.bulletSpeed = 845;
                g1.bulletLife = 145;
                g1.primary = true;
                g1.radius = 7;
                g1.barrelLen = 18;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 16, 9);
                g2.cooldown = 0.18;
                g2.damage = 2;
                g2.bulletSpeed = 845;
                g2.bulletLife = 145;
                g2.primary = true;
                g2.radius = 7;
                g2.barrelLen = 18;
                addTurret(g2);

                Turret rear = new Turret(Turret.Kind.GUN, -12, 0);
                rear.cooldown = 0.23;
                rear.damage = 1;
                rear.bulletSpeed = 780;
                rear.bulletLife = 135;
                rear.primary = true;
                rear.radius = 6;
                rear.barrelLen = 14;
                addTurret(rear);

                Turret mb = new Turret(Turret.Kind.MISSILE, 5, 0);
                mb.cooldown = 1.25;
                mb.damage = 4;
                mb.primary = false;
                mb.missileSpeed = 265;
                mb.missileTurnRate = Math.toRadians(210);
                mb.missileLife = 245;
                mb.radius = 9;
                mb.barrelLen = 14;
                addTurret(mb);

                hasCIWS = true;
                ciwsQuality = 0.22;
                ciwsRange = 235;
                ciwsCooldown = 0.14;
                ciwsPelletsPerBurst = 1;
            }

            case CRUISER, MEDIUM_CRUISER -> {
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
                g1.cooldown = 0.18;
                g1.damage = 2;
                g1.bulletSpeed = 875;
                g1.bulletLife = 155;
                g1.primary = true;
                g1.radius = 7;
                g1.barrelLen = 18;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 19, 11);
                g2.cooldown = 0.18;
                g2.damage = 2;
                g2.bulletSpeed = 875;
                g2.bulletLife = 155;
                g2.primary = true;
                g2.radius = 7;
                g2.barrelLen = 18;
                addTurret(g2);

                Turret rear = new Turret(Turret.Kind.GUN, -15, 0);
                rear.cooldown = 0.21;
                rear.damage = 1;
                rear.bulletSpeed = 800;
                rear.bulletLife = 140;
                rear.primary = true;
                rear.radius = 6;
                rear.barrelLen = 14;
                addTurret(rear);

                Turret mb = new Turret(Turret.Kind.MISSILE, 6, 0);
                mb.cooldown = 1.15;
                mb.damage = 5;
                mb.primary = false;
                mb.missileSpeed = 270;
                mb.missileTurnRate = Math.toRadians(215);
                mb.missileLife = 255;
                mb.radius = 9;
                mb.barrelLen = 14;
                addTurret(mb);

                hasCIWS = true;
                ciwsQuality = 0.24;
                ciwsRange = 250;
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
                g1.cooldown = 0.25;
                g1.damage = 3;
                g1.bulletSpeed = 940;
                g1.bulletLife = 195;
                g1.primary = true;
                g1.radius = 9;
                g1.barrelLen = 22;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 24, 13);
                g2.cooldown = 0.25;
                g2.damage = 3;
                g2.bulletSpeed = 940;
                g2.bulletLife = 195;
                g2.primary = true;
                g2.radius = 9;
                g2.barrelLen = 22;
                addTurret(g2);

                Turret g3 = new Turret(Turret.Kind.GUN, 4, -16);
                g3.cooldown = 0.28;
                g3.damage = 2;
                g3.bulletSpeed = 900;
                g3.bulletLife = 180;
                g3.primary = true;
                g3.radius = 8;
                g3.barrelLen = 20;
                addTurret(g3);

                Turret g4 = new Turret(Turret.Kind.GUN, 4, 16);
                g4.cooldown = 0.28;
                g4.damage = 2;
                g4.bulletSpeed = 900;
                g4.bulletLife = 180;
                g4.primary = true;
                g4.radius = 8;
                g4.barrelLen = 20;
                addTurret(g4);

                Turret mb = new Turret(Turret.Kind.MISSILE, 0, 0);
                mb.cooldown = 1.25;
                mb.damage = 6;
                mb.primary = false;
                mb.missileSpeed = 285;
                mb.missileTurnRate = Math.toRadians(205);
                mb.missileLife = 285;
                mb.radius = 10;
                mb.barrelLen = 16;
                addTurret(mb);

                hasCIWS = true;
                ciwsQuality = 0.22;
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
                g1.cooldown = 0.26;
                g1.damage = 3;
                g1.bulletSpeed = 920;
                g1.bulletLife = 190;
                g1.primary = true;
                g1.radius = 9;
                g1.barrelLen = 22;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 26, 14);
                g2.cooldown = 0.26;
                g2.damage = 3;
                g2.bulletSpeed = 920;
                g2.bulletLife = 190;
                g2.primary = true;
                g2.radius = 9;
                g2.barrelLen = 22;
                addTurret(g2);

                Turret g3 = new Turret(Turret.Kind.GUN, 4, -18);
                g3.cooldown = 0.30;
                g3.damage = 2;
                g3.bulletSpeed = 880;
                g3.bulletLife = 175;
                g3.primary = true;
                g3.radius = 8;
                g3.barrelLen = 20;
                addTurret(g3);

                Turret g4 = new Turret(Turret.Kind.GUN, 4, 18);
                g4.cooldown = 0.30;
                g4.damage = 2;
                g4.bulletSpeed = 880;
                g4.bulletLife = 175;
                g4.primary = true;
                g4.radius = 8;
                g4.barrelLen = 20;
                addTurret(g4);

                Turret mb = new Turret(Turret.Kind.MISSILE, -2, 0);
                mb.cooldown = 1.35;
                mb.damage = 6;
                mb.primary = false;
                mb.missileSpeed = 270;
                mb.missileTurnRate = Math.toRadians(190);
                mb.missileLife = 280;
                mb.radius = 10;
                mb.barrelLen = 16;
                addTurret(mb);

                hasCIWS = true;
                ciwsQuality = 0.20;
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
                a1.cooldown = 0.34;
                a1.damage = 4;
                a1.bulletSpeed = 980;
                a1.bulletLife = 230;
                a1.primary = true;
                a1.radius = 11;
                a1.barrelLen = 26;
                addTurret(a1);

                Turret a2 = new Turret(Turret.Kind.GUN, 32, 18);
                a2.cooldown = 0.34;
                a2.damage = 4;
                a2.bulletSpeed = 980;
                a2.bulletLife = 230;
                a2.primary = true;
                a2.radius = 11;
                a2.barrelLen = 26;
                addTurret(a2);

                // Secondary guns
                Turret s1 = new Turret(Turret.Kind.GUN, 10, -22);
                s1.cooldown = 0.28;
                s1.damage = 2;
                s1.bulletSpeed = 920;
                s1.bulletLife = 210;
                s1.primary = true;
                s1.radius = 9;
                s1.barrelLen = 22;
                addTurret(s1);

                Turret s2 = new Turret(Turret.Kind.GUN, 10, 22);
                s2.cooldown = 0.28;
                s2.damage = 2;
                s2.bulletSpeed = 920;
                s2.bulletLife = 210;
                s2.primary = true;
                s2.radius = 9;
                s2.barrelLen = 22;
                addTurret(s2);

                // Missile bank
                Turret mb = new Turret(Turret.Kind.MISSILE, 0, 0);
                mb.cooldown = 1.45;
                mb.damage = 8;
                mb.primary = false;
                mb.missileSpeed = 290;
                mb.missileTurnRate = Math.toRadians(185);
                mb.missileLife = 330;
                mb.radius = 12;
                mb.barrelLen = 18;
                addTurret(mb);

                hasCIWS = true;
                ciwsQuality = 0.30;
                ciwsRange = 285;
                ciwsCooldown = 0.14;
                ciwsPelletsPerBurst = 2;
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
                leftGun.cooldown = 0.18;
                leftGun.damage = 1;
                leftGun.primary = true;
                addTurret(leftGun);

                Turret rightGun = new Turret(Turret.Kind.GUN, 14, 18);
                rightGun.cooldown = 0.18;
                rightGun.damage = 1;
                rightGun.primary = true;
                addTurret(rightGun);

                Turret rearGun = new Turret(Turret.Kind.GUN, -14, 0);
                rearGun.cooldown = 0.22;
                rearGun.damage = 1;
                rearGun.primary = true;
                rearGun.bulletSpeed = 740;
                addTurret(rearGun);

                Turret missiles = new Turret(Turret.Kind.MISSILE, 6, 0);
                missiles.cooldown = 1.35;
                missiles.damage = 4;
                missiles.primary = false;
                missiles.missileSpeed = 255;
                missiles.missileTurnRate = Math.toRadians(200);
                missiles.missileLife = 240;
                addTurret(missiles);

                hasCIWS = true;
                ciwsQuality = 0.18;
                ciwsRange = 245;
                ciwsCooldown = 0.16;
                ciwsPelletsPerBurst = 1;

                isCarrier = true;
                fighterLaunchCooldown = 4.0;
                maxFighters = 10;
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
                g1.cooldown = 0.18;
                g1.primary = true;
                addTurret(g1);

                Turret g2 = new Turret(Turret.Kind.GUN, 14, 14);
                g2.cooldown = 0.18;
                g2.primary = true;
                addTurret(g2);

                Turret missiles = new Turret(Turret.Kind.MISSILE, 6, 0);
                missiles.cooldown = 1.55;
                missiles.damage = 4;
                missiles.primary = false;
                missiles.missileSpeed = 250;
                missiles.missileTurnRate = Math.toRadians(200);
                missiles.missileLife = 235;
                addTurret(missiles);

                hasCIWS = true;
                ciwsQuality = 0.16;
                ciwsRange = 235;
                ciwsCooldown = 0.17;
                ciwsPelletsPerBurst = 1;

                isCarrier = true;
                fighterLaunchCooldown = 3.3;
                maxFighters = 14;
            }

            case TRANSPORT -> {
                name = (faction == Faction.ENEMY ? "Enemy Transport" : "Transport");
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
                gun.cooldown = 0.22;
                gun.damage = 1;
                gun.bulletSpeed = 760;
                gun.primary = true;
                addTurret(gun);

                hasCIWS = true;
                ciwsQuality = 0.14;
                ciwsRange = 190;
                ciwsCooldown = 0.18;
                ciwsPelletsPerBurst = 1;

                // Support aura (used by Main AI)
                repairRange = 260;
                repairHullPerSec = 1.4;
                repairShieldPerSec = 6.0;
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
                gun.cooldown = 0.20;
                gun.damage = 1;
                gun.bulletSpeed = 760;
                gun.bulletLife = 120;
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
                gun.cooldown = 0.24;
                gun.damage = 1;
                gun.bulletSpeed = 740;
                gun.bulletLife = 120;
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
    }
}
