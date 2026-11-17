package nuclear.world.blocks.drone;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import nuclear.drone.*;

/** Builds the selected drone type from items put into it. */
public class DroneFactory extends Block{
    public final Seq<DronePlan> plans = new Seq<>();
    private boolean[] usable;

    public static class DronePlan{
        public final UnitType unit;
        /** Build time in ticks. */
        public final float time;
        public final ItemStack[] requirements;

        public DronePlan(UnitType unit, float time, ItemStack[] requirements){
            this.unit = unit;
            this.time = time;
            this.requirements = requirements;
        }
    }

    public DroneFactory(String name){
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        hasPower = true;
        configurable = true;
        clearOnDoubleTap = true;
        buildType = DroneFactoryBuild::new;

        config(UnitType.class, (DroneFactoryBuild b, UnitType u) -> {
            if(b.plan != u){
                b.plan = u;
                b.progress = 0f;
            }
        });
        configClear((DroneFactoryBuild b) -> {
            b.plan = null;
            b.progress = 0f;
        });
    }

    @Override
    public void init(){
        usable = new boolean[Vars.content.items().size];
        int cap = 10;
        for(DronePlan p : plans){
            for(ItemStack s : p.requirements){
                usable[s.item.id] = true;
                cap = Math.max(cap, s.amount * 2);
            }
        }
        itemCapacity = cap;
        super.init();
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("progress", (DroneFactoryBuild e) -> new Bar(
            () -> Core.bundle.get("bar.progress"),
            () -> Pal.ammo,
            () -> e.currentPlan() == null ? 0f : e.progress / e.currentPlan().time));
    }

    public class DroneFactoryBuild extends Building{
        public UnitType plan;
        public float progress;

        public DronePlan currentPlan(){
            if(plan == null) return null;
            for(int i = 0; i < plans.size; i++){
                if(plans.get(i).unit == plan) return plans.get(i);
            }
            return null;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return usable[item.id] && items.get(item) < itemCapacity;
        }

        @Override
        public int getMaximumAccepted(Item item){
            return itemCapacity;
        }

        private boolean hasAll(DronePlan p){
            for(ItemStack s : p.requirements){
                if(items.get(s.item) < s.amount) return false;
            }
            return true;
        }

        @Override
        public boolean shouldConsume(){
            DronePlan p = currentPlan();
            return p != null && hasAll(p) && DroneNet.of(team).droneCount < DroneNet.maxDronesPerTeam;
        }

        @Override
        public void updateTile(){
            DronePlan p = currentPlan();
            if(p == null || !hasAll(p)) return;
            if(DroneNet.of(team).droneCount >= DroneNet.maxDronesPerTeam) return;

            progress += edelta();
            if(progress >= p.time){
                progress = 0f;
                for(ItemStack s : p.requirements) items.remove(s.item, s.amount);

                Fx.spawn.at(x, y);
                if(!Vars.net.client()){
                    Unit u = p.unit.create(team);
                    u.set(x + Mathf.range(6f), y + Mathf.range(6f));
                    u.rotation = 90f;
                    u.add();
                }
            }
        }

        @Override
        public void buildConfiguration(Table table){
            Seq<UnitType> types = new Seq<>();
            for(DronePlan p : plans) types.add(p.unit);
            ItemSelection.buildTable(DroneFactory.this, table, types, () -> plan, this::configure);
            table.row();

            //live list of what the selected drone needs: "have / need"
            Table info = new Table();
            info.left();
            final UnitType[] shown = {null};
            final boolean[] built = {false};
            info.update(() -> {
                if(built[0] && shown[0] == plan) return;
                built[0] = true;
                shown[0] = plan;
                info.clear();

                DronePlan p = currentPlan();
                if(p == null){
                    info.add(Core.bundle.get("nuclear-industry.select-drone")).color(Color.lightGray).left();
                    return;
                }
                info.add(Core.bundle.format("nuclear-industry.build-time", Strings.fixed(p.time / 60f, 1))).color(Color.lightGray).left().row();
                for(ItemStack s : p.requirements){
                    info.table(r -> {
                        r.left();
                        r.image(s.item.uiIcon).size(Vars.iconSmall).padRight(4f);
                        r.label(() -> {
                            int have = items.get(s.item);
                            return (have >= s.amount ? "[lightgray]" : "[scarlet]") + have + "[]/" + s.amount + " " + s.item.localizedName;
                        }).left();
                    }).left().padTop(2f).row();
                }
            });
            table.add(info).left().padTop(6f).minWidth(160f);
        }

        @Override
        public Object config(){
            return plan;
        }

        @Override
        public void draw(){
            super.draw();
            DronePlan p = currentPlan();
            if(p != null && p.unit.fullIcon != null){
                float frac = Mathf.clamp(progress / p.time);
                Draw.z(Layer.blockOver);
                Draw.alpha(0.25f + 0.75f * frac);
                Draw.rect(p.unit.fullIcon, x, y, 18f, 18f);
                Draw.reset();
            }
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.s(plan == null ? -1 : plan.id);
            write.f(progress);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            int id = read.s();
            plan = id == -1 ? null : Vars.content.unit(id);
            progress = read.f();
        }
    }
}
