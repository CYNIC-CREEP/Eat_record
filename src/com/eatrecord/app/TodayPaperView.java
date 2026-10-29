package com.eatrecord.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One paper for the whole day. Meal boundaries are labels, never nested cards or inline editors. */
public final class TodayPaperView extends LinearLayout {
    static final int CONTENT_INSET=22, RULE_INSET=18;
    public interface Listener {
        void onFood(String mealKey, JSONObject item);
        void onLegacy(String mealKey);
        View editableMedia(DayFoodEntries.Entry entry, View image);
        default View editableText(DayFoodEntries.Entry entry, View text) { return text; }
    }
    public interface RotatableMedia { void setMediaRotation(int degrees); }
    /** Coordinates are local to the image View, not its padded editing wrapper. */
    public interface HitTestMedia { boolean containsMediaPoint(float x, float y); }
    public static View mediaPreview(Activity activity, String file, String fallback, boolean sticker, boolean night, int rotation) {
        FoodImage image = new FoodImage(activity, file, fallback, sticker, night);
        image.setMediaRotation(rotation); return image;
    }
    private final Activity activity;
    private final boolean night;
    private final Listener listener;

    public TodayPaperView(Activity activity, boolean night, JSONObject day, Listener listener) {
        super(activity);
        this.activity = activity;
        this.night = night;
        this.listener = listener;
        setOrientation(VERTICAL);
        setTag("today_paper");
        setPadding(dp(CONTENT_INSET), dp(18), dp(CONTENT_INSET), dp(24));
        // Header has its own trailing inset; the outer viewport still clips all four corners.
        setClipToPadding(false);
        setMinimumHeight(dp(420));
        setBackground(new JournalPaper());
        setClipToOutline(true);
        List<DayFoodEntries.Entry> entries = DayFoodEntries.collect(day);
        if(entries.isEmpty())addEmptyGuide();
        for (int i = 0; i < entries.size(); i++) addEntry(entries.get(i), i);
    }

