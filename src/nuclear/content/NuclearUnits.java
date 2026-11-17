package nuclear.content;

import arc.graphics.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.type.*;
import nuclear.drone.*;

public class NuclearUnits{
    public static UnitType workerDrone, guardDrone, medicDrone;

    public static void load(){
        workerDrone = new UnitType("drone-worker"){{
            constructor = UnitEntity::create;
            aiController = WorkerDroneAI::new;
            applyDroneDefaults(this);

            health = 50f;
            speed = 2.8f;
            hitSize = 8f;
            itemCapacity = 10;
        }};

        guardDrone = new UnitType("drone-guard"){{
            constructor = UnitEntity::create;
            aiController = GuardDroneAI::new;
            applyDroneDefaults(this);

            health = 100f;
            speed = 3f;
            hitSize = 9f;
            itemCapacity = 0;

            //flare-style twin guns, a bit stronger than the flare
            weapons.add(new Weapon(){{
                reload = 15f;
                x = 3f;
                y = 0f;
                mirror = true;
                alternate = true;
                rotate = true;
                rotateSpeed = 25f;
                shootSound = Sounds.shoot;
                ejectEffect = Fx.casing1;
                bullet = new BasicBulletType(3.2f, 13f){{
                    width = 7f;
                    height = 10f;
                    lifetime = 48f;
                    shootEffect = Fx.shootSmall;
                    smokeEffect = Fx.shootSmallSmoke;
                    backColor = Color.valueOf("6bd6ff");
                    frontColor = Color.white;
                }};
            }});
        }};

        //faster than the workers so it can catch up with a drone that is crawling to a station
        medicDrone = new UnitType("drone-medic"){{
            constructor = UnitEntity::create;
            aiController = MedicDroneAI::new;
            applyDroneDefaults(this);

            health = 100f;
            speed = 3.4f;
            hitSize = 9f;
            itemCapacity = 0;
        }};
    }

    private static void applyDroneDefaults(UnitType t){
        //the sprites already have their own dark edge, keep the auto outline thin
        t.outlineRadius = 1;
        t.flying = true;
        t.lowAltitude = true;
        t.accel = 0.08f;
        t.drag = 0.05f;
        t.engineOffset = 4f;
        t.engineSize = 2f;
        //drones are driven only by the drone network, never by the player or logic
        t.playerControllable = false;
        t.logicControllable = false;
        t.allowedInPayloads = false;
        t.isEnemy = false;
        //units over the unit cap are killed instantly, drones have their own limit in DroneNet
        t.useUnitCap = false;
        t.alwaysUnlocked = true;
    }
}
