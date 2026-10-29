package com.eatrecord.app;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.*;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;

/** One inference at a time. Model hashes are checked before loading native code. */
public final class FoodCutoutEngine {
    private static boolean loaded;
    private static Rect lastBounds;
    private static String lastModel = "";
    private static boolean lastFallback;
    public static synchronized Rect lastBounds() { return lastBounds == null ? null : new Rect(lastBounds); }
    public static synchronized String lastModel() { return lastModel; }
    public static synchronized boolean wasFallback() { return lastFallback; }
    private static native boolean loadCutoutModel(String modelPath, int mode);
    private static native Bitmap cutoutFood(Bitmap bitmap, int qualityMode);
    private static native Bitmap refineFood(Bitmap bitmap, Bitmap baseline, String encoderPath, String decoderPath);
    private static native boolean usedFocusedRefinement();
    private static native void releaseCutoutModel();
    public static synchronized Bitmap create(Context context, Bitmap source) throws Exception {
        lastBounds = null; lastModel = ""; lastFallback = false;
        if (!loaded) { System.loadLibrary("MNN"); System.loadLibrary("eatvision"); loaded = true; }
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(memory);
        // Quality is the default on 4/6GB phones too. An old slow-inference flag
        // must not permanently force the tiny model after an app upgrade.
        boolean small = manager != null && (manager.isLowRamDevice() || memory.lowMemory
                || (memory.availMem > 0 && memory.availMem < 550L * 1024 * 1024));
        try { return infer(context, source, small); }
        catch (OutOfMemoryError | RuntimeException e) {
            releaseCutoutModel();
            if (!small) return infer(context, source, true);
            throw new IOException("本机内存不足，已保留原照片");
        } catch (Exception e) {
            releaseCutoutModel();
            if (!small) return infer(context, source, true);
            throw e;
        } finally { releaseCutoutModel(); }
    }
    private static Bitmap infer(Context c, Bitmap source, boolean small) throws Exception {
        JSONObject manifest;
        try (InputStream in = c.getAssets().open("models/model_manifest.json")) {
            manifest = new JSONObject(FoodAnalysisClient.readLimited(in, 16384));
        }
        JSONObject model = manifest.getJSONObject(small ? "small" : "full");
        File file = modelFile(c, model);
        if (!loadCutoutModel(file.getAbsolutePath(), small ? 0 : 1)) {
            throw new IOException("本地抠图暂不可用");
        }
        float ratio = Math.min(1, 1024f / Math.max(source.getWidth(), source.getHeight()));
        Bitmap input = Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth()*ratio)), Math.max(1, Math.round(source.getHeight()*ratio)), true);
        Bitmap mask = null;
        boolean refined = false, focused = false;
        try {
            mask = cutoutFood(input, small ? 0 : 1);
            if (mask == null) throw new IOException("没有找到清晰的主体");
            releaseCutoutModel();
            if (!small && manifest.has("refiner")) {
                ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
                if(manager!=null)manager.getMemoryInfo(memory);
                if(manager==null || (!memory.lowMemory && memory.availMem>=850L*1024*1024)) {
                    try {
                        JSONObject refiner=manifest.getJSONObject("refiner");
                        File encoder=modelFile(c,refiner.getJSONObject("encoder"));
                        File decoder=modelFile(c,refiner.getJSONObject("decoder"));
                        Bitmap enclosing=refineFood(input,mask,encoder.getAbsolutePath(),decoder.getAbsolutePath());
                        if(enclosing!=null){mask.recycle();mask=enclosing;refined=true;focused=usedFocusedRefinement();}
                    } catch(Exception | OutOfMemoryError ignored) {
                        // The validated baseline remains usable if this optional stage fails.
                    }
                }
            }
            if (!mask.isMutable()) {
                Bitmap mutable = mask.copy(Bitmap.Config.ARGB_8888, true);
                mask.recycle(); mask = mutable;
                if (mask == null) throw new IOException("无法创建蒙版");
            }
            cleanMask(mask);
            Bitmap result = Bitmap.createBitmap(input.getWidth(), input.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result); canvas.drawBitmap(input, 0, 0, null);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            canvas.drawBitmap(mask, null, new Rect(0, 0, result.getWidth(), result.getHeight()), paint);
            Bitmap trimmed;
            try { trimmed = trim(result); }
            catch (Exception e) { result.recycle(); throw e; }
            if (trimmed != result) result.recycle();
            lastModel = small ? "U²-NetP FP16 · 320" : refined ? "IS-Net + EdgeTAM FP16 · 1024" : "IS-Net FP16 · 512";
            if(focused)lastModel += " · 主体聚焦";
            lastFallback = small;
            return trimmed;
        } finally { if (mask != null) mask.recycle(); if (input != source) input.recycle(); releaseCutoutModel(); }
    }
    private static File modelFile(Context c, JSONObject model) throws Exception {
        File file = new File(c.getFilesDir(), model.getString("file"));
        String expected = model.getString("sha256");
        if (!file.isFile() || !expected.equals(hash(file))) {
            File temporary = new File(file.getAbsolutePath() + ".partial");
            try (InputStream in = c.getAssets().open("models/" + model.getString("file")); FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[65536]; int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            if (!expected.equals(hash(temporary))) { temporary.delete(); throw new IOException("抠图模型校验失败"); }
            if (file.exists() && !file.delete()) throw new IOException("模型缓存无法替换");
            if (!temporary.renameTo(file)) throw new IOException("模型缓存无法保存");
        }
        return file;
    }
    /** Remove tiny islands and fill enclosed small holes without hardening antialiased edges. */
    private static void cleanMask(Bitmap mask) {
        int w = mask.getWidth(), h = mask.getHeight(), n = w*h;
        int[] pixels = new int[n], queue = new int[n]; boolean[] visited = new boolean[n];
        int[] neighbors = new int[4];
        mask.getPixels(pixels, 0, w, 0, 0, w, h);
        // A saliency score is not physical transparency. Keep confident subject
        // interiors opaque while retaining a short, smooth antialiased boundary.
        for (int i = 0; i < n; i++) {
            float alpha = Math.max(0, Math.min(1, (Color.alpha(pixels[i]) / 255f - .05f) / .8f));
            alpha = alpha * alpha * (3 - 2 * alpha);
            pixels[i] = (Math.round(alpha * 255) << 24) | 0x00ffffff;
        }
        for (int seed = 0; seed < n; seed++) {
            if (visited[seed]) continue;
            boolean foreground = Color.alpha(pixels[seed]) >= 128, edge = false;
            int head = 0, tail = 0; queue[tail++] = seed; visited[seed] = true;
            while (head < tail) {
                int at = queue[head++], x = at % w, y = at / w;
                if (x == 0 || y == 0 || x == w-1 || y == h-1) edge = true;
                neighbors[0]=x>0?at-1:-1; neighbors[1]=x<w-1?at+1:-1;
                neighbors[2]=y>0?at-w:-1; neighbors[3]=y<h-1?at+w:-1;
                for (int next : neighbors) {
                    if (next >= 0 && !visited[next] && (Color.alpha(pixels[next]) >= 128) == foreground) {
                        visited[next] = true; queue[tail++] = next;
                    }
                }
            }
            if ((foreground && tail < n/2000) || (!foreground && !edge && tail < n/100))
                for (int i = 0; i < tail; i++) pixels[queue[i]] = foreground ? 0x00ffffff : 0xffffffff;
        }
        mask.setPixels(pixels, 0, w, 0, 0, w, h);
    }
    static Bitmap trim(Bitmap bitmap) throws IOException {
        int w=bitmap.getWidth(), h=bitmap.getHeight(), left=w, top=h, right=-1, bottom=-1, count=0;
        int[] row = new int[w];
        for (int y=0;y<h;y++) {
            bitmap.getPixels(row,0,w,0,y,w,1);
            for (int x=0;x<w;x++) if (Color.alpha(row[x]) > 24) { left=Math.min(left,x); right=Math.max(right,x); top=Math.min(top,y); bottom=Math.max(bottom,y); count++; }
        }
        if (right < left || count < w*h/100) throw new IOException("主体不清晰，已保留原照片");
        left=Math.max(0,left-8); top=Math.max(0,top-8); right=Math.min(w-1,right+8); bottom=Math.min(h-1,bottom+8);
        lastBounds = new Rect(left, top, right + 1, bottom + 1);
        return Bitmap.createBitmap(bitmap,left,top,right-left+1,bottom-top+1);
    }
    public static String hash(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try (InputStream in=new FileInputStream(file)) { byte[] b=new byte[65536]; int n; while((n=in.read(b))!=-1) digest.update(b,0,n); }
        StringBuilder text=new StringBuilder(); for(byte b:digest.digest()) text.append(String.format(java.util.Locale.US,"%02x",b & 255)); return text.toString();
    }
}
