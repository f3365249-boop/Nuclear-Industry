package nuclear.world.blocks.drone;

import arc.*;
import mindustry.*;
import mindustry.world.blocks.storage.*;
import nuclear.*;
import nuclear.drone.*;

/** 1x1 storage for drones: holds {@code itemCapacity} (50) of every item type. Worker drones take items from here. */
public class DroneDepot extends StorageBlock{

    public DroneDepot(String name){
        super(name);
        coreMerge = false;
        update = true;
        buildType = DepotBuild::new;
        NuclearUtil.sync(this);
    }

    public class DepotBuild extends StorageBuild{
        public boolean registered;
        /** Items promised to drones that are on the way, by item id. Recomputed by {@link DroneNet}. */
        public int[] reserved = new int[Vars.content.items().size];

        @Override
        public void updateTile(){
            if(!registered){
                registered = true;
                DroneNet.register(this);
            }
        }
    }
}
