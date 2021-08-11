package com.bit747.clipboardfilter_lite;

import android.content.ClipData;
import android.content.ClipboardManager;
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
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        File conf = new File(CONF);
        if(conf.exists()){
            XposedHelpers.findAndHookMethod(ClipboardManager.class, "setPrimaryClip",
                ClipData.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        // 获取剪切板内容
                        ClipData clipData = (ClipData) param.args[0];
                        String clipStr = clipData.getItemAt(0).getText().toString();
                        //正则匹配
                        boolean isExist = false;
                        try {
                            if(CUSTOM_RULE.equals("")){
                                List<String> rules = new ArrayList<String>();
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
                                for(String rule:rules){
                                    if(Pattern.matches(rule, clipStr)){
                                        isExist = true;
                                        XposedBridge.log(TAG + "过滤成功");
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
            });
        }

    }

}
