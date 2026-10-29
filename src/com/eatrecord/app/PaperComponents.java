package com.eatrecord.app;

import android.content.Context;
import android.app.AlertDialog;
import android.view.ContextThemeWrapper;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.database.DataSetObserver;
import android.widget.*;

public final class PaperComponents {
    public static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static TextView text(Context c,String value,float size,int color,boolean bold){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(AppTypography.sp(c,size));t.setTextColor(color);
        t.setTypeface(AppTypography.typeface(c,bold));
        t.setIncludeFontPadding(true);t.setLineSpacing(dp(c,2),1.04f);return t;
    }
    public static GradientDrawable rounded(Context c,boolean night){
        GradientDrawable d=new GradientDrawable();d.setColor(UiTokens.card(night));d.setCornerRadius(dp(c,UiTokens.CARD_RADIUS));
        d.setStroke(dp(c,1),UiTokens.line(night));return d;
    }
    public static LinearLayout card(Context c,boolean night){
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);
        int p=dp(c,20);box.setPadding(p,p,p,p);box.setBackground(rounded(c,night));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(c,14);box.setLayoutParams(lp);return box;
    }
    public static TextView button(Context c,String title,boolean night,View.OnClickListener click){
        TextView b=text(c,title,15,night?UiTokens.paper(true):Color.WHITE,true);b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c,48));b.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));
        GradientDrawable d=new GradientDrawable();d.setColor(UiTokens.action(night));d.setCornerRadius(dp(c,UiTokens.CONTROL_RADIUS));b.setBackground(d);
        b.setOnClickListener(click);b.setContentDescription(title);return b;
    }
    public static EditText input(Context c,boolean night) {
        EditText input=new EditText(c);input.setMinHeight(dp(c,48));input.setTextSize(AppTypography.sp(c,16));
        input.setTypeface(AppTypography.typeface(c,false));
        input.setTextColor(UiTokens.ink(night));input.setHintTextColor(UiTokens.secondary(night));
        input.setPadding(dp(c,14),dp(c,8),dp(c,14),dp(c,8));
        GradientDrawable background=new GradientDrawable();background.setColor(UiTokens.paper(night));
        background.setCornerRadius(dp(c,UiTokens.CONTROL_RADIUS));background.setStroke(dp(c,1),UiTokens.line(night));input.setBackground(background);
        return input;
    }
    public static AlertDialog.Builder dialogBuilder(Context context, final boolean night) {
        final Context theme=new ContextThemeWrapper(context,night?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert);
        theme.getTheme().applyStyle(R.style.AppHandwriting,true);
        return new AlertDialog.Builder(theme) {
            @Override public AlertDialog show() {AlertDialog dialog=super.show();styleDialog(dialog,night);return dialog;}
        };
    }
    public static void styleDialog(AlertDialog dialog,boolean night) {
        if(dialog.getWindow()==null)return;
        Context c=dialog.getContext();GradientDrawable background=new GradientDrawable();
        background.setColor(UiTokens.card(night));background.setCornerRadius(dp(c,UiTokens.DIALOG_RADIUS));
        background.setStroke(dp(c,1),UiTokens.line(night));
        dialog.getWindow().setBackgroundDrawable(background);
        dialog.getWindow().getDecorView().setClipToOutline(true);
        sizeDialog(dialog);
        applyDialogTypography(dialog.getWindow().getDecorView());
        TextView message=dialog.findViewById(android.R.id.message);
        if(message!=null){message.setTextSize(AppTypography.sp(c,16));message.setTextColor(UiTokens.ink(night));message.setLineSpacing(dp(c,3),1.08f);}
        int titleId=c.getResources().getIdentifier("alertTitle","id","android");
        TextView title=titleId==0?null:dialog.findViewById(titleId);
        if(title!=null){title.setTextSize(AppTypography.sp(c,20));title.setTextColor(UiTokens.ink(night));}
        for(int id:new int[]{-1,-2,-3})if(dialog.getButton(id)!=null){
            Button button=dialog.getButton(id);button.setMinHeight(dp(c,48));button.setTextColor(UiTokens.ink(night));
            button.setTextSize(AppTypography.sp(c,15));button.setAllCaps(false);
        }
        // AlertController creates/recycles setItems rows after show(). Styling only the
        // decor once leaves those rows in the system face (notably the privacy menu).
        ListView list=dialog.getListView();
        if(list!=null&&list.getAdapter()!=null&&!(list.getAdapter() instanceof DialogListAdapter)){
            int selected=list.getCheckedItemPosition();
            android.util.SparseBooleanArray checked=list.getCheckedItemPositions();
            checked=checked==null?null:checked.clone();
            android.os.Parcelable listState=list.onSaveInstanceState();
            list.setAdapter(new DialogListAdapter(list.getAdapter(),night));
            if(checked!=null)for(int i=0;i<checked.size();i++)list.setItemChecked(checked.keyAt(i),checked.valueAt(i));
            else if(selected>=0)list.setItemChecked(selected,true);
            if(listState!=null)list.onRestoreInstanceState(listState);
        }
    }

    /** Shared by native AlertDialogs and the older custom-paper dialogs, never full-screen tools. */
    public static void sizeDialog(AlertDialog dialog){
        Window window=dialog.getWindow();if(window==null)return;
        window.setLayout(dialogWidth(dialog.getContext()),ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                |WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    public static int dialogWidth(Context context){
        int width=availableDialogBounds(context).width();
        return Math.max(1,Math.min(dp(context,560),width-dp(context,48)));
    }
    private static Rect availableDialogBounds(Context context){
        Context owner=context;
        while(owner instanceof android.content.ContextWrapper&&!(owner instanceof android.app.Activity)){
            Context base=((android.content.ContextWrapper)owner).getBaseContext();if(base==owner)break;owner=base;
        }
        Rect bounds=new Rect(0,0,context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels);
        if(owner instanceof android.app.Activity){
            android.app.Activity activity=(android.app.Activity)owner;
            if(android.os.Build.VERSION.SDK_INT>=30){
                android.view.WindowMetrics metrics=activity.getWindowManager().getCurrentWindowMetrics();
                bounds.set(metrics.getBounds());
                android.graphics.Insets safe=metrics.getWindowInsets().getInsetsIgnoringVisibility(
                        android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
                bounds.left+=safe.left;bounds.top+=safe.top;bounds.right-=safe.right;bounds.bottom-=safe.bottom;
            }else{
                Rect visible=new Rect();activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
                if(visible.width()>0&&visible.height()>0)bounds.set(visible);
            }
        }
        return bounds;
    }
    public static void applyDialogTypography(View view){
        if(view==null||"dialog_font_preview".equals(view.getTag()))return;
        if(view instanceof TextView)AppTypography.apply(view);
        else if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)applyDialogTypography(group.getChildAt(i));
        }
    }
    /** Keep long custom forms scrollable when large fonts or the keyboard reduce their viewport. */
    public static View dialogContent(Context context,View content){
        if(content instanceof ScrollView){
            DialogHeightFrame frame=new DialogHeightFrame(context);
            frame.addView(content,new FrameLayout.LayoutParams(-1,-2));return frame;
        }
        DialogScroll scroll=new DialogScroll(context);scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(false);scroll.setClipToOutline(true);
        scroll.setBackground(content.getBackground());content.setBackground(null);
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));return scroll;
    }
    private static int boundedDialogHeight(Context c,int spec){
        int limit=Math.max(dp(c,96),availableDialogBounds(c).height()-dp(c,48));
        int mode=View.MeasureSpec.getMode(spec);
        if(mode!=View.MeasureSpec.UNSPECIFIED)limit=Math.min(limit,View.MeasureSpec.getSize(spec));
        return View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST);
    }
    private static final class DialogScroll extends ScrollView{
        DialogScroll(Context c){super(c);}
        @Override protected void onMeasure(int width,int height){super.onMeasure(width,boundedDialogHeight(getContext(),height));}
    }
    private static final class DialogHeightFrame extends FrameLayout{
        DialogHeightFrame(Context c){super(c);}
        @Override protected void onMeasure(int width,int height){super.onMeasure(width,boundedDialogHeight(getContext(),height));}
    }
    private static final class DialogListAdapter implements WrapperListAdapter{
        private final ListAdapter delegate;private final boolean night;
        DialogListAdapter(ListAdapter delegate,boolean night){this.delegate=delegate;this.night=night;}
        @Override public ListAdapter getWrappedAdapter(){return delegate;}
        @Override public boolean areAllItemsEnabled(){return delegate.areAllItemsEnabled();}
        @Override public boolean isEnabled(int position){return delegate.isEnabled(position);}
        @Override public void registerDataSetObserver(DataSetObserver observer){delegate.registerDataSetObserver(observer);}
        @Override public void unregisterDataSetObserver(DataSetObserver observer){delegate.unregisterDataSetObserver(observer);}
        @Override public int getCount(){return delegate.getCount();}
        @Override public Object getItem(int position){return delegate.getItem(position);}
        @Override public long getItemId(int position){return delegate.getItemId(position);}
        @Override public boolean hasStableIds(){return delegate.hasStableIds();}
        @Override public int getItemViewType(int position){return delegate.getItemViewType(position);}
        @Override public int getViewTypeCount(){return delegate.getViewTypeCount();}
        @Override public boolean isEmpty(){return delegate.isEmpty();}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            View view=delegate.getView(position,recycled,parent);applyDialogTypography(view);
            if(view instanceof TextView){
                TextView text=(TextView)view;Context c=text.getContext();
                text.setTextColor(UiTokens.ink(night));text.setTextSize(AppTypography.sp(c,17));
                text.setSingleLine(false);text.setMaxLines(Integer.MAX_VALUE);
                text.setMinHeight(dp(c,56));text.setPadding(dp(c,24),dp(c,12),dp(c,24),dp(c,12));
                text.setLineSpacing(dp(c,2),1.05f);
                ViewGroup.LayoutParams lp=view.getLayoutParams();if(lp!=null){lp.height=ViewGroup.LayoutParams.WRAP_CONTENT;view.setLayoutParams(lp);}
            }
            return view;
        }
    }
    public static final class Paper extends Drawable {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final boolean night;private final float density;private final int plainTop;
        public Paper(Context c,boolean n){this(c,n,0);}
        public Paper(Context c,boolean n,int plainTop){night=n;density=c.getResources().getDisplayMetrics().density;this.plainTop=Math.max(0,plainTop);}
        @Override public void draw(Canvas c){
            Rect b=getBounds();c.drawColor(UiTokens.paper(night));
            p.setColor(night?Color.argb(24,177,153,124):Color.argb(38,181,155,120));p.setStrokeWidth(density*.5f);
            for(float y=b.top+28*density;y<b.bottom;y+=32*density)if(y>=b.top+plainTop)c.drawLine(b.left+18*density,y,b.right-18*density,y,p);
            p.setColor(night?Color.argb(15,220,200,170):Color.argb(17,144,113,77));
            for(int y=b.top;y<b.bottom;y+=17)if(y>=b.top+plainTop)for(int x=b.left+(y%19);x<b.right;x+=23)c.drawPoint(x,y,p);
        }
        @Override public void setAlpha(int alpha){} @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}
