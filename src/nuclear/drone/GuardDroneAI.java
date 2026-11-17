package nuclear.drone;

import arc.util.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.entities.units.*;

/**
 * Escorts working drones along their routes. If an enemy comes near any worker drone, flies to kill it.
 * With no workers on the road it waits near a station like everyone else.
 */
public class GuardDroneAI extends DroneAI{
    public Unit escort;

    private float scanTimer, escortTimer;
    private Unit shootTarget;

    @Override
    public void updateMovement(){
        if(unit == null || !unit.isValid()) return;
        autoFire();
        super.updateMovement();
    }

    /** Shoots whatever enemy is in range, whatever the drone is doing. */
    private void autoFire(){
        float range = unit.range();
        if((scanTimer -= Time.delta) <= 0f){
            scanTimer = 8f;
            shootTarget = Units.closestEnemy(unit.team, unit.x, unit.y, range, u -> true);
        }
        if(shootTarget != null && (!shootTarget.isValid() || !unit.within(shootTarget, range + shootTarget.hitSize / 2f))){
            shootTarget = null;
        }
        boolean shoot = shootTarget != null;
        unit.isShooting = shoot;
        if(shoot){
            unit.aimX = shootTarget.x;
            unit.aimY = shootTarget.y;
        }
        //drones skip the default AI targeting, so the weapon mounts must be driven here
        for(WeaponMount m : unit.mounts){
            m.shoot = shoot;
            m.rotate = shoot;
            if(shoot){
                m.aimX = shootTarget.x;
                m.aimY = shootTarget.y;
            }
        }
    }

    @Override
    protected boolean work(){
        Unit threat = net().nearestThreat(unit.x, unit.y);
        if(threat != null){
            float r = unit.range() * 0.65f;
            if(unit.dst(threat) > r * 1.2f){
                flyTo(threat.x, threat.y, 30f);
            }else{
                orbit(threat.x, threat.y, r, 2.2f);
            }
            return true;
        }

        if((escortTimer -= Time.delta) <= 0f || !escortValid()){
            escortTimer = 45f;
            escort = pickEscort();
        }
        if(escort != null){
            orbit(escort.x, escort.y, 32f, 1.6f);
            return true;
        }

        idle(48f, -0.7f);
        return false;
    }

    private boolean escortValid(){
        if(escort == null || !escort.isValid()) return false;
        return escort.controller() instanceof WorkerDroneAI && ((WorkerDroneAI)escort.controller()).task != null;
    }

    /** Picks a busy worker with the fewest guards already around it. */
    private Unit pickEscort(){
        DroneNet.TeamNet n = net();
        Unit best = null;
        float bestScore = Float.MAX_VALUE;
        for(int i = 0; i < n.workers.size; i++){
            Unit w = n.workers.get(i);
            if(!w.isValid() || !(w.controller() instanceof WorkerDroneAI)) continue;
            if(((WorkerDroneAI)w.controller()).task == null) continue;

            int guards = 0;
            for(int j = 0; j < n.guards.size; j++){
                Unit g = n.guards.get(j);
                if(g != unit && g.controller() instanceof GuardDroneAI && ((GuardDroneAI)g.controller()).escort == w) guards++;
            }

            float score = guards * 400f + unit.dst(w);
            if(score < bestScore){
                bestScore = score;
                best = w;
            }
        }
        return best;
    }
}
