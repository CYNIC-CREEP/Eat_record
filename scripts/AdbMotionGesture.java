import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.content.ComponentName;
import java.lang.reflect.Method;

/** Shell-only input driver with one stable downTime. No APK, instrumentation, preferences or fixtures. */
public final class AdbMotionGesture {
    private final Object manager;
    private final Method inject;
    private final long downTime=SystemClock.uptimeMillis();
    private float x,y;
    private AdbMotionGesture()throws Exception {
        Class<?> type;
        try{type=Class.forName("android.hardware.input.InputManagerGlobal");}
        catch(ClassNotFoundException old){type=Class.forName("android.hardware.input.InputManager");}
        manager=type.getMethod("getInstance").invoke(null);
        inject=type.getMethod("injectInputEvent",InputEvent.class,int.class);
    }
    private void send(int action,float x,float y)throws Exception {
        if(!foreground())throw new IllegalStateException("Target app left foreground; gesture stopped");
        this.x=x;this.y=y;
        MotionEvent event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{if(!Boolean.TRUE.equals(inject.invoke(manager,event,2)))throw new Exception("Input injection rejected");}finally{event.recycle();}
    }
    private boolean foreground()throws Exception {
        Object service=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        Object task=Class.forName("android.app.IActivityTaskManager").getMethod("getFocusedRootTaskInfo").invoke(service);
        ComponentName top=task==null?null:(ComponentName)task.getClass().getField("topActivity").get(task);
        return top!=null&&"com.eatrecord.app".equals(top.getPackageName());
    }
    private void pause(long milliseconds)throws Exception {
        long until=SystemClock.uptimeMillis()+milliseconds;
        while(SystemClock.uptimeMillis()<until){if(!foreground())throw new IllegalStateException("Target app left foreground; evidence rejected");Thread.sleep(50);}
    }
    private void stage(String value){System.out.println(value);System.out.flush();}
    public static void main(String[] args)throws Exception {
        if(args.length<3)throw new IllegalArgumentException("hold x y | drag x y");
        AdbMotionGesture driver=new AdbMotionGesture();float x=Float.parseFloat(args[1]),y=Float.parseFloat(args[2]);
        boolean released=false;
        try{
            driver.send(MotionEvent.ACTION_DOWN,x,y);
            if("pulse".equals(args[0])){
                driver.pause(240);driver.stage("HELD");driver.pause(120);
                driver.send(MotionEvent.ACTION_UP,driver.x,driver.y);released=true;
            }else if("hold".equals(args[0])){
                driver.pause(500);driver.stage("HELD");driver.pause(1500);
            }else if("drag".equals(args[0])){
                for(int i=1;i<=18;i++){driver.send(MotionEvent.ACTION_MOVE,x+300*i/18f,y);Thread.sleep(16);}
                driver.stage("HELD");driver.pause(1800);
                driver.send(MotionEvent.ACTION_MOVE,x+150,y);driver.stage("REVERSED");driver.pause(1500);
            }else throw new IllegalArgumentException("Unknown gesture");
        }finally{if(!released&&driver.foreground())driver.send(MotionEvent.ACTION_CANCEL,driver.x,driver.y);}
        driver.stage(released?"RELEASED":"CANCELLED");
    }
}
