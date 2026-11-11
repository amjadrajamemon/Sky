package com.skycast.pro;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/** Lightweight, fully local gradient surface; respects the saved widget palette. */
final class WidgetBackdropPainter {
    private WidgetBackdropPainter() {}

    static Bitmap make(int width, int height, int background, int accent, int surface, int theme) {
        int w = Math.max(64, Math.min(1000, width));
        int h = Math.max(64, Math.min(640, height));
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF rect = new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        float radius = theme == 9 ? 5 : theme == 7 ? 10 : theme == 8 ? 26 : Math.max(13, Math.min(25, h * .13f));
        int start = (background & 0xff000000) | (mix(background, accent, theme == 8 ? .32f : theme == 9 ? .035f : .13f) & 0xffffff);
        int end = (background & 0xff000000) | (mix(background, surface, theme == 9 ? .025f : theme == 5 ? .35f : .20f) & 0xffffff);
        p.setShader(new LinearGradient(0, 0, w, h, start, end, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, radius, radius, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, w / 420f));
        p.setColor((background & 0xff000000) == 0 ? 0 : ((0x45 << 24) | (accent & 0xffffff)));
        canvas.drawRoundRect(rect, radius, radius, p);
        p.setStyle(Paint.Style.FILL);
        if(theme == 8 || theme == 5) {
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1.3f,w/260f));
            p.setColor((0x32<<24) | (accent & 0xffffff));
            float cx=w*.78f,cy=h*.25f;
            canvas.drawCircle(cx,cy,h*.48f,p);
            if(theme==8)canvas.drawCircle(cx,cy,h*.27f,p);
            p.setStyle(Paint.Style.FILL);
        }
        if(theme == 9) {
            p.setColor((0x65<<24) | (accent & 0xffffff));
            canvas.drawRect(0,0,Math.max(2,w/140f),h,p);
        }
        return bitmap;
    }

    private static int mix(int a, int b, float portion) {
        int r = Math.round(((a >>> 16) & 255) * (1 - portion) + ((b >>> 16) & 255) * portion);
        int g = Math.round(((a >>> 8) & 255) * (1 - portion) + ((b >>> 8) & 255) * portion);
        int blue = Math.round((a & 255) * (1 - portion) + (b & 255) * portion);
        return (r << 16) | (g << 8) | blue;
    }
}
