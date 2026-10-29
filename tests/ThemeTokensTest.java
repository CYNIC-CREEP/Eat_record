package com.eatrecord.app;
import java.util.HashSet;
public final class ThemeTokensTest {
    private static int checks;
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private static double luminance(int value){double total=0;double[] weights={.2126,.7152,.0722};for(int i=0;i<3;i++){double channel=((value>>>(16-i*8))&255)/255d;total+=weights[i]*(channel<=.04045?channel/12.92:Math.pow((channel+.055)/1.055,2.4));}return total;}
    private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    public static void main(String[] args){
        UiTokens.configure("yellow");check(UiTokens.paper(false)==0xfffaf7f0&&UiTokens.card(false)==0xfffffdf7&&UiTokens.ink(false)==0xff573f2f&&UiTokens.line(false)==0xffe7ddcd,"yellow light colors unchanged");
        check(UiTokens.paper(true)==0xff1e1b19&&UiTokens.card(true)==0xff2b2622&&UiTokens.ink(true)==0xfff0e4d1&&UiTokens.action(true)==0xffefdfc3,"yellow dark colors unchanged");
        for(boolean night:new boolean[]{false,true}){
            HashSet<Integer> papers=new HashSet<>(),cards=new HashSet<>(),inks=new HashSet<>(),actions=new HashSet<>();
            for(String mode:new String[]{"yellow","green","red","blue","night"}){UiTokens.configure(mode);papers.add(UiTokens.paper(night));cards.add(UiTokens.card(night));inks.add(UiTokens.ink(night));actions.add(UiTokens.action(night));check(contrast(UiTokens.ink(night),UiTokens.paper(night))>=4.5,"paper text contrast");check(contrast(UiTokens.ink(night),UiTokens.card(night))>=4.5,"card text contrast");check(contrast(night?UiTokens.paper(true):0xffffffff,UiTokens.action(night))>=4.5,"action label contrast");}
            check(papers.size()==5&&cards.size()==5&&inks.size()==5&&actions.size()==5,"global theme colors differ beyond nav");
        }
        UiTokens.configure("yellow");System.out.println("PASS: "+checks+" theme palette/baseline/contrast checks");
    }
}
