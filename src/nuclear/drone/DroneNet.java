package nuclear.drone;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import nuclear.content.*;
import nuclear.drone.DroneTask.*;
import nuclear.world.blocks.drone.DroneDepot.*;
import nuclear.world.blocks.drone.DroneReceiver.*;
import nuclear.world.blocks.drone.DroneStation.*;

/**
 * The "brain" of the drone network. One {@link TeamNet} per team.
 * - keeps registries of stations / depots / receivers / drones;
 * - hands out haul jobs to idle workers (server side, every {@link #assignInterval} ticks);
 * - finds enemies near workers for the guards.
 * Reservations are recomputed from the live tasks each time, so a drone that dies never leaks a reservation.
 */
public class DroneNet{
    public static final int maxDronesPerTeam = 150;
    public static final int batch = 10;
    public static final float scanInterval = 15f, assignInterval = 20f;
    /** Enemies closer than this to any worker drone are threats. */
    public static final float protectRadius = 220f;
    public static final Color beamColor = Color.valueOf("6bd6ff");

    private static final ObjectMap<Team, TeamNet> nets = new ObjectMap<>();
    private static float scanTimer, assignTimer;

    private static final Seq<ReceiverBuild> tmpReceivers = new Seq<>();
    private static final Seq<Unit> tmpIdle = new Seq<>();

    public static class TeamNet{
        public final Team team;
        public final Seq<DroneStationBuild> stations = new Seq<>();
        public final Seq<DepotBuild> depots = new Seq<>();
        public final Seq<ReceiverBuild> receivers = new Seq<>();
        public final Seq<Unit> workers = new Seq<>(), guards = new Seq<>(), medics = new Seq<>();
        public final Seq<Unit> threats = new Seq<>();
        public int droneCount;

        TeamNet(Team team){
            this.team = team;
        }

        public DroneStationBuild closestStation(float x, float y){
            DroneStationBuild best = null;
            float bd = Float.MAX_VALUE;
            for(int i = 0; i < stations.size; i++){
                DroneStationBuild s = stations.get(i);
                if(!s.isValid()) continue;
                float d = Mathf.dst2(x, y, s.x, s.y);
                if(d < bd){
                    bd = d;
                    best = s;
                }
            }
            return best;
        }

        public Unit nearestThreat(float x, float y){
            Unit best = null;
            float bd = Float.MAX_VALUE;
            for(int i = 0; i < threats.size; i++){
                Unit u = threats.get(i);
                if(!u.isValid()) continue;
                float d = Mathf.dst2(x, y, u.x, u.y);
                if(d < bd){
                    bd = d;
                    best = u;
                }
            }
            return best;
        }
    }

    public static TeamNet of(Team team){
        TeamNet n = nets.get(team);
        if(n == null){
            n = new TeamNet(team);
            nets.put(team, n);
        }
        return n;
    }

    public static boolean valid(mindustry.gen.Building b, Team team){
        return b != null && b.isValid() && b.team == team;
    }

    public static void init(){
        Events.on(WorldLoadEvent.class, e -> nets.clear());
        Events.on(ResetEvent.class, e -> nets.clear());
        Events.run(Trigger.update, DroneNet::update);
        Events.run(Trigger.draw, DroneNet::draw);
    }

    /** Called by the drone blocks from their first update. */
    public static void register(mindustry.gen.Building b){
        TeamNet n = of(b.team);
        if(b instanceof DroneStationBuild) n.stations.addUnique((DroneStationBuild)b);
        else if(b instanceof DepotBuild) n.depots.addUnique((DepotBuild)b);
        else if(b instanceof ReceiverBuild) n.receivers.addUnique((ReceiverBuild)b);
    }

    private static void update(){
        if(!Vars.state.isGame() || Vars.state.isPaused() || Vars.net.client()) return;

        float dt = Time.delta;
        if((scanTimer -= dt) <= 0f){
            scanTimer = scanInterval;
            scan();
        }
        if((assignTimer -= dt) <= 0f){
            assignTimer = assignInterval;
            for(TeamNet n : nets.values()){
                assign(n);
            }
        }
    }

    /** Refreshes drone lists and threats, drops dead buildings from the registries. */
    private static void scan(){
        for(TeamNet n : nets.values()){
            n.workers.clear();
            n.guards.clear();
            n.medics.clear();
            n.stations.removeAll(b -> !b.isValid());
            n.depots.removeAll(b -> !b.isValid());
            n.receivers.removeAll(b -> !b.isValid());
        }

        Groups.unit.each(u -> {
            if(!u.isValid()) return;
            if(u.type == NuclearUnits.workerDrone) of(u.team).workers.add(u);
            else if(u.type == NuclearUnits.guardDrone) of(u.team).guards.add(u);
            else if(u.type == NuclearUnits.medicDrone) of(u.team).medics.add(u);
        });

        for(TeamNet n : nets.values()){
            n.droneCount = n.workers.size + n.guards.size + n.medics.size;
            n.threats.clear();
            if(n.guards.size == 0) continue;

            for(int i = 0; i < n.workers.size && n.threats.size < 24; i++){
                Unit w = n.workers.get(i);
                final TeamNet fn = n;
                Units.nearbyEnemies(n.team, w.x - protectRadius, w.y - protectRadius, protectRadius * 2f, protectRadius * 2f, e -> {
                    if(e.isValid() && e.within(w.x, w.y, protectRadius) && fn.threats.size < 24 && !fn.threats.contains(e)){
                        fn.threats.add(e);
                    }
                });
            }
        }
    }

