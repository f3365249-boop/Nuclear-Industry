package nuclear.drone;

import arc.util.*;
import mindustry.gen.*;

/**
 * Flies to the closest worker/guard below 50% hp and heals it with a beam, like a station does.
 * Drones below 6 hp (the ones crawling to a station) have priority.
 */
public class MedicDroneAI extends DroneAI{
    public static final float healRange = 36f;

    public Unit patient;
    public boolean healing;

    private float retarget;

    @Override
    protected boolean work(){
        healing = false;

        if(!patientValid() || (retarget -= Time.delta) <= 0f){
            retarget = 15f;
            if(!patientValid()) patient = pick();
        }

        if(patient == null){
            idle(62f, 0.55f);
            return false;
        }

        if(unit.within(patient, healRange)){
            patient.heal(healPerSecond * Time.delta / 60f);
            healing = true;
            //stay close, the patient keeps moving
            flyTo(patient.x, patient.y, 40f);
        }else{
            flyTo(patient.x, patient.y, 24f);
        }
        return true;
    }

    private boolean patientValid(){
        return patient != null && patient.isValid() && patient.health < patient.maxHealth * 0.9f && !docked(patient);
    }

    private Unit pick(){
        DroneNet.TeamNet n = net();
        Unit best = null;
        float bestScore = Float.MAX_VALUE;
        for(int pass = 0; pass < 2; pass++){
            arc.struct.Seq<Unit> list = pass == 0 ? n.workers : n.guards;
            for(int i = 0; i < list.size; i++){
                Unit u = list.get(i);
                if(!u.isValid() || !(u.controller() instanceof DroneAI)) continue;
                if(u.health >= u.maxHealth * chargeFraction) continue;
                if(docked(u) || claimed(n, u)) continue;

                float score = unit.dst2(u);
                if(u.health < crippledHealth) score *= 0.0001f;
                if(score < bestScore){
                    bestScore = score;
                    best = u;
                }
            }
        }
        return best;
    }

    private boolean claimed(DroneNet.TeamNet n, Unit u){
        for(int i = 0; i < n.medics.size; i++){
            Unit m = n.medics.get(i);
            if(m != unit && m.controller() instanceof MedicDroneAI && ((MedicDroneAI)m.controller()).patient == u) return true;
        }
        return false;
    }
}
