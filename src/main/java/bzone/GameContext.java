package bzone;

public class GameContext {

    public float playerX, playerZ;
    public int spawnProtected;
    public int enemyScore;
    public int playerScore;
    public long nmiCount;
    public boolean projectileBusy;
    public boolean missileBusy;
    
    public Tank.TankType tankType = Tank.TankType.SLOW;

    public CollisionChecker collisionChecker = (x, z) -> false;
    public Shooter shooter = () -> {
    };

    public interface CollisionChecker {

        boolean collides(float x, float z);
    }

    public interface Shooter {

        void shoot();
    }
}
