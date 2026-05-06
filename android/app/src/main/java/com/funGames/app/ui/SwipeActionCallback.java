package com.funGames.app.ui;

import android.graphics.*;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.funGames.app.model.Game;

/**
 * Swipe gestures on game cards:
 *   Swipe RIGHT → PLAY  (green)
 *   Swipe LEFT  → RATE  (gold stars)
 *
 * Attach with:
 *   new ItemTouchHelper(new SwipeActionCallback(adapter, listener)).attachToRecyclerView(rv);
 */
public class SwipeActionCallback extends ItemTouchHelper.SimpleCallback {

    public interface SwipeListener {
        void onSwipePlay(Game game);
        void onSwipeRate(Game game);
    }

    private final GamesAdapter  adapter;
    private final SwipeListener listener;
    private final Paint bgPaint  = new Paint();
    private final Paint txtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SwipeActionCallback(GamesAdapter a, SwipeListener l) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.adapter = a; this.listener = l;
        txtPaint.setColor(Color.WHITE);
        txtPaint.setTextSize(44f);
        txtPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        txtPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView rv,
                          @NonNull RecyclerView.ViewHolder vh,
                          @NonNull RecyclerView.ViewHolder target) { return false; }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        int pos = vh.getAdapterPosition();
        Game g = adapter.getGameAt(pos);
        // Restore item immediately (we don't remove it)
        adapter.notifyItemChanged(pos);
        if (g == null || listener == null) return;
        if (direction == ItemTouchHelper.RIGHT) listener.onSwipePlay(g);
        else                                    listener.onSwipeRate(g);
    }

    @Override
    public void onChildDraw(@NonNull Canvas c,
                            @NonNull RecyclerView rv,
                            @NonNull RecyclerView.ViewHolder vh,
                            float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {

        View item = vh.itemView;
        float cx = item.getLeft() + item.getWidth() / 2f;
        float cy = item.getTop()  + item.getHeight() / 2f;

        if (dX > 0) {
            // Swipe right → PLAY (green)
            float prog = Math.min(1f, Math.abs(dX) / (item.getWidth() * 0.4f));
            bgPaint.setColor(Color.argb((int)(200*prog), 0x06, 0x95, 0x69));
            c.drawRoundRect(new RectF(item.getLeft(), item.getTop(),
                    item.getLeft() + dX, item.getBottom()), 18,18, bgPaint);
            if (dX > 60f) {
                txtPaint.setAlpha((int)(255 * prog));
                c.drawText("▶ PLAY", item.getLeft() + dX / 2f, cy + 16f, txtPaint);
            }
        } else if (dX < 0) {
            // Swipe left → RATE (gold)
            float prog = Math.min(1f, Math.abs(dX) / (item.getWidth() * 0.4f));
            bgPaint.setColor(Color.argb((int)(200*prog), 0xB0, 0x7A, 0x0E));
            c.drawRoundRect(new RectF(item.getRight() + dX, item.getTop(),
                    item.getRight(), item.getBottom()), 18,18, bgPaint);
            if (-dX > 60f) {
                txtPaint.setAlpha((int)(255 * prog));
                c.drawText("★ RATE", item.getRight() + dX / 2f, cy + 16f, txtPaint);
            }
        }
        super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder vh) { return 0.35f; }
}
