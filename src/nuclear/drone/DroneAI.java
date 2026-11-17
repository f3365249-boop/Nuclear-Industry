package nuclear.drone;

import arc.math.*;
import arc.util.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import nuclear.content.*;
import nuclear.world.blocks.drone.DroneStation.*;

/**
 * Shared behaviour of every drone:
 * - passive discharge (5 hp per 4 s) while the drone is doing something; idle drones circling a station do not lose hp
 * - below 50% hp the drone flies to the closest station and recharges there
 * - below 6 hp the drone never dies from discharge, it just becomes very slow
 */
public abstract class DroneAI extends AIController{
    public static final float decayPerSecond = 5f / 4f;
    public static final float healPerSecond = 10f;
    public static final float chargeFraction = 0.5f;
    public static final float crippledHealth = 6f;

    /** True while the drone goes to / sits at a station to be healed. */
    public boolean charging;
    /** Set by a station every tick while it heals this drone. */
    public float dockedTime = -1000f;
    public DroneStationBuild dockStation;

    public DroneNet.TeamNet net(){
        return DroneNet.of(unit.team);
    }

    public boolean isDocked(){
        return Time.time - dockedTime < 5f;
    }

    public static boolean docked(Unit u){
        return u.controller() instanceof DroneAI && ((DroneAI)u.controller()).isDocked();
    }

    /** Does the drone's job for this tick. Returns true if the drone is "working" (so it discharges), false if it is idle. */
    protected abstract boolean work();

    @Override
    public void updateTargeting(){
        //drones pick their targets themselves
    }

    @Override
    public void updateMovement(){
        if(unit == null || !unit.isValid()) return;

        if(unit.health < crippledHealth){
            unit.apply(NuclearStatusEffects.droneCrippled, 15f);
        }

        updateCharge();

        boolean active;
        if(charging){
            active = !chargeStep();
        }else{
            active = work();
        }

        if(active){
            unit.health = Math.max(1f, unit.health - decayPerSecond * Time.delta / 60f);
        }
    }

    protected void updateCharge(){
        if(!charging){
            if(unit.health < unit.maxHealth * chargeFraction && net().closestStation(unit.x, unit.y) != null){
                charging = true;
            }
        }else if(unit.health >= unit.maxHealth - 0.5f || net().closestStation(unit.x, unit.y) == null){
            charging = false;
            dockStation = null;
        }
    }

    /** Flies to the closest station. Returns true while the station is healing this drone. */
    protected boolean chargeStep(){
        DroneStationBuild s = net().closestStation(unit.x, unit.y);
        if(s == null) return false;
        Tmp.v2.trns((unit.id % 12) * 30f, s.block.size * 4f + 14f).add(s.x, s.y);
        flyTo(Tmp.v2.x, Tmp.v2.y, 20f);
        return isDocked();
    }

    /** Smooth flight to a point. slow = distance at which the drone starts to brake. */
    protected void flyTo(float tx, float ty, float slow){
        Tmp.v1.set(tx, ty).sub(unit.x, unit.y);
        float dst = Tmp.v1.len();
        if(dst < 0.5f) return;
        Tmp.v1.setLength(unit.speed() * Mathf.clamp(dst / slow));
        unit.moveAt(Tmp.v1);
    }

    /** Flies in a circle around a point. */
    protected void orbit(float cx, float cy, float radius, float degPerTick){
        float ang = Time.time * degPerTick + unit.id * 41f;
        Tmp.v2.trns(ang, radius).add(cx, cy);
        flyTo(Tmp.v2.x, Tmp.v2.y, 16f);
    }

    /** Waits for work circling the closest station. No discharge. */
    protected void idle(float baseRadius, float degPerTick){
        DroneStationBuild s = net().closestStation(unit.x, unit.y);
        if(s == null) return;
        orbit(s.x, s.y, baseRadius + (unit.id % 3) * 7f, degPerTick);
    }
}
