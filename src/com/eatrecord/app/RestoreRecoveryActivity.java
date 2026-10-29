package com.eatrecord.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

/** Failed rollback keeps its journal and pauses editing until the old state is recovered. */
public final class RestoreRecoveryActivity extends Activity {
    private boolean working;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setGravity(android.view.Gravity.CENTER);
        int padding=Math.round(28*getResources().getDisplayMetrics().density);page.setPadding(padding,padding,padding,padding);
        TextView title=new TextView(this);title.setText("上次恢复尚未完成");title.setTextSize(24);page.addView(title);
        TextView message=new TextView(this);message.setText("原数据的恢复日志已保留。请确认手机有可用空间，再重试恢复；恢复完成前暂停编辑记录。");message.setTextSize(16);message.setPadding(0,padding,0,padding);page.addView(message);
        Button retry=new Button(this);retry.setText("重试恢复");retry.setTag("restore_recovery_retry");page.addView(retry);
        Button exit=new Button(this);exit.setText("暂时退出");exit.setOnClickListener(v->finish());page.addView(exit);
        retry.setOnClickListener(v->{
            if(working)return;working=true;retry.setEnabled(false);message.setText("正在尝试恢复原数据…");
            new Thread(()->{
                boolean success=false;try{BackupRestoreRecovery.recover(this);success=true;}catch(RuntimeException unavailable){}
                final boolean recovered=success;runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;working=false;retry.setEnabled(true);
                    if(recovered){startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}
                    else message.setText("暂时无法完成恢复，原恢复日志仍保留。请释放存储空间后重试，勿清除应用数据。");
                });
            },"record-restore-recovery").start();
        });
        setContentView(page);
    }
}
