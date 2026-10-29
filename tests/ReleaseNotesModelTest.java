package com.eatrecord.app;

import org.json.JSONObject;
import java.io.File;
import java.util.HashSet;
import javax.xml.parsers.DocumentBuilderFactory;

/** Local presentation/version checks; never contacts an API or rewrites a request model ID. */
public final class ReleaseNotesModelTest {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    public static void main(String[] args)throws Exception{
        String version=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(args[0]))
            .getDocumentElement().getAttribute("android:versionName");
        check(version.equals(ReleaseNotes.latestVersion()),"Manifest version must match latest local release notes");
        check(ReleaseNotes.contains(version),"installed version must have its own announcement");
        HashSet<String> betas=new HashSet<>();boolean unique=true;
        // Historical restart/final notes intentionally share a 0.5 version; preserve them.
        for(String listed:ReleaseNotes.versions())if(listed.startsWith("0.6."))unique&=betas.add(listed);
        check(unique,"duplicate beta versions must not shadow release notes");
        for(int beta=8;beta<=22;beta++)check(ReleaseNotes.contains("0.6.0-beta."+beta),"missing recent beta notes: "+beta);
        check(ReleaseNotes.currentText(version).startsWith(version+"\n"),"announcement starts with exact installed version");
        check(!ReleaseNotes.currentText(version).contains("0.6.0-beta.7"),"latest announcement does not substitute beta7");
        check(ReleaseNotes.recentText(version,100).startsWith(version+"\n"),"recent log starts at installed version");
        check(ReleaseNotes.recentText("0.6.0-beta.17",1).startsWith("0.6.0-beta.17\n"),"older installed version does not get future release notes");
        check(ReleaseNotes.currentText("0.6.0-beta.999").contains("暂未收录"),"unknown version uses honest missing-notes fallback");
        check(ReleaseNotes.recentText(version,0).isEmpty(),"zero recent limit is empty");
        String endpoint="https://api.deepseek.com/chat/completions",id="deepseek-flash";
        JSONObject names=new JSONObject().put(id,"DeepSeek-V4.1-Flash");
        JSONObject profile=new JSONObject().put("apiModelDisplayNames",names).put("apiModelDisplayNamesEndpoint",endpoint);
        String choice=ApiModelPresentation.choice(id,ApiModelPresentation.names(profile,endpoint),ApiVisionPolicy.IMAGE);
        check(choice.contains("DeepSeek-V4.1-Flash")&&choice.contains("API ID: deepseek-flash"),"display name and request ID are both visible");
        check(choice.contains("服务商声明")&&choice.contains("未实测"),"model list metadata is not advertised as a live vision test");
        check(ApiModelPresentation.summary(profile,endpoint,id).contains("DeepSeek-V4.1-Flash"),"settings summary uses provider name");
        check(ApiModelPresentation.names(profile,"https://other.invalid/chat/completions")==null,"display names are scoped to provider endpoint");
        check(ApiModelPresentation.selection(id,null).contains("待刷新"),"old id-only cache does not fabricate a versioned name");
        check(ApiModelPresentation.selection("custom-id",names).contains("custom-id"),"manual model IDs remain available");
        check(id.equals(profile.getJSONObject("apiModelDisplayNames").keys().next()),"presentation never mutates the API model ID");
        check("清楚名称".equals(ApiModelPresentation.metadataName(new JSONObject().put("name"," 清楚\n名称\u0001 "))),"provider display names remove control characters");
        check(ApiModelPresentation.metadataName(new JSONObject().put("name",123)).isEmpty(),"non-string display metadata is not accepted");
        System.out.println("PASS: "+checks+" release-note and model-presentation checks");
    }
}
