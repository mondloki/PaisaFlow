package com.paisaflow.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

final class CalendarIconView extends View {
    private static final int EMERALD = 0xFF0F9D78;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    CalendarIconView(Context context) {
        super(context);
        paint.setColor(EMERALD);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth(), getHeight()) / 32f;
        float left = (getWidth() - 24f * scale) / 2f;
        float top = (getHeight() - 24f * scale) / 2f;
        paint.setStrokeWidth(2f * scale);
        bounds.set(left, top + 3f * scale, left + 24f * scale, top + 24f * scale);
        canvas.drawRoundRect(bounds, 3f * scale, 3f * scale, paint);
        canvas.drawLine(left, top + 10f * scale, left + 24f * scale, top + 10f * scale, paint);
        canvas.drawLine(left + 6f * scale, top, left + 6f * scale, top + 7f * scale, paint);
        canvas.drawLine(left + 18f * scale, top, left + 18f * scale, top + 7f * scale, paint);
    }
}
