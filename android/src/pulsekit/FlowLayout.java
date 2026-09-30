package pulsekit;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Chip row. Single-line mode matches the web phone strip (swipe, never wrap). */
public final class FlowLayout extends ViewGroup {
  private final int hGap;
  private final int vGap;
  private boolean singleLine;

  public FlowLayout(Context context, int hGap, int vGap) {
    super(context);
    this.hGap = hGap;
    this.vGap = vGap;
  }

  public void setSingleLine(boolean singleLine) {
    this.singleLine = singleLine;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int maxW = MeasureSpec.getSize(widthMeasureSpec);
    int inner = maxW - getPaddingLeft() - getPaddingRight();
    boolean row = singleLine || inner < 8 || MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY;
    if (row) {
      int w = getPaddingLeft() + getPaddingRight();
      int h = getPaddingTop() + getPaddingBottom();
      int rowH = 0;
      for (int i = 0; i < getChildCount(); i++) {
        View c = getChildAt(i);
        if (c.getVisibility() == GONE) continue;
        measureChild(c, MeasureSpec.UNSPECIFIED, heightMeasureSpec);
        w += c.getMeasuredWidth() + hGap;
        rowH = Math.max(rowH, c.getMeasuredHeight());
      }
      h += rowH;
      int width = singleLine ? Math.max(maxW, w) : resolveSize(Math.max(maxW, w), widthMeasureSpec);
      setMeasuredDimension(width, resolveSize(h, heightMeasureSpec));
      return;
    }
    int x = 0;
    int y = 0;
    int rowH = 0;
    for (int i = 0; i < getChildCount(); i++) {
      View c = getChildAt(i);
      if (c.getVisibility() == GONE) continue;
      measureChild(c, widthMeasureSpec, heightMeasureSpec);
      int cw = c.getMeasuredWidth();
      int ch = c.getMeasuredHeight();
      if (x > 0 && x + cw > inner) {
        y += rowH + vGap;
        x = 0;
        rowH = 0;
      }
      x += cw + hGap;
      rowH = Math.max(rowH, ch);
    }
    int h = y + rowH + getPaddingTop() + getPaddingBottom();
    setMeasuredDimension(resolveSize(maxW, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int inner = r - l - getPaddingLeft() - getPaddingRight();
    int x = getPaddingLeft();
    int y = getPaddingTop();
    int rowH = 0;
    int startX = getPaddingLeft();
    boolean wrap = !singleLine && inner >= 8;
    for (int i = 0; i < getChildCount(); i++) {
      View c = getChildAt(i);
      if (c.getVisibility() == GONE) continue;
      int cw = c.getMeasuredWidth();
      int ch = c.getMeasuredHeight();
      if (wrap && x > startX && x + cw - startX > inner) {
        x = startX;
        y += rowH + vGap;
        rowH = 0;
      }
      c.layout(x, y, x + cw, y + ch);
      x += cw + hGap;
      rowH = Math.max(rowH, ch);
    }
  }
}
