package com.eatrecord.app;

import android.graphics.Color;

public final class UiTokens {
    private UiTokens() { }
    private static volatile String theme="yellow";
    public static void configure(String value){theme=value==null?"yellow":value;}
    private static int palette(int yellow,int green,int red,int blue,int gray){return "green".equals(theme)?green:"red".equals(theme)?red:"blue".equals(theme)?blue:"night".equals(theme)?gray:yellow;}
    public static final int CARD_RADIUS = 36;
    public static final int DIALOG_RADIUS = 32;
    public static final int CONTROL_RADIUS = 26;
    public static final int CARD_GAP = 16;
    public static final int SECTION_GAP = 28;
    public static final int MEAL_TITLE_SP = 18;
    public static final int MEAL_BODY_SP = 14;
    public static final int HEADER_CORE = 96;
    public static final int HEADER_FADE = 28;
    public static final int WEEK_SNAP_MS = 180;
    public static final int PAGE_SWAP_MS = 300;
    public static final int PAGE_SHIFT_DP = 16;
    public static final int SOFT_PAGE_SWAP_MS = 360;
    public static final int PAGE_FLOW_MS = 480;
    public static final int SOFT_PAGE_SHIFT_DP = 16;
    public static final int BOOK_OPEN_MS = 1000;
    public static final int BOOK_CLOSE_MS = 800;
    public static final int NOTEBOOK_RADIUS_DP = 24;
    public static final int BOOK_COVER_RADIUS_DP = 18;
    public static final float GESTURE_RESPONSE_SECONDS = .4f;
    public static final float NAV_GESTURE_RESPONSE_SECONDS = .3f;
    public static final float NAV_TAP_RESPONSE_SECONDS = .48f;
    public static android.animation.TimeInterpolator motionEaseOut() { return new android.view.animation.PathInterpolator(.23f,1f,.32f,1f); }
    public static android.animation.TimeInterpolator motionEasePage() { return new android.view.animation.PathInterpolator(.25f,.1f,.25f,1f); }
    public static android.animation.TimeInterpolator motionEaseSoft() { return new android.view.animation.PathInterpolator(.32f,.72f,0f,1f); }
    public static int paper(boolean night) { return night?palette(0xff1e1b19,0xff17221c,0xff281b1d,0xff17212b,0xff1d2026):palette(0xfffaf7f0,0xffeff6ee,0xfffff1ee,0xffedf5fb,0xfff0f1f4); }
    public static int card(boolean night) { return night?palette(0xff2b2622,0xff25362b,0xff3a272b,0xff253544,0xff2c3038):palette(0xfffffdf7,0xfff8fff6,0xfffffaf7,0xfff7fcff,0xfffafbfc); }
    public static int ink(boolean night) { return night?palette(0xfff0e4d1,0xffe2efdf,0xffffe6df,0xffe2eef9,0xffe8eaf0):palette(0xff573f2f,0xff304f3a,0xff673b3b,0xff304e67,0xff404653); }
    public static int secondary(boolean night) { return night?palette(0xffbaa993,0xffacc5ad,0xffd2aba7,0xffa9c0d5,0xffb5bbc8):palette(0xff85705b,0xff6c8570,0xffa07872,0xff6c849a,0xff7c8290); }
    public static int line(boolean night) { return night?palette(0xff4d4238,0xff415b47,0xff644349,0xff40586c,0xff4b525f):palette(0xffe7ddcd,0xffccdfc9,0xffecd0c9,0xffcbdfea,0xffd7dce5); }
    public static int yellow() { return Color.rgb(232,181,55); }
    public static int action(boolean night) { return night?palette(0xffefdfc3,0xffbddbbf,0xfff1c1b7,0xffbcdcf1,0xffd3d9e6):palette(0xff2b231d,0xff345f40,0xff85483f,0xff345f80,0xff485368); }
}
