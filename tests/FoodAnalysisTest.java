package com.eatrecord.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** JVM contract/compatibility checks; no network or credentials. */
public final class FoodAnalysisTest {
    private static int checks;
    private static String fixture() {
        return "{\"schema_version\":1,\"dish_name\":\"牛肉蛋炒饭\",\"items\":[{\"name\":\"炒饭\",\"estimated_grams\":450,\"calories_kcal\":676,\"carbs_g\":88,\"protein_g\":24,\"fat_g\":24,\"fiber_g\":3,\"sodium_mg\":1131,\"confidence\":0.76}],\"totals\":{\"calories_kcal\":676,\"carbs_g\":88,\"protein_g\":24,\"fat_g\":24,\"fiber_g\":3,\"sodium_mg\":1131},\"calorie_range\":[520,820],\"assumptions\":[\"约450g\"],\"confidence\":0.76}";
    }
    private static void check(boolean value,String name) { if(!value)throw new AssertionError(name);checks++; }
    private static void reject(String value,String name) throws Exception {
        boolean rejected=false;try {FoodAnalysisResult.parse(value);}catch(org.json.JSONException e){rejected=true;}
        check(rejected,name);
    }
    private static String event(String delta) throws Exception {
        return "data: "+new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("content",delta))))+"\n\n";
    }
    private static FoodAnalysisResult events(String text,FoodAnalysisClient.NameListener names)throws Exception {
        return events(new java.io.ByteArrayInputStream(text.getBytes("UTF-8")),names);
    }
    private static FoodAnalysisResult events(java.io.InputStream source,FoodAnalysisClient.NameListener names)throws Exception {
        java.lang.reflect.Method parser=FoodAnalysisClient.class.getDeclaredMethod("readEvents",java.io.InputStream.class,FoodAnalysisClient.NameListener.class);
        parser.setAccessible(true);
        try{return (FoodAnalysisResult)parser.invoke(null,source,names);}
        catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw e;}
    }
    private static FoodAnalysisResult request(JSONObject options,JSONObject[] captured,int code,String error)throws Exception {
        java.net.URL url=new java.net.URL(null,"https://food.test.invalid/v1/chat/completions",new java.net.URLStreamHandler(){
            @Override protected java.net.URLConnection openConnection(java.net.URL target){return new java.net.HttpURLConnection(target){
                final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                @Override public void connect(){}@Override public void disconnect(){}@Override public boolean usingProxy(){return false;}
                @Override public java.io.OutputStream getOutputStream(){return bytes;}
                @Override public int getResponseCode()throws java.io.IOException{try{captured[0]=new JSONObject(bytes.toString("UTF-8"));return code;}catch(Exception failure){throw new java.io.IOException(failure);}}
                @Override public java.io.InputStream getErrorStream(){try{return new java.io.ByteArrayInputStream(error.getBytes("UTF-8"));}catch(Exception impossible){return null;}}
                @Override public java.io.InputStream getInputStream()throws java.io.IOException{try{return new java.io.ByteArrayInputStream(new JSONObject().put("model","served-test-model").put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",fixture())))).toString().getBytes("UTF-8"));}catch(Exception failure){throw new java.io.IOException(failure);}}
            };}
        });
        java.lang.reflect.Method method=FoodAnalysisClient.class.getDeclaredMethod("request",java.net.URL.class,String.class,String.class,String.class,boolean.class,boolean.class,FoodAnalysisClient.NameListener.class,String.class,String.class,java.util.function.BooleanSupplier.class,JSONObject.class);method.setAccessible(true);
        try{return (FoodAnalysisResult)method.invoke(new FoodAnalysisClient(),url,"not-a-real-key","requested-test-model","data:image/jpeg;base64,test",false,true,null,"","",(java.util.function.BooleanSupplier)()->true,options);}
        catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw e;}
    }
    private static final class CompatibleTransport extends java.net.URLStreamHandler {
        final java.util.List<JSONObject> requests = new java.util.ArrayList<>();
        int forcedCode;
        String forcedError = "";
        boolean acceptAll, rejectSchemaEnum, rejectTokenLimitOnly;
        @Override protected java.net.URLConnection openConnection(java.net.URL target) {
            return new java.net.HttpURLConnection(target) {
                final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                String error = "";
                @Override public void connect() { }
                @Override public void disconnect() { }
                @Override public boolean usingProxy() { return false; }
                @Override public java.io.OutputStream getOutputStream() { return bytes; }
                @Override public int getResponseCode() throws java.io.IOException {
                    try {
                        JSONObject body = new JSONObject(bytes.toString("UTF-8")); requests.add(body);
                        if (forcedCode != 0) { error = forcedError; return forcedCode; }
                        if(rejectTokenLimitOnly){error=body.has("max_tokens")?"unsupported max_tokens":"";return error.isEmpty()?200:400;}
                        if(acceptAll)return 200;
                        if(rejectSchemaEnum){error="json_schema".equals(body.optJSONObject("response_format")==null?"":body.getJSONObject("response_format").optString("type"))
                            ? "{\"error\":{\"message\":\"unknown variant json_schema, expected text or json_object\",\"param\":\"response_format.type\"}}" : "";return error.isEmpty()?200:400;}
                        if (body.has("max_tokens")) error = "unsupported max_tokens";
                        else if (body.optBoolean("stream")) error = "unsupported stream";
                        else if (body.has("response_format")) error = "unsupported response_format json_schema";
                        return error.isEmpty() ? 200 : 400;
                    } catch (Exception failure) { throw new java.io.IOException(failure); }
                }
                @Override public java.io.InputStream getErrorStream() {
                    try { return new java.io.ByteArrayInputStream(error.getBytes("UTF-8")); }
                    catch (Exception impossible) { return null; }
                }
                @Override public java.io.InputStream getInputStream() throws java.io.IOException {
                    try { return new java.io.ByteArrayInputStream(new JSONObject().put("model", "served-compatible-model")
                            .put("choices", new JSONArray().put(new JSONObject().put("message",
                                    new JSONObject().put("content", fixture())))).toString().getBytes("UTF-8")); }
                    catch (Exception failure) { throw new java.io.IOException(failure); }
                }
            };
        }
    }
    private static FoodAnalysisResult prepared(JSONObject profile, java.net.URL url,
            java.util.function.BooleanSupplier valid) throws Exception {
        java.lang.reflect.Method method = FoodAnalysisClient.class.getDeclaredMethod("analyzePrepared", JSONObject.class,
                java.net.URL.class, String.class, FoodAnalysisClient.NameListener.class, String.class, String.class,
                java.util.function.BooleanSupplier.class);
        method.setAccessible(true);
        try { return (FoodAnalysisResult) method.invoke(new FoodAnalysisClient(), profile, url,
                "data:image/jpeg;base64,same-encoded-photo", (FoodAnalysisClient.NameListener) name -> { }, "", "", valid); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception) throw (Exception) failure.getCause();
            throw failure;
        }
    }
    private static void deepSeekProtocol() throws Exception {
        CompatibleTransport official=new CompatibleTransport();official.acceptAll=true;
        java.net.URL url=new java.net.URL(null,"https://api.deepseek.com/chat/completions",official);
        JSONObject profile=new JSONObject().put("apiKey","fixture-not-a-secret").put("apiModel","deepseek-flash")
            .put("apiModelMode","fast").put("foodJsonSchema",true);
        FoodAnalysisResult value=prepared(profile,url,()->true);JSONObject sent=official.requests.get(0);
        check(official.requests.size()==1,"official DeepSeek avoids a known incompatible format roundtrip");
        check("json_object".equals(sent.getJSONObject("response_format").getString("type")),"DeepSeek strict compatibility uses documented JSON Output");
        check(!sent.getJSONObject("response_format").has("json_schema"),"unsupported json_schema is never sent to official DeepSeek");
        check("deepseek-flash".equals(sent.getString("model")),"DeepSeek model API ID is not rewritten into a marketing display name");
        check("disabled".equals(sent.getJSONObject("thinking").getString("type")),"selected fast mode explicitly disables DeepSeek thinking");
        check(value.value.getJSONObject("nutrients").getInt("caloriesKcal")==676&&value.value.getInt("calorieHigh")==820,"JSON Output retains full nutrition/range validation");
        prepared(new JSONObject(profile.toString()).put("foodJsonSchema",false),url,()->true);
        check("json_object".equals(official.requests.get(1).getJSONObject("response_format").getString("type")),"first-use default also requests JSON without enabling strict Schema");
        check("enabled".equals(FoodAnalysisClient.requestOptions(url,new JSONObject(profile.toString()).put("apiModelMode","thinking")).getJSONObject("thinking").getString("type")),"thinking mode is explicitly preserved when selected");
        check(!FoodAnalysisClient.requestOptions(new java.net.URL("https://api.deepseek.com.evil.invalid/chat/completions"),profile).has("thinking"),"DeepSeek private mode options never leak to a lookalike host");
        check(!FoodAnalysisClient.officialDeepSeek(new java.net.URL("http://api.deepseek.com/chat/completions")),"insecure DeepSeek host is not trusted for protocol adaptation");
        check(!FoodAnalysisClient.officialDeepSeek(new java.net.URL("https://api.deepseek.com:9443/chat/completions")),"nonstandard port does not inherit official DeepSeek protocol");
        CompatibleTransport capOnly=new CompatibleTransport();capOnly.rejectTokenLimitOnly=true;
        java.net.URL capUrl=new java.net.URL(null,"https://api.deepseek.com/chat/completions",capOnly);
        JSONObject capProfile=new JSONObject(profile.toString()).put("apiKey","cap-compatibility-fixture");
        prepared(capProfile,capUrl,()->true);
        check(capOnly.requests.size()==2&&!capOnly.requests.get(1).has("max_tokens"),"explicit token-cap refusal retries only that optional cap");
        check("disabled".equals(capOnly.requests.get(0).getJSONObject("thinking").optString("type"))
                &&"disabled".equals(capOnly.requests.get(1).getJSONObject("thinking").optString("type")),"token-cap fallback never silently re-enables DeepSeek default thinking");
        check(capOnly.requests.get(0).getJSONArray("messages").toString().equals(capOnly.requests.get(1).getJSONArray("messages").toString()),"mode-safe fallback retains the exact photo and full nutrition prompt");
        prepared(capProfile,capUrl,()->true);
        check(capOnly.requests.size()==3&&!capOnly.requests.get(2).has("max_tokens")
                &&"disabled".equals(capOnly.requests.get(2).getJSONObject("thinking").optString("type")),"cached token-cap refusal also retains selected fast mode on later photos");
        prepared(new JSONObject(capProfile.toString()).put("apiModelMode","thinking"),capUrl,()->true);
        check(capOnly.requests.size()==5&&"enabled".equals(capOnly.requests.get(4).getJSONObject("thinking").optString("type")),"explicit selected thinking mode survives its own token-cap negotiation");
        CompatibleTransport modeRefused=new CompatibleTransport();modeRefused.forcedCode=400;modeRefused.forcedError="unsupported thinking and max_tokens private-error-evidence";
        java.net.URL modeUrl=new java.net.URL(null,"https://api.deepseek.com/chat/completions",modeRefused);
        boolean modeFailure=false;try{prepared(new JSONObject(profile.toString()).put("apiKey","mode-refused-fixture"),modeUrl,()->true);}
        catch(FoodAnalysisClient.Failure expected){modeFailure=!expected.retryable&&expected.getMessage().contains("未自动切换模式")&&!expected.getMessage().contains("private-error-evidence");}
        check(modeFailure&&modeRefused.requests.size()==1,"refused explicit mode fails clearly without switching mode or issuing another inference");
        CompatibleTransport qwenCap=new CompatibleTransport();qwenCap.rejectTokenLimitOnly=true;
        java.net.URL qwenUrl=new java.net.URL(null,"https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",qwenCap);
        JSONObject qwenProfile=new JSONObject(profile.toString()).put("apiModel","qwen3-vl-plus").put("apiKey","qwen-cap-fixture");
        prepared(qwenProfile,qwenUrl,()->true);
        check(qwenCap.requests.size()==2&&!qwenCap.requests.get(1).has("max_tokens")&&!qwenCap.requests.get(1).getBoolean("enable_thinking"),"Qwen fast switch is retained independently of optional output cap");
        qwenCap.forcedCode=422;qwenCap.forcedError="unsupported enable_thinking";
        int qwenBefore=qwenCap.requests.size();boolean qwenRefusal=false;
        try{prepared(qwenProfile,qwenUrl,()->true);}catch(FoodAnalysisClient.Failure expected){qwenRefusal=!expected.retryable&&expected.getMessage().contains("未自动切换模式");}
        check(qwenRefusal&&qwenCap.requests.size()==qwenBefore+1,"known hybrid fast mode is never silently disabled after its explicit refusal");
        CompatibleTransport enumTransport=new CompatibleTransport();enumTransport.rejectSchemaEnum=true;
        java.net.URL enumUrl=new java.net.URL(null,"https://enum-format.test.invalid/v1/chat/completions",enumTransport);
        prepared(profile,enumUrl,()->true);
        check(enumTransport.requests.size()==2&&!enumTransport.requests.get(1).has("response_format"),"explicit response_format enum refusal retries only the optional format");
        check(enumTransport.requests.get(0).getJSONArray("messages").toString().equals(enumTransport.requests.get(1).getJSONArray("messages").toString()),"enum fallback retains exact photo and food prompt");
        prepared(profile,enumUrl,()->true);check(enumTransport.requests.size()==3,"enum refusal is cached for subsequent images without repeated errors");
        CompatibleTransport errors=new CompatibleTransport();errors.forcedCode=400;errors.forcedError="{\"error\":{\"message\":\"Invalid image data with echoed private-details\",\"param\":\"messages[0].content[1]\"}}";
        java.net.URL bad=new java.net.URL(null,"https://input-errors.test.invalid/chat/completions",errors);
        String message="";try{prepared(profile,bad,()->true);}catch(FoodAnalysisClient.Failure e){message=e.getMessage();}
        check(errors.requests.size()==1&&message.contains("HTTP 400")&&message.contains("图片格式"),"invalid photo is diagnosed without optional retries or model blame");
        check(!message.contains("private-details")&&!message.contains("fixture-not-a-secret"),"raw error evidence never enters the displayed/persisted failure");
        errors.forcedError="{\"error\":{\"message\":\"Invalid schema: required property is missing\",\"param\":\"response_format\"}}";
        try{prepared(profile,bad,()->true);}catch(FoodAnalysisClient.Failure e){message=e.getMessage();}
        check(errors.requests.size()==2&&message.contains("HTTP 400")&&!message.startsWith("接口明确拒绝图片"),"malformed schema is not treated as unsupported vision or silently retried");
        errors.forcedError="The model does not support image input";
        try{prepared(profile,bad,()->true);}catch(FoodAnalysisClient.Failure e){message=e.getMessage();}
        check(message.contains("明确拒绝图片输入"),"only explicit provider evidence reports an image capability rejection");
        errors.forcedError="{\"error\":{\"message\":\"model_not_found\"}}";
        try{prepared(profile,bad,()->true);}catch(FoodAnalysisClient.Failure e){message=e.getMessage();}
        check(message.contains("模型调用标识无效"),"invalid model API ID is distinguished from image capability");
    }
    public static void main(String[] args) throws Exception {
        deepSeekProtocol();
        check(FoodAnalysisResult.validationMessage(new org.json.JSONException("食物明细与营养总量不一致")).contains("不需要重新配置"),"invalid model estimates are not mislabeled as lost API configuration");
        check(!FoodAnalysisResult.validationMessage(new org.json.JSONException("private provider details")).contains("private"),"validation feedback never leaks raw model output");
        JSONObject value=FoodAnalysisResult.parse(fixture()).value;
        check(value.getString("dishName").equals("牛肉蛋炒饭"),"Chinese name preserved");
        check(value.getJSONObject("nutrients").getInt("caloriesKcal")==676,"calorie total");
        check(value.getJSONObject("nutrients").getInt("sodiumMg")==1131,"sodium remains milligrams rather than salt grams");
        check(value.getInt("calorieLow")==520&&value.getInt("calorieHigh")==820,"uncertainty range retained");
        check(value.getJSONArray("assumptions").length()==1,"estimate assumptions retained");
        check(FoodAnalysisResult.parse(fixture().replace("\"confidence\":0.76","\"confidence\":0.4")).value.getDouble("confidence")==.4,"low confidence is retained for review instead of fabricated certainty");
        check("炒饭".equals(FoodAnalysisClient.earlyDishName("{\"schema_version\":1,\"dish_name\":\"炒饭\",")),"complete streamed dish name can be shown before nutrients");
        check(FoodAnalysisClient.earlyDishName("{\"dish_name\":\"炒")==null,"incomplete streamed name is not displayed");
        check(FoodAnalysisClient.earlyDishName("{\"items\":[{\"dish_name\":\"不是主标题\"}],")==null,"nested streamed name cannot replace top-level dish title");
        check("正式菜名".equals(FoodAnalysisClient.earlyDishName("{\"items\":[{\"dish_name\":\"不是主标题\"}],\"dish_name\":\"正式菜名\",")),"name scanner skips nested components");
        check("炒\"饭".equals(FoodAnalysisClient.earlyDishName("{\"dish_name\":\"炒\\\"饭\",")),"escaped streamed name handled without truncation");
        check(FoodAnalysisClient.earlyDishName("{\"dish_name\":123,")==null,"non-string preliminary title rejected");
        final java.util.List<String> names=new java.util.ArrayList<String>();int split=fixture().indexOf(",\"items\"");
        FoodAnalysisResult streamed=events(event(fixture().substring(0,split))+event(fixture().substring(split))+"data: [DONE]\n\n",names::add);
        check(streamed.value.getJSONObject("nutrients").getInt("caloriesKcal")==676,"SSE fragments join into one validated nutrition result");
        check(names.size()==1&&"牛肉蛋炒饭".equals(names.get(0)),"streamed dish name emitted only once");
        JSONObject wire=new JSONObject(fixture());
        String totals=wire.getJSONObject("totals").toString();
        String ordered="{\"schema_version\":1,\"dish_name\":\"牛肉蛋炒饭\",\"totals\":"+totals+",";
        String tail="\"items\":"+wire.getJSONArray("items")+",\"calorie_range\":[520,820],\"assumptions\":[\"约450g\"],\"confidence\":0.76}";
        check(FoodAnalysisClient.earlyCalories(ordered)==676,"complete streamed top-level total can provide display-only calories");
        check(FoodAnalysisClient.earlyCalories(ordered.substring(0,ordered.length()-2))==null,"unfinished totals cannot appear as calories");
        check(FoodAnalysisClient.earlyCalories("{\"items\":[{\"totals\":"+totals+"}],")==null,"nested totals cannot become the meal preview");
        check(FoodAnalysisClient.earlyCalories(ordered.replace("\"calories_kcal\":676","\"calories_kcal\":-1"))==null,"negative streamed calories rejected");
        check(FoodAnalysisClient.earlyCalories(ordered.replace("\"calories_kcal\":676","\"calories_kcal\":\"676\""))==null,"string streamed calories rejected");
        check(FoodAnalysisClient.earlyCalories("{\"totals\":{\"calories_kcal\":676},")==null,"partial nutrient object is not a preview");
        final java.util.List<Double> previews=new java.util.ArrayList<>();
        FoodAnalysisClient.NameListener progressive=new FoodAnalysisClient.NameListener(){public void onDishName(String name){}public void onCalories(double n){previews.add(n);}};
        FoodAnalysisResult progressiveResult=events(event(ordered)+event(tail)+"data: [DONE]\n\n",progressive);
        check(previews.size()==1&&previews.get(0)==676&&progressiveResult.value.getJSONObject("nutrients").getInt("caloriesKcal")==676,"one SSE request yields name, provisional calories, then fully validated result");
        boolean rejectedPreview=false;previews.clear();
        try{events(event(ordered)+event(tail.replace("\"calories_kcal\":676","\"calories_kcal\":1500"))+"data: [DONE]\n\n",progressive);}catch(org.json.JSONException expected){rejectedPreview=true;}
        check(rejectedPreview&&previews.size()==1,"a provisional total does not bypass final component consistency validation");
        String finishEvent=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject()).put("finish_reason","stop"))).toString();
        final byte[] stopBytes=(event(fixture())+"data: "+finishEvent+"\n\n").getBytes("UTF-8");
        java.io.InputStream keepAlive=new java.io.InputStream(){int index;@Override public int read()throws java.io.IOException{if(index>=stopBytes.length)throw new java.io.IOException("A finished response must not wait for provider keepalive");return stopBytes[index++]&255;}@Override public int read(byte[] b,int off,int len)throws java.io.IOException{if(index>=stopBytes.length)throw new java.io.IOException("Read past finish frame");int count=Math.min(len,stopBytes.length-index);System.arraycopy(stopBytes,index,b,off,count);index+=count;return count;}};
        check(events(keepAlive,null).value.getInt("estimatedGrams")==450,"finish_reason stop returns without waiting for DONE or EOF on a keepalive stream");
        boolean lengthRejected=false;try{events(event(fixture())+"data: "+finishEvent.replace("stop","length")+"\n\n",null);}catch(FoodAnalysisClient.Failure expected){lengthRejected=!expected.retryable;}
        check(lengthRejected,"length-truncated output never becomes validated nutrition or automatic paid retry");
        JSONObject fast=new JSONObject().put("apiModel","qwen3-vl-plus").put("apiModelMode","fast");
        JSONObject fastOptions=FoodAnalysisClient.requestOptions(new java.net.URL("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),fast);
        check(fastOptions.has("enable_thinking")&&!fastOptions.getBoolean("enable_thinking")&&fastOptions.getInt("max_tokens")==4096,"known hybrid provider honors selected fast mode and bounds output");
        JSONObject thinking=FoodAnalysisClient.requestOptions(new java.net.URL("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),new JSONObject(fast.toString()).put("apiModelMode","thinking"));
        check(!thinking.has("enable_thinking")&&thinking.getInt("max_tokens")==8192,"thinking mode is not silently disabled");
        check(!FoodAnalysisClient.requestOptions(new java.net.URL("https://dashscope.aliyuncs.com.evil.invalid/v1"),fast).has("enable_thinking"),"private provider parameters use exact host matching");
        check(!FoodAnalysisClient.requestOptions(new java.net.URL("https://user-provider.invalid/v1"),fast).has("enable_thinking"),"unknown endpoint receives no unverified thinking parameter");
        JSONObject[] captured={null};
        FoodAnalysisResult jsonCompatibility=request(fastOptions,captured,200,"");
        check(captured[0].getInt("max_tokens")==4096&&!captured[0].getBoolean("enable_thinking")&&captured[0].getBoolean("stream"),"bounded fast request reaches fake HTTPS transport without extra model request");
        check("served-test-model".equals(jsonCompatibility.value.optString("providerModel")),"normal JSON returned to stream request still validates and preserves response model");
        boolean unsupportedOptions=false;try{request(fastOptions,captured,400,"unsupported max_tokens raw-provider-details");}catch(FoodAnalysisClient.Failure expected){unsupportedOptions="接口不支持输出长度参数".equals(expected.getMessage());}
        check(unsupportedOptions,"rejected optional token parameter selects sanitized compatibility fallback");
        boolean modeContextOnly=false;try{request(fastOptions,captured,400,"{\"error\":{\"message\":\"max_tokens is not supported in non-thinking mode\",\"param\":\"max_tokens\"}}");}
        catch(FoodAnalysisClient.Failure expected){modeContextOnly="接口不支持输出长度参数".equals(expected.getMessage());}
        check(modeContextOnly,"a token-cap refusal mentioning non-thinking mode does not misclassify the explicit mode as rejected");
        request(null,captured,200,"");check(!captured[0].has("max_tokens")&&!captured[0].has("enable_thinking"),"a direct unconfigured-options request does not invent generation controls");
        CompatibleTransport transport = new CompatibleTransport();
        java.net.URL compatibleUrl = new java.net.URL(null, "https://compatibility.test.invalid/v1/chat/completions", transport);
        JSONObject compatibleProfile = new JSONObject().put("apiKey", "fixture-not-a-secret")
                .put("apiModel", "compatibility-fixture-model").put("foodJsonSchema", true);
        FoodAnalysisResult negotiated = prepared(compatibleProfile, compatibleUrl, () -> true);
        check(transport.requests.size() == 4, "explicit optional refusals are negotiated at most once each");
        check(negotiated.value.getJSONObject("nutrients").getInt("caloriesKcal") == 676
                && negotiated.value.getInt("calorieLow") == 520, "compatibility preserves full validated nutrients and estimate range");
        prepared(compatibleProfile, compatibleUrl, () -> true);
        check(transport.requests.size() == 5, "next photo avoids three repeated known-incompatible requests");
        check(!transport.requests.get(4).has("max_tokens") && !transport.requests.get(4).has("stream")
                && !transport.requests.get(4).has("response_format"), "only refused optional features are omitted");
        boolean sameInput = true;
        for (JSONObject sent : transport.requests) sameInput &= "data:image/jpeg;base64,same-encoded-photo".equals(sent
                .getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(1)
                .getJSONObject("image_url").getString("url"));
        check(sameInput, "compatibility never lowers or replaces the encoded input image");
        prepared(new JSONObject(compatibleProfile.toString()).put("apiModel", "different-model"), compatibleUrl, () -> true);
        check(transport.requests.size() == 9, "different model does not inherit optional capability refusals");
        prepared(new JSONObject(compatibleProfile.toString()).put("apiKey", "another-fixture-credential"), compatibleUrl, () -> true);
        check(transport.requests.size() == 13, "different credential negotiates independently");
        prepared(new JSONObject(compatibleProfile.toString()).put("apiModelMode", "thinking"), compatibleUrl, () -> true);
        check(transport.requests.size() == 17, "changed generation mode negotiates independently");
        int beforeCancelled = transport.requests.size(); boolean cancelledPrepared = false;
        try { prepared(compatibleProfile, compatibleUrl, () -> false); }
        catch (java.io.InterruptedIOException expected) { cancelledPrepared = true; }
        check(cancelledPrepared && transport.requests.size() == beforeCancelled,
                "cancelled generation does not issue a compatibility request");
        final java.util.concurrent.atomic.AtomicInteger validityChecks = new java.util.concurrent.atomic.AtomicInteger();
        boolean stoppedBeforeUpload = false;
        try { prepared(compatibleProfile, compatibleUrl, () -> validityChecks.incrementAndGet() < 4); }
        catch (java.io.InterruptedIOException expected) { stoppedBeforeUpload = true; }
        check(stoppedBeforeUpload && validityChecks.get() == 4 && transport.requests.size() == beforeCancelled,
                "stop arriving during request preparation is rechecked before image bytes are written");
        CompatibleTransport invalid = new CompatibleTransport(); invalid.forcedCode = 400; invalid.forcedError = "invalid image data";
        java.net.URL invalidUrl = new java.net.URL(null, "https://invalid-input.test.invalid/v1/chat/completions", invalid);
        boolean invalidRejected = false;
        try { prepared(compatibleProfile, invalidUrl, () -> true); }
        catch (FoodAnalysisClient.Failure expected) { invalidRejected = !expected.retryable; }
        check(invalidRejected && invalid.requests.size() == 1, "invalid image is not retried as unsupported schema");
        invalid.forcedCode = 0; prepared(compatibleProfile, invalidUrl, () -> true);
        check(invalid.requests.size() == 5, "unrelated image error never poisons compatibility cache");
        CompatibleTransport denied = new CompatibleTransport(); denied.forcedCode = 401; denied.forcedError = "unauthorized";
        java.net.URL deniedUrl = new java.net.URL(null, "https://permission.test.invalid/v1/chat/completions", denied);
        boolean deniedRejected = false;
        try { prepared(compatibleProfile, deniedUrl, () -> true); }
        catch (FoodAnalysisClient.Failure expected) { deniedRejected = !expected.retryable; }
        check(deniedRejected && denied.requests.size() == 1, "permission rejection has no automatic paid fallback");
        denied.forcedCode = 0; prepared(compatibleProfile, deniedUrl, () -> true);
        check(denied.requests.size() == 5, "permission errors never become model/format capability hints");
        check(FoodAnalysisClient.prompt().indexOf("\"totals\":")<FoodAnalysisClient.prompt().indexOf("\"items\":"),"prompt prioritizes total before long components without extra request");
        check(FoodAnalysisResult.tip(value).contains("牛肉")==false&&FoodAnalysisResult.tip(value).contains("蛋白质约24g"),"tips derive from current validated nutrition rather than invented ingredients");
        check(FoodAnalysisResult.tip(value).contains("清淡")&&!FoodAnalysisResult.tip(value).contains("WHO")&&!FoodAnalysisResult.tip(value).contains("USDA"),"local tip has no fabricated standards attribution");
        check(FoodAnalysisResult.tip(new JSONObject()).isEmpty(),"no nutrition means no fabricated tip");
        String served=event(fixture()).replace("{\"choices\"","{\"model\":\"qa-actual-served-model\",\"choices\"");
        check("qa-actual-served-model".equals(events(served+"data: [DONE]\n\n",null).value.optString("providerModel")),"SSE persists returned model identifier separately from requested alias");
        String unterminated=event(fixture());unterminated=unterminated.substring(0,unterminated.length()-2);
        check(events(unterminated,null).value.getInt("estimatedGrams")==450,"compatible provider EOF flushes last data frame without DONE or blank line");
        boolean earlyDone=false;
        try{events(event(fixture().substring(0,split))+"data: [DONE]\n\n",null);}catch(org.json.JSONException e){earlyDone=true;}
        check(earlyDone,"DONE before complete nutrition JSON is rejected");
        names.clear();String incomplete="{\"dish_name\":\"未完";
        try{events(event(incomplete)+"data: [DONE]\n\n",names::add);}catch(org.json.JSONException expected){}
        check(names.isEmpty(),"incomplete SSE dish name never emits callback");
        char[] oversized=new char[66000];java.util.Arrays.fill(oversized,'x');boolean tooLarge=false;
        try{events("data: "+new String(oversized),null);}catch(java.io.IOException e){tooLarge=true;}
        check(tooLarge,"oversized SSE frame rejected before unbounded allocation");
        check(FoodAnalysisResult.parse("```json\n"+fixture()+"\n```").value.getInt("estimatedGrams")==450,"Markdown compatibility");
        reject("not json","non JSON");
        reject(fixture().replace("\"schema_version\":1","\"schema_version\":2"),"unsupported schema");
        reject(fixture().replace("\"estimated_grams\":450","\"estimated_grams\":-1"),"negative weight");
        reject(fixture().replace("\"confidence\":0.76","\"confidence\":1.1"),"confidence bounds");
        reject(fixture().replace("\"protein_g\":24","\"protein_g\":\"24\""),"numeric strings rejected");
        JSONObject mismatch=new JSONObject(fixture());mismatch.getJSONObject("totals").put("calories_kcal",1000);reject(mismatch.toString(),"inconsistent totals");
        JSONObject badRange=new JSONObject(fixture());badRange.put("calorie_range",new JSONArray().put(700).put(800));reject(badRange.toString(),"range excludes value");
        JSONObject noFood=new JSONObject(fixture());noFood.put("items",new JSONArray());reject(noFood.toString(),"empty plate");
        JSONObject missing=new JSONObject(fixture());missing.getJSONObject("totals").remove("fat_g");reject(missing.toString(),"missing required nutrient");
        JSONObject day=new JSONObject().put("breakfast",new JSONObject().put("foodItems",new JSONArray().put(value.put("status","completed"))));
        check(NutritionStore.totals(day).getInt("count")==1,"one food item");
        check(NutritionStore.totals(day).getDouble("proteinG")==24,"macronutrient sum");
        day.getJSONObject("breakfast").getJSONArray("foodItems").getJSONObject(0).put("status","pending");
        check(NutritionStore.totals(day).getInt("count")==0,"unfinished analysis cannot affect nutrition totals");
        day.getJSONObject("breakfast").getJSONArray("foodItems").getJSONObject(0).put("status","completed").put("userEdited",true);
        check(NutritionStore.totals(day).getDouble("caloriesKcal")==676,"confirmed user edits remain countable");
        day.getJSONObject("breakfast").put("skipped",true);check(NutritionStore.totals(day).getInt("count")==0,"skipped meals excluded");
        JSONObject legacy=new JSONObject("{\"calories\":1234,\"breakfast\":{\"text\":\"原记录\",\"manualCalories\":500}}");
        check(NutritionStore.totals(legacy).getInt("count")==0,"legacy values do not fabricate macros");
        String date="2026-10-17";
        JSONObject oldMeal=new JSONObject().put("text","用户正在编辑").put("photos",new JSONArray().put("photo.jpg"));
        JSONObject freshMeal=new JSONObject().put("text","旧文字").put("foodItems",new JSONArray().put(new JSONObject().put("id","1").put("sourcePhoto","photo.jpg").put("cutoutFile","cutout.png")));
        JSONObject snapshot=new JSONObject().put(date,new JSONObject().put("lunch",oldMeal));
        JSONObject fresh=new JSONObject().put(date,new JSONObject().put("lunch",freshMeal));
        NutritionStore.mergeLatest(snapshot,fresh);
        check(oldMeal.getString("text").equals("用户正在编辑"),"worker merge preserves text");
        check(oldMeal.getJSONArray("foodItems").length()==1,"worker merge includes food");
        check(NutritionStore.media(oldMeal).contains("cutout.png"),"backup includes cutout");
        check(NutritionStore.media(oldMeal).contains("photo.jpg"),"backup includes original photo");
        JSONArray cloned=NutritionStore.forSource(oldMeal,"photo.jpg");cloned.getJSONObject(0).put("cutoutFile","edited.png");
        check(oldMeal.getJSONArray("foodItems").getJSONObject(0).getString("cutoutFile").equals("cutout.png"),"editor snapshots cannot mutate persisted meal object");
        check(NutritionStore.containsPhoto(new JSONObject().put("stickers",new JSONArray().put("alpha.png")),"alpha.png"),"transparent gallery input is a valid media reference");
        check(!NutritionStore.safeName("../secret"),"reject traversal");
        check(!NutritionStore.safeName("a/b"),"reject nested path");
        check(!NutritionStore.safeName("a\\b")&&!NutritionStore.safeName(null),"reject Windows nesting and absent draft names");
        oldMeal.remove("photos");NutritionStore.mergeLatest(snapshot,fresh);
        check(oldMeal.getJSONArray("foodItems").length()==0,"deleted photo cannot be resurrected by worker");
        check(FoodAnalysisClient.schema().getBoolean("additionalProperties")==false,"strict schema contract");
        JSONObject journal=new JSONObject();
        for(String meal:new String[]{"breakfast","lunch","dinner"})journal.put(meal,new JSONObject()
            .put("foodItems",new JSONArray().put(new JSONObject().put("id",meal).put("sourcePhoto","same.jpg").put("cutoutFile","same.png").put("dishName","炒饭").put("status","completed").put("nutrients",new JSONObject().put("caloriesKcal",100))))
            .put("photos",new JSONArray().put("same.jpg")).put("stickers",new JSONArray().put("same.png")));
        String stable=journal.toString();java.util.List<DayFoodEntries.Entry> entries=DayFoodEntries.collect(journal);
        check(entries.size()==3,"one journal entry per distinct meal food, no raw/cutout duplication");
        check("早".equals(entries.get(0).prefix)&&"午".equals(entries.get(1).prefix)&&"晚".equals(entries.get(2).prefix),"merged meal prefixes and chronological order");
        check(journal.toString().equals(stable),"daily journal projection never changes stored JSON");
        journal.getJSONObject("breakfast").put("text","旧版备注");journal.getJSONObject("dinner").getJSONArray("photos").put("legacy.jpg");
        entries=DayFoodEntries.collect(journal);check(entries.size()==4,"legacy photo retained; existing food note is moved to details, not a duplicate home entry");
        check(journal.getJSONObject("breakfast").getString("text").equals("旧版备注")&&entries.get(3).fileName.equals("legacy.jpg"),"old content remains intact and reachable after merge");
        check(DayFoodEntries.collect(null).isEmpty()&&DayFoodEntries.collect(new JSONObject()).isEmpty(),"empty day is one empty journal, not fabricated meal slots");
        journal.getJSONObject("lunch").put("skipped",true);entries=DayFoodEntries.collect(journal);
        check(!entries.get(1).isFood()&&entries.get(1).title.equals("已标记未用餐"),"skip metadata not lost in unified projection");
        check("茶".equals(DayFoodEntries.prefix("afternoon_tea"))&&"夜".equals(DayFoodEntries.prefix("snack")),"extra meals retain short prefixes");
        JSONObject rotating = new JSONObject().put("id","rotation-food").put("sourcePhoto","rotation.jpg")
            .put("cutoutFile","rotation.png").put("analysisId","same-request").put("userEdited",false)
            .put("cutoutRect",new JSONArray().put(10).put(20).put(200).put(300))
            .put("nutrients",new JSONObject().put("proteinG",24));
        JSONObject rotationMeal = new JSONObject().put("foodItems",new JSONArray().put(rotating));
        String untouchedRotation = rotating.toString();
        check(NutritionStore.displayRotation(null)==0&&NutritionStore.displayRotation(rotating)==0,"old food records without rotation stay upright");
        check(NutritionStore.normalizeDisplayRotation(-90)==270&&NutritionStore.normalizeDisplayRotation(450)==90
            &&NutritionStore.normalizeDisplayRotation(-720)==0,"signed and repeated quarter turns normalize safely");
        check(NutritionStore.normalizeDisplayRotation(45)==0&&NutritionStore.normalizeDisplayRotation(Integer.MIN_VALUE)==0,"invalid rotation metadata cannot introduce arbitrary angles");
        int quarterTurn = 0;for(int i=0;i<4;i++){quarterTurn=NutritionStore.leftDisplayRotation(quarterTurn);check(quarterTurn==new int[]{270,180,90,0}[i],"left quarter turn "+i);}
        check(NutritionStore.findByMedia(rotationMeal,"rotation.jpg")==rotating
            &&NutritionStore.findByMedia(rotationMeal,"rotation.png")==rotating,"original and alpha image locate the same food identity");
        check(NutritionStore.findByMedia(rotationMeal,"")==null&&NutritionStore.findByMedia(rotationMeal,"missing.jpg")==null
            &&NutritionStore.findByMedia(null,"rotation.jpg")==null&&NutritionStore.findByMedia(rotationMeal,"../rotation.png")==null,"missing unsafe or legacy unrelated media cannot become food items");
        check(untouchedRotation.equals(rotating.toString()),"rotation reads and media lookup never mutate source geometry nutrition or generation");
        System.out.println("PASS: "+checks+" food contract and compatibility checks");
    }
}
