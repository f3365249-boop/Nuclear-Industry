package nuclear.world.blocks.drone;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;
import nuclear.drone.*;

/**
 * Drone station. Idle drones circle around it (without losing hp). Drones below 50% hp come here and
 * are healed by blue beams, only the discharged ones.
 */
public class DroneStation extends Block{
    public float healPerSecond = DroneAI.healPerSecond;
    /** Drones closer than this to the station can be beamed. */
    public float dockRange = 70f;
    public int maxBeams = 3;

    public DroneStation(String name){
        super(name);
        update = true;
        solid = true;
        buildType = DroneStationBuild::new;
    }

    public class DroneStationBuild extends Building{
        public boolean registered;
        public final Seq<Unit> healing = new Seq<>();
        private float fillTimer;

        @Override
        public void updateTile(){
            if(!registered){
                registered = true;
                DroneNet.register(this);
            }

            for(int i = healing.size - 1; i >= 0; i--){
                Unit u = healing.get(i);
                DroneAI ai = ai(u);
                if(ai == null || !ai.charging || u.health >= u.maxHealth - 0.1f || !within(u, dockRange)){
                    healing.remove(i);
                }
            }

            if(healing.size < maxBeams && (fillTimer -= Time.delta) <= 0f){
                fillTimer = 8f;
                fill();
            }

            for(int i = 0; i < healing.size; i++){
                Unit u = healing.get(i);
                DroneAI ai = ai(u);
                u.heal(healPerSecond * Time.delta / 60f);
                ai.dockedTime = Time.time;
                ai.dockStation = this;
            }
        }

        private DroneAI ai(Unit u){
            if(u == null || !u.isValid()) return null;
            return u.controller() instanceof DroneAI ? (DroneAI)u.controller() : null;
        }

        private void fill(){
            DroneNet.TeamNet n = DroneNet.of(team);
            for(int pass = 0; pass < 3 && healing.size < maxBeams; pass++){
                Seq<Unit> list = pass == 0 ? n.workers : pass == 1 ? n.guards : n.medics;
                for(int i = 0; i < list.size && healing.size < maxBeams; i++){
                    Unit u = list.get(i);
                    DroneAI ai = ai(u);
                    if(ai == null || !ai.charging || healing.contains(u) || !within(u, dockRange)) continue;
                    //another station is already healing it
                    if(ai.dockStation != null && ai.dockStation != this && ai.dockStation.isValid() && Time.time - ai.dockedTime < 6f) continue;
                    healing.add(u);
                }
            }
        }

        @Override
        public void draw(){
            super.draw();

            for(int i = 0; i < healing.size; i++){
                Unit u = healing.get(i);
                if(u.isValid()) DroneNet.drawBeam(x, y, u.x, u.y);
            }
        }

        @Override
        public void drawSelect(){
            Drawf.dashCircle(x, y, dockRange, DroneNet.beamColor);
        }
    }
}
