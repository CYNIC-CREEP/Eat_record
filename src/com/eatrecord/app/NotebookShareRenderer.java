package com.eatrecord.app;

import android.graphics.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.BooleanSupplier;

/** The worker renders only this immutable presentation snapshot, with no Activity or View access. */
final class NotebookShareRenderer {
    static final class Snapshot {
        final String dateTitle, emoji, mealCountLine, story, timeLine, mediaLine, paperStyle;
        final List<String> storyLines;
        final List<NotebookMediaEntries.Entry> entries;
        final boolean paperOnly, splitStory, straightStickers;
        final int backgroundTop, backgroundBottom, frameFill, frameStroke;
        final Typeface headingTypeface, bookBold, bookRegular;
        final File directory;
        Snapshot(File directory, String dateTitle, String emoji, String mealCountLine, String story,
                 List<String> storyLines, String timeLine, String mediaLine, String paperStyle,
                 List<NotebookMediaEntries.Entry> entries, boolean paperOnly, boolean splitStory,
                 boolean straightStickers, int backgroundTop, int backgroundBottom, int frameFill,
                 int frameStroke, Typeface headingTypeface, Typeface bookBold, Typeface bookRegular) {
            this.directory = directory;
            this.dateTitle = dateTitle;
            this.emoji = emoji;
            this.mealCountLine = mealCountLine;
            this.story = story;
            this.storyLines = Collections.unmodifiableList(new ArrayList<>(storyLines));
            this.timeLine = timeLine;
            this.mediaLine = mediaLine;
            this.paperStyle = paperStyle;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.paperOnly = paperOnly;
            this.splitStory = splitStory;
            this.straightStickers = straightStickers;
            this.backgroundTop = backgroundTop;
            this.backgroundBottom = backgroundBottom;
            this.frameFill = frameFill;
            this.frameStroke = frameStroke;
            this.headingTypeface = headingTypeface;
            this.bookBold = bookBold;
            this.bookRegular = bookRegular;
        }
    }
    private static final class Media {
        final File file;
        final int displayRotation;
        Media(File file, int rotation) {
            this.file = file;
            this.displayRotation = rotation;
        }
    }
    private final Snapshot snapshot;
    private final BooleanSupplier cancelled;
    private final List<Media> media = new ArrayList<>();
    NotebookShareRenderer(Snapshot snapshot, BooleanSupplier cancelled) {
        this.snapshot = snapshot;
        this.cancelled = cancelled;
        HashSet<String> displayed = new HashSet<>();
        for (NotebookMediaEntries.Entry entry : snapshot.entries) {
            checkCancelled();
            String name = entry.fileName;
            File file = new File(snapshot.directory, name);
            if (!file.isFile() && NutritionStore.safeName(entry.fallbackFile)) {
                name = entry.fallbackFile;
                file = new File(snapshot.directory, name);
            }
            if (file.isFile() && displayed.add(entry.mealKey + "/" + name))
                media.add(new Media(file, entry.displayRotation));
        }
    }
    private void checkCancelled() {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())
            throw new java.util.concurrent.CancellationException();
    }
    Bitmap render() {
        checkCancelled();
        final float scale = 2f;
        boolean paperOnly = snapshot.paperOnly;
        int logicalWidth = paperOnly ? 720 : 820;
        int margin = paperOnly ? 0 : 68;
        int notebookTop = paperOnly ? 0 : 112;
        int notebookHeight = notebookShareHeight(paperOnly);
        int footerBaselineGap = paperOnly ? 0 : 88;
        int footerBottomGap = paperOnly ? 0 : 42;
        int logicalHeight = notebookTop + notebookHeight + footerBaselineGap + footerBottomGap;
        int width = Math.round(logicalWidth * scale);
        int totalHeight = Math.round(logicalHeight * scale);
        Bitmap bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(scale, scale);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            int bgTop = snapshot.backgroundTop;
            int bgBottom = snapshot.backgroundBottom;
            if (!paperOnly) {
                bgTop = blendColor(bgTop, Color.WHITE, 0.08f);
                bgBottom = blendColor(Color.WHITE, bgBottom, 0.12f);
            }
            paint.setShader(new LinearGradient(0, 0, 0, logicalHeight, bgTop, bgBottom, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, logicalWidth, logicalHeight, paint);
            paint.setShader(null);
            if (!paperOnly) {
                int sideTint = blendColor(bgBottom, bgTop, 0.30f);
                paint.setShader(new LinearGradient(0, 0, logicalWidth, 0,
                        Color.argb(0, Color.red(bgTop), Color.green(bgTop), Color.blue(bgTop)),
                        Color.argb(46, Color.red(sideTint), Color.green(sideTint), Color.blue(sideTint)),
                        Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, logicalWidth, logicalHeight, paint);
                paint.setShader(null);
            }

            if (!paperOnly) {
                Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                titlePaint.setColor(Color.rgb(24, 24, 24));
                titlePaint.setTypeface(snapshot.headingTypeface);
                titlePaint.setTextSize(48);
                canvas.drawText("吃饭记录本" + snapshot.emoji, margin, 66, titlePaint);
            }

            RectF note = new RectF(margin, notebookTop, logicalWidth - margin, notebookTop + notebookHeight);
            drawNotebookSharePaper(canvas, note, paperOnly);

            float x = note.left + 50;
            float y = note.top + 70;
            Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            datePaint.setColor(Color.rgb(95, 78, 60));
            datePaint.setTypeface(snapshot.bookBold);
            datePaint.setTextSize(38);
            canvas.drawText(snapshot.dateTitle, x, y, datePaint);
            y += 62;

            Paint storyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            storyPaint.setColor(Color.rgb(70, 64, 56));
            storyPaint.setTypeface(snapshot.bookRegular);
            storyPaint.setTextSize(34);
            if (snapshot.splitStory) {
                y = drawWrappedText(canvas, snapshot.mealCountLine, x, y, note.width() - 100, storyPaint, 2);
                y += 10;
                List<String> lines = snapshot.storyLines;
                for (String line : lines) {
                    y = drawWrappedText(canvas, line, x, y, note.width() - 100, storyPaint, 2);
                    y += 10;
                }
            } else {
                y = drawWrappedText(canvas, snapshot.story, x, y, note.width() - 100, storyPaint, 9);
            }
            y += 18;

            String times = snapshot.timeLine;
            if (times.length() > 0) {
                Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                smallPaint.setColor(Color.rgb(145, 132, 114));
                smallPaint.setTypeface(snapshot.bookRegular);
                smallPaint.setTextSize(24);
                y = drawWrappedText(canvas, times, x, y, note.width() - 100, smallPaint, 2);
                y += 12;
            }

            String mediaLine = snapshot.mediaLine;
            if (mediaLine.length() > 0) {
                Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                smallPaint.setColor(Color.rgb(158, 145, 128));
                smallPaint.setTypeface(snapshot.bookRegular);
                smallPaint.setTextSize(23);
                y = drawWrappedText(canvas, mediaLine, x, y, note.width() - 100, smallPaint, 2);
            }
            float stickerStart = Math.max(y + 18, note.top + 470);
            float stickerNeed = notebookShareStickerHeight();
            float bottomReserve = paperOnly ? 84 : 34;
            float bottomLimit = note.bottom - bottomReserve - stickerNeed;
            if (stickerNeed > 0 && stickerStart > bottomLimit) {
                stickerStart = Math.max(y + 14, bottomLimit);
            }
            drawNotebookShareStickers(canvas, note, stickerStart);

            if (paperOnly) {
                drawNotebookPaperWatermark(canvas, note);
            } else {
                drawShareFooter(canvas, logicalWidth, margin, logicalHeight, footerBottomGap);
            }
            checkCancelled();
            return bitmap;
        } catch (RuntimeException | Error failure) {
            bitmap.recycle();
            throw failure;
        }
    }

    private int notebookShareHeight(boolean paperOnly) {
        int base = paperOnly ? 626 : 592;
        int textExtra = Math.max(0, snapshot.storyLines.size() - 3) * 48;
        int stickerHeight = notebookShareStickerHeight();
        int mediaExtra = stickerHeight == 0 ? 0 : 42 + stickerHeight;
        int watermarkExtra = paperOnly ? 56 : 0;
        return Math.max(paperOnly ? 760 : 708, base + textExtra + mediaExtra + watermarkExtra);
    }

    private int notebookShareStickerHeight() {
        int mediaCount = Math.min(media.size(), 12);
        if (mediaCount == 0) {
            return 0;
        }
        int columns = notebookShareColumns(mediaCount);
        int rows = (mediaCount + columns - 1) / columns;
        boolean straight = snapshot.straightStickers;
        return 26 + rows * (straight ? 94 : 98);
    }

    private int notebookShareColumns(int mediaCount) {
        if (mediaCount <= 1) {
            return 1;
        }
        if (mediaCount == 2) {
            return 2;
        }
        if (mediaCount <= 8) {
            return 4;
        }
        if (mediaCount <= 15) {
            return 5;
        }
        return 6;
    }

    private void drawShareFooter(Canvas canvas, int width, int margin, int totalHeight, int bottomGap) {
        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(Color.argb(132, 80, 80, 80));
        footerPaint.setTextSize(24);
        footerPaint.setTypeface(snapshot.headingTypeface);
        String left = "Capturing a day of sweet treats. 📸";
        String right = "一蔬一饭";
        float baseline = totalHeight - bottomGap;
        canvas.drawText(left, margin, baseline, footerPaint);
        footerPaint.setTypeface(snapshot.bookRegular);
        float ww = footerPaint.measureText(right);
        footerPaint.setColor(Color.argb(184, 82, 84, 90));
        canvas.drawText(right, width - margin - ww, baseline, footerPaint);
    }

    private void drawNotebookShareStickers(Canvas canvas, RectF note, float y) {
        if (media.isEmpty()) {
            return;
        }
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.rgb(150, 137, 119));
        textPaint.setTypeface(snapshot.bookRegular);
        textPaint.setTextSize(18);
        float inset = 50;
        canvas.drawText("今日记录", note.left + inset, y + 13, textPaint);
        y += 36;
        int max = Math.min(media.size(), 12);
        int columns = notebookShareColumns(max);
        float available = note.width() - inset * 2f;
        float minGap = columns >= 5 ? 8 : 12;
        float maxSize = columns >= 5 ? 116 : 136;
        float cell = (available - (columns - 1) * minGap) / columns;
        float size = Math.min(maxSize, Math.max(64, cell * 0.9f));
        boolean straight = snapshot.straightStickers;
        float gap = columns <= 1 ? 0 : Math.max(minGap, Math.min(columns >= 5 ? 14 : 18, (available - columns * size) / Math.max(1, columns - 1)));
        float step = size + gap;
        float rowHeight = straight ? size + 14 : size + 18;
        for (int i = 0; i < max; i++) {
            checkCancelled();
            Media item = media.get(i);
            File file = item.file;
            if (!file.exists()) {
                continue;
            }
            Bitmap sticker = decodeFileSampled(file, 512);
            if (sticker == null) {
                continue;
            }
            try {
                float left;
                float top;
                int row = i / columns;
                int column = i % columns;
                left = note.left + inset + column * step;
                if (columns > 1 && max <= 2) {
                    float used = columns * size + (columns - 1) * gap;
                    left += Math.max(0f, (available - used) / 2f);
                }
                top = y + 4 + row * rowHeight + (straight ? 0 : (column % 2) * 4);
                RectF dst = new RectF(left, top, left + size, top + size);
                int save = canvas.save();
                canvas.rotate(item.displayRotation, dst.centerX(), dst.centerY());
                if (!straight) {
                    canvas.rotate(new float[]{-8f, 5f, -4f, 8f, -6f, 3f, -3f, 6f}[i % 8], dst.centerX(), dst.centerY());
                }
                Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                int fill = snapshot.frameFill;
                bg.setColor(Color.argb(148, Color.red(fill), Color.green(fill), Color.blue(fill)));
                canvas.drawRoundRect(dst, 18, 18, bg);
                Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.6f);
                stroke.setColor(snapshot.frameStroke);
                canvas.drawRoundRect(dst, 18, 18, stroke);
                drawBitmapContain(canvas, sticker, new RectF(dst.left + 7, dst.top + 7, dst.right - 7, dst.bottom - 7), 14);
                canvas.restoreToCount(save);
            } finally { sticker.recycle(); }
        }
    }

    private void drawNotebookPaperWatermark(Canvas canvas, RectF note) {
        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(Color.argb(118, 112, 106, 98));
        footerPaint.setTextSize(19);
        footerPaint.setTypeface(snapshot.headingTypeface);
        String left = "Capturing a day of sweet treats. 📸";
        String right = "一蔬一饭";
        float y = note.bottom - 42;
        float leftX = note.left + 52;
        canvas.drawText(left, leftX, y, footerPaint);
        footerPaint.setTypeface(snapshot.bookRegular);
        float ww = footerPaint.measureText(right);
        footerPaint.setColor(Color.argb(176, 92, 86, 80));
        canvas.drawText(right, note.right - 44 - ww, y, footerPaint);
    }

    private void drawNotebookSharePaper(Canvas canvas, RectF rect, boolean edgeToEdge) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        String style = snapshot.paperStyle;
        int top = Color.rgb(255, 253, 246);
        int bottom = Color.rgb(252, 246, 232);
        int strip = Color.rgb(246, 239, 222);
        int line = Color.rgb(229, 219, 198);
        if ("fresh".equals(style)) {
            top = Color.rgb(245, 255, 250);
            bottom = Color.rgb(230, 248, 255);
            strip = Color.rgb(216, 243, 232);
            line = Color.rgb(197, 226, 218);
        } else if ("pink".equals(style)) {
            top = Color.rgb(255, 248, 252);
            bottom = Color.rgb(255, 235, 242);
            strip = Color.rgb(253, 224, 235);
            line = Color.rgb(238, 204, 217);
        } else if ("grid".equals(style)) {
            top = Color.rgb(255, 253, 248);
            bottom = Color.rgb(248, 250, 255);
            strip = Color.rgb(236, 239, 248);
            line = Color.rgb(216, 224, 238);
        } else if ("plain".equals(style)) {
            top = Color.rgb(255, 255, 255);
            bottom = Color.rgb(249, 250, 252);
            strip = Color.rgb(240, 242, 245);
            line = Color.rgb(225, 228, 232);
        } else if ("recipe".equals(style)) {
            top = Color.rgb(255, 249, 236);
            bottom = Color.rgb(255, 242, 218);
            strip = Color.rgb(255, 226, 183);
            line = Color.rgb(232, 205, 170);
        } else if ("dotted".equals(style)) {
            top = Color.rgb(252, 253, 255);
            bottom = Color.rgb(241, 247, 255);
            strip = Color.rgb(225, 238, 255);
            line = Color.rgb(205, 218, 236);
        } else if ("memo".equals(style)) {
            top = Color.rgb(255, 254, 225);
            bottom = Color.rgb(255, 248, 194);
            strip = Color.rgb(255, 235, 141);
            line = Color.rgb(223, 205, 130);
        } else if ("letter".equals(style)) {
            top = Color.rgb(255, 251, 247);
            bottom = Color.rgb(246, 247, 255);
            strip = Color.rgb(244, 232, 220);
            line = Color.rgb(204, 218, 244);
        } else if ("fruit".equals(style)) {
            top = Color.rgb(255, 252, 237);
            bottom = Color.rgb(238, 255, 238);
            strip = Color.rgb(255, 225, 190);
            line = Color.rgb(215, 232, 183);
        } else if ("sunset".equals(style)) {
            top = Color.rgb(255, 244, 232);
            bottom = Color.rgb(248, 231, 255);
            strip = Color.rgb(255, 202, 173);
            line = Color.rgb(230, 192, 206);
        } else if ("blueprint".equals(style)) {
            top = Color.rgb(238, 248, 255);
            bottom = Color.rgb(220, 237, 255);
            strip = Color.rgb(188, 218, 248);
            line = Color.rgb(144, 190, 232);
        }
        paint.setShader(new LinearGradient(rect.left, rect.top, rect.left, rect.bottom, top, bottom, Shader.TileMode.CLAMP));
        if (edgeToEdge) {
            canvas.drawRect(rect, paint);
        } else {
            canvas.drawRoundRect(rect, 34, 34, paint);
        }
        paint.setShader(null);

        Path paperClip = new Path();
        if (edgeToEdge) {
            paperClip.addRect(rect, Path.Direction.CW);
        } else {
            paperClip.addRoundRect(rect, 34, 34, Path.Direction.CW);
        }
        int save = canvas.save();
        canvas.clipPath(paperClip);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(strip);
        canvas.drawRect(rect.left, rect.top, rect.left + 34, rect.bottom, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(line);
        for (int y = (int) rect.top + 104; y < rect.bottom - 24; y += 58) {
            canvas.drawLine(rect.left + 58, y, rect.right - 32, y, paint);
        }
        if ("grid".equals(style)) {
            for (int x = (int) rect.left + 116; x < rect.right - 28; x += 58) {
                canvas.drawLine(x, rect.top + 36, x, rect.bottom - 28, paint);
            }
        } else if ("dotted".equals(style)) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(adjustAlpha(line, 118));
            for (int yy = (int) rect.top + 82; yy < rect.bottom - 30; yy += 48) {
                for (int xx = (int) rect.left + 86; xx < rect.right - 32; xx += 48) {
                    canvas.drawCircle(xx, yy, 2.4f, paint);
                }
            }
        } else if ("blueprint".equals(style)) {
            for (int x = (int) rect.left + 104; x < rect.right - 28; x += 52) {
                canvas.drawLine(x, rect.top + 36, x, rect.bottom - 28, paint);
            }
        } else if ("letter".equals(style)) {
            paint.setColor(Color.rgb(232, 202, 188));
            canvas.drawLine(rect.left + 96, rect.top + 38, rect.left + 96, rect.bottom - 30, paint);
        } else if ("fruit".equals(style) || "sunset".equals(style)) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor("fruit".equals(style) ? Color.argb(64, 255, 105, 86) : Color.argb(58, 255, 160, 118));
            canvas.drawCircle(rect.right - 74, rect.top + 68, 26, paint);
            paint.setColor("fruit".equals(style) ? Color.argb(82, 80, 206, 115) : Color.argb(48, 180, 124, 255));
            canvas.drawCircle(rect.right - 128, rect.top + 94, 16, paint);
        }
        canvas.restoreToCount(save);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 255, 252));
        for (int y = (int) rect.top + 42; y < rect.bottom - 24; y += 58) {
            canvas.drawCircle(rect.left + 17, y, 7, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(adjustAlpha(line, 138));
        if (!edgeToEdge) {
            canvas.drawRoundRect(new RectF(rect.left + 15, rect.top + 15, rect.right - 15, rect.bottom - 15), 26, 26, paint);
        }
    }

    private float drawWrappedText(Canvas canvas, String text, float x, float y, float maxWidth, Paint paint, int maxLines) {
        if (text == null || text.length() == 0) {
            return y;
        }
        String rest = text;
        Paint.FontMetrics fm = paint.getFontMetrics();
        float lineHeight = fm.descent - fm.ascent + 10;
        int line = 0;
        while (rest.length() > 0 && line < maxLines) {
            int count = paint.breakText(rest, true, maxWidth, null);
            if (count <= 0) {
                break;
            }
            String part = rest.substring(0, count);
            if (line == maxLines - 1 && count < rest.length()) {
                part = part.length() > 1 ? part.substring(0, part.length() - 1) + "..." : "...";
            }
            canvas.drawText(part, x, y, paint);
            rest = rest.substring(count);
            y += lineHeight;
            line++;
        }
        return y;
    }


    private void drawBitmapContain(Canvas canvas, Bitmap bitmap, RectF dst, float radius) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return;
        }
        Path path = new Path();
        path.addRoundRect(dst, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(path);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float scale = Math.min(dst.width() / bitmap.getWidth(), dst.height() / bitmap.getHeight());
        float w = bitmap.getWidth() * scale;
        float h = bitmap.getHeight() * scale;
        RectF target = new RectF(
                dst.centerX() - w / 2f,
                dst.centerY() - h / 2f,
                dst.centerX() + w / 2f,
                dst.centerY() + h / 2f);
        canvas.drawBitmap(bitmap, null, target, p);
        canvas.restoreToCount(save);
    }


    private static int adjustAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
    private static int blendColor(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }
    private static Bitmap decodeFileSampled(File file, int target) {
        if (!file.isFile()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / ((long) sample * 2) >= Math.max(128, target)) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (RuntimeException | OutOfMemoryError unavailable) { return null; }
    }
}
