package com.funGames.app.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generates all casino sounds programmatically — no .mp3 files needed.
 *
 * Sounds:
 *  - playClick()       : short UI tap
 *  - playSpin()        : spinning reel whirr (loops until stopSpin())
 *  - playWin()         : ascending win chime
 *  - playJackpot()     : triumphant jackpot fanfare
 *  - playLose()        : short descending tone
 *  - playAmbient()     : soft looping casino atmosphere
 *  - stopAmbient()     : stop ambient music
 *  - setEnabled()      : master mute toggle
 */
public class SoundManager {

    private static SoundManager instance;
    public static SoundManager get() {
        if (instance == null) instance = new SoundManager();
        return instance;
    }

    private static final int SAMPLE_RATE = 44100;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Handler uiHandler   = new Handler(Looper.getMainLooper());

    private volatile boolean enabled    = true;
    private volatile boolean spinLoop   = false;
    private volatile boolean ambientLoop= false;
    private AudioTrack ambientTrack;

    private SoundManager() {}

    public void setEnabled(boolean on) { enabled = on; }
    public boolean isEnabled()         { return enabled; }

    // UI click
    public void playClick() {
        if (!enabled) return;
        pool.execute(() -> playTone(new double[]{1200}, new double[]{0.04}, new double[]{0.18}, 0.25));
    }

    // Spin start / stop
    public void playSpin() {
        if (!enabled) return;
        spinLoop = true;
        pool.execute(() -> {
            // Mechanical whirr: rapidly modulated noise-like tone
            int dur = (int)(SAMPLE_RATE * 0.12); // 120ms chunk, repeated
            while (spinLoop) {
                double freq = 180 + Math.random() * 60;
                short[] buf = buildTone(new double[]{freq, freq*1.5, freq*2.5},
                                        new double[]{0.3, 0.15, 0.08}, dur, 0.0);
                AudioTrack t = makeTrack(dur);
                t.write(buf, 0, buf.length);
                t.play();
                try { Thread.sleep(110); } catch (InterruptedException ignored) {}
                t.stop(); t.release();
            }
        });
    }

    public void stopSpin() { spinLoop = false; }

    // Win chime
    public void playWin() {
        if (!enabled) return;
        pool.execute(() -> {
            // Three ascending notes: C5 → E5 → G5 → C6
            double[] freqs  = {523.25, 659.25, 783.99, 1046.5};
            double[] delays = {0,      0.12,   0.24,   0.38};
            for (int i = 0; i < freqs.length; i++) {
                final int idx = i;
                try { Thread.sleep((long)(delays[idx] * 1000)); } catch (InterruptedException ignored) {}
                int dur = (int)(SAMPLE_RATE * 0.22);
                short[] buf = buildTone(new double[]{freqs[idx], freqs[idx]*2},
                                        new double[]{0.5, 0.15}, dur, 0.6);
                playBuf(buf, dur);
            }
        });
    }

    // Jackpot fanfare
    public void playJackpot() {
        if (!enabled) return;
        pool.execute(() -> {
            // Triumphant ascending arpeggio then sustained chord
            double[][] notes = {
                {261.63, 0.0,  0.10},  // C4
                {329.63, 0.1,  0.10},  // E4
                {392.00, 0.2,  0.10},  // G4
                {523.25, 0.3,  0.10},  // C5
                {659.25, 0.4,  0.10},  // E5
                {783.99, 0.5,  0.30},  // G5 sustained
                {1046.5, 0.55, 0.60},  // C6 big finish
            };
            for (double[] n : notes) {
                try { Thread.sleep((long)(n[1] * 1000)); } catch (InterruptedException ignored) {}
                int dur = (int)(SAMPLE_RATE * n[2]);
                short[] buf = buildTone(
                    new double[]{n[0], n[0]*2, n[0]*3},
                    new double[]{0.6,  0.25,   0.1}, dur, 0.7);
                playBuf(buf, dur);
            }
            // Coin shower: rapid high-pitched pings
            for (int i = 0; i < 12; i++) {
                try { Thread.sleep(60); } catch (InterruptedException ignored) {}
                double f = 1800 + Math.random() * 1200;
                int dur = (int)(SAMPLE_RATE * 0.06);
                short[] buf = buildTone(new double[]{f}, new double[]{0.4}, dur, 0.8);
                playBuf(buf, dur);
            }
        });
    }

