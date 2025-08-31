package bzone;

import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class Tank {

    public static enum TankType {
        SLOW, SUPER
    }

    public static final int ANGLE_STEPS = 256;

    private static final int RADAR_SPIN = 4;
    private static final int CLOSE_FIRING_ANGLE = 16;
    private static final int REVERSE_TIME_FRAMES = 24;
    private static final int FORWARD_TIME_FRAMES = 256;
    private static final int NEW_HEADING_FRAMES = 32;

    private static final float SUPER_SPEED_MULT = 2.0f;
    private static final float FWD_SPEED_SLOW = 2800f;
    private static final float TURN_DEG_PER_SEC = 90f;

    private static final float FWD_START_DISTANCE_SLOW_TANK = 1280;
    private static final float FWD_START_DISTANCE_SUPER_TANK = 2048;

    private final GameModelInstance inst;
    private final GameModelInstance radar;

    public final Vector3 pos = new Vector3();
    private final Vector3 savedPos = new Vector3();

    private boolean alive = true;
    public int facing;                     // 0..255
    private int radarFacing;                // 0..255
    private int moveCounter;                // frames left for current plan
    private int reverseFlags;               // bit0: reversing, bit1: reverse turn dir (0=R,1=L)
    private int turnTo;                     // target facing (0..255)

    public Tank(GameModelInstance inst, GameModelInstance radar) {
        this.inst = inst;
        this.radar = radar;
    }

    private void savePos() {
        savedPos.set(pos);
    }

    private void restorePos() {
        pos.set(savedPos);
    }

    private void applyWrappedTransform(GameContext ctx) {

        float refX16 = to16(ctx.playerX);
        float refZ16 = to16(ctx.playerZ);
        float obX16 = to16(pos.x);
        float obZ16 = to16(pos.z);

        float dx16 = wrapDelta16(obX16 - refX16);
        float dz16 = wrapDelta16(obZ16 - refZ16);

        float wx = ctx.playerX + dx16;
        float wz = ctx.playerZ + dz16;

        inst.transform.idt()
                .translate(wx, pos.y, wz)
                .rotate(Vector3.Y, facing * 360f / ANGLE_STEPS);

        if (this.radar != null) {
            radar.transform.set(inst.transform)
                    .translate(0, 0, -510f)
                    .rotate(Vector3.Y, radarFacing * 360f / ANGLE_STEPS);
        }
    }

    public void render(ModelBatch modelBatch, Environment environment) {

        if (!this.alive) {
            return;
        }

        modelBatch.render(this.inst, environment);
        if (this.radar != null) {
            modelBatch.render(this.radar, environment);
        }
    }

    public void update(GameContext ctx, float dt) {

        if (!this.alive) {
            return;
        }

        if (ctx.spawnProtected != 255) {
            ctx.spawnProtected = Math.min(255, ctx.spawnProtected + 1);
        }

        updateTank(ctx, dt);

        applyWrappedTransform(ctx);
    }

    private void updateTank(GameContext ctx, float dt) {

        if (this.moveCounter > 0) {
            this.moveCounter--;
        }

        this.savePos();

        this.radarFacing = u8(this.radarFacing + RADAR_SPIN);

        if ((this.reverseFlags & 0x01) != 0) {
            moveBackward(ctx, dt);
            if ((this.reverseFlags & 0x02) != 0) {
                rotateLeft(dt);
            } else {
                rotateRight(dt);
            }
            if (this.moveCounter == 0) {
                this.reverseFlags &= ~0x03;
                this.turnTo = this.facing;
                this.moveCounter = FORWARD_TIME_FRAMES;
            }
            return;
        }

        if (this.moveCounter == 0) {
            setTankTurnTo(ctx);
        }

        int delta = signed8((this.facing - this.turnTo) & 0xFF);
        int absDelta = Math.abs(delta);

        if (absDelta >= CLOSE_FIRING_ANGLE) {
            if (delta >= 0) {
                rotateRight(dt);
                tryShootPlayer(ctx);
                rotateRight(dt);
                tryShootPlayer(ctx);
                if (ctx.tankType == TankType.SUPER) {
                    rotateRight(dt);
                    tryShootPlayer(ctx);
                    rotateRight(dt);
                    tryShootPlayer(ctx);
                }
            } else {
                rotateLeft(dt);
                tryShootPlayer(ctx);
                rotateLeft(dt);
                tryShootPlayer(ctx);
                if (ctx.tankType == TankType.SUPER) {
                    rotateLeft(dt);
                    tryShootPlayer(ctx);
                    rotateLeft(dt);
                    tryShootPlayer(ctx);
                }
            }
            return;
        }
        if (absDelta != 0) {
            if (delta >= 0) {
                rotateRight(dt);
            } else {
                rotateLeft(dt);
            }
        }

        tryShootPlayer(ctx);

        // Distance → forward; extra push when perfectly aligned
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        final float forwardStart = (ctx.tankType == TankType.SUPER) ? FWD_START_DISTANCE_SUPER_TANK : FWD_START_DISTANCE_SLOW_TANK;

        if (dist >= forwardStart) {
            float mult = (ctx.tankType == TankType.SUPER) ? SUPER_SPEED_MULT : 1f;
            forward(ctx, mult, dt);
        }
    }

    private void tryShootPlayer(GameContext ctx) {
        if (ctx.spawnProtected < 32) {
            return;
        }
        if (ctx.playerScore < 2000 && ctx.spawnProtected != 255) {
            return;
        }
        int diff = Math.abs(signed8(calcAngleToPlayer(ctx) - this.facing));
        if (diff >= 2) {
            return;
        }
        if (ctx.projectileBusy) {
            return;
        }
        ctx.shooter.shoot();
    }

    private void setTankTurnTo(GameContext ctx) {
        int JIT = (int) (ctx.nmiCount & 0x03L);
        int RJIT = (int) ((ctx.nmiCount >> 1) & 0x03L);

        int scoreDiff = ctx.playerScore - ctx.enemyScore;

        if (scoreDiff == 0) {
            // GoMedium:
            if ((ctx.nmiCount & 7L) == 0L) {
                this.reverseFlags |= 0x01 | (MathUtils.randomBoolean() ? 0x02 : 0x00);
                this.moveCounter = REVERSE_TIME_FRAMES + RJIT;
                return;
            }

            // head 90° off the player
            // the tank’s facing direction is stored in an 8-bit angle space (0–255, i.e. 256 steps around a circle).
            // A full circle (360°) = 256 steps, so:
            // 1 step ≈ 1.40625°
            // 64 steps = 90°
            int ang = calcAngleToPlayer(ctx);

            //XOR produces a perfect ±90° offset from the target, depending on the current quadrant:
            //If the angle is pointing, say, North (0), XOR with 64 → East (90°).
            //If it’s already East (64), XOR with 64 → North (0°).
            //If South (128), XOR → West (192).
            //If West (192), XOR → South (128).
            //So this one bit-flip always pivots the direction exactly perpendicular (90°) to the target vector, 
            //but toggles between left/right depending on where the player is.
            this.turnTo = u8(ang ^ 64);

            this.reverseFlags &= ~0x01;
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
            return;
        }

        if (scoreDiff < 0) {
            // GoMild (player losing): small offset from previous heading
            int offset = MathUtils.random(0, 31); // 31 steps × 1.40625° ≈ up to 43.7°.
            boolean neg = ((ctx.nmiCount & 1L) == 0L);
            this.turnTo = u8(neg ? this.turnTo - offset : this.turnTo + offset);
            this.reverseFlags &= ~0x01;
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
            return;
        }

        //GoHard
        this.turnTo = calcAngleToPlayer(ctx);
        this.reverseFlags &= ~0x01;
        this.moveCounter = NEW_HEADING_FRAMES + JIT;
    }

    private void stepForward(float spd) {
        this.savePos();
        float rad = this.facing * MathUtils.PI2 / ANGLE_STEPS;
        this.pos.x += MathUtils.sin(rad) * spd;
        this.pos.z += MathUtils.cos(rad) * spd;
    }

    private void forward(GameContext ctx, float mult, float dt) {
        float spd = FWD_SPEED_SLOW * mult * dt;
        stepForward(spd);
        if (ctx.collisionChecker.collides(this.pos.x, this.pos.z)) {
            this.restorePos();
            int dir = (MathUtils.randomBoolean() ? 0x02 : 0x00) | 0x01; // reverse + dir
            this.reverseFlags |= dir;
            this.moveCounter = REVERSE_TIME_FRAMES;
        }
    }

    private void moveBackward(GameContext ctx, float dt) {
        stepForward(-FWD_SPEED_SLOW * dt);
        if (ctx.collisionChecker.collides(this.pos.x, this.pos.z)) {
            this.restorePos();
            this.reverseFlags &= ~0x01;
            int offset = MathUtils.random(0x10, 0x20);
            if ((this.reverseFlags & 0x02) != 0) {
                this.facing = u8(this.facing + offset);
            } else {
                this.facing = u8(this.facing - offset);
            }
            this.turnTo = this.facing;
            this.moveCounter = NEW_HEADING_FRAMES;
        }
    }

    private void rotateLeft(float dt) {
        turn(+dt);
    }

    private void rotateRight(float dt) {
        turn(-dt);
    }

    private void turn(float dt) {
        float stepsPerSec = (TURN_DEG_PER_SEC / 360f) * ANGLE_STEPS;
        float raw = stepsPerSec * dt;
        int step = Math.max(1, Math.round(Math.abs(raw)));
        this.facing = u8(this.facing + (raw >= 0 ? step : -step));
    }

    private int calcAngleToPlayer(GameContext ctx) {
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float a = MathUtils.atan2((float) dx16, (float) dz16); // +Z is “north”
        return ((int) Math.round((a / MathUtils.PI2) * ANGLE_STEPS)) & 0xFF;
    }

    private static int u8(int v) {
        return v & 0xFF;
    }

    private static int signed8(int v) {
        int x = v & 0xFF;
        return (x >= 128) ? (x - 256) : x;
    }

}
