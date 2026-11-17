package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class MealTextSaveTest {
    private static int checks;
    private static void check(boolean value,String message) {
        checks++;
        if(!value)throw new AssertionError(message);
    }
    private static final class MemoryStore implements MealTextSave.Store {
        final Object lock;
        String raw;
        int reads,writes,failures;
        boolean restoring;
        MemoryStore(Object lock,String raw){this.lock=lock;this.raw=raw;}
        public String read(){if(!Thread.holdsLock(lock))throw new AssertionError("read outside lock");reads++;return raw;}
        public boolean write(String value){if(!Thread.holdsLock(lock))throw new AssertionError("write outside lock");raw=value;writes++;if(failures>0){failures--;return false;}return true;}
        public boolean restoring(){return restoring;}
    }
    public static void main(String[] args)throws Exception {
        Object lock=new Object();
        JSONObject item=new JSONObject().put("id","meal-image").put("nutrients",new JSONObject().put("caloriesKcal",125));
        JSONObject meal=new JSONObject().put("text","old").put("foodItems",new JSONArray().put(item))
                .put("photos",new JSONArray().put("old.jpg")).put("manualCalories",95).put("workerRevision",7);
        JSONObject days=new JSONObject().put("2026-11-15",new JSONObject().put("lunch",meal)).put("old-date",new JSONObject().put("mood","warm"));
        MemoryStore store=new MemoryStore(lock,days.toString());
        MealTextSave.Result result=MealTextSave.save(store,lock,"2026-11-15","lunch","  rice\n egg  ",true);
        check(result.saved&&store.writes==1,"durable inline success");
        JSONObject saved=result.days.getJSONObject("2026-11-15").getJSONObject("lunch");
        check("rice\n egg".equals(saved.getString("text")),"normalized multiline text");
        check(saved.getJSONArray("foodItems").toString().equals(meal.getJSONArray("foodItems").toString()),"latest nutrition and identity retained");
        check(saved.getJSONArray("photos").getString(0).equals("old.jpg")&&saved.getInt("workerRevision")==7,"legacy media and worker fields retained");
        check(result.days.has("old-date")&&saved.getInt("manualCalories")==95,"other dates and manual calories retained");
        result=MealTextSave.save(store,lock,"2026-11-15","lunch","　\n ",true);
        saved=result.days.getJSONObject("2026-11-15").getJSONObject("lunch");
        check(result.saved&&!saved.has("text")&&!saved.has("manualTextEntry"),"inline clear preserves meal");
        check(saved.getJSONArray("foodItems").length()==1,"clear preserves image");
        int writes=store.writes;
        result=MealTextSave.save(store,lock,"2026-11-16","breakfast","",true);
        check(result.saved&&store.writes==writes&&!result.days.has("2026-11-16"),"empty unused editor creates no day");
        result=MealTextSave.save(store,lock,"2026-11-16","snack","  milk  ",false);
        check(result.saved&&result.days.getJSONObject("2026-11-16").getJSONObject("snack").getBoolean("manualTextEntry"),"manual create");
        int reads=store.reads;
        check(!MealTextSave.save(store,lock,"2026-11-16","lunch","",false).saved&&store.reads==reads,"empty manual rejected before I/O");
        check(!MealTextSave.save(store,lock,"2026-11-16","lunch",new String(new char[2001]).replace('\0','a'),true).saved,"length bound");
        check(!MealTextSave.save(store,lock,null,"lunch","rice",true).saved,"invalid target rejected");
        store.failures=1;writes=store.writes;
        check(!MealTextSave.save(store,lock,"2026-11-16","lunch","pending",true).saved,"uncertain commit reports failure");
        check(new JSONObject(store.raw).getJSONObject("2026-11-16").getJSONObject("lunch").getString("text").equals("pending"),"failure can publish in-memory intention");
        check(MealTextSave.save(store,lock,"2026-11-16","lunch","pending",true).saved&&store.writes==writes+2,"retry forces durable write despite matching memory");
        store.restoring=true;reads=store.reads;
        check(!MealTextSave.save(store,lock,"2026-11-16","lunch","blocked",true).saved&&store.reads==reads,"restore guard");
        MemoryStore corrupt=new MemoryStore(lock,"invalid json");
        check(!MealTextSave.save(corrupt,lock,"2026-11-16","lunch","rice",true).saved&&corrupt.writes==0,"corrupt diary is preserved");
        MealTextSave.Store throwing=new MealTextSave.Store(){public String read()throws Exception{throw new Exception("synthetic");}public boolean write(String raw){throw new AssertionError();}};
        check(!MealTextSave.save(throwing,lock,"2026-11-16","lunch","rice",true).saved,"read failure is recoverable");
        MealTextSave.Store exhausted=new MealTextSave.Store(){public String read(){throw new OutOfMemoryError("synthetic");}public boolean write(String raw){return true;}};
        check(!MealTextSave.save(exhausted,lock,"2026-11-16","lunch","rice",true).saved,"allocation failure reports retryable result");
        check(!MealTextSave.latest(exhausted,lock).saved,"latest snapshot allocation failure reports result");
        MemoryStore concurrent=new MemoryStore(lock,"{}");CountDownLatch start=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread[] workers=new Thread[2];
        for(int i=0;i<workers.length;i++){
            final int index=i;
            workers[i]=new Thread(()->{try{start.await();if(!MealTextSave.save(concurrent,lock,"2026-11-"+(15+index),"lunch","meal-"+index,true).saved)throw new AssertionError("save failed");}catch(Throwable error){failure.compareAndSet(null,error);}});
            workers[i].start();
        }
        start.countDown();for(Thread worker:workers)worker.join(3000);
        if(failure.get()!=null)throw new AssertionError(failure.get());
        check(!workers[0].isAlive()&&!workers[1].isAlive(),"concurrent writes finish");
        JSONObject both=new JSONObject(concurrent.raw);
        check(both.has("2026-11-15")&&both.has("2026-11-16")&&concurrent.writes==2,"concurrent writes merge latest dates");
        System.out.println("PASS: "+checks+" meal text transaction, retry, restore and concurrent merge checks");
    }
}
