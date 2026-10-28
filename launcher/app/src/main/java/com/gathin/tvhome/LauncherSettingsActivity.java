package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private TextView nowPlayingChoice, closeAppsChoice, homePageChoice, recsChoice, continueChoice, weatherChoice, weatherUnitChoice, dockBandChoice, topShelfChoice;
    private String homePage = "classic";
    private boolean nowPlayingOn, closeAppsOn, recsOn, continueOn, weatherOn, fahrenheit, dockBandOn, topShelfOn;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        buildBackdrop();
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        continueOn = config.continueWatching;
        nowPlayingOn = config.nowPlaying;
        recsOn = config.recommendations;
        homePage = config.featured ? "featured" : config.landscape ? "landscape" : "classic";
        closeAppsOn = config.closeApps;
        closeAppsChoice = homeToggle(R.id.closeAppsChoice, "close_apps", () -> closeAppsOn = !closeAppsOn, () -> closeAppsOn);
        homePageChoice = findViewById(R.id.homePageChoice);
        homePageChoice.setOnClickListener(v -> {
            homePage = "featured".equals(homePage) ? "landscape" : "landscape".equals(homePage) ? "classic" : "featured";
            choices.edit().putString("home_page", homePage).apply();
            changed();
            refresh();
        });
        recsChoice = homeToggle(R.id.recsChoice, "recommendations", () -> recsOn = !recsOn, () -> recsOn);
        nowPlayingChoice = homeToggle(R.id.nowPlayingChoice, "now_playing", () -> nowPlayingOn = !nowPlayingOn, () -> nowPlayingOn);
        weatherOn = config.weather;
        fahrenheit = config.weatherFahrenheit;
        dockBandOn = config.dockBand;
        topShelfOn = config.cinematicHome;
        continueChoice = homeToggle(R.id.continueChoice, "continue_watching", () -> continueOn = !continueOn, () -> continueOn);
        weatherChoice = homeToggle(R.id.weatherChoice, "weather", () -> weatherOn = !weatherOn, () -> weatherOn);
        weatherUnitChoice = homeToggle(R.id.weatherUnitChoice, "weather_fahrenheit", () -> fahrenheit = !fahrenheit, () -> fahrenheit);
        dockBandChoice = homeToggle(R.id.dockBandChoice, "dock_band", () -> dockBandOn = !dockBandOn, () -> dockBandOn);
        topShelfChoice = homeToggle(R.id.topShelfChoice, "cinematic_home", () -> topShelfOn = !topShelfOn, () -> topShelfOn);

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(OpenOnStart.KEY).commit();
            SharedPreferences.Editor homeChoices = choices.edit();
            Config.clearHomeChoices(homeChoices);
            homeChoices.commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            continueOn = current.continueWatching;
            nowPlayingOn = current.nowPlaying;
            recsOn = current.recommendations;
            homePage = current.featured ? "featured" : current.landscape ? "landscape" : "classic";
            closeAppsOn = current.closeApps;
            weatherOn = current.weather;
            fahrenheit = current.weatherFahrenheit;
            dockBandOn = current.dockBand;
            topShelfOn = current.cinematicHome;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        setUpNavigation();
        roundPane();
        scaleOnFocus(findViewById(android.R.id.content));
        findViewById(R.id.navLayout).requestFocus();
    }

    /** A Home-screen switch: flips the value, saves it as an on-TV choice over the theme, and tells Home to rebuild. */
    private TextView homeToggle(int id, String key, Runnable flip, java.util.function.BooleanSupplier value) {
        TextView row = findViewById(id);
        row.setOnClickListener(v -> {
            flip.run();
            choices.edit().putBoolean(key, value.getAsBoolean()).apply();
            changed();
            refresh();
        });
        return row;
    }

    /** The settings picture: the launcher's generated landscape for the hour, blurred, behind a dark veil. */
    private void buildBackdrop() {
        final ImageView backdrop = findViewById(R.id.settingsBackdrop);
        final int hour = java.time.LocalTime.now().getHour();
        final long day = java.time.LocalDate.now().toEpochDay();
        new Thread(() -> {
            Bitmap scene = LandscapeArt.render(480, 270, hour, day);
            int[] px = new int[480 * 270];
            scene.getPixels(px, 0, 480, 0, 0, 480, 270);
            BoxBlur.blur(px, 480, 270, 9, 3);
            scene.setPixels(px, 0, 480, 0, 0, 480, 270);
            runOnUiThread(() -> {
                if (!isDestroyed()) backdrop.setImageBitmap(scene);
            });
        }, "settings-backdrop").start();
    }

    /** The left menu: focusing a section shows its panel; OK moves into it. */
    private void setUpNavigation() {
        final int[] navIds = {R.id.navLayout, R.id.navHome, R.id.navMotion, R.id.navSystem};
        final int[] sectionIds = {R.id.sectionLayout, R.id.sectionHome, R.id.sectionMotion, R.id.sectionSystem};
        for (int i = 0; i < navIds.length; i++) {
            final int index = i;
            View nav = findViewById(navIds[i]);
            nav.setOnFocusChangeListener((v, has) -> {
                if (has) showSection(index, navIds, sectionIds);
                v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(120).start();
            });
            nav.setOnClickListener(v -> {
                View first = firstFocusableIn(findViewById(sectionIds[index]));
                if (first != null) first.requestFocus();
            });
        }
        showSection(0, navIds, sectionIds);
    }

    private void showSection(int index, int[] navIds, int[] sectionIds) {
        for (int i = 0; i < sectionIds.length; i++) {
            findViewById(sectionIds[i]).setVisibility(i == index ? View.VISIBLE : View.GONE);
            findViewById(navIds[i]).setActivated(i == index);
        }
    }

    private static View firstFocusableIn(View v) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if (v.isFocusable() && !(v instanceof android.view.ViewGroup)) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = firstFocusableIn(g.getChildAt(i));
                if (f != null) return f;
            }
        }
        return null;
    }

    /** A focused row grows a touch, like a launcher tile; cheap on a screen that does not otherwise move. */
    private void scaleOnFocus(View v) {
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) scaleOnFocus(g.getChildAt(i));
        } else if (v.isFocusable() && v.getOnFocusChangeListener() == null) {
            v.setOnFocusChangeListener((view, has) -> view.animate().scaleX(has ? 1.015f : 1f).scaleY(has ? 1.015f : 1f).setDuration(110).start());
        }
    }

    /** Clips the panel's contents to its rounded corners, so scrolled rows are cut by the curve and not by a square edge. */
    private void roundPane() {
        View pane = (View) findViewById(R.id.sectionLayout).getParent().getParent();
        final float radius = 34f * getResources().getDisplayMetrics().density;
        pane.setClipToOutline(true);
        pane.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        toggle(animationsChoice, labelOf(getString(R.string.animations_state, "")), animations);
        toggle(advancedChoice, labelOf(getString(R.string.advanced_effects_state, "")), advancedEffects);
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(accentValue(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default))));
        openOnStartChoice.setText(accentValue(getString(R.string.open_on_start_state, openOnStartLabel())));
        toggle(nowPlayingChoice, getString(R.string.now_playing_state), nowPlayingOn);
        toggle(continueChoice, getString(R.string.continue_state), continueOn);
        homePageChoice.setText(accentValue(getString(R.string.home_page_state, getString("featured".equals(homePage) ? R.string.home_page_featured : "landscape".equals(homePage) ? R.string.home_page_landscape : R.string.home_page_classic))));
        toggle(closeAppsChoice, getString(R.string.close_apps_state), closeAppsOn);
        toggle(recsChoice, getString(R.string.recommendations_state), recsOn);
        toggle(weatherChoice, getString(R.string.weather_state), weatherOn);
        weatherUnitChoice.setText(accentValue(getString(R.string.weather_unit_state, fahrenheit ? "\u00B0F" : "\u00B0C")));
        weatherUnitChoice.setEnabled(weatherOn);
        weatherUnitChoice.setAlpha(weatherOn ? 1f : 0.5f);
        toggle(dockBandChoice, getString(R.string.dock_band_state), dockBandOn);
        toggle(topShelfChoice, getString(R.string.top_shelf_state), topShelfOn);
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    /** A switch row: the label, and the switch at its end drawn from the view's activated state. */
    private static void toggle(TextView row, String label, boolean on) {
        row.setText(label);
        row.setActivated(on);
    }

    /** "Animations: %1$s" with the value cut off: the translated label alone. */
    private static String labelOf(String s) {
        int i = s.indexOf(':');
        return i > 0 ? s.substring(0, i).trim() : s.trim();
    }

    /** The text after the first ": " in the accent colour, so a value reads apart from its label. */
    private static CharSequence accentValue(String s) {
        int i = s.indexOf(": ");
        if (i < 0) return s;
        android.text.SpannableString spanned = new android.text.SpannableString(s);
        spanned.setSpan(new android.text.style.ForegroundColorSpan(0xFF7CD4FF), i + 2, s.length(), 0);
        return spanned;
    }

    private boolean hasOnTvChoices() {
        for (String key : Config.HOME_KEYS) if (choices.contains(key)) return true;
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
