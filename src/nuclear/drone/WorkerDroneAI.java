package nuclear.drone;

import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import nuclear.drone.DroneTask.*;
import nuclear.world.blocks.drone.DroneDepot.*;
import nuclear.world.blocks.drone.DroneReceiver.*;

/** Hauls items from drone depots to drone receivers. Jobs are handed out by {@link DroneNet}. */
public class WorkerDroneAI extends DroneAI{
    public static final float arriveDist = 10f;

    public DroneTask task;

    public boolean canTakeJob(){
        return task == null && !charging && unit != null && unit.isValid() && !unit.hasItem()
            && unit.health >= unit.maxHealth * chargeFraction;
    }

    public void assign(DroneTask t){
        task = t;
    }

    @Override
    protected boolean work(){
        if(task == null){
            if(unit.hasItem()) salvage();
            if(task == null){
                idle(34f, 0.9f);
                return false;
            }
        }

        Building target = target();
        if(target == null){
            salvage();
            if(task == null) return false;
            target = target();
            if(target == null){
                task = null;
                return false;
            }
        }

        flyTo(target.x, target.y, 24f);
        if(unit.within(target, arriveDist + target.block.size * 4f)){
            arrive();
        }
        return true;
    }

    private Building target(){
        switch(task.stage){
            case pickup:
                return DroneNet.valid(task.depot, unit.team) && task.depot.items.get(task.item) > 0 ? task.depot : null;
            case deliver:
                return DroneNet.valid(task.receiver, unit.team) && task.receiver.filter == task.item ? task.receiver : null;
            default:
                return DroneNet.valid(task.depot, unit.team) ? task.depot : null;
        }
    }

    private void arrive(){
        switch(task.stage){
            case pickup:{
                DepotBuild d = task.depot;
                int n = Math.min(Math.min(task.amount, d.items.get(task.item)), unit.maxAccepted(task.item));
                if(n <= 0){
                    task = null;
                    return;
                }
                d.items.remove(task.item, n);
                unit.addItem(task.item, n);
                task.amount = n;
                task.stage = Stage.deliver;
                break;
            }
            case deliver:{
                ReceiverBuild r = task.receiver;
                Item it = unit.item();
                int acc = r.acceptStack(it, unit.stack.amount, unit);
                if(acc > 0){
                    r.handleStack(it, acc, unit);
                    unit.stack.amount -= acc;
                    if(unit.stack.amount <= 0) unit.clearItem();
                }
                if(unit.hasItem()) salvage(); else task = null;
                break;
            }
            default:{
                DepotBuild d = task.depot;
                Item it = unit.item();
                int acc = d.acceptStack(it, unit.stack.amount, unit);
                if(acc > 0){
                    d.handleStack(it, acc, unit);
                    unit.stack.amount -= acc;
                    if(unit.stack.amount <= 0) unit.clearItem();
                }
                if(unit.hasItem()) salvage(); else task = null;
                break;
            }
        }
    }

    /** The drone is holding items but its target is gone/full: find another receiver or put the items back into a depot. */
    private void salvage(){
        if(!unit.hasItem()){
            task = null;
            return;
        }

        int tries = task == null ? 1 : task.retries + 1;
        if(tries > 4){
            unit.clearItem();
            task = null;
            return;
        }

        Item it = unit.item();
        DroneNet.TeamNet n = net();

        ReceiverBuild bestR = null;
        float best = Float.MAX_VALUE;
        for(int i = 0; i < n.receivers.size; i++){
            ReceiverBuild r = n.receivers.get(i);
            if(!DroneNet.valid(r, unit.team) || r.filter != it || r.need() < 1) continue;
            float d = unit.dst2(r);
            if(d < best){
                best = d;
                bestR = r;
            }
        }

        if(bestR != null){
            DroneTask t = new DroneTask();
            t.receiver = bestR;
            t.item = it;
            t.amount = unit.stack.amount;
            t.stage = Stage.deliver;
            t.retries = tries;
            task = t;
            return;
        }

        DepotBuild bestD = null;
        best = Float.MAX_VALUE;
        for(int i = 0; i < n.depots.size; i++){
            DepotBuild d = n.depots.get(i);
            if(!DroneNet.valid(d, unit.team) || d.items.get(it) >= d.block.itemCapacity) continue;
            float dd = unit.dst2(d);
            if(dd < best){
                best = dd;
                bestD = d;
            }
        }

        if(bestD != null){
            DroneTask t = new DroneTask();
            t.depot = bestD;
            t.item = it;
            t.amount = unit.stack.amount;
            t.stage = Stage.back;
            t.retries = tries;
            task = t;
        }else{
            unit.clearItem();
            task = null;
        }
    }
}
