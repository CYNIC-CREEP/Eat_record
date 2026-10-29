package com.eatrecord.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.VelocityTracker;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Calendar;
import java.util.List;

/** Home-only week/month calendar. The selected day and browsing anchor are independent. */
public final class HomeCalendarView extends LinearLayout {
    private static final int MONTH_WEEKS = 6;
    public interface Listener {
        void onDay(Calendar date);
        void onWeek(Calendar anchor);
        void onExpanded(boolean expanded);
        String holiday(String date);
    }

    private final boolean night;
    private final Calendar selected;
    private Calendar anchor, month;
    private final Listener listener;
    private final CalendarBody body;
    private final CalendarHeader header;
    private final LinearLayout monthPage, monthGrid, toggle;
    private final MonthPager monthPager;
    private final TextView monthLabel;
    private final HeaderIcon previous, next, chevron;
    private TodayWeekStrip week;
    private final GestureSpring revealSpring;
    private VelocityTracker velocityTracker;
    private boolean expanded, notifiedExpanded;
    private boolean rootActive=true,rootResumeReveal,rootResumeMonth;
    private float rootRevealVelocity,rootMonthVelocity;
    private boolean datePickedDuringExpansion, restoreTodayAfterCollapse;
    private boolean restoreSelectedAfterCollapse;
    private boolean externalCollapse, externalWasExpanded;
    private float externalStartReveal, externalDistance;
    private float reveal;
    private int accent, accentSoft;
    private float downX, downY, gestureReveal;
    private boolean verticalDrag, gestureCancelled, gestureWasExpanded, gestureFromDates;
    private int pointerId = -1;
    private long linkedFromStamp=Long.MIN_VALUE,linkedToStamp=Long.MIN_VALUE;
    private String linkedFromKey,linkedToKey,selectedKey,linkedFromMonthLabel,linkedToMonthLabel;
    private String selectedSuffix,linkedFromSuffix,linkedToSuffix;
    private int linkedMonthShift;
    private float linkedProgress;
    private boolean dayPreviewActive;

    public HomeCalendarView(Context context, boolean night, Calendar selected,
            Calendar anchor, boolean expanded, Listener listener) {
        super(context);
        this.night = night;
        this.selected = HomeCalendarDates.day(selected);
        selectedKey=HomeCalendarDates.key(this.selected);
        selectedSuffix="_"+selectedKey;
        this.anchor = HomeCalendarDates.day(anchor);
        this.month = HomeCalendarDates.month(anchor);
        this.expanded = this.notifiedExpanded = expanded;
        this.reveal = expanded ? 1f : 0f;
        this.listener = listener;
        revealSpring = new GestureSpring(new GestureSpring.Listener() {
            public void onUpdate(float value) {
                if(isAttachedToWindow()&&!HomeCalendarView.this.ownsRoot()){pauseRootMotion();return;}
                applyReveal(value / springDistance);
            }
            public void onEnd() { if(ownsRoot())finishReveal(); }
        });
        accent = UiTokens.ink(night);
        accentSoft = UiTokens.line(night);
        setTag("home_calendar");
        setOrientation(VERTICAL);
        setClipChildren(true);
        setClipToPadding(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        header = new CalendarHeader(context);
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setTag("home_calendar_header_content");
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        previous = new HeaderIcon(context, -1);
        previous.setTag("home_calendar_previous");
        previous.setContentDescription("上个月");
        previous.setOnClickListener(v -> { if (previous.isEnabled()) changeMonth(-1); });
        headerRow.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(48)));

