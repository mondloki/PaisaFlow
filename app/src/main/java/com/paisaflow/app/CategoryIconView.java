package com.paisaflow.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

final class CategoryIconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private String icon = "dot";
    private int iconColor = 0xFF667085;

    CategoryIconView(Context context) { super(context); }
    CategoryIconView(Context context, AttributeSet attrs) { super(context, attrs); }

    void setIcon(String icon, int color) {
        this.icon = icon == null ? "dot" : icon;
        this.iconColor = color;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth(), getHeight()) / 48f;
        float dx = (getWidth() - 48f * scale) / 2f;
        float dy = (getHeight() - 48f * scale) / 2f;
        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(iconColor);
        canvas.drawRoundRect(new RectF(0, 0, 48, 48), 14, 14, paint);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(3f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        path.reset();

        switch (icon) {
            case "chart":
                path.moveTo(12, 33); path.lineTo(20, 25); path.lineTo(27, 29); path.lineTo(37, 16);
                canvas.drawPath(path, paint); canvas.drawLine(30, 16, 37, 16, paint); canvas.drawLine(37, 16, 37, 23, paint);
                break;
            case "bond":
                canvas.drawRoundRect(new RectF(11, 14, 37, 34), 3, 3, paint);
                canvas.drawCircle(24, 24, 5, paint); canvas.drawLine(14, 19, 18, 19, paint); canvas.drawLine(30, 29, 34, 29, paint);
                break;
            case "layers":
                drawDiamond(canvas, 24, 15, 13, 7); drawDiamond(canvas, 24, 23, 13, 7); drawDiamond(canvas, 24, 31, 13, 7);
                break;
            case "swap":
                canvas.drawLine(12, 18, 35, 18, paint); canvas.drawLine(30, 13, 36, 18, paint); canvas.drawLine(36, 18, 30, 23, paint);
                canvas.drawLine(36, 30, 13, 30, paint); canvas.drawLine(18, 25, 12, 30, paint); canvas.drawLine(12, 30, 18, 35, paint);
                break;
            case "food":
                canvas.drawCircle(25, 25, 9, paint); canvas.drawCircle(25, 25, 5, paint);
                canvas.drawLine(12, 14, 12, 34, paint); canvas.drawLine(9, 14, 9, 21, paint); canvas.drawLine(15, 14, 15, 21, paint);
                canvas.drawLine(36, 14, 36, 34, paint); path.moveTo(36, 14); path.quadTo(42, 20, 36, 25); canvas.drawPath(path, paint);
                break;
            case "car":
                path.moveTo(10, 29); path.lineTo(13, 20); path.lineTo(18, 16); path.lineTo(31, 16); path.lineTo(36, 22); path.lineTo(39, 29); path.close();
                canvas.drawPath(path, paint); canvas.drawCircle(16, 31, 3, paint); canvas.drawCircle(34, 31, 3, paint); canvas.drawLine(15, 22, 34, 22, paint);
                break;
            case "bolt":
                path.moveTo(27, 9); path.lineTo(15, 27); path.lineTo(23, 27); path.lineTo(20, 39); path.lineTo(34, 20); path.lineTo(26, 20); path.close();
                canvas.drawPath(path, paint); break;
            case "wifi":
                canvas.drawArc(new RectF(9, 13, 39, 39), 220, 100, false, paint);
                canvas.drawArc(new RectF(15, 20, 33, 37), 220, 100, false, paint); canvas.drawCircle(24, 33, 1.5f, paint); break;
            case "phone":
                canvas.drawRoundRect(new RectF(16, 9, 32, 39), 3, 3, paint); canvas.drawLine(21, 13, 27, 13, paint); canvas.drawCircle(24, 34, 1, paint); break;
            case "water":
                path.moveTo(24, 9); path.cubicTo(18, 18, 14, 23, 14, 29); path.cubicTo(14, 37, 34, 37, 34, 29); path.cubicTo(34, 23, 30, 18, 24, 9); path.close();
                canvas.drawPath(path, paint); break;
            case "flame":
                path.moveTo(25, 8); path.cubicTo(27, 17, 38, 20, 35, 31); path.cubicTo(33, 40, 16, 40, 13, 30); path.cubicTo(11, 23, 17, 19, 20, 14); path.cubicTo(20, 20, 24, 21, 25, 8); path.close();
                canvas.drawPath(path, paint); break;
            case "home":
                path.moveTo(9, 24); path.lineTo(24, 11); path.lineTo(39, 24); path.moveTo(14, 22); path.lineTo(14, 37); path.lineTo(34, 37); path.lineTo(34, 22);
                canvas.drawPath(path, paint); canvas.drawRect(new RectF(21, 28, 27, 37), paint); break;
            case "tools":
                canvas.drawLine(13, 35, 34, 14, paint); canvas.drawCircle(12, 36, 3, paint); canvas.drawLine(15, 13, 36, 34, paint); canvas.drawCircle(36, 35, 3, paint); break;
            case "shield":
                path.moveTo(24, 9); path.lineTo(36, 14); path.lineTo(34, 29); path.quadTo(31, 36, 24, 39); path.quadTo(17, 36, 14, 29); path.lineTo(12, 14); path.close();
                canvas.drawPath(path, paint); canvas.drawLine(18, 24, 22, 28, paint); canvas.drawLine(22, 28, 30, 20, paint); break;
            case "repeat":
                canvas.drawArc(new RectF(11, 13, 37, 34), 205, 225, false, paint); canvas.drawLine(12, 15, 12, 23, paint); canvas.drawLine(12, 15, 20, 15, paint);
                canvas.drawArc(new RectF(11, 15, 37, 36), 25, 225, false, paint); canvas.drawLine(36, 33, 36, 25, paint); canvas.drawLine(36, 33, 28, 33, paint); break;
            case "card":
                canvas.drawRoundRect(new RectF(9, 14, 39, 35), 3, 3, paint); canvas.drawLine(10, 21, 38, 21, paint); canvas.drawLine(14, 29, 22, 29, paint); break;
            case "bank":
                path.moveTo(9, 18); path.lineTo(24, 10); path.lineTo(39, 18); path.close(); canvas.drawPath(path, paint);
                canvas.drawLine(12, 36, 36, 36, paint); canvas.drawLine(15, 21, 15, 33, paint); canvas.drawLine(24, 21, 24, 33, paint); canvas.drawLine(33, 21, 33, 33, paint); break;
            case "receipt":
                path.moveTo(14, 9); path.lineTo(18, 12); path.lineTo(22, 9); path.lineTo(26, 12); path.lineTo(30, 9); path.lineTo(34, 12); path.lineTo(34, 39); path.lineTo(30, 36); path.lineTo(26, 39); path.lineTo(22, 36); path.lineTo(18, 39); path.lineTo(14, 36); path.close();
                canvas.drawPath(path, paint); canvas.drawLine(19, 21, 29, 21, paint); canvas.drawLine(19, 27, 29, 27, paint); break;
            case "health":
                canvas.drawLine(24, 13, 24, 35, paint); canvas.drawLine(13, 24, 35, 24, paint); break;
            case "bag":
                canvas.drawRoundRect(new RectF(12, 17, 36, 38), 3, 3, paint); canvas.drawArc(new RectF(18, 9, 30, 24), 180, 180, false, paint); break;
            case "send":
                path.moveTo(9, 12); path.lineTo(40, 23); path.lineTo(27, 28); path.lineTo(21, 39); path.lineTo(19, 27); path.close(); canvas.drawPath(path, paint); break;
            case "deposit":
            case "piggy":
                canvas.drawLine(24, 10, 24, 29, paint);
                canvas.drawLine(17, 22, 24, 29, paint);
                canvas.drawLine(31, 22, 24, 29, paint);
                path.moveTo(12, 29); path.lineTo(12, 36); path.lineTo(36, 36); path.lineTo(36, 29);
                canvas.drawPath(path, paint);
                break;
            case "dividend":
                canvas.drawCircle(24, 24, 13, paint);
                canvas.drawLine(17, 24, 31, 24, paint);
                canvas.drawLine(24, 17, 24, 31, paint);
                break;
            case "refund":
                canvas.drawArc(new RectF(11, 11, 37, 37), 45, 285, false, paint); canvas.drawLine(11, 14, 11, 23, paint); canvas.drawLine(11, 14, 20, 14, paint); break;
            case "rupee":
                drawTextIcon(canvas, "₹", 27); break;
            case "percent":
                drawTextIcon(canvas, "%", 25); break;
            case "coin":
                canvas.drawCircle(24, 24, 13, paint); canvas.drawCircle(24, 24, 9, paint); canvas.drawLine(24, 17, 24, 31, paint); canvas.drawArc(new RectF(19, 17, 29, 24), 90, 240, false, paint); break;
            case "dots":
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(15, 24, 3, paint); canvas.drawCircle(24, 24, 3, paint); canvas.drawCircle(33, 24, 3, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            default:
                canvas.drawCircle(24, 24, 5, paint); break;
        }
        canvas.restore();
    }

    private void drawDiamond(Canvas canvas, float x, float y, float width, float height) {
        path.reset(); path.moveTo(x, y - height / 2); path.lineTo(x + width, y); path.lineTo(x, y + height / 2); path.lineTo(x - width, y); path.close(); canvas.drawPath(path, paint);
    }

    private void drawTextIcon(Canvas canvas, String value, float size) {
        paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(size); paint.setFakeBoldText(true);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(value, 24, 24 - (metrics.ascent + metrics.descent) / 2f, paint);
        paint.setFakeBoldText(false); paint.setTextAlign(Paint.Align.LEFT); paint.setStyle(Paint.Style.STROKE);
    }
}
