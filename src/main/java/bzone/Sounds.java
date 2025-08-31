package bzone;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;

public class Sounds {

    public enum Effect {

        EXPLOSION("explosion.ogg", false);

        private Sound sound;
        private boolean looping;

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

}
