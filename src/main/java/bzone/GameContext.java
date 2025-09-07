package bzone;

public class GameContext {

    public float hdFromCam;
    public float playerX, playerZ;
    public int spawnProtected;
    public int enemyScore;
    public int playerScore;
    public long nmiCount;
    public int lives = 3;

    public Tank.TankType tankType = Tank.TankType.SLOW;

    public CollisionChecker collisionChecker = (x, z) -> false;
    public HitChecker hitChecker = (x, z) -> false;
    public Shooter shooter = () -> {/* */ };
    public TankSpawn tankSpawn = () -> {/* */ };
    public PlayerSpawn playerSpawn = () -> {/* */ };
    public SpatterSpawn spatterSpawn = (x, z) -> {/* */ };

    public interface CollisionChecker {

        boolean collides(float x, float z);
    }

    public interface HitChecker {

        boolean hits(float x, float z);
    }

    public interface Shooter {

        void shoot();
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
