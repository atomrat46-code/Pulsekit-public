package pulsekit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Colors and the small view factories every page uses. */
abstract class UiKit extends Activity {
    static final int BG = Color.parseColor((String)"#0A0B0C");

    static final int SURFACE = Color.parseColor((String)"#131416");

    static final int ELEV = Color.parseColor((String)"#1B1D1F");

    static final int FG = Color.parseColor((String)"#ECEBE6");

    static final int MUTED = Color.parseColor((String)"#8A8B86");

    static final int SUBTLE = Color.parseColor((String)"#5C5D59");

    static final int HIT = Color.parseColor((String)"#9AAB9C");

    static final int BORDER = Color.parseColor((String)"#2A2B2C");

    static final int VEL_HI = Color.parseColor((String)"#6F7D71");

    static final int VEL_LO = Color.parseColor((String)"#3A3C3A");

    static final int ACCENT = Color.parseColor((String)"#D9A441");

    static final int ACC_CELL = Color.parseColor((String)"#3A3220");

    void fillRound(TextView textView, int n, int n2) {
        Drawable drawable = textView.getBackground();
        if (drawable instanceof GradientDrawable) {
            ((GradientDrawable)drawable).setColor(n);
        } else {
            textView.setBackground((Drawable)this.round(n, n2));
        }
    }

    void paintChip(TextView textView, boolean bl) {
        this.paintChip(textView, bl, false);
    }

    void paintChip(TextView textView, boolean bl, boolean bl2) {
        textView.setBackground((Drawable)this.round(bl ? FG : ELEV, 16));
        textView.setTextColor(bl ? BG : FG);
        int n = textView.getPaintFlags();
        if (bl2) {
            textView.setPaintFlags(n | 8);
        } else {
            textView.setPaintFlags(n & 0xFFFFFFF7);
        }
    }

