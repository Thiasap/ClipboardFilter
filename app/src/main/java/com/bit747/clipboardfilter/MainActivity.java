package com.bit747.clipboardfilter;


import android.app.Activity;
import android.opengl.ETC1;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.regex.Pattern;

import de.robv.android.xposed.XposedBridge;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button bt = findViewById(R.id.goTest);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText ruleE = findViewById(R.id.rules);
                EditText content = findViewById(R.id.content);
                String ct = content.getText().toString();
                boolean isMatch = false;
                String[] rules = (ruleE.getText().toString()).split("\n");
                try{
                    for(String rule:rules){
                        if(Pattern.matches(rule, ct)){
                            isMatch=true;
                            break;
                        }

                    }
                    if(isMatch){
                        Toast.makeText(MainActivity.this,"匹配成功！",Toast.LENGTH_SHORT).show();
                    }else{
                        Toast.makeText(MainActivity.this,"匹配失败！",Toast.LENGTH_SHORT).show();
                    }
                }catch(Exception e) {
                    Toast.makeText(MainActivity.this,"正则语句出错了！",Toast.LENGTH_SHORT).show();
                }


            }
        });
    }
}