        toggle = new LinearLayout(context);
        toggle.setTag("home_calendar_toggle");
        toggle.setGravity(Gravity.CENTER);
        toggle.setMinimumHeight(dp(48));
        toggle.setClickable(true);
        toggle.setFocusable(true);
        monthLabel = PaperComponents.text(context, "", 14, UiTokens.ink(night), true);
        monthLabel.setTag("home_calendar_month");
        monthLabel.setSingleLine(true);
        monthLabel.setEllipsize(TextUtils.TruncateAt.END);
        toggle.addView(monthLabel, new LinearLayout.LayoutParams(-2, -2));
        chevron = new HeaderIcon(context, 0);
        chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        toggle.addView(chevron, new LinearLayout.LayoutParams(dp(22), dp(48)));
        toggle.setOnClickListener(v -> { if (toggle.isEnabled()) setExpanded(!this.expanded, true); });
        toggle.setOnKeyListener((v, keyCode, event) -> {
            boolean activation = keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE;
            if (!activation || !toggle.isEnabled()) return false;
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                setExpanded(!this.expanded, false);
            }
            return true;
        });
        toggle.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
                info.addAction(HomeCalendarView.this.expanded
                        ? AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE
                        : AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
            }
            @Override public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (!host.isEnabled()) return false;
                if (action == AccessibilityNodeInfo.ACTION_EXPAND || action == AccessibilityNodeInfo.ACTION_COLLAPSE) {
                    setExpanded(action == AccessibilityNodeInfo.ACTION_EXPAND, false);
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
        headerRow.addView(toggle, new LinearLayout.LayoutParams(0, -2, 1));
        next = new HeaderIcon(context, 1);
        next.setTag("home_calendar_next");
        next.setContentDescription("下个月");
        next.setOnClickListener(v -> { if (next.isEnabled()) changeMonth(1); });
        headerRow.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(headerRow, new FrameLayout.LayoutParams(-1, -2));
        addView(header, new LinearLayout.LayoutParams(-1, -2));

        body = new CalendarBody(context);
        addView(body, new LinearLayout.LayoutParams(-1, -2));
        monthPage = new LinearLayout(context);
        monthPage.setOrientation(VERTICAL);
        String[] weekdays = {"一", "二", "三", "四", "五", "六", "日"};
        LinearLayout names = new LinearLayout(context);
        names.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        for (String name : weekdays) {
            TextView label = PaperComponents.text(context, name, 11, UiTokens.secondary(night), false);
            label.setGravity(Gravity.CENTER);
            label.setMinHeight(dp(24));
            names.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        }
        monthPage.addView(names, new LinearLayout.LayoutParams(-1, -2));
        monthPager = new MonthPager(context);
        monthGrid = monthPager.pages[1];
        monthPage.addView(monthPager, new LinearLayout.LayoutParams(-1, -2));
        body.addView(monthPage, new FrameLayout.LayoutParams(-1, -2));
        rebuildWeek();
        rebuildMonth();
        updateHeader();
        applyReveal(reveal);
    }

    private int dp(float value) { return PaperComponents.dp(getContext(), value); }

    /** MainActivity supplies its actual accent, including custom/night theme adjustments. */
    public void setPalette(int accent, int accentSoft) {
        this.accent = accent;
        this.accentSoft = accentSoft;
        rebuildMonth();
        previous.invalidate();
        next.invalidate();
        chevron.invalidate();
    }

    public Calendar weekStart() { return week.weekStart(); }
    public Calendar monthAnchor() { return (Calendar) month.clone(); }
    public boolean isExpanded() { return expanded; }
    public TodayWeekStrip weekStrip() { return week; }
    public boolean canCollapse() { return reveal > 0f || expanded; }
    public void setDatePickedDuringExpansion(boolean picked) { datePickedDuringExpansion = picked; }
    public boolean wasDatePickedDuringExpansion() { return datePickedDuringExpansion; }

    /** Update the day marker while the journal is still following a drag/release spring. */
    private boolean ownsRoot(){return rootActive&&TodayDayPager.isLiveHierarchy(this);}
    void setRootActive(boolean active){
        if(rootActive==active){if(active)resumeRootMotion();return;}
        rootActive=active;
        if(!active)pauseRootMotion();else resumeRootMotion();
    }
    private void pauseRootMotion(){
        boolean revealRunning=revealSpring.isRunning(),monthRunning=monthPager.spring.isRunning();
        rootResumeReveal|=revealRunning||verticalDrag||externalCollapse||Math.abs(reveal-(expanded?1f:0f))>.001f;
        rootResumeMonth|=monthRunning||monthPager.dragging;
        if(revealRunning)rootRevealVelocity=revealSpring.velocity();
        if(monthRunning)rootMonthVelocity=monthPager.spring.velocity();
        stopAnimation();monthPager.pauseGesture();releaseTracker();verticalDrag=false;externalCollapse=false;gestureCancelled=true;pointerId=-1;
        if(week!=null)week.setRootActive(false);
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
    }
    private void resumeRootMotion(){
        if(!ownsRoot())return;
        gestureCancelled=false;if(week!=null)week.setRootActive(true);
        if(rootResumeMonth){rootResumeMonth=false;float velocity=rootMonthVelocity;rootMonthVelocity=0;if(!dayPreviewActive)monthPager.settle(monthPager.direction,velocity);}
        if(rootResumeReveal){
            rootResumeReveal=false;float velocity=rootRevealVelocity;rootRevealVelocity=0;
            if(!ValueAnimator.areAnimatorsEnabled()||!isAttachedToWindow()){finishReveal();return;}
            springDistance=revealDistance();revealSpring.start(reveal*springDistance,(expanded?1f:0f)*springDistance,velocity);
        }else notifyExpanded();
    }
    private void finishReveal(){
        if(!expanded)monthPager.cancelGesture();
        if(!expanded&&restoreTodayAfterCollapse)restoreTodayWeek();
        else if(!expanded&&restoreSelectedAfterCollapse)restoreWeek(selected);
        restoreTodayAfterCollapse=restoreSelectedAfterCollapse=false;applyReveal(expanded?1f:0f);notifyExpanded();
    }
    public boolean isInteracting(){return rootActive&&(verticalDrag||revealSpring.isRunning()||monthPager.dragging||monthPager.spring.isRunning()||week!=null&&week.isInteracting());}
    public void setSelectedDate(Calendar date) {
        String key=HomeCalendarDates.key(date);
        if(selectedKey.equals(key))return;
        rootResumeMonth=false;rootMonthVelocity=0;
        selectedKey=key;selectedSuffix="_"+key;dayPreviewActive=false;monthLabel.setAlpha(1);
        selected.setTimeInMillis(HomeCalendarDates.day(date).getTimeInMillis());
        anchor=HomeCalendarDates.day(date);month=HomeCalendarDates.month(date);
        if(expanded||reveal>0)datePickedDuringExpansion=true;
        restoreTodayAfterCollapse=false;restoreSelectedAfterCollapse=false;
        rebuildWeek();rebuildMonth();updateHeader();
    }

    /** Follow the actual meal-sheet displacement. Never recreate either calendar per MOVE. */
    public void setDayProgress(Calendar from,Calendar to,float fraction){
        float progress=Math.max(0,Math.min(1,fraction));
        boolean moving=from.getTimeInMillis()!=to.getTimeInMillis()&&progress>0;
        if(!moving&&!dayPreviewActive&&selected.getTimeInMillis()==from.getTimeInMillis())return;
        if(linkedFromStamp!=from.getTimeInMillis()||linkedToStamp!=to.getTimeInMillis()){
            if(moving&&dayPreviewActive&&linkedToStamp==from.getTimeInMillis()&&!HomeCalendarDates.sameMonth(month,from)){
                month=HomeCalendarDates.month(from);monthPager.cancelGesture();rebuildMonth();updateHeader();
            }
            linkedFromStamp=from.getTimeInMillis();linkedToStamp=to.getTimeInMillis();
            linkedFromKey=HomeCalendarDates.key(from);linkedToKey=HomeCalendarDates.key(to);
            linkedFromSuffix="_"+linkedFromKey;linkedToSuffix="_"+linkedToKey;
            linkedMonthShift=HomeDayMotion.monthShift(month,to);
            linkedFromMonthLabel=monthTitle(month);linkedToMonthLabel=monthTitle(to);
            if(!moving&&!HomeCalendarDates.sameMonth(month,from)){
                month=HomeCalendarDates.month(from);monthPager.cancelGesture();rebuildMonth();updateHeader();
            }else if(moving)monthPager.prepareLinked(to,linkedMonthShift);
            else if(monthPager.linkedTargetMonth!=null)rebuildMonth();
            if(!moving){linkedMonthShift=0;linkedFromMonthLabel=monthTitle(month);}
        }
        linkedProgress=progress;dayPreviewActive=moving;
        Calendar shown=progress>=.5f?to:from;String key=progress>=.5f?linkedToKey:linkedFromKey;
        if(!selectedKey.equals(key)){
            selected.setTimeInMillis(shown.getTimeInMillis());selectedKey=key;selectedSuffix="_"+key;anchor=HomeCalendarDates.day(shown);
            if(expanded||reveal>0)datePickedDuringExpansion=true;
            restoreTodayAfterCollapse=restoreSelectedAfterCollapse=false;monthPager.updateSelectedSemantics();
        }
        week.setDayProgress(from,to,progress);
        if(expanded||reveal>0){
            monthPager.spring.cancel();monthPager.offset=dayPreviewActive?-linkedMonthShift*monthPager.getWidth()*progress:0;monthPager.positionPages();
            String label=linkedMonthShift!=0&&dayPreviewActive&&progress>=.5f?linkedToMonthLabel:linkedFromMonthLabel;
            if(!label.contentEquals(monthLabel.getText())){monthLabel.setText(label);toggle.setContentDescription(label+(expanded?"，收起日历":"，展开日历"));}
            monthLabel.setAlpha(linkedMonthShift!=0&&dayPreviewActive?Math.abs(1f-2f*progress):1);
        }else monthPager.invalidate();
    }
    private String monthTitle(Calendar date){return date.get(Calendar.YEAR)+"年"+(date.get(Calendar.MONTH)+1)+"月";}

    private void rebuildWeek() {
        if (week != null) body.removeView(week);
        week = new TodayWeekStrip(getContext(), night, selected, anchor, new TodayWeekStrip.Listener() {
            @Override public void onDay(Calendar date) {
                if (expanded || reveal > 0f) datePickedDuringExpansion = true;
                if (listener != null&&HomeCalendarView.this.ownsRoot()) listener.onDay(HomeCalendarDates.day(date));
            }
            @Override public void onWeek(Calendar start) {
                HomeCalendarView.this.anchor = HomeCalendarDates.day(start);
                month = HomeCalendarDates.month(start);
                rebuildMonth();
                updateHeader();
                if (listener != null&&HomeCalendarView.this.ownsRoot()) listener.onWeek((Calendar) HomeCalendarView.this.anchor.clone());
            }
        });
        week.setRootActive(rootActive);
        body.addView(week, 0, new FrameLayout.LayoutParams(-1, -2));
        applyReveal(reveal);
    }

    private void rebuildMonth() {
        monthPager.rebuild();
        body.requestLayout();
    }

    private void populateMonth(LinearLayout grid, Calendar pageMonth, boolean current) {
        grid.removeAllViews();
        grid.setTag(current ? "home_calendar_grid" : "home_calendar_adjacent_grid");
        List<Calendar> dates = HomeCalendarDates.grid(pageMonth);
        // A month carousel needs one stable vertical viewport, even across 4/5/6
        // week months. Fill the remaining weeks with real adjacent-month dates
        // rather than growing the viewport on the first horizontal MOVE.
        Calendar nextDate = (Calendar) dates.get(dates.size() - 1).clone();
        while (dates.size() < MONTH_WEEKS * 7) {
            nextDate.add(Calendar.DATE, 1);
            dates.add((Calendar) nextDate.clone());
        }
        String todayKey = HomeCalendarDates.key(Calendar.getInstance());
        String selectedKey = this.selectedKey;
        for (int row = 0; row < dates.size() / 7; row++) {
            LinearLayout line = new LinearLayout(getContext());
            line.setGravity(Gravity.CENTER_VERTICAL);
            for (int column = 0; column < 7; column++) {
                final Calendar date = dates.get(row * 7 + column);
                final String dateKey = HomeCalendarDates.key(date);
                boolean future = dateKey.compareTo(todayKey) > 0;
                boolean active = dateKey.equals(selectedKey);
                boolean outMonth = !HomeCalendarDates.sameMonth(date, pageMonth);
                LinearLayout cell = new LinearLayout(getContext());
                cell.setOrientation(VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setMinimumHeight(dp(40));
                cell.setPadding(dp(1), dp(2), dp(1), dp(2));
                cell.setTag((current ? "home_calendar_day_" : "home_calendar_adjacent_day_") + dateKey);
                cell.setClickable(!future);
                cell.setFocusable(!future);
                cell.setEnabled(!future);
                cell.setSelected(active);
                cell.setAlpha(future ? .27f : outMonth ? .43f : 1f);
                TextView number = PaperComponents.text(getContext(),
                        String.valueOf(date.get(Calendar.DAY_OF_MONTH)), 14, UiTokens.ink(night), false);
                number.getPaint().setFakeBoldText(active);
                number.setSingleLine(true);
                number.setGravity(Gravity.CENTER);
                number.setIncludeFontPadding(false);
                cell.addView(number, new LinearLayout.LayoutParams(-1, -2));
                String holiday = listener == null ? "" : listener.holiday(dateKey);
                if (holiday == null) holiday = "";
                TextView note = PaperComponents.text(getContext(), holiday, 8, accent, true);
                note.setSingleLine(true);
                note.setEllipsize(TextUtils.TruncateAt.END);
                note.setGravity(Gravity.CENTER);
                note.setIncludeFontPadding(false);
                note.setMinHeight(dp(11));
                cell.addView(note, new LinearLayout.LayoutParams(-1, -2));
                if (dateKey.equals(todayKey)) {
                    GradientDrawable selectedBackground = new GradientDrawable();
                    selectedBackground.setColor(android.graphics.Color.TRANSPARENT);
                    selectedBackground.setCornerRadius(dp(17));
                    selectedBackground.setStroke(dp(1), accentSoft);
                    cell.setBackground(selectedBackground);
                }
                cell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
                number.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                note.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                cell.setContentDescription(dateKey + (holiday.isEmpty() ? "" : "，" + holiday)
                        + (outMonth ? "，非本月" : "")
                        + (future ? "，未来日期不可选择" : active ? "，已选择" : ""));
                cell.setOnClickListener(v -> {
                    if (!future && listener != null&&HomeCalendarView.this.ownsRoot()) {
                        datePickedDuringExpansion = true;
                        listener.onDay((Calendar) date.clone());
                    }
                });
                line.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
            }
            // Equal height within a week: a holiday or large font cannot clip the last date.
            grid.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    public void changeMonth(int direction) {
        if (!expanded || direction == 0) return;
        monthPager.navigate(direction < 0 ? -1 : 1, true);
    }

    private void commitMonth(int direction) {
        month.add(Calendar.MONTH, direction < 0 ? -1 : 1);
        month = HomeCalendarDates.month(month);
        anchor = (Calendar) month.clone();
        rebuildWeek();
        rebuildMonth();
        updateHeader();
        if (listener != null&&ownsRoot()) listener.onWeek((Calendar) anchor.clone());
        announceForAccessibility(monthLabel.getText());
    }

    private void updateHeader() {
        monthLabel.setText(month.get(Calendar.YEAR) + "年" + (month.get(Calendar.MONTH) + 1) + "月");
        toggle.setContentDescription(monthLabel.getText() + (expanded ? "，收起日历" : "，展开日历"));
        updateHeaderAccess();
        chevron.invalidate();
    }

    private void updateHeaderAccess() {
        // The compact week has no invisible month button or reserved header space.
        // Keep full-size content inside the clipped viewport so the dates never jump
        // when a drag starts or changes direction.
        boolean ready = expanded && reveal >= 1f;
        header.setVisibility(reveal > 0f ? VISIBLE : INVISIBLE);
        header.setAlpha(reveal);
        header.setImportantForAccessibility(ready ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setHeaderButtonReady(previous, ready);
        setHeaderButtonReady(toggle, ready);
        setHeaderButtonReady(next, ready);
        setContentDescription(ready ? monthLabel.getText() + "，完整日历，向上滑动收起"
                : "本周日期，向下滑动展开日历");
    }

    private void setHeaderButtonReady(View button, boolean ready) {
        button.setEnabled(ready);
        button.setClickable(ready);
        button.setFocusable(ready);
    }

    private float springDistance = 1f;
    private void stopAnimation() { revealSpring.cancel(); }

    public void setExpanded(boolean target, boolean animate) {
        externalCollapse = false;
        settleReveal(target, animate, false, revealSpring.isRunning() ? revealSpring.velocity() : 0f);
    }

    private void settleReveal(boolean target, boolean animate, boolean cancelled, float velocityY) {
        stopAnimation();
        if (target && reveal <= 0f) datePickedDuringExpansion = false;
        restoreTodayAfterCollapse = !target && !cancelled && !datePickedDuringExpansion;
        restoreSelectedAfterCollapse = !target && !cancelled && datePickedDuringExpansion;
        expanded = target;
        updateHeader();
        if (target && Math.abs(monthPager.offset) > .5f) monthPager.settle(
                Math.abs(monthPager.offset) >= monthPager.getWidth() * .5f
                ? monthPager.offset < 0f ? 1 : -1 : 0, 0f);
        final float destination = target ? 1f : 0f;
        if(!rootActive){rootResumeReveal=true;rootRevealVelocity=velocityY;return;}
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || !isAttachedToWindow()
                || Math.abs(destination - reveal) < .001f) {
            applyReveal(destination);
            if (!target) monthPager.cancelGesture();
            if (restoreTodayAfterCollapse) restoreTodayWeek();
            else if (restoreSelectedAfterCollapse) restoreWeek(selected);
            restoreTodayAfterCollapse = false;
            restoreSelectedAfterCollapse = false;
            notifyExpanded();
            return;
        }
        // Accordion height is the deliberate layout exception; position remains tied to the finger.
        springDistance = revealDistance();
        revealSpring.start(reveal * springDistance, destination * springDistance, velocityY);
    }

    private int revealDistance() {
        return Math.max(dp(120), header.fullHeight + body.monthHeight - body.weekHeight);
    }

    private void restoreTodayWeek() {
        restoreWeek(Calendar.getInstance());
    }

    private void restoreWeek(Calendar date) {
        anchor = HomeCalendarDates.day(date);
        month = HomeCalendarDates.month(anchor);
        rebuildWeek();
        rebuildMonth();
        updateHeader();
        if (listener != null&&ownsRoot()) listener.onWeek(HomeCalendarDates.monday(anchor));
    }

    /** The home container may collapse from anywhere, but must never use this to expand. */
    public boolean beginExternalCollapse() {
        if (!rootActive)return false;
        if (!canCollapse() || verticalDrag) return false;
        externalStartReveal = reveal;
        externalWasExpanded = expanded;
        externalDistance = 0f;
        externalCollapse = true;
        stopAnimation();
        monthPager.pauseGesture();
        return true;
    }

    /** Positive cumulative distance means upward, not an incremental delta. */
    public void updateExternalCollapse(float upwardDistancePx) {
        if (!externalCollapse) return;
        externalDistance = Math.max(0f, upwardDistancePx);
        if (ValueAnimator.areAnimatorsEnabled()) applyReveal(externalStartReveal - externalDistance / revealDistance());
    }

    public void endExternalCollapse(float velocityYPxPerSecond, boolean cancelled) {
        if (!externalCollapse) return;
        externalCollapse = false;
        float projected = reveal + velocityYPxPerSecond / revealDistance() * .099f;
        boolean target = cancelled ? externalWasExpanded
                : !ValueAnimator.areAnimatorsEnabled() ? externalDistance < dp(36)
                : projected >= .5f && externalDistance < dp(36);
        settleReveal(target, true, cancelled, cancelled ? 0f : velocityYPxPerSecond);
    }

    private void notifyExpanded() {
        if(!ownsRoot())return;
        if (notifiedExpanded == expanded) return;
        notifiedExpanded = expanded;
        if (listener != null&&ownsRoot()) listener.onExpanded(expanded);
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private void applyReveal(float value) {
        reveal = Math.max(0f, Math.min(1f, value));
        if (header != null && monthLabel != null) {
            updateHeaderAccess();
            header.requestLayout();
        }
        if (week == null || monthPage == null) return;
        week.setVisibility(reveal >= 1f ? INVISIBLE : VISIBLE);
        monthPage.setVisibility(reveal <= 0f ? INVISIBLE : VISIBLE);
        week.setAlpha(1f - reveal);
        monthPage.setAlpha(reveal);
        week.setImportantForAccessibility(reveal<=0f
                ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        monthPage.setImportantForAccessibility(reveal>=1f
                ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        body.requestLayout();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if(!rootActive)return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            releaseTracker();
            velocityTracker = VelocityTracker.obtain();
            trackGesture(event);
            pointerId = event.getPointerId(0);
            downX = event.getRawX();
            downY = event.getRawY();
            gestureReveal = reveal;
            gestureWasExpanded = expanded;
            verticalDrag = false;
            gestureCancelled = false;
            gestureFromDates = event.getY() >= body.getTop() && event.getY() < body.getBottom();
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            gestureCancelled = true;
            if (verticalDrag) settleReveal(gestureWasExpanded, true, true, 0f);
        } else if (action == MotionEvent.ACTION_MOVE && !gestureCancelled) {
            trackGesture(event);
            int index = event.findPointerIndex(pointerId);
            if (index < 0) return false;
            float dx = screenX(event, index) - downX, dy = screenY(event, index) - downY;
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (verticalDrag || Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) * 1.3f
                    && (reveal > 0f && dy < 0f || gestureFromDates && reveal < 1f && dy > 0f)) {
                if (!verticalDrag) { stopAnimation(); monthPager.pauseGesture(); gestureReveal = reveal; verticalDrag = true; }
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        }
        return verticalDrag;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if(!rootActive)return false;
        if (!verticalDrag) return super.onTouchEvent(event);
        int action = event.getActionMasked();
        trackGesture(event);
        int index = event.findPointerIndex(pointerId);
        if (index < 0) {
            verticalDrag = false;
            gestureCancelled = true;
            pointerId = -1;
            releaseTracker();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            settleReveal(gestureWasExpanded, true, true, 0f);
            return true;
        }
        float dy = screenY(event, index) - downY;
        if (action == MotionEvent.ACTION_MOVE && !gestureCancelled) {
            if (ValueAnimator.areAnimatorsEnabled()) {
                applyReveal(gestureReveal + dy / revealDistance());
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_POINTER_DOWN) {
            boolean cancelled = action != MotionEvent.ACTION_UP || gestureCancelled;
            float velocity = 0f;
            if (!cancelled && velocityTracker != null) {
                velocityTracker.computeCurrentVelocity(1000, ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity());
                velocity = velocityTracker.getYVelocity(pointerId);
            }
            float projected = reveal + velocity / revealDistance() * .099f;
            boolean target = cancelled ? gestureWasExpanded : Math.abs(dy) >= dp(36)
                    ? dy > 0f : projected >= .5f;
            verticalDrag = false;
            pointerId = -1;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            releaseTracker();
            settleReveal(target, true, cancelled, cancelled ? 0f : velocity);
            return true;
        }
        return true;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(expanded ? AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE
                : AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_EXPAND || action == AccessibilityNodeInfo.ACTION_COLLAPSE) {
            setExpanded(action == AccessibilityNodeInfo.ACTION_EXPAND, false);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override protected void onDetachedFromWindow() {
        rootResumeReveal=rootResumeMonth=false;rootRevealVelocity=rootMonthVelocity=0;
        stopAnimation();
        releaseTracker();
        externalCollapse = false;
        restoreTodayAfterCollapse = false;
        restoreSelectedAfterCollapse = false;
        verticalDrag = false;
        pointerId = -1;
        applyReveal(expanded ? 1f : 0f);
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE && body != null) {
            rootResumeReveal=rootResumeMonth=false;rootRevealVelocity=rootMonthVelocity=0;
            boolean pendingExpansion = revealSpring.isRunning();
            stopAnimation();
            releaseTracker();
            externalCollapse = false;
            restoreTodayAfterCollapse = false;
            restoreSelectedAfterCollapse = false;
            verticalDrag = false;
            pointerId = -1;
            applyReveal(expanded ? 1f : 0f);
            if (pendingExpansion) notifyExpanded();
        }
    }

    private void releaseTracker() {
        if (velocityTracker != null) { velocityTracker.recycle(); velocityTracker = null; }
    }

    private float screenX(MotionEvent event, int index) {
        return event.getRawX() + event.getX(index) - event.getX();
    }

    private float screenY(MotionEvent event, int index) {
        return event.getRawY() + event.getY(index) - event.getY();
    }

    private void trackGesture(MotionEvent event) {
        if (velocityTracker == null) return;
        // Header/body heights change together. Track in screen space, not a child
        // coordinate system that can move during the same pointer sequence.
        MotionEvent screen = MotionEvent.obtain(event);
        screen.offsetLocation(event.getRawX() - event.getX(), event.getRawY() - event.getY());
        velocityTracker.addMovement(screen);
        screen.recycle();
    }

    /** Real adjacent month pages are translated, never replaced at the finger position. */
    private final class MonthPager extends FrameLayout {
        final LinearLayout[] pages = new LinearLayout[3];
        final GestureSpring spring;
        VelocityTracker tracker;
        float offset, downX, downY, lastX;
        int pointer = -1, direction;
        boolean dragging, cancelled;
        final Paint markerPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF markerFrom=new RectF(),markerTo=new RectF(),markerBounds=new RectF();
        String linkedTargetMonth;

        MonthPager(Context context) {
            super(context);
            setTag("home_calendar_month_pager");
            setClipChildren(true);
            setClipToPadding(true);
            setClickable(true);
            setFocusable(true);
            setWillNotDraw(false);
            spring = new GestureSpring(new GestureSpring.Listener() {
                public void onUpdate(float value) {
                    if(isAttachedToWindow()&&!HomeCalendarView.this.ownsRoot()){HomeCalendarView.this.pauseRootMotion();return;}
                    offset = value; positionPages();
                }
                public void onEnd() {
                    int change = direction;
                    direction = 0;
                    offset = 0;
                    if (change != 0) commitMonth(change);
                    positionPages();
                    requestLayout();
                }
            });
            for (int i = 0; i < 3; i++) {
                pages[i] = new LinearLayout(context);
                pages[i].setOrientation(VERTICAL);
                pages[i].setImportantForAccessibility(i == 1 ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                        : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                addView(pages[i], new FrameLayout.LayoutParams(-1, -2));
            }
        }

        void rebuild() {
            linkedTargetMonth=null;
            for (int i = 0; i < 3; i++) {
                Calendar pageMonth = (Calendar) month.clone();
                pageMonth.add(Calendar.MONTH, i - 1);
                populateMonth(pages[i], pageMonth, i == 1);
            }
            setContentDescription("月份日期，左右滑动切换月份");
            positionPages();
            requestLayout();
        }

        void prepareLinked(Calendar to,int shift){
            String target=HomeCalendarDates.key(HomeCalendarDates.month(to));
            boolean natural=HomeDayMotion.hasMonthPage(month,to);
            if(natural&&linkedTargetMonth==null)return;
            if(target.equals(linkedTargetMonth))return;
            for(int i:new int[]{0,2}){Calendar adjacent=(Calendar)month.clone();adjacent.add(Calendar.MONTH,i-1);populateMonth(pages[i],adjacent,false);}
            if(!natural&&shift!=0)populateMonth(pages[1+shift],HomeCalendarDates.month(to),false);
            linkedTargetMonth=natural?null:target;requestLayout();
        }

        void positionPages() {
            for (int i = 0; i < 3; i++) pages[i].setTranslationX((i - 1) * getWidth() + offset);
            invalidate();
        }

        private boolean markerRect(LinearLayout page,String key,RectF out){
            String suffix=key==selectedKey?selectedSuffix:key==linkedFromKey?linkedFromSuffix:linkedToSuffix;
            for(int r=0;r<page.getChildCount();r++){
                LinearLayout row=(LinearLayout)page.getChildAt(r);
                for(int c=0;c<row.getChildCount();c++){
                    View cell=row.getChildAt(c);Object tag=cell.getTag();
                    if(tag!=null&&tag.toString().endsWith(suffix)){
                        out.set(page.getLeft()+page.getTranslationX()+row.getLeft()+cell.getLeft(),page.getTop()+row.getTop()+cell.getTop(),
                                page.getLeft()+page.getTranslationX()+row.getLeft()+cell.getRight(),page.getTop()+row.getTop()+cell.getBottom());return true;
                    }
                }
            }return false;
        }
        void updateSelectedSemantics(){
            for(LinearLayout page:pages)for(int r=0;r<page.getChildCount();r++){
                LinearLayout row=(LinearLayout)page.getChildAt(r);
                for(int c=0;c<row.getChildCount();c++){
                    View cell=row.getChildAt(c);Object tag=cell.getTag();boolean active=tag!=null&&tag.toString().endsWith("_"+selectedKey);
                    if(cell.isSelected()!=active){cell.setSelected(active);TextView number=(TextView)((LinearLayout)cell).getChildAt(0);number.getPaint().setFakeBoldText(active);number.invalidate();String text=String.valueOf(cell.getContentDescription()).replace("，已选择","");cell.setContentDescription(text+(active?"，已选择":""));}
                }
            }invalidate();
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            for(int i=0;i<3;i++){
                LinearLayout page=pages[i];
                boolean from=markerRect(page,dayPreviewActive?linkedFromKey:selectedKey,markerFrom);
                boolean to=dayPreviewActive&&markerRect(page,linkedToKey,markerTo);
                if(dayPreviewActive&&linkedMonthShift!=0){
                    if(i==1&&from)drawMarker(canvas,markerFrom,1-linkedProgress);
                    if(i==1+linkedMonthShift&&to)drawMarker(canvas,markerTo,linkedProgress);
                }else if(from&&to&&Math.abs(markerFrom.top-markerTo.top)<1){
                    markerBounds.set(markerFrom.left+(markerTo.left-markerFrom.left)*linkedProgress,markerFrom.top,
                            markerFrom.right+(markerTo.right-markerFrom.right)*linkedProgress,markerFrom.bottom);drawMarker(canvas,markerBounds,1);
                }else{
                    if(from)drawMarker(canvas,markerFrom,dayPreviewActive?1-linkedProgress:1);
                    if(to)drawMarker(canvas,markerTo,linkedProgress);
                }
            }
        }
        private void drawMarker(Canvas canvas,RectF rect,float opacity){
            if(opacity<=0||rect.width()<=0||rect.height()<=0||rect.right<=0||rect.left>=getWidth())return;
            markerPaint.setStyle(Paint.Style.FILL);markerPaint.setColor(accentSoft);markerPaint.setAlpha(Math.round(255*opacity));canvas.drawRoundRect(rect,dp(17),dp(17),markerPaint);
            markerPaint.setStyle(Paint.Style.STROKE);markerPaint.setStrokeWidth(dp(1));markerPaint.setColor(accent);markerPaint.setAlpha(Math.round(255*opacity));canvas.drawRoundRect(rect,dp(17),dp(17),markerPaint);
            markerPaint.setStyle(Paint.Style.FILL);markerPaint.setAlpha(255);
        }

        void navigate(int change, boolean animate) {
            spring.cancel();
            direction = change;
            if(!rootActive){rootResumeMonth=true;rootMonthVelocity=0;return;}
            float target = -change * getWidth();
            if (!animate || !ValueAnimator.areAnimatorsEnabled() || !isAttachedToWindow() || getWidth() == 0) {
                direction = 0;
                offset = 0;
                if (change != 0) commitMonth(change);
                positionPages();
                requestLayout();
                return;
            }
            requestLayout();
            spring.start(offset, target, spring.velocity());
        }

        void settle(int change, float velocity) {
            spring.cancel();
            direction = change;
            if(!rootActive){rootResumeMonth=true;rootMonthVelocity=velocity;return;}
            if (!ValueAnimator.areAnimatorsEnabled() || !isAttachedToWindow() || getWidth() == 0) {
                offset = 0;
                direction = 0;
                if (change != 0) commitMonth(change);
                positionPages();
                requestLayout();
            } else spring.start(offset, -change * getWidth(), velocity);
        }

        void dragBy(float dx) {
            if (!ValueAnimator.areAnimatorsEnabled()) return;
            offset += dx;
            int width = getWidth();
            if (width > 0) {
                while (offset <= -width) { offset += width; commitMonth(1); }
                while (offset >= width) { offset -= width; commitMonth(-1); }
            }
            positionPages();
        }

        void releaseTracker() { if (tracker != null) { tracker.recycle(); tracker = null; } }
        void cancelGesture() {
            pauseGesture();
            offset = 0;
            direction = 0;
            positionPages();
            requestLayout();
        }

        void pauseGesture() {
            spring.cancel();
            dragging = false;
            cancelled = true;
            pointer = -1;
            releaseTracker();
        }

        void startTouch(MotionEvent event) {
            spring.cancel();
            releaseTracker();
            tracker = VelocityTracker.obtain();
            tracker.addMovement(event);
            pointer = event.getPointerId(0);
            downX = lastX = event.getX();
            downY = event.getY();
            dragging = cancelled = false;
        }

        boolean horizontal(float x, float y) {
            float dx = x - downX, dy = y - downY;
            return Math.abs(dx) > ViewConfiguration.get(getContext()).getScaledTouchSlop()
                    && Math.abs(dx) > Math.abs(dy) * 1.3f;
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) startTouch(event);
            else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                cancelled = true;
                settle(0, 0f);
                releaseTracker();
                return true; // Cancel a child tap when a second pointer arrives.
            } else if (action == MotionEvent.ACTION_MOVE && !cancelled) {
                if (tracker != null) tracker.addMovement(event);
                int index = event.findPointerIndex(pointer);
                if (index >= 0 && horizontal(event.getX(index), event.getY(index))) {
                    dragging = true;
                    requestLayout();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
            }
            return dragging;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) { startTouch(event); return true; }
            if (tracker != null) tracker.addMovement(event);
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                cancelled = true;
                dragging = false;
                settle(0, 0f);
                releaseTracker();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            int index = event.findPointerIndex(pointer);
            float x = index < 0 ? lastX : event.getX(index);
            float y = index < 0 ? downY : event.getY(index);
            if (action == MotionEvent.ACTION_MOVE && !cancelled && index >= 0) {
                if (dragging || horizontal(x, y)) {
                    if (!dragging) { dragging = true; requestLayout(); }
                    dragBy(x - lastX);
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                } else if (Math.abs(y - downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) cancelled = true;
                lastX = x;
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (action == MotionEvent.ACTION_UP && !cancelled && index >= 0 && (dragging || horizontal(x, y))) {
                    dragBy(x - lastX);
                    float velocity = 0f;
                    if (tracker != null) {
                        tracker.computeCurrentVelocity(1000, ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity());
                        velocity = tracker.getXVelocity(pointer);
                    }
                    float position = ValueAnimator.areAnimatorsEnabled() ? offset : x - downX;
                    float projected = position + velocity / 1000f * .99f / (1f - .99f);
                    boolean change = Math.abs(projected) >= Math.min(dp(36), getWidth() * .15f);
                    settle(change ? (projected < 0f ? 1 : -1) : 0, velocity);
                } else settle(0, 0f);
                dragging = false;
                pointer = -1;
                releaseTracker();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            return true;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int childWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            int height = 0;
            for (int i = 0; i < 3; i++) {
                pages[i].measure(childWidth, natural);
                height = Math.max(height, pages[i].getMeasuredHeight());
            }
            setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            for (LinearLayout page : pages) page.layout(0, 0, getWidth(), page.getMeasuredHeight());
            positionPages();
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w != oldw) { spring.cancel(); offset = 0; direction = 0; }
            positionPages();
        }

        @Override protected void onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow(); }
        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility != VISIBLE && spring != null) cancelGesture();
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setScrollable(true);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        }

        @Override public boolean performAccessibilityAction(int action, Bundle args) {
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    || action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                navigate(action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD ? -1 : 1, false);
                return true;
            }
            return super.performAccessibilityAction(action, args);
        }
    }

    /** Full-size header content clipped to the same reveal as the month body. */
    private final class CalendarHeader extends FrameLayout {
        int fullHeight;

        CalendarHeader(Context context) {
            super(context);
            setTag("home_calendar_header");
            setClipChildren(true);
            setClipToPadding(true);
            fullHeight = dp(48);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            View row = getChildAt(0);
            row.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            fullHeight = Math.max(dp(48), row.getMeasuredHeight());
            setMeasuredDimension(resolveSize(width, widthSpec),
                    resolveSize(Math.round(fullHeight * reveal), heightSpec));
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            getChildAt(0).layout(0, 0, getWidth(), fullHeight);
        }
    }

    private final class CalendarBody extends FrameLayout {
        int weekHeight, monthHeight;
        CalendarBody(Context context) { super(context); setClipChildren(true); setClipToPadding(true); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int childWidth = MeasureSpec.makeMeasureSpec(Math.max(0, width), MeasureSpec.EXACTLY);
            int intrinsicHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            week.measure(childWidth, intrinsicHeight);
            monthPage.measure(childWidth, intrinsicHeight);
            weekHeight = week.getMeasuredHeight();
            monthHeight = monthPage.getMeasuredHeight();
            setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(
                    Math.round(weekHeight + (monthHeight - weekHeight) * reveal), heightSpec));
        }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            week.layout(0, 0, getWidth(), weekHeight);
            monthPage.layout(0, 0, getWidth(), monthHeight);
        }
    }

    private final class HeaderIcon extends View {
        final int direction;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        HeaderIcon(Context context, int direction) {
            super(context);
            this.direction = direction;
            if (direction != 0) {
                setClickable(true);
                setFocusable(true);
                setOnKeyListener((v, keyCode, event) -> {
                    boolean activation = keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE;
                    if (!activation || !isEnabled()) return false;
                    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled() && expanded)
                        monthPager.navigate(direction, false);
                    return true;
                });
            }
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            paint.setColor(accent);
            if (direction == 0) {
                paint.setStyle(Paint.Style.FILL);
                Path path = new Path();
                path.moveTo(cx - dp(3.5f), cy + (expanded ? dp(2) : -dp(2)));
                path.lineTo(cx + dp(3.5f), cy + (expanded ? dp(2) : -dp(2)));
                path.lineTo(cx, cy + (expanded ? -dp(2.5f) : dp(2.5f)));
                path.close();
                canvas.drawPath(path, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.8f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                Path path = new Path();
                path.moveTo(cx - direction * dp(3), cy - dp(6));
                path.lineTo(cx + direction * dp(3), cy);
                path.lineTo(cx - direction * dp(3), cy + dp(6));
                canvas.drawPath(path, paint);
            }
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (direction != 0) info.setClassName("android.widget.Button");
        }
        @Override public boolean performAccessibilityAction(int action, Bundle args) {
            if (direction != 0 && action == AccessibilityNodeInfo.ACTION_CLICK && expanded && isEnabled()) {
                monthPager.navigate(direction, false);
                return true;
            }
            return super.performAccessibilityAction(action, args);
        }
    }
}
