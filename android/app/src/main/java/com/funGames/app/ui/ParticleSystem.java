package com.funGames.app.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Physics-based particle system.
 * Call burst(cx,cy,W,H) → fires particles.
 * Call draw(canvas) every frame.
 * Call isAlive() to know when all particles are dead.
 */
public class ParticleSystem {

    private static final int[] COLORS = {
        0xFFFFD84A, 0xFFD4AF37, 0xFFFFE680, 0xFF34D399,
        0xFF8B5CF6, 0xFFFF8C00, 0xFFF87171, 0xFFFFFFFF
    };

    public static class Particle {
        float x, y, vx, vy, alpha, size, rot, rotSpeed;
        int color;
        boolean isRect; // rect or circle

        Particle(float x, float y, float vx, float vy, int color, float size, boolean isRect) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.color = color; this.size = size; this.isRect = isRect;
            this.alpha = 1f; this.rot = (float)(Math.random()*360);
            this.rotSpeed = -8f + (float)(Math.random()*16);
        }

        // returns false when dead
        boolean update(float gravity, float drag) {
            vy += gravity;
            vx *= drag; vy *= drag;
            x += vx; y += vy;
            alpha -= 0.018f;
            rot += rotSpeed;
            return alpha > 0f;
        }
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random();
    private ValueAnimator ticker;
    private Runnable onDead;

    public void burst(float cx, float cy, int W, int H, boolean isJackpot) {
        particles.clear();
        int count = isJackpot ? 180 : 90;
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(rng.nextDouble() * 360);
            float speed  = 4f + rng.nextFloat() * (isJackpot ? 22f : 14f);
            float vx = (float)(Math.cos(angle) * speed);
            float vy = (float)(Math.sin(angle) * speed) - (isJackpot ? 8f : 4f);
            int col = COLORS[rng.nextInt(COLORS.length)];
            float size = 4f + rng.nextFloat() * (isJackpot ? 12f : 8f);
            particles.add(new Particle(cx, cy, vx, vy, col, size, rng.nextBoolean()));
        }
    }

    public void draw(Canvas canvas) {
        float gravity = 0.45f, drag = 0.97f;
        List<Particle> dead = new ArrayList<>();
        for (Particle p : particles) {
            if (!p.update(gravity, drag)) { dead.add(p); continue; }
            paint.setColor(p.color);
            paint.setAlpha((int)(p.alpha * 255));
            canvas.save();
            canvas.translate(p.x, p.y);
            canvas.rotate(p.rot);
            if (p.isRect) {
                canvas.drawRect(-p.size/2, -p.size/4, p.size/2, p.size/4, paint);
            } else {
                canvas.drawCircle(0, 0, p.size/2, paint);
            }
            canvas.restore();
        }
        particles.removeAll(dead);
        if (particles.isEmpty() && onDead != null) { onDead.run(); onDead = null; }
    }

    public boolean isAlive() { return !particles.isEmpty(); }
    public void setOnDeadListener(Runnable r) { this.onDead = r; }
    public void clear() { particles.clear(); }
}
