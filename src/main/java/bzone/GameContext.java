package bzone;

public class GameContext {

    public float playerX, playerZ;         // player world pos (XZ plane)
    public int rezProtect;                 // frames since (re)spawn; 0xFF = safe to “go hard”
    public int enemyScore;
    public int playerScore;
    public long nmiCount;                   // monotonically increasing
    public boolean projectileBusy;
    public EnemyAI.TankType tankType = EnemyAI.TankType.SLOW;

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
