package bzone;

import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public final class EnemyAI {

    public static final int ANGLE_STEPS = 256;

    private static final int RADAR_SPIN = 2;
    private static final int CLOSE_FIRING_ANGLE = 16;
    private static final int REVERSE_TIME_FRAMES = 48;
    private static final int FORWARD_TIME_FRAMES = 256;
    private static final int NEW_HEADING_FRAMES = 128;

    private static final float SUPER_SPEED_MULT = 2.0f;
    private static final float FWD_SPEED_SLOW = 2800f;
    private static final float TURN_DEG_PER_SEC = 90f;

    private static final float FWD_START_DISTANCE_SLOW_TANK = 1280;
    private static final float FWD_START_DISTANCE_SUPER_TANK = 2048;

    public static enum TankType {
        SLOW, SUPER
    }

    public static final class Enemy {

        public final ModelInstance instance;
        public ModelInstance radar;
        public final Vector3 pos = new Vector3();

        public boolean alive = true;
        public boolean isMissile = false;
        public int facing;                     // 0..255
        public int radarFacing;                // 0..255
        public int moveCounter;                // frames left for current plan
        public int reverseFlags;               // bit0: reversing, bit1: reverse turn dir (0=R,1=L)
        public int turnTo;                     // target facing (0..255)
        public int treadDripCtr;               // cosmetic

        private final Vector3 savedPos = new Vector3();

        public Enemy(ModelInstance instance) {
            this.instance = instance;
            instance.transform.getTranslation(pos);
            facing = 0;
            turnTo = 0;
        }

        public void setRadar(ModelInstance radar) {
            this.radar = radar;
        }

        public ModelInstance radar() {
            return this.radar;
        }

        public void savePos() {
            savedPos.set(pos);
        }

        public void restorePos() {
            pos.set(savedPos);
        }

        public float getX() {
            return pos.x;
        }

        public float getZ() {
            return pos.z;
        }

        public void applyWrappedTransform(GameContext ctx) {

            float refX16 = to16(ctx.playerX);
            float refZ16 = to16(ctx.playerZ);
            float obX16 = to16(pos.x);
            float obZ16 = to16(pos.z);

            float dx16 = wrapDelta16(obX16 - refX16);
            float dz16 = wrapDelta16(obZ16 - refZ16);

            float wx = ctx.playerX + dx16;
            float wz = ctx.playerZ + dz16;

            instance.transform.idt()
                    .translate(wx, pos.y, wz)
                    .rotate(Vector3.Y, facing * 360f / ANGLE_STEPS);

            if (this.radar != null) {
                radar.transform.set(instance.transform)
                        .translate(0, 0, -510f)
                        .rotate(Vector3.Y, radarFacing * 360f / ANGLE_STEPS);
            }
        }

    }

    public static void update(Enemy enmy, GameContext ctx, float dt) {

        if (ctx.rezProtect != 255) {
            ctx.rezProtect = Math.min(255, ctx.rezProtect + 1);
        }

        if (!enmy.alive) {
            return;
        }

        if (enmy.isMissile) {
            return;
        }

        updateTank(enmy, ctx, dt);

        enmy.applyWrappedTransform(ctx);
    }

    private static void updateTank(Enemy enmy, GameContext ctx, float dt) {

        if (enmy.moveCounter > 0) {
            enmy.moveCounter--;
        }

        enmy.savePos();

        enmy.radarFacing = u8(enmy.radarFacing + RADAR_SPIN);

        if ((enmy.reverseFlags & 0x01) != 0) {
            moveBackward(enmy, ctx, dt);
            if ((enmy.reverseFlags & 0x02) != 0) {
                rotateLeft(enmy, dt);
            } else {
                rotateRight(enmy, dt);
            }
            enmy.treadDripCtr--;
            if (enmy.moveCounter == 0) {
                enmy.reverseFlags &= ~0x03;
                enmy.turnTo = enmy.facing;
                enmy.moveCounter = FORWARD_TIME_FRAMES;
            }
            return;
        }

        if (enmy.moveCounter == 0) {
            setTankTurnTo(enmy, ctx);
        }

        final int delta = signed8((enmy.facing - enmy.turnTo) & 0xFF);
        final int absDelta = Math.abs(delta);

        if (absDelta >= CLOSE_FIRING_ANGLE) {
            if (delta >= 0) {
                rotateRight(enmy, dt);
                tryShootPlayer(enmy, ctx);
                rotateRight(enmy, dt);
                tryShootPlayer(enmy, ctx);
                if (ctx.tankType == TankType.SUPER) {
                    rotateRight(enmy, dt);
                    tryShootPlayer(enmy, ctx);
                    rotateRight(enmy, dt);
                    tryShootPlayer(enmy, ctx);
                }
            } else {
                rotateLeft(enmy, dt);
                tryShootPlayer(enmy, ctx);
                rotateLeft(enmy, dt);
                tryShootPlayer(enmy, ctx);
                if (ctx.tankType == TankType.SUPER) {
                    rotateLeft(enmy, dt);
                    tryShootPlayer(enmy, ctx);
                    rotateLeft(enmy, dt);
                    tryShootPlayer(enmy, ctx);
                }
            }
            return;
        }
        if (absDelta != 0) {
            if (delta >= 0) {
                rotateRight(enmy, dt);
            } else {
                rotateLeft(enmy, dt);
            }
        }

        tryShootPlayer(enmy, ctx);

        // Distance → forward; extra push when perfectly aligned
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(enmy.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(enmy.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        final float forwardStart = (ctx.tankType == TankType.SUPER) ? FWD_START_DISTANCE_SUPER_TANK : FWD_START_DISTANCE_SLOW_TANK;

        if (dist >= forwardStart) {
            float mult = (ctx.tankType == TankType.SUPER) ? SUPER_SPEED_MULT : 1f;
            forward(enmy, ctx, mult, dt);
        }
    }

    private static void setTankTurnTo(Enemy enmy, GameContext ctx) {

        final int JIT = (int) (ctx.nmiCount & 0x03L);          // for NEW_HEADING_FRAMES
        final int RJIT = (int) ((ctx.nmiCount >> 1) & 0x03L);   // for REVERSE_TIME_FRAMES

        // 1) Immediate hard if rez_protect == 0xFF
        if (ctx.rezProtect == 255) {
            enmy.turnTo = calcAngleToPlayer(enmy, ctx);
            enmy.reverseFlags &= ~0x01;                          // clear reverse
            enmy.moveCounter = NEW_HEADING_FRAMES + JIT;         // ~4s (+0..3)
            return;
        }

        // 2) 50% coin flip -> GoHard (ROM-ish)
        if (MathUtils.randomBoolean()) {
            enmy.turnTo = calcAngleToPlayer(enmy, ctx);
            enmy.reverseFlags &= ~0x01;
            enmy.moveCounter = NEW_HEADING_FRAMES + JIT;         // ~4s (+0..3)
            return;
        }

        // 3) Score >= 100k -> GoHard
        if (ctx.playerScore >= 100_000) {
            enmy.turnTo = calcAngleToPlayer(enmy, ctx);
            enmy.reverseFlags &= ~0x01;
            enmy.moveCounter = NEW_HEADING_FRAMES + JIT;         // ~4s (+0..3)
            return;
        }

        // 4) Compare player vs enemy "score"
        int diff = ctx.playerScore - ctx.enemyScore;

        if (diff == 0) {
            // GoMedium:
            // 1-in-8 frames: reverse for ~3s
            if ((ctx.nmiCount & 7L) == 0L) {
                enmy.reverseFlags |= 0x01 | (MathUtils.randomBoolean() ? 0x02 : 0x00);
                enmy.moveCounter = REVERSE_TIME_FRAMES + RJIT;   // ~3s (+0..3)
                return;
            }
            // 7-of-8: head 90° off the player
            int aim = calcAngleToPlayer(enmy, ctx);
            enmy.turnTo = u8(aim ^ 0x40);                        // ±90°
            enmy.reverseFlags &= ~0x01;                          // clear reverse
            enmy.moveCounter = NEW_HEADING_FRAMES + JIT;         // ~4s (+0..3)
            return;
        }

        if (diff < 0) {
            // GoMild (player losing): small offset from previous heading
            int offset = MathUtils.random(0, 0x1F);              // up to 45°
            boolean neg = ((ctx.nmiCount & 1L) == 0L);
            enmy.turnTo = u8(neg ? enmy.turnTo - offset : enmy.turnTo + offset);
            enmy.reverseFlags &= ~0x01;                          // clear reverse
            enmy.moveCounter = NEW_HEADING_FRAMES + JIT;         // ~4s (+0..3)
            return;
        }

        // diff > 0 → GoHard (player winning)
        enmy.turnTo = calcAngleToPlayer(enmy, ctx);
        enmy.reverseFlags &= ~0x01;
        enmy.moveCounter = NEW_HEADING_FRAMES + JIT;             // ~4s (+0..3)
    }

    private static void stepForward(Enemy enmy, float spd) {
        enmy.savePos();
        float rad = enmy.facing * MathUtils.PI2 / ANGLE_STEPS;
        enmy.pos.x += MathUtils.sin(rad) * spd;
        enmy.pos.z += MathUtils.cos(rad) * spd;
    }

    private static void forward(Enemy enmy, GameContext ctx, float mult, float dt) {
        float spd = FWD_SPEED_SLOW * mult * dt;
        stepForward(enmy, spd);
        if (ctx.collisionChecker.collides(enmy.pos.x, enmy.pos.z)) {
            enmy.restorePos();
            int dir = (MathUtils.randomBoolean() ? 0x02 : 0x00) | 0x01; // reverse + dir
            enmy.reverseFlags |= dir;
            enmy.moveCounter = REVERSE_TIME_FRAMES;
        } else {
            enmy.treadDripCtr++;
        }
    }

    private static void moveBackward(Enemy enmy, GameContext ctx, float dt) {
        stepForward(enmy, -FWD_SPEED_SLOW * dt);
        if (ctx.collisionChecker.collides(enmy.pos.x, enmy.pos.z)) {
            enmy.restorePos();
            enmy.reverseFlags &= ~0x01;
            int offset = MathUtils.random(0x10, 0x20);
            if ((enmy.reverseFlags & 0x02) != 0) {
                enmy.facing = u8(enmy.facing + offset);
            } else {
                enmy.facing = u8(enmy.facing - offset);
            }
            enmy.turnTo = enmy.facing;
            enmy.moveCounter = NEW_HEADING_FRAMES;
        } else {
            enmy.treadDripCtr--;
        }
    }

    private static void rotateLeft(Enemy e, float dt) {
        turn(e, +dt);
    }

    private static void rotateRight(Enemy e, float dt) {
        turn(e, -dt);
    }

    private static void turn(Enemy enmy, float dt) {
        float stepsPerSec = (TURN_DEG_PER_SEC / 360f) * ANGLE_STEPS;
        float raw = stepsPerSec * dt;
        int step = Math.max(1, Math.round(Math.abs(raw)));
        enmy.facing = u8(enmy.facing + (raw >= 0 ? step : -step));
    }

    private static int calcAngleToPlayer(Enemy enmy, GameContext ctx) {
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(enmy.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(enmy.pos.z));
        float a = MathUtils.atan2((float) dx16, (float) dz16); // +Z is “north”
        return ((int) Math.round((a / MathUtils.PI2) * ANGLE_STEPS)) & 0xFF;
    }

    private static void tryShootPlayer(Enemy enmy, GameContext ctx) {
        if (ctx.rezProtect < 32) {
            return; // be nice for ~2 sec
        }
        if (ctx.playerScore < 2000 && ctx.rezProtect != 255) {
            return;
        }
        int diff = Math.abs(signed8(calcAngleToPlayer(enmy, ctx) - enmy.facing));
        if (diff >= 2) {
            return;
        }
        if (ctx.projectileBusy) {
            return;
        }
        ctx.shooter.shoot();
    }

    private static int u8(int v) {
        return v & 0xFF;
    }

    private static int signed8(int v) {
        int x = v & 0xFF;
        return (x >= 128) ? (x - 256) : x;
    }
}