    void paintOutline(TextView textView, boolean bl) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(bl ? Color.parseColor((String)"#2A322C") : 0);
        gradientDrawable.setCornerRadius((float)this.dp(16));
        gradientDrawable.setStroke(Math.max(1, this.dp(1)), bl ? HIT : BORDER);
        textView.setBackground((Drawable)gradientDrawable);
        textView.setTextColor(bl ? FG : MUTED);
    }

    LinearLayout col() {
        LinearLayout linearLayout = new LinearLayout((Context)this);
        linearLayout.setOrientation(1);
        return linearLayout;
    }

    HorizontalScrollView chipStrip(View view) {
        HorizontalScrollView horizontalScrollView = new HorizontalScrollView((Context)this);
        horizontalScrollView.setHorizontalScrollBarEnabled(false);
        horizontalScrollView.setOverScrollMode(1);
        view.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
        horizontalScrollView.addView(view);
        horizontalScrollView.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        horizontalScrollView.setFillViewport(false);
        return horizontalScrollView;
    }

    LinearLayout row() {
        LinearLayout linearLayout = new LinearLayout((Context)this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        return linearLayout;
    }

    TextView hint(String string) {
        TextView textView = this.text(string, 13, false);
        textView.setTextColor(MUTED);
        textView.setPadding(0, this.dp(10), 0, this.dp(6));
        return textView;
    }

    TextView sectionLabel(String string) {
        TextView textView = this.text(string, 11, true);
        textView.setTextColor(SUBTLE);
        textView.setPadding(0, this.dp(6), 0, this.dp(2));
        if (Build.VERSION.SDK_INT >= 21) {
            textView.setLetterSpacing(0.12f);
        }
        return textView;
    }

    TextView text(String string, int n, boolean bl) {
        TextView textView = new TextView((Context)this);
        textView.setText((CharSequence)string);
        textView.setTextColor(FG);
        textView.setTextSize(2, (float)n);
        textView.setTypeface(Typeface.SANS_SERIF, bl ? 1 : 0);
        textView.setIncludeFontPadding(false);
        return textView;
    }

    TextView pill(String string, boolean bl, View.OnClickListener onClickListener) {
        TextView textView = this.text(string, 13, true);
        textView.setGravity(17);
        textView.setPadding(this.dp(12), this.dp(7), this.dp(12), this.dp(7));
        this.paintChip(textView, bl);
        textView.setOnClickListener(onClickListener);
        LinearLayout.LayoutParams layoutParams = this.wrap();
        layoutParams.setMargins(this.dp(3), this.dp(4), this.dp(3), this.dp(4));
        textView.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
        return textView;
    }

    TextView outline(String string, boolean bl, View.OnClickListener onClickListener) {
        TextView textView = this.text(string, 12, true);
        textView.setGravity(17);
        textView.setPadding(this.dp(12), this.dp(6), this.dp(12), this.dp(6));
        this.paintOutline(textView, bl);
        textView.setOnClickListener(onClickListener);
        LinearLayout.LayoutParams layoutParams = this.wrap();
        layoutParams.setMargins(this.dp(3), this.dp(4), this.dp(3), this.dp(4));
        textView.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
        return textView;
    }

    TextView cell(String string, View.OnClickListener onClickListener) {
        TextView textView = this.text(string, 9, true);
        textView.setGravity(17);
        textView.setBackground((Drawable)this.round(ELEV, 6));
        textView.setTextColor(SUBTLE);
        textView.setMinHeight(0);
        textView.setMinimumHeight(0);
        textView.setIncludeFontPadding(false);
        textView.setOnClickListener(onClickListener);
        return textView;
    }

    TextView labelCell(String string, int n) {
        TextView textView = this.text(string, 11, true);
        textView.setTextColor(n);
        textView.setWidth(this.dp(40));
        textView.setTextSize(2, 14.0f);
        textView.setGravity(16);
        textView.setPadding(0, this.dp(4), this.dp(4), this.dp(4));
        return textView;
    }

    TextView pad(String string, String string2, View.OnClickListener onClickListener) {
        TextView textView = this.text(string + "\n" + string2, 12, true);
        textView.setGravity(17);
        textView.setBackground((Drawable)this.round(ELEV, 12));
        textView.setTextColor(FG);
        textView.setOnClickListener(onClickListener);
        return textView;
    }

    TextView action(String string, int n, int n2, View.OnClickListener onClickListener) {
        TextView textView = this.text(string, 15, true);
        textView.setGravity(17);
        textView.setBackground((Drawable)this.round(n, 12));
        textView.setTextColor(n2);
        textView.setOnClickListener(onClickListener);
        textView.setMinHeight(this.dp(44));
        return textView;
    }

    GradientDrawable round(int n, int n2) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(n);
        gradientDrawable.setCornerRadius((float)this.dp(n2));
        return gradientDrawable;
    }

    LinearLayout.LayoutParams accLp() {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(this.dp(26), this.dp(40));
        layoutParams.setMargins(this.dp(1), this.dp(2), this.dp(1), this.dp(2));
        return layoutParams;
    }

    LinearLayout.LayoutParams cellLp() {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(this.dp(26), this.dp(40));
        layoutParams.setMargins(this.dp(1), this.dp(2), this.dp(1), this.dp(2));
        return layoutParams;
    }

    LinearLayout.LayoutParams flex(int n) {
        return new LinearLayout.LayoutParams(0, -2, (float)n);
    }

    LinearLayout.LayoutParams flexFill() {
        return new LinearLayout.LayoutParams(-1, 0, 1.0f);
    }

    LinearLayout.LayoutParams flexBtn() {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, this.dp(44), 1.0f);
        layoutParams.setMargins(this.dp(4), 0, this.dp(4), 0);
        return layoutParams;
    }

    LinearLayout.LayoutParams square() {
        return new LinearLayout.LayoutParams(this.dp(48), this.dp(48));
    }

    LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    int dp(int n) {
        return Math.round((float)n * this.getResources().getDisplayMetrics().density);
    }
}
