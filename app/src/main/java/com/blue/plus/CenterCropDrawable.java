package com.blue.plus;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class CenterCropDrawable extends Drawable {
    private final Bitmap bitmap;
    private final Paint paint;

    public CenterCropDrawable(Bitmap bitmap) {
        this.bitmap = bitmap;
        this.paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    }

    @Override
    public void draw(Canvas canvas) {
        if (bitmap == null || bitmap.isRecycled()) return;
        Rect bounds = getBounds();
        int viewWidth = bounds.width();
        int viewHeight = bounds.height();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        int bmpWidth = bitmap.getWidth();
        int bmpHeight = bitmap.getHeight();

        float scale;
        float dx = 0;
        float dy = 0;

        if (bmpWidth * viewHeight > viewWidth * bmpHeight) {
            scale = (float) viewHeight / (float) bmpHeight;
            dx = (viewWidth - bmpWidth * scale) * 0.5f;
        } else {
            scale = (float) viewWidth / (float) bmpWidth;
            dy = (viewHeight - bmpHeight * scale) * 0.5f;
        }

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
