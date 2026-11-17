package nuclear.world.blocks.power;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import nuclear.*;
import nuclear.content.*;
import nuclear.radiation.*;

/**
 * Uranium reactor (4x4). Burns uranium-235 (one item per {@link #burnSeconds}, regardless of load).
 * The more uranium-235 is inside, the faster it heats up. The hotter it is, the more water it boils
 * into steam (which cools it down). It explodes leaving a radiation zone if destroyed while hotter
 * than {@link #dangerTemp} or if left without water for {@link #meltdownSeconds}.
 */
public class UraniumReactor extends Block{
    public Item fuelItem;
    public Liquid inputLiquid = Liquids.water;
    public Liquid outputLiquid;

    /** Temperatures, in degrees Celsius. */
    public float maxTemp = 1250f, dangerTemp = 800f, ambientTemp = 20f;
    /** Seconds one fuel item burns. Does not depend on how much fuel is inside. */
    public float burnSeconds = 120f;
    /** Heating in degrees per second with a completely full fuel load. Scales linearly with the fuel inside. */
    public float maxHeatPerSecond = 30f;
    /** Water boiled / steam produced per second at max temperature (scales linearly with temperature). */
    public float steamPerSecond = 60f;
    /** Temperature at which a completely full, fully watered reactor settles. Less fuel settles lower. */
    public float equilibriumTemp = 1100f;
    /** Fraction of temperature lost to the air per second (slow passive cooling). */
    public float passiveCooling = 0.001f;
    /** Seconds without water above danger temperature before the reactor blows. */
    public float meltdownSeconds = 3f;

    public float explosionRadiusTiles = 27f;
    public float explosionDamage = 10000f; //about 2 thorium reactors
    public float zoneBaseRadiusTiles = 8f;
    public float zoneRadiusPerFuelTiles = 0.6f;
    public float zoneSeconds = 600f; //10 minutes

    protected float coolPerWater;
    public TextureRegion lightsRegion;

    public UraniumReactor(String name){
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        hasLiquids = true;
        outputsLiquid = true;
        rebuildable = false;
        emitLight = true;
        buildType = UraniumReactorBuild::new;

        //make clients receive temperature etc. if this game version supports block syncing
        NuclearUtil.sync(this);
    }

    @Override
    public void init(){
        super.init();
        float boilAtEq = steamPerSecond * (equilibriumTemp - ambientTemp) / (maxTemp - ambientTemp);
        coolPerWater = (maxHeatPerSecond - passiveCooling * (equilibriumTemp - ambientTemp)) / boilAtEq;
    }

    @Override
    public void load(){
        super.load();
        lightsRegion = Core.atlas.find(name + "-lights");
    }

    @Override
    public void setBars(){
        super.setBars();
        removeBar("liquid");

        addBar("nuclear-temp", (UraniumReactorBuild e) -> new Bar(
            () -> Core.bundle.format("bar.nuclear-industry.temp", Mathf.round(e.temp)),
            () -> Pal.lightOrange,
            () -> Mathf.clamp(e.temp / maxTemp)));

        addBar("nuclear-fuel", (UraniumReactorBuild e) -> new Bar(
            () -> Core.bundle.get("bar.nuclear-industry.fuel"),
            () -> NuclearFx.radColor,
            () -> e.items.get(fuelItem) / (float)itemCapacity));

        addBar("nuclear-water", (UraniumReactorBuild e) -> new Bar(
            () -> inputLiquid.localizedName,
            () -> inputLiquid.barColor(),
            () -> e.liquids.get(inputLiquid) / liquidCapacity));

        addBar("nuclear-steam", (UraniumReactorBuild e) -> new Bar(
            () -> outputLiquid.localizedName,
            () -> Color.lightGray,
            () -> e.liquids.get(outputLiquid) / liquidCapacity));
    }

    public class UraniumReactorBuild extends Building{
        public float temp = ambientTemp;
        /** Ticks left on the fuel item currently burning. */
        public float burnLeft;
        /** Seconds spent starved of water above danger temperature. */
        public float meltdown;
        /** Smoothed fraction (0..1) of the water demand that is being met. */
        public float satisfaction = 1f;

        @Override
        public boolean acceptItem(Building source, Item item){
            return item == fuelItem && items.get(item) < itemCapacity;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            return liquid == inputLiquid && liquids.get(liquid) < liquidCapacity;
        }

