package com.bit747.clipboardfilter;

import android.content.ClipData;
import android.content.ClipboardManager;
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
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if(lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) return;
        XSharedPreferences pref = getPref("rules");
        String rules_r;
        if (pref == null) {
            XposedBridge.log(TAG+"【Warning】Cannot load pref.");
            StringBuilder sb = new StringBuilder();
            if(new File(Environment.getDataDirectory(), "data/" + BuildConfig.APPLICATION_ID + "/shared_prefs/rules.xml").exists())
                sb.append("shared_prefs file exists.\n");
            else sb.append("shared_prefs file does not exists.\n");
            sb.append("Xposed version: ").append(XposedBridge.getXposedVersion());
            sb.append("\nAndroid version: ").append(Build.VERSION.SDK_INT);
            XposedBridge.log(TAG+sb.toString());
            return;
        }else {
            rules_r = decode(pref.getString("rules", ""));
        }
        String[] rules = rules_r.split("\n");
        if("".equals(rules_r)) return;
        XposedHelpers.findAndHookMethod(ClipboardManager.class, "setPrimaryClip",
                ClipData.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
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
                        if(pref != null){
                            boolean LogEnable = pref.getBoolean("LogEnable",false);
                            if(LogEnable){
                                boolean LogAll = pref.getBoolean("LogAll",false);
                                if ((!LogAll)&&(!isExist)) return;
                                String log=TAG+appName+" 写入了剪贴板。";
                                log = log+(isExist?"【已过滤】":"");
                                boolean LogDetails = pref.getBoolean("LogDetails",false);
                                if (LogDetails){
                                    log = log + "内容：" + clipStr;
                                }
                                XposedBridge.log(log);
                            }
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