    private static void assign(TeamNet n){
        if(n.receivers.isEmpty() || n.depots.isEmpty() || n.workers.isEmpty()) return;

        //reservations from live tasks
        for(int i = 0; i < n.depots.size; i++) java.util.Arrays.fill(n.depots.get(i).reserved, 0);
        for(int i = 0; i < n.receivers.size; i++) n.receivers.get(i).reserved = 0;

        tmpIdle.clear();
        for(int i = 0; i < n.workers.size; i++){
            Unit w = n.workers.get(i);
            if(!w.isValid() || !(w.controller() instanceof WorkerDroneAI)) continue;
            WorkerDroneAI ai = (WorkerDroneAI)w.controller();
            DroneTask t = ai.task;
            if(t == null){
                if(ai.canTakeJob()) tmpIdle.add(w);
                continue;
            }
            if(t.stage == Stage.pickup){
                if(valid(t.depot, n.team)) t.depot.reserved[t.item.id] += t.amount;
                if(valid(t.receiver, n.team)) t.receiver.reserved += t.amount;
            }else if(t.stage == Stage.deliver){
                if(valid(t.receiver, n.team)) t.receiver.reserved += w.stack.amount;
            }
        }

        if(tmpIdle.isEmpty()) return;

        //hungriest receivers first, so 20 receivers are served fairly
        tmpReceivers.clear();
        for(int i = 0; i < n.receivers.size; i++){
            ReceiverBuild r = n.receivers.get(i);
            if(r.isValid() && r.filter != null && r.need() >= Math.min(5, r.block.itemCapacity)) tmpReceivers.add(r);
        }
        tmpReceivers.sort((a, b) -> {
            int c = Float.compare(a.fillFraction(), b.fillFraction());
            return c != 0 ? c : Float.compare(a.lastServed, b.lastServed);
        });

        for(int ri = 0; ri < tmpReceivers.size && !tmpIdle.isEmpty(); ri++){
            ReceiverBuild r = tmpReceivers.get(ri);
            Item item = r.filter;

            int need = r.need();
            while(need >= Math.min(5, r.block.itemCapacity) && !tmpIdle.isEmpty()){
                int want = Math.min(batch, need);
                DepotBuild depot = pickDepot(n, r, item, want);
                if(depot == null) break;

                int amount = Math.min(want, depot.items.get(item) - depot.reserved[item.id]);
                if(amount <= 0) break;

                //closest idle drone to that depot
                int bi = -1;
                float bd = Float.MAX_VALUE;
                for(int i = 0; i < tmpIdle.size; i++){
                    Unit w = tmpIdle.get(i);
                    float d = w.dst2(depot);
                    if(d < bd){
                        bd = d;
                        bi = i;
                    }
                }
                Unit drone = tmpIdle.get(bi);
                tmpIdle.remove(bi);

                DroneTask t = new DroneTask();
                t.depot = depot;
                t.receiver = r;
                t.item = item;
                t.amount = amount;
                ((WorkerDroneAI)drone.controller()).assign(t);

                depot.reserved[item.id] += amount;
                r.reserved += amount;
                r.lastServed = Time.time;
                need -= amount;
            }
        }
    }

    /** Best depot for a receiver: has the stock, is close to the receiver, prefers full batches and fuller depots. */
    private static DepotBuild pickDepot(TeamNet n, ReceiverBuild r, Item item, int want){
        DepotBuild best = null;
        float bestScore = Float.MAX_VALUE;
        for(int i = 0; i < n.depots.size; i++){
            DepotBuild d = n.depots.get(i);
            if(!d.isValid()) continue;
            int avail = d.items.get(item) - d.reserved[item.id];
            if(avail < 1) continue;

            float score = d.dst(r) + (avail >= want ? 0f : 600f) - Math.min(avail, 50) * 0.5f;
            if(score < bestScore){
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    /** Medic beams. Drawn from the AI state, so only where the AI runs (host / singleplayer). */
    private static void draw(){
        if(Vars.headless || !Vars.state.isGame()) return;

        Groups.unit.each(u -> {
            if(u.type != NuclearUnits.medicDrone || !(u.controller() instanceof MedicDroneAI)) return;
            MedicDroneAI ai = (MedicDroneAI)u.controller();
            if(!ai.healing || ai.patient == null || !ai.patient.isValid()) return;
            drawBeam(u.x, u.y, ai.patient.x, ai.patient.y);
        });
    }

    public static void drawBeam(float x1, float y1, float x2, float y2){
        float z = Draw.z();
        Draw.z(Layer.effect);
        Draw.color(beamColor);
        Draw.alpha(0.55f + 0.25f * Mathf.absin(Time.time, 5f, 1f));
        Lines.stroke(2.2f + Mathf.absin(Time.time, 6f, 0.8f));
        Lines.line(x1, y1, x2, y2);
        Draw.color(Color.white);
        Draw.alpha(0.8f);
        Lines.stroke(0.8f);
        Lines.line(x1, y1, x2, y2);
        Draw.color(beamColor);
        Fill.circle(x2, y2, 2f + Mathf.absin(Time.time, 6f, 1.2f));
        Draw.reset();
        Draw.z(z);
    }
}
