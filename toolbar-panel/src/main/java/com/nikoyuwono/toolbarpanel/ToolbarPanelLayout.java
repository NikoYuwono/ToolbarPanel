
package com.nikoyuwono.toolbarpanel;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RelativeLayout;

/**
 * Created by nyuwono on 11/16/15.
 */
public class ToolbarPanelLayout extends RelativeLayout {

    private static final String TAG = "ToolbarPanelLayout";

    /**
     * The drawer is unlocked.
     */
    public static final int LOCK_MODE_UNLOCKED = 0;

    /**
     * The drawer is locked closed. The user may not open it, though the app may open it
     * programmatically.
     */
    public static final int LOCK_MODE_LOCKED_CLOSED = 1;

    /**
     * The drawer is locked open. The user may not close it, though the app may close it
     * programmatically.
     */
    public static final int LOCK_MODE_LOCKED_OPEN = 2;

    /**
     * Minimum velocity that will be detected as a fling
     */
    private static final int MIN_FLING_VELOCITY = 400; // dips per second

    private Drawable shadowDrawable;
    private final ViewDragHelper dragHelper;
    private ToolbarPanelListener toolbarPanelListener;
    private boolean inLayout;

    private int lockMode;
    private int dragRange;
    private int toolbarTopBound;
    private int panelHeight;
    private float dragOffset;

    private int toolbarId;
    private int panelId;
    private Toolbar toolbar;
    private View panel;

    public ToolbarPanelLayout(Context context) {
        this(context, null);
    }

