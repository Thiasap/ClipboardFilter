package com.bit747.clipboardfilter;

import android.app.AndroidAppHelper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class xposedInit implements IXposedHookLoadPackage{
    private final String TAG = "剪切板过滤器：";
    private final String CONF = "/sdcard/Android/hooker/clipboardfilter.conf";
    private final String CUSTOM_RULE="";
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        File conf = new File(CONF);
        if(conf.exists()){
            XposedHelpers.findAndHookMethod(ClipboardManager.class, "setPrimaryClip",
                ClipData.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        XposedBridge.log(TAG + "hook成功");
                        // 获取剪切板内容
                        ClipData clipData = (ClipData) param.args[0];
                        String clipStr = clipData.getItemAt(0).getText().toString();
                        // 得到应用上下文
                        Context curContext =
                                AndroidAppHelper.currentApplication().getApplicationContext();
                        PackageManager pm = curContext.getPackageManager();
                        // 获取应用名
                        String appName = lpparam.appInfo.loadLabel(pm).toString();
                        // 获取应用图标
                        //Drawable icon = lpparam.appInfo.loadIcon(pm);
                        //正则匹配
                        List<String> rules = new ArrayList<String>();
                        try {
                            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                                InputStream instream = new FileInputStream(conf);
                                if (instream != null) {
                                    InputStreamReader inputreader = new InputStreamReader(instream);
                                    BufferedReader buffreader = new BufferedReader(inputreader);
                                    String line;
                                    //分行读取
                                    while (( line = buffreader.readLine()) != null) {
                                        rules.add(line);
                                    }
                                    instream.close();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        boolean isExist = false;
                        try {
                            if(CUSTOM_RULE.equals("")){
                                for(String rule:rules){
                                    if(Pattern.matches(rule, clipStr)){
                                        isExist=true;
                                        // 准备剪切板内容
                                        String showText = "\"" + appName + "\"" + "写入剪贴板被过滤。内容为:" + clipStr;
                                        // Xposed打印log信息
                                        XposedBridge.log(TAG + showText);
                                        break;
                                    }
                                }
                            }else {
                                isExist = Pattern.matches(CUSTOM_RULE, clipStr);
                            }
                        }catch(Exception e){
                            isExist = false;
                        }
                        if (isExist){
                            ClipData c= ClipData.newPlainText("","");
                            param.args[0] = c;
                        }

                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                    }
            });
        }

    }

}
