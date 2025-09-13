package bzone;

public class GameContext {

    public float hdFromCam;
    public float playerX, playerZ;
    public int spawnProtected;
    public int enemyScore;
    public int playerScore;
    public long nmiCount;
    public int lives = 3;
    public boolean alive = true;
    public int missileCount = 0;
    public int inactivityCount = 0;
    public int saucer_ttl = 0;

    public CollisionChecker collisionChecker = (x, z) -> false;
    public HitChecker hitsEnemy = (x, z) -> false;
    public HitChecker hitsObstacle = (x, z) -> false;
    public TankSpawn tankSpawn = () -> {/* */ };
    public PlayerSpawn playerSpawn = () -> {/* */ };

    public boolean isSuperTank() {
        return this.missileCount >= 5;
    }

    public interface CollisionChecker {

        boolean collides(float x, float z);
    }

    public interface HitChecker {

        boolean hits(float x, float z);
    }

    public interface PlayerSpawn {

        void spawn();
    }

    public interface TankSpawn {

        void spawn();
    }

    public interface SpatterSpawn {

        void spawn(float x, float z);
    }
}