    public ToolbarPanelLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ToolbarPanelLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs,
                R.styleable.ToolbarPanelLayout, 0, 0);

        try {
            toolbarId = a.getResourceId(R.styleable.ToolbarPanelLayout_toolbarId, -1);
            panelId = a.getResourceId(R.styleable.ToolbarPanelLayout_panelId, -1);
        } finally {
            a.recycle();
        }

        if (toolbarId == -1 && panelId == -1) {
            throw new IllegalStateException("Need to specify toolbarId and panelId");
        }

        Resources resources = getResources();

        final float density = resources.getDisplayMetrics().density;
        shadowDrawable = resources.getDrawable(R.drawable.drop_shadow);
        setWillNotDraw(false);

        dragHelper = ViewDragHelper.create(this, 1.2f, new DragHelperCallback());
        dragHelper.setMinVelocity(MIN_FLING_VELOCITY * density);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        toolbar = (Toolbar) findViewById(toolbarId);
        panel = findViewById(panelId);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            dragHelper.cancel();
            return false;
        }
        return dragHelper.shouldInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        dragHelper.processTouchEvent(ev);
        return true;
    }

    @Override
    public void computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);

        if (toolbar == null || shadowDrawable == null) {
            // No need to draw a shadow if we don't have one.
            return;
        }

        final int shadowHeight = shadowDrawable.getIntrinsicHeight();
        final int right = toolbar.getRight();
        final int top = toolbar.getBottom();
        final int left = toolbar.getLeft();
        final int bottom = top + shadowHeight;
        shadowDrawable.setBounds(left, top, right, bottom);
        shadowDrawable.draw(c);
    }

    private class DragHelperCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            if (getLockMode() != LOCK_MODE_UNLOCKED) {
                return false;
            }

            boolean shouldCaptureView = false;
            if (isToolbarView(child)) {
                shouldCaptureView = true;
            } else if (isPanelView(child)) {
                dragHelper.captureChildView(toolbar, pointerId);
            }
            return shouldCaptureView;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            final int topBound = getPaddingTop();
            final int bottomBound = getHeight() - toolbar.getHeight();

            return Math.min(Math.max(top, topBound), bottomBound);
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            toolbarTopBound = top;
            dragOffset = (float) top / dragRange;
            toolbar.setPivotX(toolbar.getWidth());
            toolbar.setPivotY(toolbar.getHeight());
            panel.layout(0, toolbarTopBound - panelHeight, getWidth(), toolbarTopBound);
            dispatchOnDrawerSlide(dragOffset);
            invalidate();
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            final MarginLayoutParams lp = (MarginLayoutParams) releasedChild.getLayoutParams();
            int top = getPaddingTop() + lp.topMargin;
            if (yvel > 0 || (yvel == 0 && dragOffset > 0.3f)) {
                top += dragRange;
            }
            dragHelper.settleCapturedViewAt(releasedChild.getLeft(), top);
            invalidate();
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return isToolbarView(child) ? dragRange : -dragRange;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            super.onViewDragStateChanged(state);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChildren(widthMeasureSpec, heightMeasureSpec);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY || heightMode != MeasureSpec.EXACTLY) {
            if (isInEditMode()) {
                // Don't crash the layout editor. Consume all of the space if specified
                // or pick a magic number from thin air otherwise.
                // TODO Better communication with tools of this bogus state.
                // It will crash on a real device.
                if (widthMode == MeasureSpec.AT_MOST) {
                    widthMode = MeasureSpec.EXACTLY;
                } else if (widthMode == MeasureSpec.UNSPECIFIED) {
                    widthMode = MeasureSpec.EXACTLY;
                    widthSize = 300;
                }
                if (heightMode == MeasureSpec.AT_MOST) {
                    heightMode = MeasureSpec.EXACTLY;
                } else if (heightMode == MeasureSpec.UNSPECIFIED) {
                    heightMode = MeasureSpec.EXACTLY;
                    heightSize = 300;
                }
            } else {
                throw new IllegalArgumentException(
                        "SlidingDownToolbarLayout must be measured with MeasureSpec.EXACTLY.");
            }
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        inLayout = true;
        int measuredHeight = getMeasuredHeight();
        int toolbarMeasuredHeight = toolbar.getMeasuredHeight();
        int toolbarBottomBound = toolbarTopBound + toolbarMeasuredHeight;
        panelHeight = measuredHeight - toolbarMeasuredHeight;
        dragRange = getHeight() - toolbar.getHeight();

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            if (isToolbarView(child)) {
                child.layout(0, toolbarTopBound, r, toolbarBottomBound);
            } else if (isPanelView(child)) {
                child.layout(0, toolbarTopBound - panelHeight, getWidth(), toolbarTopBound);
            } else { // This should be the content view
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                int contentTopBound = lp.topMargin + toolbarBottomBound;
                child.layout(lp.leftMargin, contentTopBound,
                        lp.leftMargin + child.getMeasuredWidth(),
                        contentTopBound + child.getMeasuredHeight());
            }
        }
        inLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!inLayout) {
            super.requestLayout();
        }
    }

    private boolean isToolbarView(View view) {
        return view.getId() == toolbarId;
    }

    private boolean isPanelView(View view) {
        return view.getId() == panelId;
    }

    public void setLockMode(int lockMode) {

        this.lockMode = lockMode;

        if (lockMode != LOCK_MODE_UNLOCKED) {
            // Cancel interaction in progress
            dragHelper.cancel();
        }
        switch (lockMode) {
            case LOCK_MODE_LOCKED_OPEN:
                openPanel();
                break;
            case LOCK_MODE_LOCKED_CLOSED:
                closePanel();
                break;
            // default: do nothing
        }
    }

    public int getLockMode() {
        return lockMode;
    }

    /**
     * Open the panel view by animating it into view.
     */
    public void openPanel() {
        dragHelper.smoothSlideViewTo(toolbar, 0, dragRange);
        invalidate();
    }

    /**
     * Close the panel view by animating it into view.
     */
    public void closePanel() {
        dragHelper.smoothSlideViewTo(toolbar, 0, 0);
        invalidate();
    }

    /**
     * Set a listener to be notified of drawer events.
     *
     * @param toolbarPanelListener Listener to notify when panel events occur
     * @see ToolbarPanelListener
     */
    public void setToolbarPanelListener(ToolbarPanelListener toolbarPanelListener) {
        this.toolbarPanelListener = toolbarPanelListener;
    }

    /**
     * Resolve the shared state of all drawers from the component ViewDragHelpers.
     * Should be called whenever a ViewDragHelper's state changes.
     */
    void updateDrawerState(int activeState) {

        if (activeState == ViewDragHelper.STATE_IDLE) {
            if (dragOffset == 0) {
                dispatchOnPanelClosed();
            } else if (dragOffset == 1) {
                dispatchOnDrawerOpened();
            }
        }
    }

    void dispatchOnPanelClosed() {
        if (toolbarPanelListener != null) {
            toolbarPanelListener.onPanelClosed(toolbar, panel);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void dispatchOnDrawerOpened() {
        if (toolbarPanelListener != null) {
            toolbarPanelListener.onPanelOpened(toolbar, panel);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void dispatchOnDrawerSlide(float slideOffset) {
        if (toolbarPanelListener != null) {
            toolbarPanelListener.onPanelSlide(toolbar, panel, slideOffset);
        }
    }
}
