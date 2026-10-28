package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

/** The "CONTINUE WATCHING" and "RECOMMENDED FOR YOU" headings above their groups' first rows, and a gap below each. */
final class WatchDecoration extends RecyclerView.ItemDecoration {
    private final AppAdapter adapter;
    private final float density;
    private final String continueHeading, recsHeading;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    WatchDecoration(Context ctx, AppAdapter adapter) {
        this.adapter = adapter;
        this.density = ctx.getResources().getDisplayMetrics().density;
        this.continueHeading = ctx.getString(R.string.continue_heading);
        this.recsHeading = ctx.getString(R.string.recommended_heading);
        paint.setTypeface(UiFont.get(ctx, R.string.font_medium));
        paint.setTextSize(18 * density);
        paint.setLetterSpacing(0.01f);
        paint.setColor(0xB3FFFFFF);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos < 0) return;
        for (WatchRows rows : new WatchRows[]{adapter.watchRows(), adapter.recRows()}) {
            if (rows == null) continue;
            if (rows.inFirstRow(pos)) outRect.top += (int) (36 * density);
            if (rows.inLastRow(pos)) outRect.bottom += (int) (22 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        draw(c, parent, adapter.watchRows(), continueHeading);
        draw(c, parent, adapter.recRows(), recsHeading);
    }

    private void draw(Canvas c, RecyclerView parent, WatchRows rows, String heading) {
        if (rows == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (parent.getChildAdapterPosition(child) != rows.start) continue;
            View card = child.findViewById(R.id.card);
            float left = child.getLeft() + (card == null ? 0 : card.getLeft());
            c.drawText(heading, left, child.getTop() - 10 * density, paint);
            return;
        }
    }
}
