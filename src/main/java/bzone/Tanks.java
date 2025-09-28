package bzone;

import bzone.Models.Mesh;
import static bzone.Models.Mesh.*;
import com.badlogic.gdx.graphics.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Tanks {

    private final Map<Mesh, BaseTank> map = new HashMap<>();
    private final Mesh[] meshes = new Mesh[]{SLOW_TANK, GIR_DRAXON, STALKER, HEAVY_TANK, PROWLER, HOVERCRAFT, SAND_SLED, LASER_TANK};
    private Random random = new Random();
    
    public Tanks(Projectile projectile) {

        GameModelInstance rm = Models.getModelInstance(Mesh.RADAR, Color.GREEN, 1);

        GameModelInstance t1 = Models.getModelInstance(Mesh.SLOW_TANK, Color.GREEN, 1);
        GameModelInstance t2 = Models.getModelInstance(Mesh.SUPER_TANK, Color.GREEN, 1);
        GameModelInstance t3 = Models.getModelInstance(Mesh.GIR_DRAXON, Color.GREEN, 1);
        GameModelInstance t4 = Models.getModelInstance(Mesh.STALKER, Color.GREEN, 1);
        GameModelInstance t5 = Models.getModelInstance(Mesh.HEAVY_TANK, Color.GREEN, 1);
        GameModelInstance t6 = Models.getModelInstance(Mesh.PROWLER, Color.GREEN, 1);
        GameModelInstance t7 = Models.getModelInstance(Mesh.HOVERCRAFT, Color.GREEN, 1);
        GameModelInstance t8 = Models.getModelInstance(Mesh.SAND_SLED, Color.GREEN, 1);
        GameModelInstance t9 = Models.getModelInstance(Mesh.LASER_TANK, Color.GREEN, 1);

        map.put(Mesh.SLOW_TANK, new Tank(t1, t2, rm, projectile));
        map.put(Mesh.GIR_DRAXON, new Stalker(t3, projectile));
        map.put(Mesh.STALKER, new Stalker(t4, projectile));
        map.put(Mesh.HEAVY_TANK, new HeavyTank(t5, projectile));
        map.put(Mesh.PROWLER, new Prowler(t6, projectile));
        map.put(Mesh.HOVERCRAFT, new HoverCraft(t7, projectile));
        map.put(Mesh.SAND_SLED, new SandSled(t8, projectile));
        map.put(Mesh.LASER_TANK, new LaserTank(t9, projectile));
    }

    public BaseTank nextTank(GameContext ctx) {
        
        if (ctx.missileCount >= 5 ) {
            Mesh picked = this.meshes[this.random.nextInt(meshes.length)];
            return map.get(picked);
        }

        return map.get(Mesh.SLOW_TANK);
    }

}
