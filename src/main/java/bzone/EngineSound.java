package bzone;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.math.MathUtils;

public class EngineSound {

    private static final int SAMPLE_RATE = 22050;
    private static final int FRAME_SAMPLES = 1024;

    // ===== Engine trim (you can tweak or expose) =====
    // These are the 555-equivalent "clock" frequencies at throttle 0 and 1
    private float idleClockHz = 240f;   // ~4 Hz beat (240/60); feels like idle throb
    private float maxClockHz = 1200f;  // ~20 Hz beat at full throttle

    // Carrier (the "engine tone" you actually hear) maps independently
    private static final float CARRIER_IDLE = 38f;   // Hz
    private static final float CARRIER_MAX = 165f;  // Hz

    // Gains
    private static final float MASTER_GAIN = 0.22f;
    private static final float TONE_GAIN = 0.85f;
    private static final float NOISE_BASE = 0.14f;  // at idle
    private static final float NOISE_EXTRA = 0.12f;  // added at max throttle

    // Smoothing toward throttle target (emulates RC ramp feel)
    private static final float RAMP_UP_PER_S = 2.0f;
    private static final float RAMP_DOWN_PER_S = 1.6f;

    // ===== Runtime state =====
    private AudioDevice device;
    private boolean enabled = true;
    private boolean playing = false;

    private float targetThrottle = 0f; // 0..1
    private float throttle = 0f;       // smoothed

    // 555 clock phase accumulator (0..1). Tick counters when it wraps.
    private float clockPhase = 0f;

    // ÷10 and ÷12 counters (like two LS161 chains)
    private int c10 = 0; // 0..9
    private int c12 = 0; // 0..11

    // LFO derived from counter taps (smoothed a touch to avoid clicks)
    private float lfo = 0f;

    // Carrier oscillator phases (0..1)
    private float ph1 = 0f, ph2 = 0f, ph3 = 0f, ph5 = 0f;

    // Simple RNG for noise (no allocs per frame)
    private int rng = 0x13572468;

    // Reusable buffer
    private final float[] buf = new float[FRAME_SAMPLES];

    // ===== Public API =====
    public void start() {
        if (device == null) {
            device = Gdx.audio.newAudioDevice(SAMPLE_RATE, true); // mono
        }
        playing = true;
    }

    public void stop() {
        playing = false;
        if (device != null) {
            device.dispose();
            device = null;
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 0..1, emulates the pot; call on keyDown/Up or analog input.
     */
    public void setThrottle(float t01) {
        targetThrottle = MathUtils.clamp(t01, 0f, 1f);
    }

    /**
     * Trim idle "555" clock (like R17 on the board).
     */
    public void setIdleClockHz(float hz) {
        idleClockHz = Math.max(20f, hz);
    }

    /**
     * Trim max "555" clock.
     */
    public void setMaxClockHz(float hz) {
        maxClockHz = Math.max(idleClockHz + 1f, hz);
    }

    public boolean isPlaying() {
        return playing && device != null;
    }

    /**
     * Call from your render(delta).
     */
    public void update(float deltaSeconds) {
        if (!isPlaying()) {
            return;
        }

        // Smooth RC-like ramp to target
        float d = targetThrottle - throttle;
        if (d > 0f) {
            throttle += Math.min(d, RAMP_UP_PER_S * deltaSeconds);
        } else if (d < 0f) {
            throttle += Math.max(d, -RAMP_DOWN_PER_S * deltaSeconds);
        }

        // If disabled, fade throttle to zero (motor off)
        float audible = enabled ? throttle : 0f;

        // Slightly nonlinear mapping -> more authority at low end (feels like torque)
        float shaped = (float) Math.pow(audible, 0.7);

        // 555 clock in Hz (controls the counters, NOT the audible pitch directly)
        float clockHz = MathUtils.lerp(idleClockHz, maxClockHz, shaped);

        // Audible carrier base frequency (rumbly engine note)
        float carrierHz = MathUtils.lerp(CARRIER_IDLE, CARRIER_MAX, shaped);

        // Precompute increments
        float incClock = clockHz / SAMPLE_RATE;    // ticks counters when >= 1
        float inc1 = carrierHz / SAMPLE_RATE;  // fundamental
        float inc2 = (carrierHz * 2f) / SAMPLE_RATE;
        float inc3 = (carrierHz * 3f) / SAMPLE_RATE;
        float inc5 = (carrierHz * 5f) / SAMPLE_RATE;

        // Noise amount increases with throttle
        float noiseAmt = NOISE_BASE + NOISE_EXTRA * shaped;

        // Write one chunk per update()
        for (int i = 0; i < buf.length; i++) {
            // Advance 555 clock; tick counters on wrap(s)
            clockPhase += incClock;
            while (clockPhase >= 1f) {
                clockPhase -= 1f;
                c10 = (c10 + 1) % 10;
                c12 = (c12 + 1) % 12;

                // Sample counter taps -> stepped LFO seed (sum "several of the bits")
                // We'll use equal-weight taps like a simple resistor summer.
                int b10 = bit(c10, 0) + bit(c10, 1) + bit(c10, 3); // taps Q0,Q1,Q3
                int b12 = bit(c12, 0) + bit(c12, 2) + bit(c12, 3); // taps Q0,Q2,Q3
                float raw = (b10 + b12) / 6f; // normalize 0..1

                // Touch of smoothing (one-pole) to keep it organic
                lfo += (raw - lfo) * 0.35f;
            }

            // Carrier (harmonic-rich, no allocated math)
            float s1 = fastSin(ph1);
            float s2 = fastSin(ph2);
            float s3 = fastSin(ph3);
            float s5 = fastSin(ph5);
            float tone = (s1 + 0.45f * shaped * s2 + 0.28f * shaped * s3 + 0.18f * shaped * s5) * TONE_GAIN;

            // Broadband rumble
            float noise = (rand01() * 2f - 1f) * noiseAmt;

            // Use the counter-sum LFO to gate/shape amplitude (keeps some baseline at idle)
            float amp = 0.35f + 0.65f * lfo;

            float sample = (tone + noise) * amp * MASTER_GAIN;
            sample = softClip(sample);

            buf[i] = sample;

            // Advance carrier phases
            ph1 = wrap01(ph1 + inc1);
            ph2 = wrap01(ph2 + inc2);
            ph3 = wrap01(ph3 + inc3);
            ph5 = wrap01(ph5 + inc5);
        }

        device.writeSamples(buf, 0, buf.length);
    }

    // ===== Helpers =====
    private static int bit(int v, int n) {
        return (v >> n) & 1;
    }

    private static float wrap01(float x) {
        x -= (int) x;
        return (x < 0f) ? x + 1f : x;
    }

    // Compact sine approximation, phase in [0,1) -> [-1,1]
    private static float fastSin(float p01) {
        float x = p01 * MathUtils.PI2;
        float a = 16f * x * (MathUtils.PI - x);
        float b = 5f * MathUtils.PI * MathUtils.PI - 4f * x * (MathUtils.PI - x);
        return a / b;
    }

    private static float softClip(float x) {
        // gentle saturation
        return x * (27f + x * x) / (27f + 9f * x * x);
    }

    private float rand01() {
        rng = rng * 1103515245 + 12345;
        return ((rng >>> 8) & 0x00FFFFFF) / (float) 0x01000000;
    }
}
