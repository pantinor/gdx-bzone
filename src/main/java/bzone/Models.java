package bzone;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public class Models {

    private static final Vector3 TMP1 = new Vector3();
    private static final Vector3 TMP2 = new Vector3();
    private static final Vector3 TMP3 = new Vector3();
    private static final Vector3 TMP4 = new Vector3();
    private static final Vector3 TMP5 = new Vector3();
    private static final Vector3 TMP6 = new Vector3();

    public static enum Mesh {
        //original bzone meshes from ROM
        NARROW_PYRAMID("W1 V 6 512 -640 -512 -512 -640 -512 -512 -640 512 512 -640 512 0 640 0 0 0 0 E 8 0 4 4 1 1 0 0 3 3 4 4 2 2 3 2 1 P 0"),
        TALL_BOX("W1 V 9 512 -640 -512 -512 -640 -512 -512 -640 512 512 -640 512 512 640 -512 -512 640 -512 -512 640 512 512 640 512 0 0 0 E 12 0 1 1 2 2 3 3 0 0 4 4 5 5 6 6 7 7 4 5 1 2 6 7 3 P 0"),
        SLOW_TANK("W1 V 25 512 -640 -736 -512 -640 -736 -512 -640 968 512 -640 968 568 -416 -1024 -568 -416 -1024 -568 -416 1248 568 -416 1248 344 -240 -680 -344 -240 -680 -344 -240 680 344 -240 680 168 96 -512 -168 96 -512 40 -16 -128 -40 -16 -128 -40 -96 128 40 -96 128 -40 -16 1120 -40 -96 1120 40 -16 1120 40 -96 1120 0 96 -512 0 160 -512 0 0 0 E 38 23 22 12 13 14 20 20 18 18 15 15 14 14 17 17 16 16 19 19 21 21 17 15 16 19 18 20 21 3 0 0 4 4 7 7 6 6 2 2 3 3 7 7 11 11 10 10 6 6 5 5 9 9 10 10 13 13 9 9 8 8 11 11 12 12 8 8 4 4 5 5 1 1 2 1 0 P 0"),
        PROJECTILE("W1 V 6 40 -96 -40 40 -16 -40 -40 -16 -40 -40 -96 -40 0 -56 80 0 0 0 E 8 0 4 4 1 1 0 0 3 3 4 4 2 2 3 2 1 P 0"),
        REAR_TREAD_0("W1 V 7 552 -472 -948 -552 -472 -948 536 -552 -844 -536 -552 -844 516 -632 -736 -516 -632 -736 0 0 0 E 3 0 1 2 3 4 5 P 0"),
        REAR_TREAD_1("W1 V 7 556 -456 -972 -556 -456 -972 540 -536 -868 -540 -536 -868 520 -616 -764 -520 -616 -764 0 0 0 E 3 0 1 2 3 4 5 P 0"),
        REAR_TREAD_2("W1 V 7 564 -432 -1000 -564 -432 -1000 544 -512 -896 -544 -512 -896 528 -592 -792 -528 -592 -792 0 0 0 E 3 0 1 2 3 4 5 P 0"),
        REAR_TREAD_3("W1 V 7 568 -416 -1024 -568 -416 -1024 548 -496 -920 -548 -496 -920 532 -576 -816 -532 -576 -816 0 0 0 E 3 0 1 2 3 4 5 P 0"),
        FRONT_TREAD_0("W1 V 7 568 -416 1248 -568 -416 1248 548 -496 1152 -548 -496 1152 532 -576 1056 -532 -576 1056 0 0 0 E 3 0 1 2 3 4 5 P 0"),
        FRONT_TREAD_1("W1 V 7 564 -432 1224 -564 -432 1224 544 -512 1128 -544 -512 1128 528 -592 1032 -528 -592 1032 0 0 0 E 3 0 1 2 3 4 5 P 0"),
        FRONT_TREAD_2("W1 V 7 556 -456 1200 -556 -456 1200 540 -536 1104 -540 -536 1104 520 -616 1008 -520 -616 1008 0 0 0 E 3 0 1 2 3 4 5 P 0"),
        FRONT_TREAD_3("W1 V 7 552 -472 1176 -552 -472 1176 536 -552 1080 -536 -552 1080 516 -632 984 -516 -632 984 0 0 0 E 3 0 1 2 3 4 5 P 0"),
        WIDE_PYRAMID("W1 V 6 800 -640 -800 -800 -640 -800 -800 -640 800 800 -640 800 0 800 0 0 0 0 E 8 0 4 4 1 1 0 0 3 3 4 4 2 2 3 2 1 P 0"),
        RADAR1("W1 V 9 80 160 0 160 200 80 160 240 80 80 280 0 -80 160 0 -160 200 80 -160 240 80 -80 280 0 0 0 0 E 10 0 1 1 2 2 3 3 0 0 4 4 5 5 6 6 7 7 4 7 3 P 0"),
        PROJECTILE_EXPLOSION("W1 V 2 0 0 0 0 0 0 E 0 P 0"),
        SHORT_BOX("W1 V 9 640 -640 -640 -640 -640 -640 -640 -640 640 640 -640 640 640 -80 -640 -640 -80 -640 -640 -80 640 640 -80 640 0 0 0 E 12 0 1 1 2 2 3 3 0 0 4 4 5 5 6 6 7 7 4 5 1 2 6 7 3 P 0"),
        CHUNK0_TANK_10("W1 V 7 0 -544 220 80 -376 -320 -80 -192 340 0 -712 -184 80 -512 -124 -80 -416 -116 0 0 0 E 10 0 3 3 5 5 2 2 0 0 1 1 2 2 5 5 4 4 1 4 3 P 0"),
        CHUNK1_TANK_11("W1 V 9 120 -640 -240 -64 -560 -376 -160 -768 720 120 -640 640 64 -160 -40 -32 -120 0 160 -400 56 -200 -480 120 0 0 0 E 12 0 1 1 2 2 3 3 0 0 4 4 6 6 7 7 5 5 4 5 1 7 2 3 6 P 0"),
        CHUNK2_TANK("W1 V 15 344 -296 -588 -344 -296 -588 -344 -976 588 344 -976 588 168 -96 -272 -168 -96 -272 40 -376 0 -40 -376 0 -40 -576 180 40 -576 180 -40 -1000 1080 -40 -1072 1040 40 -1000 1080 40 -1072 1040 0 0 0 E 21 0 1 1 2 2 3 3 0 0 4 4 5 5 1 5 2 3 4 6 12 12 10 10 7 7 6 6 9 9 8 8 11 11 13 13 9 7 8 11 10 12 13 P 0"),
        RADAR2("W1 V 9 80 160 0 160 200 80 160 240 80 80 280 0 -80 160 0 -160 200 80 -160 240 80 -80 280 0 0 0 0 E 10 0 1 1 2 2 3 3 0 0 4 4 5 5 6 6 7 7 4 7 3 P 0"),
        CHUNK1_TANK_14("W1 V 9 120 -640 -240 -64 -560 -376 -160 -768 720 120 -640 640 64 -160 -40 -32 -120 0 160 -400 56 -200 -480 120 0 0 0 E 12 0 1 1 2 2 3 3 0 0 4 4 6 6 7 7 5 5 4 5 1 7 2 3 6 P 0"),
        CHUNK0_TANK_15("W1 V 7 0 -544 220 80 -376 -320 -80 -192 340 0 -712 -184 80 -512 -124 -80 -416 -116 0 0 0 E 10 0 3 3 5 5 2 2 0 0 1 1 2 2 5 5 4 4 1 4 3 P 0"),
        MISSILE("W1 V 27 -144 0 -384 -72 96 -384 72 96 -384 144 0 -384 72 -96 -384 -72 -96 -384 -288 0 -96 -192 192 -96 192 192 -96 288 0 -96 192 -192 -96 -192 -192 -96 0 0 1152 0 0 1392 144 -336 -144 -144 -336 -144 -144 -336 144 144 -336 144 48 -184 -48 -48 -184 -48 -48 -168 48 48 -168 48 0 192 -96 -72 96 528 72 96 528 0 288 48 0 0 0 E 43 13 12 12 6 6 0 0 1 1 7 7 8 8 9 9 10 10 11 11 6 6 7 7 12 12 8 8 2 2 3 3 9 9 12 12 10 10 4 4 5 5 11 11 12 24 23 23 22 22 24 24 25 25 23 25 22 1 2 3 4 5 0 18 19 19 20 20 21 21 18 18 14 14 15 15 16 16 17 17 14 15 19 20 16 17 21 P 0"),
        LOGO_BA("W1 V 21 -5120 64 224 -3840 64 224 -3200 176 672 -3520 288 1120 -3200 400 1600 -3840 512 2048 -5120 512 2048 -4480 176 672 -4160 176 672 -4480 288 1120 -4160 400 1600 -4480 400 1600 -3200 64 224 -2240 176 32 -1280 64 224 -2240 512 2048 -2560 224 896 -2240 256 1024 -1920 224 896 -2240 336 1344 0 0 0 E 20 0 1 1 2 2 3 3 4 4 5 5 6 6 0 7 8 8 9 9 10 10 11 11 7 12 13 13 14 14 15 15 12 16 17 17 18 18 19 19 16 P 0"),
        CHUNK1_MISSILE("W1 V 9 120 -640 -240 -64 -560 -376 -160 -768 720 120 -640 640 64 -160 -40 -32 -120 0 160 -400 56 -200 -480 120 0 0 0 E 12 0 1 1 2 2 3 3 0 0 4 4 6 6 7 7 5 5 4 5 1 7 2 3 6 P 0"),
        CHUNK4_MISSILE_19("W1 V 9 72 -368 -300 168 -368 -232 272 -472 -232 272 -568 -300 -168 -408 -96 -12 -384 40 260 -648 40 232 -808 -96 0 0 0 E 10 1 2 2 3 3 7 7 6 6 5 5 4 4 0 0 1 1 5 6 2 P 0"),
        CHUNK0_MISSILE_1A("W1 V 7 0 -544 220 80 -376 -320 -80 -192 340 0 -712 -184 80 -512 -124 -80 -416 -116 0 0 0 E 10 0 3 3 5 5 2 2 0 0 1 1 2 2 5 5 4 4 1 4 3 P 0"),
        CHUNK5_MISSILE("W1 V 5 12 -576 -80 -112 -864 472 44 24 800 16 -536 88 0 0 0 E 6 0 2 2 1 1 3 3 0 0 1 2 3 P 0"),
        CHUNK0_MISSILE_1C("W1 V 7 0 -544 220 80 -376 -320 -80 -192 340 0 -712 -184 80 -512 -124 -80 -416 -116 0 0 0 E 10 0 3 3 5 5 2 2 0 0 1 1 2 2 5 5 4 4 1 4 3 P 0"),
        CHUNK4_MISSILE_1D("W1 V 9 72 -368 -300 168 -368 -232 272 -472 -232 272 -568 -300 -168 -408 -96 -12 -384 40 260 -648 40 232 -808 -96 0 0 0 E 10 1 2 2 3 3 7 7 6 6 5 5 4 4 0 0 1 1 5 6 2 P 0"),
        LOGO_TTLE("W1 V 22 -640 64 224 -320 400 1600 640 400 1600 960 64 224 1280 400 1600 2240 400 1600 2240 64 224 3840 64 224 5440 112 448 4480 176 672 4480 224 896 5120 288 1120 4480 336 1344 4480 400 1600 5440 448 1824 3840 512 2048 2880 176 672 2880 512 2048 -1920 512 2048 -1920 400 1600 -960 400 1600 0 0 0 E 22 0 1 1 2 2 3 3 4 4 5 5 6 6 7 7 8 8 9 9 10 10 11 11 12 12 13 13 14 14 15 15 7 7 16 16 17 17 18 18 19 19 20 20 0 P 0"),
        LOGO_ZONE("W1 V 26 -4800 -512 -2048 -2240 -512 -2048 -3520 -400 -1600 -2240 -64 -224 -4800 -64 -224 -3520 -176 -672 -320 -512 -2048 -320 -64 -224 -1600 -400 -1600 -960 -400 -1600 -960 -176 -672 -1600 -176 -672 0 -512 -2048 640 -288 -1120 2560 -512 -2048 4160 -448 -1824 3200 -400 -1600 3200 -336 -1344 3840 -288 -1120 3200 -224 -896 3200 -176 -672 4160 -112 -448 2560 -64 -224 1920 -288 -1120 0 -64 -224 0 0 0 E 28 1 0 0 5 5 4 4 3 3 2 2 1 1 3 3 7 7 6 6 1 9 8 8 11 11 10 10 9 14 22 22 23 23 24 24 12 12 13 13 14 14 15 15 16 16 17 17 18 18 19 19 20 20 21 21 22 P 0"),
        SAUCER("W1 V 18 0 -80 -240 -160 -80 -160 -240 -80 0 -160 -80 160 0 -80 240 160 -80 160 240 -80 0 160 -80 -160 0 160 -960 -680 160 -680 -960 160 0 -680 160 680 0 160 960 680 160 680 960 160 0 680 160 -680 0 560 0 0 0 0 E 32 16 8 8 9 9 16 16 10 10 11 11 16 16 12 12 13 13 16 16 14 14 15 15 16 0 7 7 15 15 8 8 0 0 1 1 9 9 10 10 2 2 3 3 11 11 12 12 4 4 5 5 13 13 14 14 6 6 7 6 5 4 3 2 1 P 0"),
        SUPER_TANK("W1 V 26 -368 -640 1456 -552 -640 -456 552 -640 -456 368 -640 1456 -456 -184 -456 456 -184 -456 0 -552 1096 -272 -232 -272 -272 -184 -456 272 -184 -456 272 -232 -272 -184 88 -272 -184 88 -456 184 88 -456 184 88 -272 -88 -88 1280 -88 -88 88 88 -88 88 88 -88 1280 -88 0 1280 -88 0 -88 88 0 -88 88 0 1280 0 88 -456 0 552 -456 0 0 0 E 34 0 1 1 4 4 0 0 3 3 2 2 5 5 3 2 1 4 5 9 10 10 6 6 14 14 13 13 9 9 8 8 7 7 6 6 11 11 12 12 8 12 13 14 11 19 22 22 21 21 20 20 16 16 15 15 18 18 17 17 16 15 19 22 18 17 21 23 24 P 0"),
        SPATTER_0("W1 V 9 -52 -360 0 -36 -360 36 0 -360 52 36 -360 36 52 -360 0 36 -360 -36 0 -360 -52 -36 -360 -36 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        SPATTER_1("W1 V 9 -100 -400 0 -72 -400 72 0 -400 100 72 -400 72 100 -400 0 72 -400 -72 0 -400 -100 -72 -400 -72 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        SPATTER_2("W1 V 9 -152 -440 0 -108 -440 108 0 -440 152 108 -440 108 152 -440 0 108 -440 -108 0 -440 -152 -108 -440 -108 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        SPATTER_3("W1 V 9 -200 -480 0 -144 -480 144 0 -480 200 144 -480 144 200 -480 0 144 -480 -144 0 -480 -200 -144 -480 -144 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        SPATTER_4("W1 V 9 -252 -520 0 -176 -520 176 0 -520 252 176 -520 176 252 -520 0 176 -520 -176 0 -520 -252 -176 -520 -176 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        SPATTER_5("W1 V 9 -300 -560 0 -212 -560 212 0 -560 300 212 -560 212 300 -560 0 212 -560 -212 0 -560 -300 -212 -560 -212 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        SPATTER_6("W1 V 9 -352 -600 0 -264 -600 264 0 -600 352 264 -600 264 352 -600 0 264 -600 -264 0 -600 -352 -264 -600 -264 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        SPATTER_7("W1 V 9 -400 -640 0 -284 -640 284 0 -640 400 284 -640 284 400 -640 0 284 -640 -284 0 -640 -400 -284 -640 -284 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        //original stellar 7 meshes from ROM
        GIR_DRAXON("W1 V 29 321 -320 600 -321 -320 600 -511 -320 -726 511 -320 -726 405 -200 914 -405 -200 914 -596 -200 -803 596 -200 -803 -406 -40 -717 406 -40 -717 313 -152 521 -313 -152 521 -322 -40 -680 322 -40 -680 -241 240 -479 241 240 -479 0 88 -104 0 -32 232 144 32 86 -144 32 86 0 88 992 0 -32 992 147 32 989 -147 32 989 0 -40 -720 0 320 -720 0 480 -720 0 480 -1000 0 320 -1000 E 43 0 1 1 2 2 3 3 0 4 5 5 6 6 7 7 4 5 8 8 9 9 4 0 4 1 5 2 6 3 7 6 8 7 9 10 11 11 12 13 10 11 14 14 15 15 10 12 14 13 15 16 19 19 17 17 18 18 16 20 23 23 21 21 22 22 20 16 20 17 21 18 22 19 23 24 26 26 27 27 28 28 25 25 27 26 28 P 0"),
        WARPLINK("W1 V 14 0 960 0 0 -800 0 141 240 141 141 -80 141 -141 240 141 -141 -80 141 -141 240 -141 -141 -80 -141 141 240 -141 141 -80 -141 639 80 639 -639 80 639 -639 80 -639 639 80 -639 E 28 0 2 0 4 0 6 0 8 1 3 1 5 1 7 1 9 2 4 4 6 6 8 2 8 3 5 5 7 7 9 9 3 10 11 11 12 12 13 13 10 2 10 4 11 6 12 8 13 3 10 5 11 7 12 9 13 P 0"),
        FUELBAY("W1 V 13 707 320 707 707 -480 707 -707 320 707 -707 -480 707 -707 320 -707 -707 -480 -707 707 320 -707 707 -480 -707 339 -240 339 -339 -240 339 -339 -240 -339 339 -240 -339 0 1000 0 E 24 0 2 2 4 4 6 6 0 1 3 3 5 5 7 7 1 0 8 1 8 2 9 3 9 4 10 5 10 6 11 7 11 8 9 9 10 10 11 11 8 0 12 2 12 4 12 6 12 P 0"),
        SEEKER("W1 V 18 204 112 204 204 -112 204 -204 112 204 -204 -112 204 -204 112 -204 -204 -112 -204 204 112 -204 204 -112 -204 407 320 407 407 -320 407 -407 320 407 -407 -320 407 -407 320 -407 -407 -320 -407 407 320 -407 407 -320 -407 0 304 0 0 -304 0 E 28 0 1 2 3 4 5 6 7 0 2 2 4 4 6 6 0 1 3 3 5 5 7 7 1 0 8 1 9 2 10 3 11 4 12 5 13 6 14 7 15 0 16 2 16 4 16 6 16 1 17 3 17 5 17 7 17 P 0"),
        STINGER("W1 V 9 0 -120 1000 0 -200 -720 -286 80 -798 286 80 -798 -625 80 -801 625 80 -801 -160 0 0 160 0 0 0 280 -640 E 13 0 1 0 2 0 3 1 3 4 5 6 7 2 8 3 8 6 8 7 8 1 2 4 6 5 7 P 0"),
        SKIMMER("W1 V 13 63 -96 637 63 -136 637 -63 -96 637 -63 -136 637 201 -136 -318 -201 -136 -318 0 -16 -200 522 88 77 521 88 -313 965 -40 -319 -522 88 77 -521 88 -313 -965 -40 -319 E 21 0 1 2 3 0 2 1 3 1 4 3 5 0 6 4 5 4 6 1 7 4 8 7 8 7 9 8 9 3 10 5 11 10 11 10 12 11 12 2 6 6 5 P 0"),
        PULSAR("W1 V 12 720 -160 0 0 -160 720 -720 -160 0 0 -160 -720 1000 -160 0 0 -160 1000 -1000 -160 0 0 -160 -1000 0 400 0 0 240 0 0 -480 0 0 -640 0 E 18 0 1 1 2 2 3 3 0 0 4 1 5 2 6 3 7 9 0 9 1 9 2 9 3 10 0 10 1 10 2 10 3 8 9 10 11 P 0"),
        GUN_BATTERY("W1 V 17 0 104 1000 0 -24 1000 148 40 997 -148 40 997 0 104 -192 0 -24 -192 143 40 -193 -143 40 -193 481 40 -557 0 40 -1000 -481 40 -557 0 536 -560 0 360 -560 0 -280 -560 0 -456 -560 798 40 -562 -798 40 -562 E 28 0 2 2 1 1 3 3 0 4 6 6 5 5 7 7 4 0 4 2 6 3 7 1 5 6 8 8 9 9 10 10 7 12 4 12 8 12 9 12 10 13 5 13 8 13 9 13 10 11 12 13 14 15 8 16 10 P 0"),
        LASER_BATTERY("W1 V 14 0 -40 960 0 16 -720 0 -176 -720 89 -32 -723 89 -128 -723 -89 -32 -723 -89 -128 -723 0 -80 -960 0 400 -480 0 -560 -480 481 160 -481 481 -320 -481 -481 160 -481 -481 -320 -481 E 30 1 3 3 4 4 2 2 6 6 5 5 1 0 1 0 2 0 3 0 4 0 5 0 6 7 1 7 2 7 3 7 4 7 5 7 6 8 10 10 11 11 9 9 13 13 12 12 8 1 8 2 9 3 10 4 11 5 12 6 13 P 0"),
        STALKER("W1 V 20 321 -320 600 -321 -320 600 -511 -320 -726 511 -320 -726 405 -200 914 -405 -200 914 -596 -200 -803 596 -200 -803 -406 -40 -717 406 -40 -717 313 -152 521 -313 -152 521 -322 -40 -680 322 -40 -680 -226 240 -477 226 240 -477 0 144 -200 143 0 151 -143 0 151 0 48 1000 E 31 0 1 1 2 2 3 3 0 4 5 5 6 6 7 7 4 5 8 8 9 9 4 0 4 1 5 2 6 3 7 6 8 7 9 10 11 11 12 13 10 11 14 14 15 15 10 12 14 13 15 16 18 17 16 16 19 17 19 18 19 17 18 P 0"),
        HEAVY_TANK("W1 V 22 363 -480 640 -363 -480 640 -363 -480 -722 363 -480 -722 471 -240 882 -471 -240 882 -471 -240 -882 471 -240 -882 -360 -96 109 360 -96 109 318 200 -201 -318 200 -201 -320 200 -636 320 200 -636 96 96 -96 95 -32 16 -96 96 -96 -95 -32 16 -98 96 995 -98 -32 995 98 96 995 98 -32 995 E 37 0 1 1 2 2 3 3 0 4 5 5 6 6 7 7 4 0 4 1 5 2 6 3 7 5 8 8 6 8 9 9 7 9 4 9 10 8 11 6 12 7 13 10 11 11 12 12 13 13 10 14 15 16 17 18 19 20 21 14 16 16 18 18 20 20 14 15 17 17 19 19 21 21 15 P 0"),
        PROWLER("W1 V 20 -319 -480 303 319 -480 303 321 -480 -724 -321 -480 -724 -443 -200 568 443 -200 568 450 -200 -893 -450 -200 -893 -285 160 -644 285 160 -644 -95 24 963 -95 -72 963 95 24 963 95 -72 963 -81 24 -209 -81 -72 128 81 24 -209 81 -72 128 124 48 -717 124 640 -717 E 30 0 1 1 2 2 3 3 0 4 5 5 6 6 7 7 4 8 9 0 4 1 5 2 6 3 7 4 8 7 8 5 9 6 9 10 11 12 13 14 15 16 17 10 12 11 13 14 16 15 17 10 14 12 16 11 15 13 17 18 19 P 0"),
        HOVERCRAFT("W1 V 21 0 -240 560 -480 -240 0 0 -240 -640 480 -240 0 0 -360 400 -320 -120 0 -320 -360 0 0 -120 -480 0 -360 -480 320 -120 0 320 -360 0 -83 120 200 83 120 200 35 -40 358 29 -80 391 -35 -40 358 -29 -80 391 39 -40 799 39 -80 799 -39 -40 799 -39 -80 799 E 35 0 1 1 2 2 3 3 0 4 6 6 8 8 10 10 4 5 7 7 9 9 5 11 12 0 4 1 6 2 8 3 10 1 5 2 7 3 9 5 11 9 12 0 11 0 12 13 17 15 19 14 18 16 20 17 19 18 20 17 18 19 20 13 15 14 16 13 14 15 16 P 0"),
        SAND_SLED("W1 V 17 363 -480 940 212 -480 944 -363 -480 940 -212 -480 944 354 -480 -918 207 -480 -921 -354 -480 -918 -207 -480 -921 286 -400 -798 -286 -400 -798 -281 -400 198 281 -400 198 0 40 -600 0 -144 -280 0 -176 520 81 -224 -128 -81 -224 -128 E 22 0 1 2 3 4 5 6 7 0 4 1 5 2 6 3 7 8 9 9 10 10 11 11 8 8 12 9 12 10 12 11 12 13 14 13 15 13 16 15 16 14 15 14 16 P 0"),
        LASER_TANK("W1 V 20 477 -480 643 -477 -480 643 -477 -480 -643 477 -480 -643 596 -240 803 -596 -240 803 -596 -240 -803 596 -240 -803 404 -120 315 -404 -120 315 -396 -120 -482 396 -120 -482 199 160 119 -199 160 119 -201 160 -355 201 160 -355 0 24 1000 0 80 176 128 -32 240 -128 -32 240 E 34 0 1 1 2 2 3 3 0 4 5 5 6 6 7 7 4 8 9 9 10 10 11 11 8 12 13 13 14 14 15 15 12 0 4 1 5 2 6 3 7 4 8 5 9 6 10 7 11 8 12 9 13 10 14 11 15 16 17 16 18 16 19 17 18 18 19 19 17 P 0"), //
        ;

        private final Wireframe wireframe;

        Mesh(String text) {
            this.wireframe = wireframeFromString(text);
        }

        public Wireframe wf() {
            return this.wireframe;
        }

    }

    public static final class Wireframe {

        public static final class Vertex {

            public final int x, y, z;

            Vertex(int x, int y, int z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }
        }

        public static final class Edge {

            public final int a, b;

            Edge(int a, int b) {
                this.a = a;
                this.b = b;
            }
        }

        private final List<Vertex> vertices = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();
        private final List<Integer> points = new ArrayList<>();

        public int addVertex(int x, int y, int z) {
            vertices.add(new Vertex(x, y, z));
            return vertices.size() - 1;
        }

        public void addEdge(int a, int b) {
            edges.add(new Edge(a, b));
        }

        public void addPoint(int vi) {
            points.add(vi);
        }

        public boolean validate(StringBuilder outMsg) {
            int n = vertices.size();
            for (Edge e : edges) {
                if (e.a < 0 || e.a >= n || e.b < 0 || e.b >= n) {
                    if (outMsg != null) {
                        outMsg.append("Edge out of range");
                    }
                    return false;
                }
            }
            for (int p : points) {
                if (p < 0 || p >= n) {
                    if (outMsg != null) {
                        outMsg.append("Point out of range");
                    }
                    return false;
                }
            }
            return true;
        }

        public List<Vertex> getVertices() {
            return vertices;
        }

        public List<Edge> getEdges() {
            return edges;
        }

        public List<Integer> getPoints() {
            return points;
        }
    }

    private static Wireframe wireframeFromString(String s) {
        if (s == null) {
            throw new IllegalArgumentException("null wireframe string");
        }
        String[] t = s.trim().split("\\s+");  // split on ANY whitespace
        int i = 0;

        // Header
        if (i >= t.length || !"W1".equals(t[i++])) {
            throw new IllegalArgumentException("Bad header (expected W1)");
        }

        // Vertices
        if (i >= t.length || !"V".equals(t[i++])) {
            throw new IllegalArgumentException("Missing V section");
        }
        int vCount = Integer.parseInt(t[i++]);
        Wireframe wf = new Wireframe();
        for (int k = 0; k < vCount; k++) {
            if (i + 2 >= t.length) {
                throw new IllegalArgumentException("Truncated vertices");
            }
            int x = Integer.parseInt(t[i++]);
            int y = Integer.parseInt(t[i++]);
            int z = Integer.parseInt(t[i++]);
            wf.addVertex(x, y, z);
        }

        // Edges
        if (i >= t.length || !"E".equals(t[i++])) {
            throw new IllegalArgumentException("Missing E section");
        }
        int eCount = Integer.parseInt(t[i++]);
        for (int k = 0; k < eCount; k++) {
            if (i + 1 >= t.length) {
                throw new IllegalArgumentException("Truncated edges");
            }
            int a = Integer.parseInt(t[i++]);
            int b = Integer.parseInt(t[i++]);
            // optional light bounds check; skip invalid
            if (a >= 0 && b >= 0 && a < wf.getVertices().size() && b < wf.getVertices().size()) {
                wf.addEdge(a, b);
            }
        }

        // Points (optional but expected in your writer)
        if (i < t.length) {
            if (!"P".equals(t[i++])) {
                throw new IllegalArgumentException("Missing P section");
            }
            int pCount = Integer.parseInt(t[i++]);
            for (int k = 0; k < pCount; k++) {
                if (i >= t.length) {
                    throw new IllegalArgumentException("Truncated points");
                }
                int pi = Integer.parseInt(t[i++]);
                if (pi >= 0 && pi < wf.getVertices().size()) {
                    wf.addPoint(pi);
                }
            }
        }

        return wf;
    }

    public static Model buildWireframeModel(Wireframe wf, Color color, float unitScale, float pointSize) {

        final List<Wireframe.Vertex> verts = wf.getVertices();

        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Material mat = new Material(ColorAttribute.createDiffuse(color));
        MeshPartBuilder b = mb.part("wire", GL20.GL_LINES, VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorUnpacked, mat);
        b.setColor(color);

        // Edges → lines
        for (Wireframe.Edge e : wf.getEdges()) {
            if (e.a < 0 || e.a >= verts.size() || e.b < 0 || e.b >= verts.size()) {
                continue;
            }
            Wireframe.Vertex va  = verts.get(e.a);
            Wireframe.Vertex vb = verts.get(e.b);

            TMP1.set(va.x * unitScale, va.y * unitScale, va.z * unitScale);
            TMP2.set(vb.x * unitScale, vb.y * unitScale, vb.z * unitScale);
            b.line(TMP1, TMP2);
        }

        // Points → tiny crosses (3 axes)
        if (pointSize > 0f) {
            float r = pointSize;
            for (int vi : wf.getPoints()) {
                if (vi < 0 || vi >= verts.size()) {
                    continue;
                }
                Wireframe.Vertex v = verts.get(vi);
                float x = v.x * unitScale, y = v.y * unitScale, z = v.z * unitScale;

                // X axis
                TMP1.set(x - r, y, z);
                TMP2.set(x + r, y, z);
                b.line(TMP1, TMP2);

                // Y axis
                TMP3.set(x, y - r, z);
                TMP4.set(x, y + r, z);
                b.line(TMP3, TMP4);

                // Z axis
                TMP5.set(x, y, z - r);
                TMP6.set(x, y, z + r);
                b.line(TMP5, TMP6);
            }
        }

        return mb.end();
    }

    public static GameModelInstance buildWireframeInstance(Wireframe wf, Color color, float unitScale, float pointSize, float x, float y, float z) {
        Model model = buildWireframeModel(wf, color, unitScale, pointSize);
        GameModelInstance instance = new GameModelInstance(model, x, y, z);
        return instance;
    }

    public static ModelInstance buildXZGrid(int halfLines, float spacing, Color color) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Material mat = new Material(ColorAttribute.createDiffuse(color));
        MeshPartBuilder b = mb.part("xzGrid", GL20.GL_LINES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorUnpacked, mat);
        b.setColor(color);

        float extent = halfLines * spacing;

        // Lines parallel to X (vary Z)
        for (int i = -halfLines; i <= halfLines; i++) {
            float z = i * spacing;
            b.line(-extent, 0f, z, +extent, 0f, z);
        }
        // Lines parallel to Z (vary X)
        for (int i = -halfLines; i <= halfLines; i++) {
            float x = i * spacing;
            b.line(x, 0f, -extent, x, 0f, +extent);
        }

        // Bold axes (X: red-ish, Z: blue-ish, Y: green)
        Color xAxis = new Color(0.9f, 0.2f, 0.2f, 1f);
        Color zAxis = new Color(0.2f, 0.4f, 0.9f, 1f);
        Color yAxis = new Color(0.2f, 0.9f, 0.2f, 1f);

        b.setColor(xAxis);
        b.line(-extent, 0f, 0f, +extent, 0f, 0f);

        b.setColor(zAxis);
        b.line(0f, 0f, -extent, 0f, 0f, +extent);

        // Add Y axis line upward from origin
        b.setColor(yAxis);
        b.line(0f, 0f, 0f, 0f, extent, 0f);

        Model gridModel = mb.end();
        return new ModelInstance(gridModel, new Matrix4().idt());
    }

    // inside class Models
    public static final class EdgeBoxes {

        public final Model boxModel;                 // dispose this when done
        public final List<ModelInstance> instances;  // all edge markers

        EdgeBoxes(Model m, List<ModelInstance> list) {
            this.boxModel = m;
            this.instances = list;
        }
    }

    /**
     * Build small colored boxes spaced along the world torus seams: x =
     * worldMin, x = worldMax, z = worldMin, z = worldMax.
     *
     * @param worldMin usually 0
     * @param worldMax e.g., 65536
     * @param spacing distance between boxes along the edges (e.g., 10)
     * @param y height for the box centers (e.g., 0.5f)
     * @param boxSize cube size (edge length), e.g., 4
     * @param color box color
     */
    public static EdgeBoxes buildEdgeBoxes(
            float worldMin, float worldMax,
            float spacing, float y, float boxSize,
            Color color
    ) {
        float worldSize = Math.max(0f, worldMax - worldMin);
        if (spacing <= 0f) {
            spacing = 10f;
        }

        // Build a single colored box model (unlit: Position | ColorUnpacked)
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Material mat = new Material(); // color per-vertex via ColorUnpacked
        MeshPartBuilder part = mb.part(
                "box",
                GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorUnpacked,
                mat
        );
        part.setColor(color);
        // centered at origin; we’ll place with transforms
        part.box(0f, 0f, 0f, boxSize, boxSize, boxSize);
        Model boxModel = mb.end();

        List<ModelInstance> out = new ArrayList<>();

        // Helper to add an instance at (x,z)
        java.util.function.BiConsumer<Float, Float> add = (x, z) -> {
            ModelInstance mi = new ModelInstance(boxModel);
            mi.transform.setToTranslation(x, y, z);
            out.add(mi);
        };

        // Robust stepping that includes both ends (min and max) without double-adding corners
        int steps = (int) Math.floor(worldSize / spacing);
        float remainder = worldSize - steps * spacing;
        float step = spacing + (remainder / Math.max(1, steps)); // spreads residual so we land near max

        // z-min edge: (x from min..max, z = min)
        float x = worldMin;
        for (int i = 0; i <= steps; i++, x = Math.min(worldMax, x + step)) {
            add.accept(x, worldMin);
        }

        // z-max edge: (x from min..max, z = max), skip corners to avoid duplicates
        x = worldMin + step;
        for (int i = 1; i < steps; i++, x = Math.min(worldMax, x + step)) {
            add.accept(x, worldMax);
        }

        // x-min edge: (z from min..max, x = min), skip corners
        float z = worldMin + step;
        for (int i = 1; i < steps; i++, z = Math.min(worldMax, z + step)) {
            add.accept(worldMin, z);
        }

        // x-max edge: (z from min..max, x = max), skip corners
        z = worldMin + step;
        for (int i = 1; i < steps; i++, z = Math.min(worldMax, z + step)) {
            add.accept(worldMax, z);
        }

        return new EdgeBoxes(boxModel, out);
    }

}
