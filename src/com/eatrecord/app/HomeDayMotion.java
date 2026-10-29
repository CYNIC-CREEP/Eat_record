package com.eatrecord.app;

import java.util.Calendar;

/** Pure projections shared by the meal sheet and its continuously linked calendar. */
final class HomeDayMotion {
    private HomeDayMotion() { }
    static float fraction(float offset,float stride) {
        return stride>0&&Float.isFinite(offset)?Math.min(1f,Math.abs(offset)/stride):0f;
    }
    /** The live sheet positions also cover reversed and non-adjacent date selections. */
    static float between(float offset,float fromPosition,float toPosition) {
        float distance=fromPosition-toPosition;
        return !Float.isFinite(offset)||!Float.isFinite(distance)||Math.abs(distance)<.001f
                ?0:Math.max(0,Math.min(1,(offset+fromPosition)/distance));
    }
    /** Keep a newly requested sheet outside every currently visible sheet. */
    static float incoming(float stride,int direction,float visibleEdge) {
        return direction>0?Math.max(stride,visibleEdge+stride):Math.min(-stride,visibleEdge-stride);
    }
    /** A new grab belongs to the already visible sheet, not an uncommitted old center. */
    static int grabDirection(float offset,float stride){
        if(!Float.isFinite(offset)||!Float.isFinite(stride)||stride<=0)return 0;
        return offset>stride*.5f?-1:offset<-stride*.5f?1:0;
    }
    static float rebasedOffset(float offset,float stride,int direction){return offset+direction*stride;}
    static float rubber(float distance,float dimension) {
        return distance*dimension*.55f/(dimension+.55f*Math.abs(distance));
    }
    static float inverseRubber(float value,float dimension) {
        float size=Math.max(1f,dimension),amount=Math.min(Math.abs(value),size-.01f);
        return Math.copySign(amount*size/(.55f*(size-amount)),value);
    }
    static int weekShift(Calendar from,Calendar to) {
        long a=HomeCalendarDates.monday(from).getTimeInMillis(),b=HomeCalendarDates.monday(to).getTimeInMillis();
        return a==b?0:b>a?1:-1;
    }
    static int monthShift(Calendar from,Calendar to) {
        int a=from.get(Calendar.YEAR)*12+from.get(Calendar.MONTH),b=to.get(Calendar.YEAR)*12+to.get(Calendar.MONTH);
        return Integer.compare(b,a);
    }
    /** The three existing carousel rows already contain the same/adjacent week. */
    static boolean hasWeekPage(Calendar anchor,Calendar target){
        Calendar base=HomeCalendarDates.monday(anchor),wanted=HomeCalendarDates.monday(target);
        int direction=Integer.compare(wanted.compareTo(base),0);
        base.add(Calendar.DATE,direction*7);
        return HomeCalendarDates.key(base).equals(HomeCalendarDates.key(wanted));
    }
    /** Avoid rebuilding hidden grids for the common same/adjacent month change. */
    static boolean hasMonthPage(Calendar anchor,Calendar target){
        Calendar base=HomeCalendarDates.month(anchor);base.add(Calendar.MONTH,monthShift(anchor,target));
        return HomeCalendarDates.sameMonth(base,target);
    }
}