    // Lose sound
    public void playLose() {
        if (!enabled) return;
        pool.execute(() ->
            playTone(new double[]{300, 220}, new double[]{0.35, 0.2}, new double[]{0.18, 0.22}, 0.7));
    }

    // Ambient casino music
    public void playAmbient() {
        if (!enabled || ambientLoop) return;
        ambientLoop = true;
        pool.execute(() -> {
            // Soft repeating jazzy chord progression: Cmaj7 → Am7 → Fmaj7 → G7
            double[][][] chords = {
                {{261.63,0.15},{329.63,0.10},{392.00,0.10},{493.88,0.07}}, // Cmaj7
                {{220.00,0.14},{261.63,0.09},{329.63,0.09},{392.00,0.06}}, // Am7
                {{174.61,0.13},{220.00,0.09},{261.63,0.09},{329.63,0.06}}, // Fmaj7
                {{196.00,0.14},{246.94,0.09},{293.66,0.09},{392.00,0.06}}, // G7
            };
            while (ambientLoop) {
                for (double[][] chord : chords) {
                    if (!ambientLoop) break;
                    int dur = (int)(SAMPLE_RATE * 1.2);
                    short[] buf = new short[dur];
                    for (double[] note : chord) {
                        short[] t = buildTone(new double[]{note[0]}, new double[]{note[1]}, dur, 0.5);
                        for (int i = 0; i < dur; i++) buf[i] = clamp(buf[i] + t[i]);
                    }
                    playBuf(buf, dur);
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                }
            }
        });
    }

    public void stopAmbient() {
        ambientLoop = false;
    }

    // Helpers

    /** Play a sequence of tones one after another */
    private void playTone(double[] freqs, double[] amps, double[] durations, double decay) {
        for (int i = 0; i < freqs.length; i++) {
            int dur = (int)(SAMPLE_RATE * durations[i]);
            short[] buf = buildTone(new double[]{freqs[i]}, new double[]{amps[i]}, dur, decay);
            playBuf(buf, dur);
            try { Thread.sleep((long)(durations[i] * 900)); } catch (InterruptedException ignored) {}
        }
    }

    /** Build a multi-partial sine wave with exponential decay */
    private short[] buildTone(double[] freqs, double[] amps, int samples, double decayRate) {
        short[] buf = new short[samples];
        for (int i = 0; i < samples; i++) {
            double t     = (double) i / SAMPLE_RATE;
            double env   = decayRate > 0 ? Math.exp(-decayRate * t * 10.0) : 1.0;
            double value = 0;
            for (int p = 0; p < freqs.length; p++) {
                value += amps[p] * Math.sin(2 * Math.PI * freqs[p] * t);
            }
            buf[i] = (short) Math.max(-32767, Math.min(32767, (int)(value * env * 32767)));
        }
        return buf;
    }

    private void playBuf(short[] buf, int samples) {
        AudioTrack t = makeTrack(samples);
        t.write(buf, 0, buf.length);
        t.play();
        try { Thread.sleep((long)(samples * 1000L / SAMPLE_RATE)); } catch (InterruptedException ignored) {}
        t.stop(); t.release();
    }

