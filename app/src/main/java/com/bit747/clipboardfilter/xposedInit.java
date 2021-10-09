package com.bit747.clipboardfilter;

import android.content.ClipData;
import android.content.ClipboardManager;

import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class xposedInit implements IXposedHookLoadPackage{
    private final String TAG = "剪切板过滤器：";
    private static XSharedPreferences getPref(String path) {
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
        if (pref == null) {
            XposedBridge.log(TAG+"【Error】Cannot load pref");
            return;
        }
        String rules_r = decode(pref.getString("rules", ""));

        String[] rules = rules_r.split("\n");
        if("".equals(rules_r)) return;
        XposedHelpers.findAndHookMethod(ClipboardManager.class, "setPrimaryClip",
                ClipData.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        // 获取剪切板内容
                        ClipData clipData = (ClipData) param.args[0];
                        String clipStr = clipData.getItemAt(0).getText().toString();
                        if("".equals(clipStr)) return;
                        // 获取应用名
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
                            //boolean LogAll = pref.getBoolean("LogAll",false);

                        }

                        if (isExist){
                            ClipData c= ClipData.newPlainText("","");
                            param.args[0] = c;
                        }
                    }
                });

    }



}
