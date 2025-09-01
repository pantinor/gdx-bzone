package bzone;

import static bzone.BattleZone.SCREEN_HEIGHT;
import static bzone.BattleZone.SCREEN_WIDTH;
import static bzone.BattleZone.WORLD_WRAP_HALF_16BIT;
import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import java.util.List;

public class Radar {

    private static final float SWEEP_REV_PER_SEC = 1f / 3f;
    private static final float STEPS_PER_SEC = 256f * SWEEP_REV_PER_SEC;

    private static final float RADAR_RANGE_UNITS = WORLD_WRAP_HALF_16BIT * 1.41421356f; //SQRT2
    private static final float RADAR_CX = SCREEN_WIDTH / 2f;
    private static final float RADAR_CY = SCREEN_HEIGHT - 110f;
    private static final float RADAR_RADIUS = 100;

    private boolean topLatched = false;       // prevents repeats while we're inside the window
    private float sweep256 = 0f;

    public void drawRadar2D(PerspectiveCamera cam, ShapeRenderer sr, Tank tank, Missile missile, List<GameModelInstance> obstacles, float dt) {

        if (dt > 0.1f) {
            dt = 0.1f;
        }

        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.05f, 0.05f, 0.05f, 0.35f);
        sr.circle(RADAR_CX, RADAR_CY, RADAR_RADIUS);
        sr.end();

        sweep256 = (sweep256 + STEPS_PER_SEC * dt) % 256f;
        int sweep8 = ((int) sweep256) & 0xFF;

        int playerHeading8 = angle256(
                (int) Math.round(cam.direction.x * 32767f),
                (int) Math.round(cam.direction.z * 32767f));
        int sweepRel8 = (sweep8 - playerHeading8) & 0xFF;

        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0f, 1f, 0f, 0.25f);

        // draw sweep line
        float a = (sweepRel8 / 256f) * MathUtils.PI2;
        float cs = MathUtils.cos(a), sn = MathUtils.sin(a);
        float ex = RADAR_CX - sn * RADAR_RADIUS;
        float ey = RADAR_CY + cs * RADAR_RADIUS;
        sr.line(RADAR_CX, RADAR_CY, ex, ey);

        sr.end();
        
        sr.begin(ShapeRenderer.ShapeType.Filled);

        if (atTop(sweepRel8)) {
            if (!topLatched) {
                Sounds.play(Sounds.Effect.RADAR);
                topLatched = true;
            }
        } else {
            topLatched = false;
        }

        for (GameModelInstance inst : obstacles) {
            float dx16 = wrapDelta16(to16(inst.initialPos.x) - to16(cam.position.x));
            float dz16 = wrapDelta16(to16(inst.initialPos.z) - to16(cam.position.z));

            int bearing8 = angle256(dx16, dz16);
            int rel8 = (bearing8 - playerHeading8) & 0xFF;

            float dist = (float) Math.sqrt((float) dx16 * dx16 + (float) dz16 * dz16);
            float t = MathUtils.clamp(dist / RADAR_RANGE_UNITS, 0f, 1f);
            float r = t * RADAR_RADIUS;

            float ang = (rel8 / 256f) * MathUtils.PI2;
            float cb = MathUtils.cos(ang), sb = MathUtils.sin(ang);
            float px = RADAR_CX - sb * r;
            float py = RADAR_CY + cb * r;

            sr.setColor(0.2f, 0.2f, 0.2f, 0.65f);
            sr.circle(px, py, 1);
        }

        if (tank.alive) {
            float edx16 = wrapDelta16(to16(tank.pos.x) - to16(cam.position.x));
            float edz16 = wrapDelta16(to16(tank.pos.z) - to16(cam.position.z));
            int enemyBearing8 = angle256(edx16, edz16);
            int rel8 = (enemyBearing8 - playerHeading8) & 0xFF;

            float dist = (float) Math.sqrt((float) edx16 * edx16 + (float) edz16 * edz16);
            float t = MathUtils.clamp(dist / RADAR_RANGE_UNITS, 0f, 1f);
            float mid = t * RADAR_RADIUS;

            //draw blip of tank
            float th = (rel8 / 256f) * MathUtils.PI2;
            float cb = MathUtils.cos(th), sb = MathUtils.sin(th);
            float px = RADAR_CX - sb * mid;
            float py = RADAR_CY + cb * mid;
            sr.setColor(1f, 0f, 0f, 0.65f);
            sr.circle(px, py, 2);
        }

        if (missile.active) {
            float edx16 = wrapDelta16(to16(missile.pos.x) - to16(cam.position.x));
            float edz16 = wrapDelta16(to16(missile.pos.z) - to16(cam.position.z));
            int enemyBearing8 = angle256(edx16, edz16);
            int rel8 = (enemyBearing8 - playerHeading8) & 0xFF;

            float dist = (float) Math.sqrt((float) edx16 * edx16 + (float) edz16 * edz16);
            float t = MathUtils.clamp(dist / RADAR_RANGE_UNITS, 0f, 1f);
            float mid = t * RADAR_RADIUS;

            //draw blip of missile
            float th = (rel8 / 256f) * MathUtils.PI2;
            float cb = MathUtils.cos(th), sb = MathUtils.sin(th);
            float px = RADAR_CX - sb * mid;
            float py = RADAR_CY + cb * mid;
            sr.setColor(1f, 1f, 0f, 0.65f);
            sr.circle(px, py, 2);
        }

        sr.end();
    }

    private static int angle256(float dx, float dz) {
        float ang = MathUtils.atan2(dx, dz); // 0 = +Z
        return Math.round((ang / MathUtils.PI2) * 256f) & 0xFF;
    }

    private static boolean atTop(int angle) {
        return angle <= 3 || angle >= (256 - 3);    // ~4.2° (3/256 of a turn)
    }
}
