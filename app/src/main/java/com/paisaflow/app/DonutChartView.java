package com.paisaflow.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

final class DonutChartView extends View {
    private int ink = 0xFF0B1220;
    private int muted = 0xFF667085;
    private int empty = 0xFFE3E7E0;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final ArrayList<LedgerModels.CategoryTotal> totals = new ArrayList<>();
    private String caption = "EXPENSES";

    DonutChartView(Context context) { super(context); }

    void setDarkMode(boolean dark) {
        ink = dark ? 0xFFF2F5F9 : 0xFF0B1220;
        muted = dark ? 0xFFA5AFBF : 0xFF667085;
        empty = dark ? 0xFF303B4B : 0xFFE3E7E0;
        invalidate();
    }

    void setData(List<LedgerModels.CategoryTotal> data, String caption) {
        totals.clear();
        if (data != null) totals.addAll(data);
        this.caption = caption;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight()) - dp(24);
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        bounds.set(left, top, left + size, top + size);
        float stroke = size * 0.19f;
        bounds.inset(stroke / 2f, stroke / 2f);
        long total = 0;
        for (LedgerModels.CategoryTotal item : totals) total += item.amountMinor;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.BUTT);
        if (total == 0) {
            paint.setColor(empty);
            canvas.drawArc(bounds, -90, 360, false, paint);
        } else {
            float start = -90f;
            for (int i = 0; i < totals.size(); i++) {
                LedgerModels.CategoryTotal item = totals.get(i);
                float sweep = i == totals.size() - 1 ? 270f - start : 360f * item.amountMinor / total;
                paint.setColor(item.category.color);
                canvas.drawArc(bounds, start + 0.7f, Math.max(0, sweep - 1.4f), false, paint);
                start += sweep;
            }
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(muted);
        paint.setTextSize(dp(10));
        paint.setFakeBoldText(true);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        canvas.drawText(caption, centerX, centerY - dp(7), paint);
        paint.setColor(ink);
        paint.setTextSize(dp(18));
        canvas.drawText(Money.format(total), centerX, centerY + dp(17), paint);
        paint.setFakeBoldText(false);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
