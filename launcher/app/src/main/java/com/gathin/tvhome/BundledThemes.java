package com.gathin.tvhome;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Themes copied from the repo's themes/ into the APK's assets at build time (app/build.gradle), so they can be
 * picked on the TV instead of pushed with tools/theme.sh. Applying one writes the same files theme.sh would.
 */
final class BundledThemes {
    private static final String ROOT = "themes";
    /** This TV's own setup lives in config.xml; a theme picked on the TV changes the look, never these. */
    private static final String[] KEPT_KEYS = {"dock_apps", "hidden_apps", "app_keys", "live_tv_package",
            "live_tv_action", "live_tv_input_id", "settings_key_keycodes", "settings_key_scancodes"};

    private BundledThemes() {}

    static boolean available(Context ctx) {
        return !names(ctx).isEmpty();
    }

    static List<String> names(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            String[] dirs = ctx.getAssets().list(ROOT);
            if (dirs != null) {
                Arrays.sort(dirs);
                for (String d : dirs) if (hasConfig(ctx.getAssets(), d)) out.add(d);
            }
        } catch (IOException ignored) {
            // no bundled themes
        }
        return out;
    }

    /** "six-columns" shows as "Six columns". */
    static String label(String name) {
        String s = name.replace('-', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Replaces the theme files with the bundled theme {@code name}, or clears them when null. */
    static boolean apply(Context ctx, String name) {
        File dir = Config.dir(ctx);
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return false;
        clear(dir);
        AssetManager assets = ctx.getAssets();
        try {
            if (name != null) {
                String base = ROOT + "/" + name;
                write(new File(dir, Config.FILE_NAME), withoutKeptKeys(read(assets, base + "/" + Config.FILE_NAME)));
                String[] top = assets.list(base);
                if (top != null && Arrays.asList(top).contains("wallpaper.jpg")) {
                    write(new File(dir, "wallpaper.jpg"), read(assets, base + "/wallpaper.jpg"));
                }
                String[] images = assets.list(base + "/" + ThemeFiles.WALLPAPERS);
                if (images != null && images.length > 0) {
                    File collection = new File(dir, ThemeFiles.WALLPAPERS);
                    if (!collection.isDirectory() && !collection.mkdirs()) return false;
                    for (String img : images) {
                        if (!ThemeFiles.collectionImage(img) && !WallpaperCaptions.FILE_NAME.equals(img)) continue;
                        write(new File(collection, img), read(assets, base + "/" + ThemeFiles.WALLPAPERS + "/" + img));
                    }
                }
            }
            // A new revision makes Config drop the choices saved for the previous theme, as theme.sh does.
            write(new File(dir, "theme_revision"),
                    Long.toString(System.currentTimeMillis()).getBytes(StandardCharsets.US_ASCII));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasConfig(AssetManager assets, String name) throws IOException {
        String[] files = assets.list(ROOT + "/" + name);
        return files != null && Arrays.asList(files).contains(Config.FILE_NAME);
    }

    private static void clear(File dir) {
        new File(dir, Config.FILE_NAME).delete();
        new File(dir, "wallpaper.jpg").delete();
        File[] old = new File(dir, ThemeFiles.WALLPAPERS).listFiles(
                (d, n) -> ThemeFiles.collectionImage(n) || WallpaperCaptions.FILE_NAME.equals(n));
        if (old != null) for (File f : old) f.delete();
    }

    private static byte[] withoutKeptKeys(byte[] json) throws Exception {
        JSONObject o = new JSONObject(new String(json, StandardCharsets.UTF_8));
        for (String k : KEPT_KEYS) o.remove(k);
        return o.toString(2).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] read(AssetManager assets, String path) throws IOException {
        try (InputStream in = assets.open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void write(File f, byte[] data) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }
}