    private AudioTrack makeTrack(int samples) {
        return new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(samples * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build();
    }

    private short clamp(int v) {
        return (short) Math.max(-32767, Math.min(32767, v));
    }

    // Per-game sounds

    /** Roulette: ball bouncing around the wheel */
    public void playRouletteBall() {
        if (!enabled) return;
        pool.execute(() -> {
            // Rapid high-pitched ticks that slow down (ball decelerating)
            int ticks = 18;
            for (int i = 0; i < ticks; i++) {
                double freq = 2800 - i * 60;
                int dur = (int)(SAMPLE_RATE * 0.03);
                short[] buf = buildTone(new double[]{freq}, new double[]{0.5}, dur, 2.0);
                playBuf(buf, dur);
                long delay = 30 + i * 8L;  // slows down
                try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
            }
            // Final "clunk" into pocket
            int dur = (int)(SAMPLE_RATE * 0.08);
            short[] clunk = buildTone(new double[]{220, 180}, new double[]{0.6, 0.3}, dur, 1.5);
            playBuf(clunk, dur);
        });
    }

    /** Slot machine: reels spinning with mechanical clicks */
    public void playSlotReels() {
        if (!enabled) return;
        spinLoop = true;
        pool.execute(() -> {
            int tick = 0;
            while (spinLoop) {
                // Mechanical reel click
                double freq = 600 + (tick % 3) * 120;
                int dur = (int)(SAMPLE_RATE * 0.025);
                short[] buf = buildTone(new double[]{freq, freq*1.5}, new double[]{0.4, 0.15}, dur, 3.0);
                AudioTrack t = makeTrack(dur);
                t.write(buf, 0, buf.length);
                t.play();
                try { Thread.sleep(55); } catch (InterruptedException ignored) {}
                t.stop(); t.release();
                tick++;
            }
        });
    }

    /** Slot machine: reels stopping one by one */
    public void playSlotReelStop(int reelIndex) {
        if (!enabled) return;
        pool.execute(() -> {
            // Each reel stop: descending thud
            double freq = 400 - reelIndex * 60;
            int dur = (int)(SAMPLE_RATE * 0.12);
            short[] buf = buildTone(new double[]{freq, freq*0.7}, new double[]{0.7, 0.3}, dur, 1.2);
            playBuf(buf, dur);
        });
    }

    /** Poker: card dealing sound (crisp swish) */
    public void playCardDeal() {
        if (!enabled) return;
        pool.execute(() -> {
            // Short broadband swish
            int dur = (int)(SAMPLE_RATE * 0.05);
            short[] buf = new short[dur];
            java.util.Random rng = new java.util.Random();
            for (int i = 0; i < dur; i++) {
                double env = Math.exp(-4.0 * i / dur);
                // filtered noise sweep
                double v = (rng.nextDouble() * 2 - 1) * 0.55 * env;
                // Add a high-freq tone
                v += 0.2 * Math.sin(2 * Math.PI * 3200 * i / SAMPLE_RATE) * env;
                buf[i] = (short)(v * 32767);
            }
            playBuf(buf, dur);
        });
    }

    /** Poker: card flip */
    public void playCardFlip() {
        if (!enabled) return;
        pool.execute(() -> {
            int dur = (int)(SAMPLE_RATE * 0.07);
            short[] buf = new short[dur];
            java.util.Random rng = new java.util.Random();
            for (int i = 0; i < dur; i++) {
                double env = i < dur/3 ?
                    (double)i/(dur/3) :
                    Math.exp(-5.0 * (i - dur/3) / dur);
                double v = (rng.nextDouble()*2-1) * 0.4 * env;
                v += 0.25 * Math.sin(2 * Math.PI * 2400 * i / SAMPLE_RATE) * env;
                buf[i] = (short)(v * 32767);
            }
            playBuf(buf, dur);
        });
    }

    /** Lucky wheel: tick-tick as wheel spins */
    public void playWheelTick() {
        if (!enabled) return;
        pool.execute(() -> {
            int dur = (int)(SAMPLE_RATE * 0.028);
            short[] buf = buildTone(new double[]{1100, 550}, new double[]{0.45, 0.2}, dur, 4.0);
            playBuf(buf, dur);
        });
    }

    /** Game-specific ambient: select which ambient to play based on game type */
    public void playGameAmbient(String gameType) {
        if (!enabled || ambientLoop) return;
        ambientLoop = true;
        pool.execute(() -> {
            if (gameType.contains("slot")) {
                playSlotAmbient();
            } else if (gameType.contains("roulette")) {
                playRouletteAmbient();
            } else if (gameType.contains("poker")) {
                playPokerAmbient();
            } else {
                // wheel / default: upbeat
                playWheelAmbient();
            }
        });
    }

    private void playSlotAmbient() {
        // Bright, energetic: major 7th chord loop
        double[][][] chords = {
            {{349.23,0.12},{440.0,0.09},{523.25,0.09},{698.46,0.06}},  // Fmaj7
            {{392.0, 0.12},{493.88,0.09},{587.33,0.09},{783.99,0.06}}, // Gmaj7
        };
        while (ambientLoop) {
            for (double[][] chord : chords) {
                if (!ambientLoop) break;
                int dur = (int)(SAMPLE_RATE * 0.9);
                short[] buf = new short[dur];
                for (double[] note : chord) {
                    short[] t = buildTone(new double[]{note[0]}, new double[]{note[1]}, dur, 0.4);
                    for (int i = 0; i < dur; i++) buf[i] = clamp(buf[i] + t[i]);
                }
                playBuf(buf, dur);
                try { Thread.sleep(40); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void playRouletteAmbient() {
        // Sophisticated, tense: minor chords
        double[][][] chords = {
            {{220.0,0.10},{261.63,0.07},{329.63,0.07},{415.30,0.05}},  // Am7
            {{196.0,0.10},{246.94,0.07},{293.66,0.07},{369.99,0.05}},  // Gm7
            {{174.61,0.10},{220.0,0.07},{261.63,0.07},{329.63,0.05}},  // Fm7
            {{185.0,0.10},{233.08,0.07},{277.18,0.07},{349.23,0.05}},  // F#m7
        };
        while (ambientLoop) {
            for (double[][] chord : chords) {
                if (!ambientLoop) break;
                int dur = (int)(SAMPLE_RATE * 1.4);
                short[] buf = new short[dur];
                for (double[] note : chord) {
                    short[] t = buildTone(new double[]{note[0]}, new double[]{note[1]}, dur, 0.35);
                    for (int i = 0; i < dur; i++) buf[i] = clamp(buf[i] + t[i]);
                }
                playBuf(buf, dur);
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void playPokerAmbient() {
        // Cool jazz: dom7 chords
        double[][][] chords = {
            {{261.63,0.12},{329.63,0.09},{392.0,0.09},{466.16,0.06}},  // C7
            {{246.94,0.12},{311.13,0.09},{369.99,0.09},{440.0,0.06}},  // B7
            {{220.0,0.12},{277.18,0.09},{329.63,0.09},{415.30,0.06}},  // A7
        };
        while (ambientLoop) {
            for (double[][] chord : chords) {
                if (!ambientLoop) break;
                int dur = (int)(SAMPLE_RATE * 1.6);
                short[] buf = new short[dur];
                for (double[] note : chord) {
                    short[] t = buildTone(new double[]{note[0]}, new double[]{note[1]}, dur, 0.3);
                    for (int i = 0; i < dur; i++) buf[i] = clamp(buf[i] + t[i]);
                }
                playBuf(buf, dur);
                try { Thread.sleep(60); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void playWheelAmbient() {
        // Playful & upbeat
        double[][][] chords = {
            {{261.63,0.13},{329.63,0.10},{392.0,0.10},{493.88,0.07}},  // Cmaj7
            {{293.66,0.13},{369.99,0.10},{440.0,0.10},{554.37,0.07}},  // Dmaj7
            {{261.63,0.13},{329.63,0.10},{392.0,0.10},{493.88,0.07}},  // Cmaj7
            {{246.94,0.13},{311.13,0.10},{369.99,0.10},{466.16,0.07}}, // Bdom7
        };
        while (ambientLoop) {
            for (double[][] chord : chords) {
                if (!ambientLoop) break;
                int dur = (int)(SAMPLE_RATE * 1.0);
                short[] buf = new short[dur];
                for (double[] note : chord) {
                    short[] t = buildTone(new double[]{note[0]}, new double[]{note[1]}, dur, 0.45);
                    for (int i = 0; i < dur; i++) buf[i] = clamp(buf[i] + t[i]);
                }
                playBuf(buf, dur);
                try { Thread.sleep(40); } catch (InterruptedException ignored) {}
            }
        }
    }

    public void release() {
        spinLoop   = false;
        ambientLoop= false;
        pool.shutdownNow();
    }
}
