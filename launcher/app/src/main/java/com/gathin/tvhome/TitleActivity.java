package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * A featured title's page: backdrop, summary, rating, and one button for each app on this TV that streams it. The
 * apps give no way to open a title from outside, so a button opens the app; the title is a search away there.
 * Everything shown arrives in the intent, so the page needs no network of its own beyond the picture.
 */
public final class TitleActivity extends Activity {
    private static final String TYPE = "type", ID = "id", NAME = "name", YEAR = "year", RATING = "rating", TAG = "tag",
            OVERVIEW = "overview", BACKDROP = "backdrop", PKGS = "pkgs";

    static Intent intent(Context ctx, FeaturedRules.Title t) {
        return new Intent(ctx, TitleActivity.class).putExtra(TYPE, t.type).putExtra(ID, t.id).putExtra(NAME, t.title)
                .putExtra(YEAR, t.year).putExtra(RATING, t.rating).putExtra(TAG, t.tag).putExtra(OVERVIEW, t.overview)
                .putExtra(BACKDROP, t.backdrop).putExtra(PKGS, String.join(",", t.pkgs));
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_title);
        Intent in = getIntent();
        List<String> pkgs = new ArrayList<>();
        String csv = in.getStringExtra(PKGS);
        if (csv != null) for (String p : csv.split(",")) if (!p.isEmpty()) pkgs.add(p);
        FeaturedRules.Title t = new FeaturedRules.Title(String.valueOf(in.getStringExtra(TYPE)), in.getLongExtra(ID, 0),
                String.valueOf(in.getStringExtra(NAME)), String.valueOf(in.getStringExtra(YEAR)), in.getDoubleExtra(RATING, 0),
                String.valueOf(in.getStringExtra(TAG)), String.valueOf(in.getStringExtra(OVERVIEW)), String.valueOf(in.getStringExtra(BACKDROP)), pkgs);
        ((TextView) findViewById(R.id.titleTag)).setText(t.tag.toUpperCase(java.util.Locale.getDefault()));
        ((TextView) findViewById(R.id.titleName)).setText(t.title);
        ((TextView) findViewById(R.id.titleMeta)).setText(FeaturedRules.meta(t));
        ((TextView) findViewById(R.id.titleOverview)).setText(t.overview);
        TextView label = findViewById(R.id.titleWatchLabel);
        LinearLayout buttons = findViewById(R.id.titleButtons);
        PackageManager pm = getPackageManager();
        View first = null;
        for (String pkg : pkgs) {
            if (!Recs.installed(pm, pkg)) continue;
            TextView b = new TextView(this);
            b.setTextAppearance(R.style.SettingsRow);
            b.setBackgroundResource(R.drawable.settings_choice_bg);
            b.setTextColor(0xFFFFFFFF);
            b.setTextSize(18);
            b.setGravity(android.view.Gravity.CENTER_VERTICAL);
            b.setFocusable(true);
            b.setClickable(true);
            b.setPadding((int) (18 * getResources().getDisplayMetrics().density), 0, (int) (24 * getResources().getDisplayMetrics().density), 0);
            b.setText(getString(R.string.title_watch_on, TmdbRules.providerName(pkg)));
            try {
                Drawable icon = pm.getApplicationIcon(pkg);
                int size = (int) (32 * getResources().getDisplayMetrics().density);
                icon.setBounds(0, 0, size, size);
                b.setCompoundDrawables(icon, null, null, null);
                b.setCompoundDrawablePadding((int) (12 * getResources().getDisplayMetrics().density));
            } catch (PackageManager.NameNotFoundException e) {
                // no icon: the text alone
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroupWrap, (int) (54 * getResources().getDisplayMetrics().density));
            lp.setMarginEnd((int) (12 * getResources().getDisplayMetrics().density));
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> open(pkg));
            buttons.addView(b);
            if (first == null) first = b;
        }
        if (first == null) {
            label.setText(R.string.title_not_on_apps);
        } else {
            label.setText(R.string.title_watch_label);
            first.requestFocus();
        }
        final int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        final ImageView backdrop = findViewById(R.id.titleBackdrop);
        PosterCache.load(this, t.backdrop, w / 2, h / 2, bitmap -> {
            if (bitmap != null && !isDestroyed()) backdrop.setImageDrawable(new BitmapDrawable(getResources(), bitmap));
        });
    }

    private static final int ViewGroupWrap = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

    private void open(String pkg) {
        PackageManager pm = getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        try {
            if (launch == null) throw new android.content.ActivityNotFoundException();
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
