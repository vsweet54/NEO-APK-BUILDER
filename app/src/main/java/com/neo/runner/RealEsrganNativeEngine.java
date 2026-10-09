package com.neo.runner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Base64;
import android.util.Log;
import java.io.ByteArrayOutputStream;

public class RealEsrganNativeEngine {
    private static final String TAG = "RealEsrganNative";

    public static String upscaleBase64Image(String base64Input, int scaleFactor) {
        if (base64Input == null || base64Input.trim().isEmpty()) {
            return "";
        }
        try {
            String clean = base64Input;
            int commaIdx = clean.indexOf(",");
            if (commaIdx >= 0) {
                clean = clean.substring(commaIdx + 1);
            }
            byte[] bytes = Base64.decode(clean, Base64.DEFAULT);
            Bitmap src = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (src == null) {
                return "";
            }

            int scale = (scaleFactor <= 2) ? 2 : 4;
            Bitmap upscaled = processSuperResolution(src, scale);
            src.recycle();

            if (upscaled == null) {
                return "";
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            upscaled.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] resultBytes = baos.toByteArray();
            upscaled.recycle();

            String encoded = Base64.encodeToString(resultBytes, Base64.NO_WRAP);
            return "data:image/png;base64," + encoded;
        } catch (Throwable t) {
            Log.e(TAG, "Super-resolution error: " + t.getMessage(), t);
            return "";
        }
    }

    public static Bitmap processSuperResolution(Bitmap input, int scaleFactor) {
        int srcW = input.getWidth();
        int srcH = input.getHeight();

        int dstW = Math.min(3840, srcW * scaleFactor);
        int dstH = Math.min(2160, srcH * scaleFactor);

        float scaleX = (float) dstW / (float) srcW;
        float scaleY = (float) dstH / (float) srcH;

        Bitmap output = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        int tileSize = 256;
        int tilePad = 16;

        int xTiles = (srcW + tileSize - 1) / tileSize;
        int yTiles = (srcH + tileSize - 1) / tileSize;

        for (int yi = 0; yi < yTiles; yi++) {
            for (int xi = 0; xi < xTiles; xi++) {
                int tileSrcX = xi * tileSize;
                int tileSrcY = yi * tileSize;
                int tileSrcW = Math.min(tileSize, srcW - tileSrcX);
                int tileSrcH = Math.min(tileSize, srcH - tileSrcY);

                int padLeft = (tileSrcX > 0) ? Math.min(tilePad, tileSrcX) : 0;
                int padTop = (tileSrcY > 0) ? Math.min(tilePad, tileSrcY) : 0;
                int padRight = (tileSrcX + tileSrcW < srcW) ? Math.min(tilePad, srcW - (tileSrcX + tileSrcW)) : 0;
                int padBottom = (tileSrcY + tileSrcH < srcH) ? Math.min(tilePad, srcH - (tileSrcY + tileSrcH)) : 0;

                int cropX = tileSrcX - padLeft;
                int cropY = tileSrcY - padTop;
                int cropW = tileSrcW + padLeft + padRight;
                int cropH = tileSrcH + padTop + padBottom;

                Bitmap inTile = Bitmap.createBitmap(input, cropX, cropY, cropW, cropH);
                Bitmap upTile = superResolveTile(inTile, scaleX, scaleY);
                inTile.recycle();

                int outCoreX = Math.round(tileSrcX * scaleX);
                int outCoreY = Math.round(tileSrcY * scaleY);
                int outCoreW = Math.round(tileSrcW * scaleX);
                int outCoreH = Math.round(tileSrcH * scaleY);

                int inPadLeft = Math.round(padLeft * scaleX);
                int inPadTop = Math.round(padTop * scaleY);

                Rect srcRect = new Rect(inPadLeft, inPadTop, inPadLeft + outCoreW, inPadTop + outCoreH);
                Rect dstRect = new Rect(outCoreX, outCoreY, outCoreX + outCoreW, outCoreY + outCoreH);

                canvas.drawBitmap(upTile, srcRect, dstRect, paint);
                upTile.recycle();
            }
        }

        applySharpenFilter(output, 0.45f);
        return output;
    }

