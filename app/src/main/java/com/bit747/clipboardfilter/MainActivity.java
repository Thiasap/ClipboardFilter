package com.bit747.clipboardfilter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Pattern;

public class MainActivity extends Activity implements View.OnClickListener{

    Button bt,saveRules;
    EditText ruleE;
    EditText content;
    SharedPreferences sp;
    Context context;
    String rules;
    CheckBox LogEnable,LogDetails,LogAll;
    CheckBox HideIcon;
    Uri uri_rules = Uri.parse("content://com.bit747.clipboardfilter/rules");
    Uri uri_log = Uri.parse("content://com.bit747.clipboardfilter/log");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }
    String encode(String str){
        return str.replaceAll("\\\\","\\\\\\\\").trim();
    }
    String decode(String str){
        return str.replaceAll("\\\\\\\\","\\\\").trim();
    }
    public void init(){
        ruleE = findViewById(R.id.rules);
        content = findViewById(R.id.content);
        context = this;
        try{
            sp = context.getSharedPreferences("rules", Context.MODE_WORLD_READABLE);
        }catch (Exception e){
            sp = context.getSharedPreferences("rules", Context.MODE_PRIVATE);
        }
        rules = decode(sp.getString("rules",""));
        boolean isFirstOpen = sp.getBoolean("isFirstOpen",true);
        if (isFirstOpen){
            rules="(1.fu:).*\n^(\\d+:/\\^).*.(\\^)$\n^(\\$\\w+@.?\\w+).*.(\\$)$";
            SharedPreferences.Editor editor = sp.edit();
            editor.putString("rules",encode(rules));
            editor.putBoolean("isFirstOpen",false);
            editor.commit();

            ContentValues values = new ContentValues();
            values.put("_id", 1);
            values.put("rules", encode(rules));
            ContentResolver resolver =  getContentResolver();
            resolver.insert(uri_rules,values);
            values.clear();
            values.put("_id", 1);
            values.put("LogEnable", "0");
            values.put("LogDetails", "0");
            values.put("LogAll", "0");
            resolver.insert(uri_log,values);
        }
        ruleE.setText(rules);
        bt = findViewById(R.id.goTest);
        saveRules = findViewById(R.id.saveRules);
        LogEnable = findViewById(R.id.LogEnable);
        LogAll = findViewById(R.id.LogAll);
        LogDetails = findViewById(R.id.LogDetails);
        bt.setOnClickListener(this);
        saveRules.setOnClickListener(this);
        LogEnable.setOnClickListener(this);
        LogAll.setOnClickListener(this);
        LogDetails.setOnClickListener(this);
        boolean LogA  = sp.getBoolean("LogEnable",false);
        LogEnable.setChecked(LogA);
        LogAll.setEnabled(LogA);
        LogDetails.setEnabled(LogA);
        LogAll.setChecked(sp.getBoolean("LogAll",false));
        LogDetails.setChecked(sp.getBoolean("LogDetails",false));
        ((TextView)findViewById(R.id.tv1)).setMovementMethod(LinkMovementMethod.getInstance());
        ((TextView)findViewById(R.id.tv2)).setMovementMethod(LinkMovementMethod.getInstance());
        ((TextView)findViewById(R.id.tv3)).setMovementMethod(LinkMovementMethod.getInstance());
        HideIcon = findViewById(R.id.HideIcon);
        HideIcon.setChecked(sp.getBoolean("HideIcon",false));
        HideIcon.setOnClickListener(this);
    }
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.saveRules:
                saveRules();
                break;
            case R.id.goTest:
                String ct = content.getText().toString();
                rules = ruleE.getText().toString();
                checkRule(ct,rules.split("\n"),context);
                break;
            case R.id.LogEnable:
                CBLog(R.id.LogEnable);
                break;
            case R.id.LogDetails:
                CBLog(R.id.LogDetails);
                break;
            case R.id.LogAll:
                CBLog(R.id.LogAll);
                break;
            case R.id.HideIcon:
                doHideIcon();
                break;
            default:
                break;
        }
    }
    public void toast(String msg){
        Toast.makeText(context,msg,Toast.LENGTH_SHORT).show();
    }
    public void CBLog(int id){
        CheckBox cb = findViewById(id);
        SharedPreferences.Editor editor = sp.edit();
        String LogType="";
        String LogS = cb.isChecked()?"1":"0";
        switch (id){
            case R.id.LogEnable:
                LogType="LogEnable";
                LogAll.setEnabled(cb.isChecked());
                LogDetails.setEnabled(cb.isChecked());
                toast("开启日志功能");
                break;
            case R.id.LogDetails:
                LogType="LogDetails";
                toast("开启详细日志");
                break;
            case R.id.LogAll:
                LogType="LogAll";
                toast("开启所有日志");
                break;
            default:
                break;
        }
        editor.putBoolean(LogType,cb.isChecked());
        ContentValues values = new ContentValues();
        values.put(LogType, LogS);
        ContentResolver resolver =  getContentResolver();
        resolver.update(uri_log,values,"_id = ?",new String[]{"1"});
        editor.commit();
    }
    public void doHideIcon(){
        int needHide = HideIcon.isChecked() ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("HideIcon", HideIcon.isChecked());
        editor.commit();
        PackageManager packageManager = getApplicationContext().getPackageManager();
        ComponentName componentName = new ComponentName(getApplicationContext(), MainActivity.class);
        packageManager.setComponentEnabledSetting(componentName,needHide , PackageManager.DONT_KILL_APP);
    }
    public void saveRules(){
        final AlertDialog.Builder alterDiaglog = new AlertDialog.Builder(MainActivity.this);
        alterDiaglog.setTitle("保存规则");
        alterDiaglog.setMessage("是否覆盖已存在的规则？");
        alterDiaglog.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                rules = ruleE.getText().toString();
                SharedPreferences.Editor editor = sp.edit();
                editor.putString("rules",encode(rules));
                editor.commit();
                ContentValues values = new ContentValues();
                values.put("rules", encode(rules));
                ContentResolver resolver =  getContentResolver();
                resolver.update(uri_rules,values,"_id = ?",new String[]{"1"});
                if((sp.getString("rules","")).equals(encode(rules))){
                    Toast.makeText(context,"保存成功！",Toast.LENGTH_SHORT).show();
                }
            }
        });
        alterDiaglog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        alterDiaglog.show();
    }

    public void checkRule(String ct, String[] rules, Context ctx){
        boolean isMatch = false;
        try{
            for(String rule:rules){
                if(Pattern.matches(rule, ct)){
                    isMatch=true;
                    break;
                }
            }
            if(isMatch){
                Toast.makeText(ctx,"匹配成功！",Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(ctx,"匹配失败！",Toast.LENGTH_SHORT).show();
            }
        }catch(Exception e) {
            Toast.makeText(ctx,"正则语句出错了！",Toast.LENGTH_SHORT).show();
        }
    }
}