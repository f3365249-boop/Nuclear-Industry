package nuclear.radiation;

/** A circular radiation area. All values are in world units / ticks. */
public class RadiationZone{
    public long id;
    public float x, y, radius, remaining;

    public RadiationZone(long id, float x, float y, float radius, float remaining){
        this.id = id;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.remaining = remaining;
    }

    /** True if a circle at (cx, cy) with given extent touches this zone. */
    public boolean touches(float cx, float cy, float extent){
        float dx = cx - x, dy = cy - y, r = radius + extent;
        return dx * dx + dy * dy <= r * r;
    }

    public String serialize(){
        return id + "," + x + "," + y + "," + radius + "," + remaining;
    }

    public static RadiationZone parse(String s){
        try{
            String[] p = s.split(",");
            return new RadiationZone(Long.parseLong(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]), Float.parseFloat(p[4]));
        }catch(Throwable t){
            return null;
        }
    }
}
