package com.atakmap.android.plugintemplate;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * A {@link FrameLayout} that intercepts horizontal swipe gestures and delivers
 * them as left/right callbacks — before child views have a chance to consume
 * them.  Vertical scrolling inside child ScrollViews is unaffected.
 *
 * <p>Usage in XML (PluginLayoutInflater recognises fully-qualified class names):
 * <pre>
 *   &lt;com.atakmap.android.plugintemplate.SwipeContainerLayout
 *       android:id="@+id/page_container"
 *       android:layout_width="match_parent"
 *       android:layout_height="0dp"
 *       android:layout_weight="1"/&gt;
 * </pre>
 */
public class SwipeContainerLayout extends FrameLayout {

    public interface OnSwipeListener {
        void onSwipeLeft();
        void onSwipeRight();
    }

    /** Minimum horizontal travel (px) before a swipe is recognised. */
    private static final float SWIPE_THRESHOLD   = 50f;
    /** Swipe must be this many times more horizontal than vertical. */
    private static final float DIRECTION_RATIO   = 1.5f;

    private OnSwipeListener swipeListener;
    private float startX, startY;
    private boolean stealingTouch = false;

    public SwipeContainerLayout(Context context) {
        super(context);
    }

    public SwipeContainerLayout(Context context,
                                android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public SwipeContainerLayout(Context context,
                                android.util.AttributeSet attrs,
                                int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnSwipeListener(OnSwipeListener listener) {
        this.swipeListener = listener;
    }

    // ── Touch interception ────────────────────────────────────────────────────

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX        = ev.getX();
                startY        = ev.getY();
                stealingTouch = false;
                break;

            case MotionEvent.ACTION_MOVE: {
                float dX = Math.abs(ev.getX() - startX);
                float dY = Math.abs(ev.getY() - startY);
                if (!stealingTouch
                        && dX > SWIPE_THRESHOLD
                        && dX > dY * DIRECTION_RATIO) {
                    // Clearly horizontal — steal from child views
                    stealingTouch = true;
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                stealingTouch = false;
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_UP:
                float dX = ev.getX() - startX;
                if (Math.abs(dX) >= SWIPE_THRESHOLD && swipeListener != null) {
                    if (dX < 0) swipeListener.onSwipeLeft();
                    else        swipeListener.onSwipeRight();
                }
                stealingTouch = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                stealingTouch = false;
                return true;
        }
        return stealingTouch; // consume all events we've stolen
    }
}
