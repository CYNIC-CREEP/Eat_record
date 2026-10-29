package com.eatrecord.app;

import java.util.Calendar;
import java.util.TimeZone;

public final class HomeDayMotionTest {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    private static Calendar day(String zone,int year,int month,int date){Calendar result=Calendar.getInstance(TimeZone.getTimeZone(zone));result.clear();result.set(year,month-1,date,12,0,0);return result;}
    public static void main(String[] args){
        check(HomeDayMotion.fraction(0,500)==0,"zero starts at same date");
        check(HomeDayMotion.fraction(125,500)==.25f,"quarter drag immediately advances marker");
        check(HomeDayMotion.fraction(-125,500)==.25f,"forward and reverse are symmetric");
        check(HomeDayMotion.fraction(999,500)==1,"bounded end progress");
        check(HomeDayMotion.fraction(Float.NaN,500)==0,"invalid progress cannot poison view state");
        check(HomeDayMotion.fraction(1,0)==0,"prelayout progress is safe");
        check(HomeDayMotion.between(0,0,500)==0,"date tap starts from live centered source");
        check(HomeDayMotion.between(-125,0,500)==.25f,"date tap shares quarter progress with gesture");
        check(HomeDayMotion.between(-500,0,500)==1,"date tap settles on exact requested date");
        check(HomeDayMotion.between(0,350,-150)==.7f,"reverse tap preserves live progress");
        check(HomeDayMotion.between(-100,350,-150)==.5f,"reversal returns through identical midpoint");
        check(HomeDayMotion.between(0,-150,850)==.15f,"third tap retains visible source displacement");
        check(HomeDayMotion.between(-850,-150,850)==1,"distant retarget reaches requested sheet directly");
        check(HomeDayMotion.between(1000,0,500)==0,"overscroll cannot select outside source");
        check(HomeDayMotion.between(-1000,0,500)==1,"overscroll cannot pass target");
        check(HomeDayMotion.between(Float.NaN,0,500)==0,"invalid selection progress is safe");
        check(HomeDayMotion.between(0,500,500)==0,"same-date selection does not divide by zero");
        check(HomeDayMotion.incoming(500,1,350)==850,"newer requested sheet preserves visible incoming sheet");
        check(HomeDayMotion.incoming(500,-1,-350)==-850,"older requested sheet preserves visible outgoing sheet");
        check(HomeDayMotion.incoming(500,1,-150)==500,"newer requested sheet starts fully outside viewport");
        check(HomeDayMotion.incoming(500,-1,150)==-500,"older requested sheet starts fully outside viewport");
        check(HomeDayMotion.grabDirection(249,500)==0,"grab before halfway retains committed center");
        check(HomeDayMotion.grabDirection(251,500)==-1,"grab past halfway adopts visible previous date");
        check(HomeDayMotion.grabDirection(-251,500)==1,"grab past halfway adopts visible next date");
        check(HomeDayMotion.grabDirection(Float.NaN,500)==0,"invalid presentation does not change date");
        check(HomeDayMotion.grabDirection(251,0)==0,"prelayout grab is safe");
        // A chain of grabs must advance the center instead of repeatedly clamping
        // against the same uncommitted neighbour. Both visible sheets keep pixels.
        for(int sign:new int[]{-1,1})for(float size:new float[]{344,1080}){
            int date=0;
            for(int gesture=1;gesture<=8;gesture++){
                float presentation=sign*size*.8f;
                int direction=HomeDayMotion.grabDirection(presentation,size);
                float carried=HomeDayMotion.rebasedOffset(presentation,size,direction);
                date+=direction;
                check(date==-sign*gesture,"continuous grabs advance each already-visible date");
                check(Math.abs(carried+(-direction)*size-presentation)<.001f,"grab keeps outgoing paper at its exact pixel position");
                check(Math.abs(carried-(presentation+direction*size))<.001f,"grab keeps incoming paper at its exact pixel position");
                check(Math.abs(carried)<size*.5f,"new drag has room for the next date");
            }
        }
        for(float size:new float[]{320,1080})for(float distance:new float[]{-2,-80,-300}){
            float edge=HomeDayMotion.rubber(distance,size),restored=HomeDayMotion.inverseRubber(edge,size);
            check(Math.abs(restored-distance)<.01f,"grabbed future edge inverse is continuous");
            check(Math.abs(edge)<Math.abs(distance),"future edge resists instead of opening a future day");
        }
        for(String zone:new String[]{"Asia/Shanghai","America/New_York","Pacific/Auckland"}){
            Calendar mon=day(zone,2026,10,19),tue=day(zone,2026,10,20),sun=day(zone,2026,10,18);
            check(HomeDayMotion.weekShift(mon,tue)==0,"same-week marker does not move whole row");
            check(HomeDayMotion.weekShift(mon,sun)==-1,"Monday to Sunday links previous week");
            check(HomeDayMotion.weekShift(sun,mon)==1,"Sunday to Monday links next week");
            check(HomeDayMotion.hasWeekPage(mon,tue),"same week reuses current row");
            check(HomeDayMotion.hasWeekPage(mon,sun),"previous week reuses existing row");
            check(HomeDayMotion.hasWeekPage(mon,day(zone,2026,10,27)),"next week reuses existing row");
            check(!HomeDayMotion.hasWeekPage(mon,day(zone,2026,10,4)),"far previous week needs a target row");
            check(!HomeDayMotion.hasWeekPage(mon,day(zone,2026,11,8)),"far next week needs a target row");
            check(HomeDayMotion.hasWeekPage(day(zone,2026,3,2),day(zone,2026,3,9)),"DST boundary still reuses the adjacent week");
            check(HomeDayMotion.hasMonthPage(mon,tue),"same month reuses hidden grids");
            check(HomeDayMotion.hasMonthPage(day(zone,2026,12,31),day(zone,2027,1,1)),"year boundary reuses next month grid");
            check(HomeDayMotion.hasMonthPage(day(zone,2027,1,1),day(zone,2026,12,31)),"year boundary reuses previous month grid");
            check(!HomeDayMotion.hasMonthPage(mon,day(zone,2026,8,4)),"far previous month needs a target grid");
            check(!HomeDayMotion.hasMonthPage(mon,day(zone,2027,1,8)),"far next month needs a target grid");
            check(HomeDayMotion.monthShift(day(zone,2026,12,31),day(zone,2027,1,1))==1,"year boundary month advances");
            check(HomeDayMotion.monthShift(day(zone,2027,1,1),day(zone,2026,12,31))==-1,"year boundary reversal restores");
        }
        System.out.println("PASS: "+checks+" continuous day/calendar motion checks");
    }
}
