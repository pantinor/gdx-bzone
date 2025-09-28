/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package bzone;

import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.math.MathUtils;

/**
 * LaserTank — mobile ground unit that behaves like a class-1 "ground mover"
 * (per ROCK2 Move1_Ground patterns) but fires a laser-type shot.
 *
 * Behaviour sketch (ROM-inspired, adapted to this codebase):
 *   - Alternates short “plans” picked every few dozen frames:
 *       • CHASE: face player and advance.
 *       • STRAFE: face ±90° from the player and advance (side-slip).
 *       • WOBBLE: keep current heading with a small random offset and advance.
 *       • HOLD: face the player and hold position for a brief aim window.
 *   - Fires precisely when closely aligned (BaseTank.tryShootPlayer), with
 *     an extra permissive "laserTryShoot" gate that allows near-aligned shots.
 *   - Reverses on collision exactly like Tank (uses BaseTank reverse flags).
 */
public class LaserTank extends BaseTank {

    private static final float LASER_SPEED_MULT = 1.25f;  // a bit nimbler than slow tank
    private static final float FWD_START_DISTANCE_LASER_TANK = 1536f;  // start pushing forward sooner
    private static final int WOBBLE_MAX_OFFSET_STEPS = 31;     // up to ~43.7°
    private static final int STRAFE_OFFSET_STEPS = 64;     // 90° in 256-step space

    public LaserTank(GameModelInstance laserTank, Projectile projectile) {
        super(laserTank, null, projectile);
        this.facing = MathUtils.random(0, ANGLE_STEPS - 1);
        this.radarFacing = this.facing;
        this.moveCounter = NEW_HEADING_FRAMES + (int) (MathUtils.random(0, 7));
    }

    @Override
    protected void updateTank(GameContext ctx, float dt) {

        if (this.moveCounter > 0) {
            this.moveCounter--;
        }

        this.savePos();

        // spin the radar dish
        this.radarFacing = u8(this.radarFacing + RADAR_SPIN);

        // --- Reverse handling (mirrors Tank) ---
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

        // If no active plan, choose a new heading "plan"
        if (this.moveCounter == 0) {
            setLaserTurnTo(ctx);
        }

        // Rotate toward current target heading
        int delta = signed8((this.facing - this.turnTo) & 0xFF);
        int absDelta = Math.abs(delta);
        if (absDelta != 0) {
            if (delta >= 0) {
                rotateRight(dt);
            } else {
                rotateLeft(dt);
            }
        }

        // Shooting: precise when aligned; otherwise allow near-aligned laser shots
        tryShootPlayer(ctx);     // strict 2-step alignment gate
        laserTryShoot(ctx);      // permissive near-aligned shots while maneuvering

        // Distance-based advance
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        if (dist >= FWD_START_DISTANCE_LASER_TANK) {
            forward(ctx, LASER_SPEED_MULT, dt);
        }
    }

    /**
     * Plan selection for the laser tank; quick ROM-flavored mix.
     */
    private void setLaserTurnTo(GameContext ctx) {
        int JIT = (int) (ctx.nmiCount & 0x03L);
        int RJIT = (int) ((ctx.nmiCount >> 1) & 0x03L);

        // Occasionally reverse (like tanks do)
        if ((ctx.nmiCount & 7L) == 0L) {
            this.reverseFlags |= 0x01 | (MathUtils.randomBoolean() ? 0x02 : 0x00);
            this.moveCounter = REVERSE_TIME_FRAMES + RJIT;
            return;
        }

        int angToPlayer = calcAngleToPlayer(ctx);

        // Distance-informed pattern choice
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        // Heuristic probabilities (roughly: more strafing at mid range)
        int roll = MathUtils.random(0, 255);
        if (dist > 6000f) {
            // Far: mostly chase, with some wobble
            if (roll < 200) {
                // CHASE
                this.turnTo = angToPlayer;
            } else {
                // WOBBLE
                int off = MathUtils.random(0, WOBBLE_MAX_OFFSET_STEPS);
                boolean neg = ((ctx.nmiCount & 1L) == 0L);
                this.turnTo = u8(neg ? angToPlayer - off : angToPlayer + off);
            }
        } else if (dist > 1200f) {
            // Mid: mix of strafe and chase
            if (roll < 112) {
                // STRAFE
                boolean left = ((ctx.nmiCount & 1L) == 0L);
                this.turnTo = u8(left ? angToPlayer - STRAFE_OFFSET_STEPS
                        : angToPlayer + STRAFE_OFFSET_STEPS);
            } else if (roll < 208) {
                // CHASE
                this.turnTo = angToPlayer;
            } else {
                // WOBBLE
                int off = MathUtils.random(0, WOBBLE_MAX_OFFSET_STEPS);
                boolean neg = MathUtils.randomBoolean();
                this.turnTo = u8(neg ? this.facing - off : this.facing + off);
            }
        } else {
            // Near: hold/aim windows punctuated by micro-wobbles
            if (roll < 160) {
                // HOLD/AIM
                this.turnTo = angToPlayer;
            } else {
                // micro WOBBLE around current heading
                int off = MathUtils.random(0, 15);
                boolean neg = ((ctx.nmiCount & 1L) == 0L);
                this.turnTo = u8(neg ? this.facing - off : this.facing + off);
            }
        }

        this.reverseFlags &= ~0x01;
        this.moveCounter = NEW_HEADING_FRAMES + JIT;
    }

    /**
     * Permissive laser firing when nearly aligned and within a reasonable
     * range.
     */
    private void laserTryShoot(GameContext ctx) {
        if (ctx.spawnProtected < SPAWN_PROTECTION) {
            return;
        }
        if (ctx.playerScore < 2000 && ctx.spawnProtected != SPAWN_PROTECTION) {
            return;
        }

        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        int diffSteps = Math.abs(signed8(calcAngleToPlayer(ctx) - this.facing));

        // Laser tanks are allowed to fire a bit off-axis at mid/close range
        if (dist < 12000f) {
            int gate = (dist < 2400f) ? 10 : 6; // looser when very close
            if (diffSteps <= gate) {
                this.projectile.spawnFromTank(this, ctx);
            }
        }
    }
}
