package nuclear.drone;

import mindustry.type.*;
import nuclear.world.blocks.drone.DroneDepot.*;
import nuclear.world.blocks.drone.DroneReceiver.*;

/** One haul job of a worker drone: depot -> receiver. */
public class DroneTask{
    public enum Stage{
        /** Flying to the depot to take items. */
        pickup,
        /** Carrying items to the receiver. */
        deliver,
        /** Receiver is gone, returning items to a depot. */
        back
    }

    public DepotBuild depot;
    public ReceiverBuild receiver;
    public Item item;
    public int amount;
    public Stage stage = Stage.pickup;
    public int retries;
}