    private int dp(float value) { return PaperComponents.dp(activity, value); }
    private void addEmptyGuide(){
        LinearLayout guide=new LinearLayout(activity);guide.setOrientation(VERTICAL);guide.setGravity(Gravity.CENTER);
        guide.setTag("day_empty_guide");guide.setPadding(0,dp(16),0,dp(16));guide.setMinimumHeight(dp(230));
        // This is a hint, not an overlapping add target: only the bottom star adds a photo.
        guide.setFocusable(false);guide.setClickable(false);
        guide.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        guide.setContentDescription("还没有餐食，点击下方星标可以添加美食喔");
        View doodle=new View(activity){private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();
            @Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()*.43f,s=dp(1);p.setColor(UiTokens.secondary(night));p.setAlpha(85);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setStrokeCap(Paint.Cap.ROUND);
                path.reset();path.moveTo(x-35*s,y);path.cubicTo(x-30*s,y+40*s,x+30*s,y+40*s,x+35*s,y);path.close();c.drawPath(path,p);
                for(int i=-1;i<=1;i++){path.reset();path.moveTo(x+i*15*s,y-9*s);path.cubicTo(x+(i*15-8)*s,y-20*s,x+(i*15+8)*s,y-23*s,x+i*15*s,y-34*s);c.drawPath(path,p);}
            }};
        doodle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);guide.addView(doodle,new LayoutParams(dp(110),dp(90)));
        TextView instruction=text("点击下方星标可以添加美食喔",17,UiTokens.secondary(night));
        instruction.setTag("day_empty_instruction");
        guide.addView(instruction,new LayoutParams(-1,-2));
        View arrow=new View(activity){private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();
            @Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=dp(8);p.setColor(UiTokens.secondary(night));p.setAlpha(85);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
                path.reset();path.moveTo(x+dp(6),y);path.cubicTo(x+dp(35),y+dp(30),x-dp(29),y+dp(29),x,y+dp(64));path.moveTo(x-dp(10),y+dp(59));path.lineTo(x,y+dp(64));path.lineTo(x+dp(4),y+dp(53));c.drawPath(path,p);
            }};
        arrow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);guide.addView(arrow,new LayoutParams(dp(90),dp(80)));
        // ScrollView.fillViewport measures the paper to its visible height. The
        // guide receives the space below the summary and centers as one group.
        addView(guide,new LayoutParams(-1,0,1f));
    }
    /** The paper edge belongs to the visible window, not to the length of the diary document. */
    public FrameLayout wrapInViewport(ScrollView scroll) {
        Drawable paper = getBackground();
        setBackground(null); setMinimumHeight(0); setClipToOutline(false);
        FrameLayout viewport = new FrameLayout(activity);
        viewport.setTag("today_paper_viewport"); viewport.setBackground(paper); viewport.setClipToOutline(true);
        scroll.setTag("today_paper_scroll"); scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER); scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(0, 0, 0, 0);
        scroll.addView(this, new ScrollView.LayoutParams(-1, -2));
        viewport.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        return viewport;
    }
    private TextView text(String value, float size, int color) {
        TextView label = PaperComponents.text(activity, value, size, color, false);
        label.setGravity(Gravity.CENTER);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return label;
    }
    private void addEntry(final DayFoodEntries.Entry entry, int index) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(VERTICAL);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(12));
        row.setMinimumHeight(dp(64));
        if(entry.isText()){
            row.setGravity(Gravity.CENTER);
            row.setPadding(dp(44),dp(12),dp(44),dp(12));
        }
        row.setFocusable(!entry.isFood());
        row.setImportantForAccessibility(entry.isFood()?IMPORTANT_FOR_ACCESSIBILITY_NO:IMPORTANT_FOR_ACCESSIBILITY_YES);
        String id = entry.item == null ? "legacy_" + index : entry.item.optString("id", "item_" + index);
        row.setTag((entry.isFood()?"day_food_":"day_legacy_") + entry.mealKey + "_" + id);
        if(!entry.isFood())row.setBackground(pressBackground());
        if (!entry.fileName.isEmpty()) {
            FoodImage image = new FoodImage(activity, entry.fileName, entry.fallbackFile, entry.sticker, night);
            image.setMediaRotation(NutritionStore.displayRotation(entry.item));
            image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            View media = listener == null ? image : listener.editableMedia(entry, image);
            LayoutParams imageParams = new LayoutParams(dp(entry.sticker ? 184 : 172), dp(entry.sticker ? 184 : 172));
            imageParams.bottomMargin = dp(10);
            row.addView(media, imageParams);
        }
        TextView title = text(entry.prefix + " · " + entry.title, 17, UiTokens.ink(night));
        if(entry.isText())title.setIncludeFontPadding(false);
        title.setTag("day_entry_label");
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LayoutParams(-1, -2));
        String subtitle = subtitle(entry);
        if (!subtitle.isEmpty()) {
            TextView calories = text(subtitle, Double.isFinite(entry.calories) ? 17 : 12, UiTokens.secondary(night));
            calories.setTag("day_entry_energy");
            calories.setPadding(0, dp(3), 0, 0);
            row.addView(calories, new LayoutParams(-1, -2));
        }
        String estimate = entry.isFood() && Double.isFinite(entry.calories)
                ? entry.item.optBoolean("userEdited") ? "，用户修正" : "，AI 估算" : "";
        if(!entry.isFood())row.setContentDescription(entry.mealTitle + "，" + entry.title
                + (subtitle.isEmpty() ? "" : "，" + subtitle) + estimate + "，点按查看和修改");
        if(!entry.isFood())row.setOnClickListener(v -> {
            if (listener == null) return;
            listener.onLegacy(entry.mealKey);
        });
        LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = dp(index == 0 ? 14 : 26);
        View presentation=entry.isText()&&listener!=null?listener.editableText(entry,row):row;
        addView(presentation, params);
    }
    private String subtitle(DayFoodEntries.Entry entry) {
        if (Double.isFinite(entry.calories)) return number(entry.calories) + " kcal";
        if (!entry.isFood()) return "";
        String status = entry.item.optString("analysisStatus");
        if ("cutting_out".equals(status)) return "正在生成食物贴纸…";
        if ("recognizing".equals(status) || "pending".equals(status)) return "营养识别中…";
        return "营养待补充";
    }
    private static String number(double value) {
        return Math.abs(value - Math.rint(value)) < .05
                ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
    private Drawable pressBackground() {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(22));
        return new RippleDrawable(ColorStateList.valueOf(night ? 0x18ffffff : 0x126e543b), null, mask);
    }

    /** Faint ruling belongs to the paper, not to individual food components. */
    private final class JournalPaper extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            RectF bounds = new RectF(b.left + .5f, b.top + .5f, b.right - .5f, b.bottom - .5f);
            float radius = dp(UiTokens.CARD_RADIUS);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(UiTokens.card(night));
            canvas.drawRoundRect(bounds, radius, radius, paint);
            canvas.save();
            Path clip = new Path(); clip.addRoundRect(bounds, radius, radius, Path.Direction.CW); canvas.clipPath(clip);
            paint.setColor(night ? Color.argb(19, 220, 199, 170) : Color.argb(28, 175, 148, 115));
            paint.setStrokeWidth(Math.max(.5f, dp(1) * .4f));
            for (float y = b.top + dp(120); y < b.bottom - dp(18); y += dp(38))
                canvas.drawLine(b.left + dp(RULE_INSET), y, b.right - dp(RULE_INSET), y, paint);
            canvas.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, dp(1) * .7f));
            paint.setColor(UiTokens.line(night));
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public void getOutline(android.graphics.Outline outline) {
            outline.setRoundRect(getBounds(), dp(UiTokens.CARD_RADIUS));
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Bounded async decoding. Every displayed bitmap belongs to this View and dies on detach. */
    private static final class FoodImage extends View implements RotatableMedia, HitTestMedia {
        private static final Handler MAIN = new Handler(Looper.getMainLooper());
        private static final ThreadPoolExecutor LOADERS = new ThreadPoolExecutor(1, 2, 20, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(64), runnable -> {
                    Thread worker = new Thread(runnable, "food-paper-image"); worker.setDaemon(true); return worker;
                }, new ThreadPoolExecutor.AbortPolicy());
        private final File primary, fallback;
        private final android.content.Context app;
        private final boolean sticker, night;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF destination = new RectF();
        private final FoodImageGeometry geometry = new FoodImageGeometry();
        private Bitmap bitmap;
        private Future<?> request;
        private LoadRetry retry;
        private int retryDelayMs = 125;
        private int generation;
        private boolean missing;
        private boolean renderedSticker;
        private int displayRotation;
        public void setMediaRotation(int degrees) { displayRotation = NutritionStore.normalizeDisplayRotation(degrees); invalidate(); }
        private void updateGeometry() {
            if(bitmap!=null&&!bitmap.isRecycled())geometry.set(getWidth(),getHeight(),bitmap.getWidth(),bitmap.getHeight(),
                    PaperComponents.dp(getContext(),4),displayRotation);
        }
        @Override public boolean containsMediaPoint(float x,float y) {
            if(bitmap==null||bitmap.isRecycled())return false;
            updateGeometry();
            if(!geometry.contains(x,y,renderedSticker?0:PaperComponents.dp(getContext(),16)))return false;
            int px=(int)geometry.imageX(x,y),py=(int)geometry.imageY(x,y);
            if(px<0||py<0||px>=bitmap.getWidth()||py>=bitmap.getHeight())return false;
            // The rendered bitmap includes its visible white/dashed border. Transparent padding
            // outside that outline must never become a rectangular nutrition-details target.
            return Color.alpha(bitmap.getPixel(px,py))>=16;
        }
        FoodImage(Activity activity, String file, String fallbackName, boolean sticker, boolean night) {
            super(activity);
            app=activity.getApplicationContext();
            primary = new File(activity.getFilesDir(), file);
            fallback = NutritionStore.safeName(fallbackName) ? new File(activity.getFilesDir(), fallbackName) : null;
            this.sticker = sticker;
            this.night = night;
            setTag("day_entry_image");
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            missing = false;
            retryDelayMs = 125;
            requestLoad(++generation);
        }
        private void requestLoad(final int token) {
            if (!isAttachedToWindow() || generation != token) return;
            final WeakReference<FoodImage> weak = new WeakReference<FoodImage>(this);
            final File file = primary, alternate = fallback;
            final boolean addBorder = sticker;
            final android.content.Context context = app;
            try {
                request = LOADERS.submit(() -> {
                    Bitmap loaded = decode(file);
                    boolean outlined = addBorder && loaded != null;
                    if (loaded == null && alternate != null && !file.equals(alternate)) loaded = decode(alternate);
                    if (loaded != null && outlined) {
                        Bitmap raw = loaded;
                        try {
                            loaded = FoodStickerRenderer.create(context,raw, Math.max(7, Math.max(raw.getWidth(), raw.getHeight()) / 38));
                        } catch (RuntimeException | OutOfMemoryError ignored) { loaded = raw; }
                        if (loaded != raw) raw.recycle();
                    }
                    final Bitmap result = loaded;
                    final boolean isSticker = outlined;
                    MAIN.post(() -> {
                        FoodImage view = weak.get();
                        if (view == null || !view.isAttachedToWindow() || view.generation != token) {
                            if (result != null && !result.isRecycled()) result.recycle();
                            return;
                        }
                        view.bitmap = result;
                        view.renderedSticker = isSticker;
                        view.missing = result == null;
                        view.invalidate();
                    });
                });
                retryDelayMs = 125;
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                request = null;
                // A busy gallery is not a missing image. Keep at most one weak, deferred retry
                // per attached View; decoding and outlining never fall back to the UI thread.
                if (retry == null) {
                    retry = new LoadRetry(this, token);
                    MAIN.postDelayed(retry, retryDelayMs);
                    retryDelayMs = Math.min(1000, retryDelayMs * 2);
                }
            }
        }
        private static final class LoadRetry implements Runnable {
            private final WeakReference<FoodImage> weak;
            private final int generation;
            LoadRetry(FoodImage view, int generation) {
                weak = new WeakReference<FoodImage>(view);
                this.generation = generation;
            }
            @Override public void run() {
                FoodImage view = weak.get();
                if (view == null || view.retry != this) return;
                view.retry = null;
                if (view.isAttachedToWindow() && view.generation == generation)
                    view.requestLoad(generation);
            }
        }
        @Override protected void onDetachedFromWindow() {
            generation++;
            if (retry != null) { MAIN.removeCallbacks(retry); retry = null; }
            if (request != null) { request.cancel(true); LOADERS.purge(); request = null; }
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            bitmap = null;
            super.onDetachedFromWindow();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null || bitmap.isRecycled()) {
                if (missing) {
                    paint.setColor(UiTokens.secondary(night));
                    paint.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("图片暂不可用", getWidth() / 2f, getHeight() / 2f, paint);
                }
                return;
            }
            updateGeometry();
            destination.set(geometry.left,geometry.top,geometry.right,geometry.bottom);
            canvas.save();
            canvas.rotate(displayRotation, getWidth() / 2f, getHeight() / 2f);
            if (!renderedSticker) {
                Path clip = new Path();
                float radius = PaperComponents.dp(getContext(), 16);
                clip.addRoundRect(destination, radius, radius, Path.Direction.CW);
                canvas.clipPath(clip);
            }
            canvas.drawBitmap(bitmap, null, destination, paint);
            canvas.restore();
        }
        private static Bitmap decode(File file) {
            if (!file.isFile()) return null;
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                if (bounds.outWidth < 1 || bounds.outHeight < 1) return null;
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                return bitmap;
            } catch (RuntimeException | OutOfMemoryError ignored) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                return null;
            }
        }
    }
}
