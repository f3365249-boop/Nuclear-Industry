package nuclear;

import mindustry.world.*;

public class NuclearUtil{
    /**
     * Makes the server periodically send the full state (items, liquids, custom fields) of this block to clients.
     * Needed for every block whose contents are changed by something that clients cannot simulate on their own
     * (drones, random results). Silently does nothing on game versions without the flag.
     */
    public static void sync(Block block){
        try{
            Block.class.getField("sync").setBoolean(block, true);
        }catch(Throwable ignored){}
    }
}
