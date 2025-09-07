package bzone;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.math.MathUtils;

public class EngineSound {

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAME_SAMPLES = 2048;

    private static final float F0_IDLE_HZ = 33.5f;
    private static final float F0_CRUISE_HZ = 59.0f;

    private static final float[] HARM_IDLE = {1.00f, 0.95f, 0.46f, 0.22f, 0.08f};
    private static final float[] HARM_CRUISE = {1.00f, 0.60f, 0.26f, 0.15f, 0.07f};

    private static final float LP_IDLE_HZ = 1050f;
    private static final float LP_HIGH_HZ = 1400f;
    private static final float NOISE_IDLE = 0.044f;
    private static final float NOISE_HIGH = 0.026f;

    private static final float MASTER_GAIN = 0.14f;

    private static final float RAMP_UP_PER_S = 2.0f;
    private static final float RAMP_DOWN_PER_S = 1.6f;

    private AudioDevice device;
    private boolean playing = false;

    private float targetThrottle = 0f;
    private float throttle = 0f;

    private static final int HCOUNT = Math.max(HARM_IDLE.length, HARM_CRUISE.length);
    private final float[] harmNow = new float[HCOUNT];
    private final float[] phases = new float[HCOUNT];

    private float lpState = 0f;
    private float lpCutZ = LP_IDLE_HZ;

    private int rng = 0x13572468;

    private final float[] buf = new float[FRAME_SAMPLES];

    public void start() {
        if (device == null) {
            device = Gdx.audio.newAudioDevice(SAMPLE_RATE, true);
        }
        for (int i = 0; i < phases.length; i++) {
            phases[i] = 0f;
        }
        lpState = 0f;
        lpCutZ = LP_IDLE_HZ;
        playing = true;
    }

    public void stop() {
        playing = false;
        if (device != null) {
            device.dispose();
            device = null;
        }
    }

    public void setThrottle(float t01) {
        targetThrottle = MathUtils.clamp(t01, 0f, 1f);
    }

    public void update(float deltaSeconds) {
        if (!playing || device == null) {
            return;
        }

        float d = targetThrottle - throttle;
        throttle += (d > 0f ? Math.min(d, RAMP_UP_PER_S * deltaSeconds)
                : Math.max(d, -RAMP_DOWN_PER_S * deltaSeconds));

        float shaped = (float) Math.pow(throttle, 0.7f);

        float f0 = MathUtils.lerp(F0_IDLE_HZ, F0_CRUISE_HZ, shaped);
        float nAmt = MathUtils.lerp(NOISE_IDLE, NOISE_HIGH, shaped);
        float lpCut = MathUtils.lerp(LP_IDLE_HZ, LP_HIGH_HZ, shaped);

        lerpHarm(shaped, HARM_IDLE, HARM_CRUISE, harmNow);

        final float Z = 0.08f;
        lpCutZ += Z * (lpCut - lpCutZ);

        final float twoPi = (float) (2.0 * Math.PI);
        float alpha = 1f - (float) Math.exp(-twoPi * lpCutZ / SAMPLE_RATE);

        float invSR = 1f / SAMPLE_RATE;
        float f0Inc = f0 * invSR;

        float peakAbs = 0f;
        double sum2 = 0.0;

        for (int i = 0; i < buf.length; i++) {
            float s = 0f;
            for (int h = 0; h < HCOUNT; h++) {
                phases[h] += (h + 1) * f0Inc;
                if (phases[h] >= 1f) {
                    phases[h] -= 1f;
                }
                s += harmNow[h] * sin01(phases[h]);
            }

            float noise = (rand01() * 2f - 1f) * nAmt;
            s += noise;

            lpState += alpha * (s - lpState);
            s = lpState;

            s *= MASTER_GAIN;
            s = softClip(s);

            buf[i] = s;
            float a = Math.abs(s);
            if (a > peakAbs) {
                peakAbs = a;
            }
            sum2 += s * s;
        }

        device.writeSamples(buf, 0, buf.length);

    }

    private static void lerpHarm(float t, float[] a, float[] b, float[] out) {
        for (int i = 0; i < out.length; i++) {
            float ai = i < a.length ? a[i] : 0f;
            float bi = i < b.length ? b[i] : 0f;
            out[i] = ai + t * (bi - ai);
        }
    }

    private static float sin01(float p01) {
        return (float) Math.sin(2.0 * Math.PI * p01);
    }

    private static float softClip(float x) {
        return x * (27f + x * x) / (27f + 9f * x * x);
    }

    private float rand01() {
        rng = rng * 1103515245 + 12345;
        return ((rng >>> 8) & 0x00FFFFFF) / 16777216f;
    }
}
