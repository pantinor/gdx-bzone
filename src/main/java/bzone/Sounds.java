package bzone;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;

public class Sounds {

    public enum Effect {

        EXPLOSION("explosion.ogg", false),
        FIRE("fire.ogg", false),
        BUMP("bump.ogg", false),
        ERROR("error.ogg", false),
        SPAWN("spawn.ogg", false),
        SAUCER_ACTIVE("saucer.ogg", false),
        SAUCER_HIT("saucer-hit.ogg", false),
        MISSILE_MAX("maximize.ogg", false),
        MISSILE_MIN("minimize.ogg", false),
        RADAR("radar.ogg", false);

        private final Sound sound;
        private final boolean looping;

        private Effect(String file, boolean looping) {
            this.sound = Gdx.audio.newSound(Gdx.files.internal("assets/audio/" + file));
            this.looping = looping;
        }

        public Sound sound() {
            return this.sound;
        }

        public boolean isLooping() {
            return this.looping;
        }

    }

    private static final float VOLUME = 1f;

    public static void play(Effect s) {
        if (s.isLooping()) {
            s.sound.loop(VOLUME);
        } else {
            s.sound.play(VOLUME);
        }
    }

    public static void stop(Effect s) {
        s.sound.stop();
    }
}