        @Override
        public void updateTile(){
            float dt = delta() / 60f;

            //fuel
            if(burnLeft <= 0f && items.get(fuelItem) > 0){
                items.remove(fuelItem, 1);
                burnLeft = burnSeconds * 60f;
            }
            boolean burning = burnLeft > 0f;
            if(burning) burnLeft -= delta();

            //boiling water to steam: the hotter, the more water is eaten and the more steam is made
            float frac = Mathf.clamp((temp - ambientTemp) / (maxTemp - ambientTemp));
            float wanted = steamPerSecond * frac * dt;
            float used = Math.min(wanted, liquids.get(inputLiquid));
            if(used > 0f){
                liquids.remove(inputLiquid, used);
                liquids.add(outputLiquid, used);
            }
            float ratio = wanted > 0.00001f ? used / wanted : 1f;
            satisfaction = Mathf.lerpDelta(satisfaction, ratio, 0.1f);

            //excess steam is vented so cooling never stalls
            float excess = liquids.get(outputLiquid) - liquidCapacity;
            if(excess > 0f){
                liquids.remove(outputLiquid, excess);
                if(Mathf.chanceDelta(0.3f)){
                    NuclearFx.steamVent.at(x + Mathf.range(size * Vars.tilesize / 2f), y + Mathf.range(size * Vars.tilesize / 2f));
                }
            }

            //temperature
            //heating scales with the uranium inside (the burning rod counts too)
            float inside = items.get(fuelItem) + (burning ? 1f : 0f);
            float load = Mathf.clamp(inside / (float)itemCapacity);
            float heat = burning ? maxHeatPerSecond * load * dt : 0f;
            temp += heat - used * coolPerWater - (temp - ambientTemp) * passiveCooling * dt;
            temp = Mathf.clamp(temp, ambientTemp, maxTemp);

            //meltdown countdown
            if(temp >= dangerTemp && satisfaction < 0.35f){
                meltdown += dt;
                if(meltdown >= meltdownSeconds && !Vars.net.client()){
                    kill();
                }
            }else{
                meltdown = Math.max(0f, meltdown - dt * 2f);
            }

            dumpLiquid(outputLiquid);
        }

 @Override
        public void onDestroyed(){
            float fuel = items.get(fuelItem) + (burnLeft > 0f ? 1f : 0f);
            boolean explodes = temp >= dangerTemp && Vars.state.rules.reactorExplosions;
            float cx = x, cy = y;
            super.onDestroyed();
            if(!explodes) return;

            float fuelFrac = Mathf.clamp(fuel / itemCapacity);
            float radius = explosionRadiusTiles * Vars.tilesize * (0.6f + 0.4f * fuelFrac);
            float damage = explosionDamage * (0.6f + 0.4f * fuelFrac);

            Fx.reactorExplosion.at(cx, cy);
            for(int i = 0; i < 6; i++){
                Time.run(Mathf.random(40f), () -> Fx.massiveExplosion.at(cx + Mathf.range(30f), cy + Mathf.range(30f)));
            }
            Effect.shake(6f, 16f, cx, cy);

            if(!Vars.net.client()){
                Damage.damage(cx, cy, radius, damage);
            }

            for(int i = 0; i < 20; i++){
                Time.run(Mathf.random(80f), () -> {
                    Tmp.v1.rnd(Mathf.random(40f));
                    Fx.explosion.at(Tmp.v1.x + cx, Tmp.v1.y + cy);
                });
            }
            for(int i = 0; i < 70; i++){
                Time.run(Mathf.random(90f), () -> {
                    Tmp.v1.rnd(Mathf.random(120f));
                    Fx.smokeCloud.at(Tmp.v1.x + cx, Tmp.v1.y + cy);
                });
            }

            //zone is created by the server (or singleplayer) and synced to clients
            if(!Vars.net.client()){
                float zoneRadius = (zoneBaseRadiusTiles + fuel * zoneRadiusPerFuelTiles) * Vars.tilesize;
                RadiationSystem.addZone(cx, cy, zoneRadius, zoneSeconds * 60f);
            }
        }

        @Override
        public void draw(){
            super.draw();

            float frac = Mathf.clamp((temp - ambientTemp) / (maxTemp - ambientTemp));
            if(frac > 0.01f && lightsRegion != null && lightsRegion.found()){
                Draw.color(Tmp.c1.set(Color.valueOf("ffb25a")).lerp(Color.scarlet, frac));
                Draw.alpha(frac * 0.9f);
                Draw.blend(Blending.additive);
                Draw.rect(lightsRegion, x, y);
                Draw.blend();
                Draw.reset();
            }

            if(meltdown > 0f){
                float a = Mathf.clamp(meltdown / meltdownSeconds) * (0.45f + 0.35f * Mathf.absin(Time.time, 4f, 1f));
                Draw.mixcol(Color.scarlet, a);
                Draw.rect(block.region, x, y);
                Draw.mixcol();
            }
        }

        @Override
        public void drawLight(){
            float frac = Mathf.clamp(temp / maxTemp);
            Drawf.light(x, y, (100f + Mathf.absin(5f, 5f)) * frac, Color.valueOf("ff8a3a"), 0.6f * frac);
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(temp);
            write.f(burnLeft);
            write.f(meltdown);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            temp = read.f();
            burnLeft = read.f();
            meltdown = read.f();
        }
    }
}
