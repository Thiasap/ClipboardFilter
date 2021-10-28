package com.bit747.clipboardfilter;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class xposedInit implements IXposedHookLoadPackage{
    private final String TAG = "剪切板过滤器：";
    private  XSharedPreferences getPref(String path) {
        XSharedPreferences pref = new XSharedPreferences(BuildConfig.APPLICATION_ID, path);
        return pref.getFile().canRead() ? pref : null;
    }
    String decode(String str){
        return str.replaceAll("\\\\\\\\","\\\\").trim();
    }
    String[] readConfigFromCP(String key, String[] col,Context ctx){
        Uri uri = Uri.parse("content://"+BuildConfig.APPLICATION_ID+"/"+key);
        ContentResolver resolver =  ctx.getContentResolver();
        Cursor cursor = resolver.query(uri, col, null, null, null);
        if(cursor == null )return new String[]{""};
        String[] rel = new String[col.length];
        while (cursor.moveToNext()) {
            for (int i = 0; i < col.length; i++) {
                rel[i] = cursor.getString(cursor.getColumnIndex(col[i]));
            }
        }
        cursor.close();
        return rel;
    }
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if(lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) return;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Context ctx = (Context) param.args[0];
                        StartHook(lpparam,ctx);
                    }
                });

    }
    public void StartHook(final XC_LoadPackage.LoadPackageParam lpparam,Context ctx){
        XposedHelpers.findAndHookMethod(ClipboardManager.class, "setPrimaryClip",
                ClipData.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        XSharedPreferences pref = getPref("rules");
                        String rules_r;
                        if (pref == null) {
                            XposedBridge.log(TAG
                                    + "【Warning】Cannot load pref. Try to use Content Provider.");
                            rules_r = decode(readConfigFromCP("rules",new String[]{"rules"},ctx)[0]);
                            if("".equals(rules_r)){
                                XposedBridge.log(TAG+"【Error】rules: "+rules_r);
                                StringBuilder sb = new StringBuilder();
                                String spPath="data/"
                                        + BuildConfig.APPLICATION_ID
                                        + "/shared_prefs/rules.xml";
                                if(new File(Environment.getDataDirectory(), spPath).exists())
                                    sb.append("shared_prefs file exists.\n");
                                else sb.append("shared_prefs file does not exists.\n");
                                sb.append("Xposed version: ").append(XposedBridge.getXposedVersion());
                                sb.append("\nAndroid version: ").append(Build.VERSION.SDK_INT);
                                XposedBridge.log(TAG+sb.toString());
                                return;
                            }
                        }else {
                            rules_r = decode(pref.getString("rules", ""));
                        }
                        String[] rules = rules_r.split("\n");
                        //-------------source
                        // https://github.com/congshengwu/Xposed_Clipboard/blob/master/app/src/main/java/com/csw/xposedclipboard/Xposed.java
                        // 获取剪切板内容
                        ClipData clipData = (ClipData) param.args[0];
                        String clipStr = clipData.getItemAt(0).getText().toString();
                        //-------------source
                        if("".equals(clipStr)) return;
                        // 获取包名
                        String appName = lpparam.packageName;
                        //正则匹配
                        boolean isExist = false;
                        try {
                            for(String rule:rules){
                                if(Pattern.matches(rule, clipStr)){
                                    isExist = true;
                                    break;
                                }
                            }
                        }catch(Exception e){
                            isExist = false;
                        }
                        boolean LogEnable,LogAll,LogDetails;
                        if(pref != null) {
                            LogEnable = pref.getBoolean("LogEnable", false);
                            LogDetails = pref.getBoolean("LogDetails", false);
                            LogAll = pref.getBoolean("LogAll", false);
                        }else {
                            String[] log = readConfigFromCP("log",
                                    new String[]{"LogEnable","LogDetails","LogAll"},
                                    ctx);
                            LogEnable = "1".equals(log[0]);
                            LogDetails = "1".equals(log[1]);
                            LogAll = "1".equals(log[2]);
                        }
                        if(LogEnable){
                            if ((!LogAll)&&(!isExist)) return;
                            String log=TAG+appName+" 写入了剪贴板。";
                            log = log+(isExist?"【已过滤】":"");
                            if (LogDetails){
                                log = log + "内容：" + clipStr;
                            }
                            XposedBridge.log(log);

                        }else {
                            XposedBridge.log(TAG+appName+" 写入了剪贴板。【已过滤】");
                        }
                        if (isExist){
                            ClipData c= ClipData.newPlainText("","");
                            param.args[0] = c;
                        }
                    }
                });
    }


}
