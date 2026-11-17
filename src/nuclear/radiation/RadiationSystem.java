package nuclear.radiation;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.entities.units.*;
import mindustry.graphics.*;
import mindustry.world.blocks.ConstructBlock.*;
import nuclear.content.*;

/**
 * Handles every radiation zone: damage, build blocking, rendering, persistence and network sync.
 * The server owns the zones, clients receive new ones through a custom packet; existing
 * ones are stored in the save (Rules.tags), so they also reach clients joining later.
 */
public class RadiationSystem{
    /** 2% of max health every 2 seconds. */
    public static final float damagePercent = 0.02f, damageInterval = 120f;
    /** How long the status lingers after leaving a zone. */
    public static final float statusTicks = 180f;

    private static final String tag = "nuclear-industry-zones", packet = "nuclear-zone-add";
    private static final Color zoneColor = Color.valueOf("5cff3a");

    public static final Seq<RadiationZone> zones = new Seq<>();

    private static final Seq<BuildPlan> tmpPlans = new Seq<>();
    private static float scanTimer, saveTimer, fxTimer, toastTimer, activeTimer;

    public static void init(){
        Events.on(WorldLoadEvent.class, e -> load());
        Events.on(ResetEvent.class, e -> zones.clear());
        Events.run(Trigger.update, RadiationSystem::update);
        Events.run(Trigger.draw, RadiationSystem::draw);
        Events.on(BlockBuildBeginEvent.class, RadiationSystem::onBuildBegin);

        if(Vars.netClient != null){
            Vars.netClient.addPacketHandler(packet, str -> {
                RadiationZone z = RadiationZone.parse(str);
                if(z != null) insert(z);
            });
        }
    }

    /** Creates a zone. Call on server / singleplayer only. */
    public static void addZone(float x, float y, float radius, float ticks){
        RadiationZone z = new RadiationZone(Mathf.rand.nextLong(), x, y, radius, ticks);
        insert(z);
        if(Vars.net.server()){
            Call.clientPacketReliable(packet, z.serialize());
        }
    }

    private static void insert(RadiationZone z){
        if(zones.contains(o -> o.id == z.id)) return;
        zones.add(z);
        if(!Vars.headless) NuclearFx.radiationWave.at(z.x, z.y, z.radius);
        saveTimer = 0f;
    }

    /** True if a block of the given size centered here would be inside a radiation zone. */
    public static boolean blocked(float cx, float cy, int size){
        float extent = size * Vars.tilesize / 2f;
        for(RadiationZone z : zones){
            if(z.touches(cx, cy, extent)) return true;
        }
        return false;
    }

    private static void load(){
        zones.clear();
        String s = Vars.state.rules.tags.get(tag, "");
        if(s.isEmpty()) return;
        for(String part : s.split(";")){
            RadiationZone z = RadiationZone.parse(part);
            if(z != null) zones.add(z);
        }
    }

    private static void save(){
        if(Vars.net.client()) return;
        if(zones.isEmpty()){
            Vars.state.rules.tags.remove(tag);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for(RadiationZone z : zones){
            if(sb.length() > 0) sb.append(';');
            sb.append(z.serialize());
        }
        Vars.state.rules.tags.put(tag, sb.toString());
    }

    private static void update(){
        if(!Vars.state.isGame() || Vars.state.isPaused()) return;

        float dt = Time.delta;
        for(int i = zones.size - 1; i >= 0; i--){
            RadiationZone z = zones.get(i);
            z.remaining -= dt;
            if(z.remaining <= 0f){
                zones.remove(i);
                saveTimer = 0f;
            }
        }

        if((saveTimer -= dt) <= 0f){
            saveTimer = 60f;
            save();
        }

        if(!zones.isEmpty()) activeTimer = statusTicks + 60f;
        else activeTimer -= dt;
        if(activeTimer <= 0f) return;

        //no building inside zones
        if(!zones.isEmpty()) filterPlans();

        //spawn particles around the camera
        if(!Vars.headless && !zones.isEmpty() && (fxTimer -= dt) <= 0f){
            fxTimer = 5f;
            Core.camera.bounds(Tmp.r1);
            for(RadiationZone z : zones){
                for(int i = 0; i < 2; i++){
                    float px = z.x + Mathf.range(z.radius), py = z.y + Mathf.range(z.radius);
                    if(Mathf.dst(px, py, z.x, z.y) <= z.radius && Tmp.r1.contains(px, py)){
                        NuclearFx.radiationParticle.at(px, py);
                    }
                }
            }
        }

        if(Vars.net.client()) return;

        //apply the status
        if((scanTimer -= dt) <= 0f){
            scanTimer = 10f;
            for(RadiationZone z : zones){
                Groups.unit.each(u -> {
                    if(u.isValid() && u.within(z.x, z.y, z.radius + u.hitSize / 2f)){
                        u.apply(NuclearStatusEffects.radiation, statusTicks);
                    }
                });
            }
        }

        //percentage damage, ignores armor
        Groups.unit.each(u -> {
            if(u.isValid() && u.hasEffect(NuclearStatusEffects.radiation)){
                u.damageContinuousPierce(u.maxHealth * damagePercent / damageInterval);
            }
        });
    }

    private static void filterPlans(){
        Groups.unit.each(u -> {
            if(u.plans == null || u.plans.size == 0) return;

            boolean any = false;
            for(BuildPlan p : u.plans){
                if(isBlockedPlan(p)){
                    any = true;
                    break;
                }
            }
            if(!any) return;

            tmpPlans.clear();
            for(BuildPlan p : u.plans){
                if(!isBlockedPlan(p)) tmpPlans.add(p);
            }
            u.plans.clear();
            for(BuildPlan p : tmpPlans) u.plans.addLast(p);

            if(!Vars.headless && u == Vars.player.unit() && (toastTimer -= Time.delta) <= 0f){
                toastTimer = 60f;
                Vars.ui.showInfoToast(Core.bundle.get("nuclear-industry.zone-no-build"), 2f);
            }
        });
    }

    private static boolean isBlockedPlan(BuildPlan p){
        return !p.breaking && p.block != null && blocked(p.drawx(), p.drawy(), p.block.size);
    }

    /** Fallback if a unit managed to start construction before the plan filter ran. */
    private static void onBuildBegin(BlockBuildBeginEvent e){
        if(e.breaking || Vars.net.client() || zones.isEmpty()) return;
        if(e.tile.build instanceof ConstructBuild){
            ConstructBuild cb = (ConstructBuild) e.tile.build;
            if(cb.previous == Blocks.air && blocked(cb.x, cb.y, cb.block.size)){
                e.tile.setNet(Blocks.air);
            }
        }
    }

    private static void draw(){
        if(zones.isEmpty()) return;

        Core.camera.bounds(Tmp.r1);
        float z = Draw.z();
        Draw.z(Layer.block - 5f);
        Draw.color(zoneColor, 0.08f);
        for(RadiationZone zone : zones){
            Tmp.r2.setCentered(zone.x, zone.y, zone.radius * 2f);
            if(Tmp.r1.overlaps(Tmp.r2)){
                Fill.circle(zone.x, zone.y, zone.radius);
            }
        }
        Draw.reset();
        Draw.z(z);
    }
}