package io.github.mpdairy.monopaint;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

/** Previews a smaller tablet's panel at physical pixels, never a scaled copy of this tablet's UI. */
final class PanelPreviewLayout extends ViewGroup {
    /** The simulated tablet, or null for this tablet's whole screen. */
    private Device simulated;
    private boolean enabled;
    private final Paint edge = new Paint();
    private View panel;
    private final Rect panelBounds = new Rect();
    Runnable afterLayout;
    private View rotationHint;
    private int hintTurn, hintSize;

    void setRotationHint(View hint,int size) {rotationHint=hint;hintSize=size;addView(hint);}
    void turnRotationHint(int turn) {hintTurn=turn;layoutRotationHint();}
    /** The prompt's button in the middle of the screen (the owner, 2026-10-27), whichever way it is turned; only its glyph follows the suggested turn. */
    private void layoutRotationHint() {
        if(rotationHint==null || getChildCount()==0)return;
        View child=getChildAt(0);
        int hx=(child.getLeft()+child.getRight()-hintSize)/2, hy=(child.getTop()+child.getBottom()-hintSize)/2;
        rotationHint.layout(hx,hy,hx+hintSize,hy+hintSize);rotationHint.setRotation(hintTurn);
    }

    void showPanel(View view, Rect bounds) {
        panel=view;panelBounds.set(bounds);addView(view);
        measurePanel();view.layout(bounds.left,bounds.top,bounds.right,bounds.bottom);
    }
    void hidePanel() {
        if(panel!=null)removeView(panel);
        panel=null;panelBounds.setEmpty();
    }
    private void measurePanel() {
        if(panel!=null)panel.measure(MeasureSpec.makeMeasureSpec(panelBounds.width(),MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(panelBounds.height(),MeasureSpec.EXACTLY));
    }

    PanelPreviewLayout(Context context) {
        super(context);
        setBackgroundColor(Color.WHITE);
        setClickable(true); // Blank margins must not deliver pen gestures to the canvas.
        edge.setColor(Color.BLACK);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1);
    }

    void setSimulated(Device device) {
        simulated = device; enabled = device != null;
        setBackgroundColor(enabled ? Color.BLACK : Color.WHITE);
        requestLayout();
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        getChildAt(0).measure(MeasureSpec.makeMeasureSpec(enabled ? Math.min(simulated.panelWidth,width) : width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(enabled ? Math.min(simulated.panelHeight,height) : height, MeasureSpec.EXACTLY));
        measurePanel();
        if(rotationHint!=null)rotationHint.measure(MeasureSpec.makeMeasureSpec(hintSize,MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(hintSize,MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        View child = getChildAt(0);
        int x = (getWidth()-child.getMeasuredWidth())/2, y = (getHeight()-child.getMeasuredHeight())/2;
        child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());
        layoutRotationHint();
        if(panel!=null)panel.layout(panelBounds.left,panelBounds.top,panelBounds.right,panelBounds.bottom);
        if(afterLayout!=null)afterLayout.run();
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (enabled) {
            View child = getChildAt(0);
            // Keep the physical edge crisp against the black simulation margins.
            canvas.drawRect(child.getLeft()-.5f,child.getTop()-.5f,
                    child.getRight()+.5f,child.getBottom()+.5f,edge);
        }
    }
}
