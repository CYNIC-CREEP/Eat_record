package com.eatrecord.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Recognition requires the user's API configuration and explicit photo-upload consent. */
final class FoodRecognitionSetup {
    interface Host {
        JSONObject profile(); boolean night();
        void chooseSource(String source, boolean enabled);
        void rememberChoice(); void configureApi(Runnable saved); void refresh();
    }
    private final Activity activity;
    private final Host host;
    private AlertDialog choice,consent;
    private boolean disposed;
    FoodRecognitionSetup(Activity activity,Host host){this.activity=activity;this.host=host;}
    private boolean visible(){return !disposed&&!activity.isFinishing()&&!activity.isDestroyed();}
    private int dp(float value){return PaperComponents.dp(activity,value);}
    private void show(AlertDialog dialog){dialog.show();PaperComponents.styleDialog(dialog,host.night());}
    void choose(boolean firstUse){
        if(!visible()||choice!=null&&choice.isShowing())return;
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("food_recognition_choice");box.setPadding(dp(24),dp(8),dp(24),dp(12));
        TextView introduction=PaperComponents.text(activity,"抠图已内置，可直接使用。菜名和营养估算需要配置你自己的 HTTPS 视觉 API，并在发送餐图前明确同意；也可以先使用手写记录。",14,UiTokens.ink(host.night()),false);box.addView(introduction);
        TextView api=PaperComponents.button(activity,"配置自己的 API",host.night(),v->{host.rememberChoice();choice.dismiss();cloudConsent();});
        api.setTag("food_source_api");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(20);box.addView(api,lp);
        choice=PaperComponents.dialogBuilder(activity,host.night()).setTitle(firstUse?"配置餐食识别":"餐食识别设置")
                .setView(box).setNegativeButton("稍后设置",(d,w)->host.rememberChoice()).create();
        choice.setOnCancelListener(d->host.rememberChoice());show(choice);
    }
    private void cloudConsent(){
        if(!visible()||consent!=null&&consent.isShowing())return;
        AlertDialog dialog=PaperComponents.dialogBuilder(activity,host.night()).setTitle("开启营养估算？")
                .setMessage("仅向你自己配置的 HTTPS 视觉 API 发送餐图，服务商可能收费。应用不提供公共密钥。AI 菜名、份量和营养仅供参考；新照片仍需你点确定才加入记录。")
                .setNegativeButton("取消",null).setPositiveButton("同意并配置",(d,w)->{
                    host.rememberChoice();
                    if(FoodAnalysisJobService.apiConfigured(host.profile())){host.chooseSource("api",true);host.refresh();}
                    else host.configureApi(()->{if(visible()&&FoodAnalysisJobService.apiConfigured(host.profile())){host.chooseSource("api",true);host.refresh();}});
                }).create();consent=dialog;show(dialog);
    }
    void dispose(){disposed=true;if(choice!=null)choice.dismiss();if(consent!=null)consent.dismiss();}
}
