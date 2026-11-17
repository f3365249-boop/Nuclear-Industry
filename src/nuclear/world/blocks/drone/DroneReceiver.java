package nuclear.world.blocks.drone;

import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.storage.*;
import nuclear.*;
import nuclear.drone.*;

/**
 * 1x1 storage (50 of every item) with an item filter, configured like a vanilla sorter.
 * Worker drones keep it topped up with the selected item taken from any drone depot.
 */
public class DroneReceiver extends StorageBlock{

    public DroneReceiver(String name){
        super(name);
        coreMerge = false;
        update = true;
        configurable = true;
        saveConfig = true;
        clearOnDoubleTap = true;
        buildType = ReceiverBuild::new;
        NuclearUtil.sync(this);

        config(Item.class, (ReceiverBuild b, Item i) -> b.filter = i);
        configClear((ReceiverBuild b) -> b.filter = null);
    }

    public class ReceiverBuild extends StorageBuild{
        public Item filter;
        public boolean registered;
        /** Items promised to drones that are on the way. Recomputed by {@link DroneNet}. */
        public int reserved;
        public float lastServed;

        @Override
        public void updateTile(){
            if(!registered){
                registered = true;
                DroneNet.register(this);
            }
        }

        /** How many more items drones should bring. */
        public int need(){
            if(filter == null) return 0;
            return Math.max(0, block.itemCapacity - items.get(filter) - reserved);
        }

        public float fillFraction(){
            if(filter == null) return 1f;
            return (items.get(filter) + reserved) / (float)block.itemCapacity;
        }

        @Override
        public void buildConfiguration(Table table){
            ItemSelection.buildTable(DroneReceiver.this, table, Vars.content.items(), () -> filter, this::configure);
        }

        @Override
        public Item config(){
            return filter;
        }

        @Override
        public void draw(){
            super.draw();
            if(filter != null){
                Draw.rect(filter.fullIcon, x, y, 5f, 5f);
            }
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.s(filter == null ? -1 : filter.id);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            int id = read.s();
            filter = id == -1 ? null : Vars.content.item(id);
        }
    }
}