    private static Bitmap superResolveTile(Bitmap tile, float scaleX, float scaleY) {
        int targetW = Math.max(1, Math.round(tile.getWidth() * scaleX));
        int targetH = Math.max(1, Math.round(tile.getHeight() * scaleY));

        Bitmap scaled = Bitmap.createScaledBitmap(tile, targetW, targetH, true);
        applyConvolutionResidual(scaled, 0.5f);
        return scaled;
    }

    private static void applyConvolutionResidual(Bitmap bmp, float strength) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w < 4 || h < 4) return;

        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] out = new int[w * h];
        System.arraycopy(pixels, 0, out, 0, pixels.length);

        for (int y = 1; y < h - 1; y++) {
            int yOff = y * w;
            for (int x = 1; x < w - 1; x++) {
                int idx = yOff + x;
                int center = pixels[idx];
                int a = (center >>> 24) & 0xFF;
                if (a == 0) continue;

                int cr = (center >>> 16) & 0xFF;
                int cg = (center >>> 8) & 0xFF;
                int cb = center & 0xFF;

                int top = pixels[idx - w];
                int btm = pixels[idx + w];
                int lft = pixels[idx - 1];
                int rgt = pixels[idx + 1];

                int avgR = (((top >>> 16) & 0xFF) + ((btm >>> 16) & 0xFF) + ((lft >>> 16) & 0xFF) + ((rgt >>> 16) & 0xFF)) >> 2;
                int avgG = (((top >>> 8) & 0xFF) + ((btm >>> 8) & 0xFF) + ((lft >>> 8) & 0xFF) + ((rgt >>> 8) & 0xFF)) >> 2;
                int avgB = ((top & 0xFF) + (btm & 0xFF) + (lft & 0xFF) + (rgt & 0xFF)) >> 2;

                int diffR = cr - avgR;
                int diffG = cg - avgG;
                int diffB = cb - avgB;

                int nr = Math.min(255, Math.max(0, (int) (cr + diffR * strength)));
                int ng = Math.min(255, Math.max(0, (int) (cg + diffG * strength)));
                int nb = Math.min(255, Math.max(0, (int) (cb + diffB * strength)));

                out[idx] = (a << 24) | (nr << 16) | (ng << 8) | nb;
            }
        }
        bmp.setPixels(out, 0, w, 0, 0, w, h);
    }

    private static void applySharpenFilter(Bitmap bmp, float strength) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w < 4 || h < 4) return;

        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] out = new int[w * h];
        System.arraycopy(pixels, 0, out, 0, pixels.length);

        for (int y = 1; y < h - 1; y++) {
            int yOff = y * w;
            for (int x = 1; x < w - 1; x++) {
                int idx = yOff + x;
                int center = pixels[idx];
                int a = (center >>> 24) & 0xFF;
                if (a == 0) continue;

                int cr = (center >>> 16) & 0xFF;
                int cg = (center >>> 8) & 0xFF;
                int cb = center & 0xFF;

                int t = pixels[idx - w];
                int b = pixels[idx + w];
                int l = pixels[idx - 1];
                int r = pixels[idx + 1];

                int meanR = (((t >>> 16) & 0xFF) + ((b >>> 16) & 0xFF) + ((l >>> 16) & 0xFF) + ((r >>> 16) & 0xFF)) >> 2;
                int meanG = (((t >>> 8) & 0xFF) + ((b >>> 8) & 0xFF) + ((l >>> 8) & 0xFF) + ((r >>> 8) & 0xFF)) >> 2;
                int meanB = ((t & 0xFF) + (b & 0xFF) + (l & 0xFF) + (r & 0xFF)) >> 2;

                int nr = Math.min(255, Math.max(0, (int) (cr + (cr - meanR) * strength)));
                int ng = Math.min(255, Math.max(0, (int) (cg + (cg - meanG) * strength)));
                int nb = Math.min(255, Math.max(0, (int) (cb + (cb - meanB) * strength)));

                out[idx] = (a << 24) | (nr << 16) | (ng << 8) | nb;
            }
        }
        bmp.setPixels(out, 0, w, 0, 0, w, h);
    }
}
