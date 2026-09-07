package com.bit747.clipboardfilter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Pattern;

import io.github.libxposed.service.XposedService;

/**
 * 模块 App 的设置界面：编辑正则规则、正则试测、三个日志开关、隐藏桌面图标。
 *
 * <p>规则与日志开关保存在框架侧 Remote Preferences（group = {@link Config#GROUP}），
 * 经 {@link XposedService#getRemotePreferences} 读写；{@link Config#KEY_HIDE_ICON}
 * 是纯本地 UI 状态，存在本地偏好 {@link Config#LOCAL_PREFS}。
 *
 * <p>service 可能未绑定（设备未运行支持 libxposed 的框架 / embedded 框架）：
 * 此时控件禁用并显示降级提示，不崩溃。
 */
public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "ClipboardFilterUI";

    private EditText ruleE;
    private EditText content;
    private CheckBox logEnable;
    private CheckBox logDetails;
    private CheckBox logAll;
    private CheckBox hideIcon;
    private TextView statusView;
    private Button saveRules;

    /** 可写的 Remote Preferences；service 未绑定时为 null。 */
    private SharedPreferences remote;
    /** 仅模块 App 自身使用的本地偏好。 */
    private SharedPreferences local;

    private final CFApplication.ServiceStateListener stateListener =
            new CFApplication.ServiceStateListener() {
                @Override
                public void onServiceStateChanged(XposedService service) {
                    // 回调可能在 Binder 线程，切回主线程再操作 UI
                    CFApplication.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            bindServiceState();
                        }
                    });
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        local = getSharedPreferences(Config.LOCAL_PREFS, Context.MODE_PRIVATE);
        initViews();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // notifyImmediately=true：处理"service 比 Activity 先到"的情况
        CFApplication.addServiceStateListener(stateListener, true);
    }

    @Override
    protected void onStop() {
        CFApplication.removeServiceStateListener(stateListener);
        super.onStop();
    }

    private void initViews() {
        ruleE = findViewById(R.id.rules);
        content = findViewById(R.id.content);
        logEnable = findViewById(R.id.LogEnable);
        logDetails = findViewById(R.id.LogDetails);
        logAll = findViewById(R.id.LogAll);
        hideIcon = findViewById(R.id.HideIcon);
        statusView = findViewById(R.id.serviceStatus);
        saveRules = findViewById(R.id.saveRules);

        Button bt = findViewById(R.id.goTest);
        bt.setOnClickListener(this);
        saveRules.setOnClickListener(this);
        logEnable.setOnClickListener(this);
        logDetails.setOnClickListener(this);
        logAll.setOnClickListener(this);
        hideIcon.setOnClickListener(this);

        ((TextView) findViewById(R.id.tv1)).setMovementMethod(LinkMovementMethod.getInstance());
        ((TextView) findViewById(R.id.tv2)).setMovementMethod(LinkMovementMethod.getInstance());
        ((TextView) findViewById(R.id.tv3)).setMovementMethod(LinkMovementMethod.getInstance());

        // 一次性迁移旧版 HideIcon：旧版把该标志存在 legacy "rules" 本地文件里，且组件状态已生效
        if (!local.contains(Config.KEY_HIDE_ICON)) {
            try {
                SharedPreferences legacy = getSharedPreferences(Config.LEGACY_PREFS, Context.MODE_PRIVATE);
                if (legacy.contains(Config.KEY_HIDE_ICON)) {
                    local.edit()
                            .putBoolean(Config.KEY_HIDE_ICON, legacy.getBoolean(Config.KEY_HIDE_ICON, false))
                            .apply();
                }
            } catch (Throwable ignored) {
                // legacy 文件损坏等场景忽略，使用默认值
            }
        }
        hideIcon.setChecked(local.getBoolean(Config.KEY_HIDE_ICON, false));

        // 配置未加载前先禁用需要写配置的控件，由 bindServiceState 决定最终状态
        setConfigEnabled(false);
    }

    /** service 状态变化：尝试绑定远程配置并填充 UI，未绑定则显示降级提示。 */
    private void bindServiceState() {
        final XposedService service = CFApplication.getService();
        if (service == null) {
            remote = null;
            statusView.setText(R.string.service_disconnected);
            setConfigEnabled(false);
            return;
        }
        // getRemotePreferences 是同步 Binder 调用，放后台线程
        CFApplication.runOnBackground(new Runnable() {
            @Override
            public void run() {
                SharedPreferences prefs = null;
                try {
                    prefs = service.getRemotePreferences(Config.GROUP);
                } catch (Throwable t) {
                    Log.e(TAG, "getRemotePreferences failed", t);
                }
                final SharedPreferences result = prefs;
                CFApplication.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        applyRemote(result);
                    }
                });
            }
        });
    }

    /** 把远程配置填到 UI；prefs 为 null 时走降级提示。 */
    private void applyRemote(SharedPreferences prefs) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (prefs == null) {
            remote = null;
            statusView.setText(R.string.service_disconnected);
            setConfigEnabled(false);
            return;
        }
        remote = prefs;
        statusView.setText(R.string.service_connected);
        setConfigEnabled(true);

        if (!prefs.contains(Config.KEY_FIRST_OPEN)) {
            // 首次打开：一次性导入旧版本地配置（存在时）或写入默认规则
            Config.initialize(this, prefs);
        }
        ruleE.setText(Config.decode(prefs.getString(Config.KEY_RULES, "")));

        boolean enableLog = prefs.getBoolean(Config.KEY_LOG_ENABLE, false);
        logEnable.setChecked(enableLog);
        logAll.setEnabled(enableLog);
        logDetails.setEnabled(enableLog);
        logAll.setChecked(prefs.getBoolean(Config.KEY_LOG_ALL, false));
        logDetails.setChecked(prefs.getBoolean(Config.KEY_LOG_DETAILS, false));
    }

    /** 控制"需要远程配置"的控件可用性；正则试测是纯本地操作，始终可用。 */
    private void setConfigEnabled(boolean enabled) {
        if (ruleE != null) {
            ruleE.setEnabled(enabled);
        }
        if (saveRules != null) {
            saveRules.setEnabled(enabled);
        }
        if (logEnable != null) {
            logEnable.setEnabled(enabled);
        }
        if (logAll != null) {
            logAll.setEnabled(enabled && logEnable.isChecked());
        }
        if (logDetails != null) {
            logDetails.setEnabled(enabled && logEnable.isChecked());
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.saveRules) {
            saveRules();
        } else if (id == R.id.goTest) {
            String ct = content.getText().toString();
            String rules = ruleE.getText().toString();
            checkRule(ct, rules.split("\n"));
        } else if (id == R.id.LogEnable) {
            onLogCheckBox(id);
        } else if (id == R.id.LogDetails) {
            onLogCheckBox(id);
        } else if (id == R.id.LogAll) {
            onLogCheckBox(id);
        } else if (id == R.id.HideIcon) {
            doHideIcon();
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** 日志开关点击：写入远程配置，联动启用/禁用子开关。文案与旧版保持一致。 */
    private void onLogCheckBox(int id) {
        CheckBox cb = findViewById(id);
        boolean checked = cb.isChecked();
        String key;
        if (id == R.id.LogEnable) {
            key = Config.KEY_LOG_ENABLE;
            logAll.setEnabled(checked);
            logDetails.setEnabled(checked);
            toast("开启日志功能");
        } else if (id == R.id.LogDetails) {
            key = Config.KEY_LOG_DETAILS;
            toast("开启详细日志");
        } else if (id == R.id.LogAll) {
            key = Config.KEY_LOG_ALL;
            toast("开启所有日志");
        } else {
            return;
        }
        if (remote == null) {
            // 理论不会走到：未连接时开关已禁用；保留兜底
            toast(getString(R.string.service_disconnected));
            return;
        }
        try {
            remote.edit().putBoolean(key, checked).apply();
        } catch (Throwable t) {
            Log.e(TAG, "save log switch failed: " + key, t);
            toast("保存失败：" + t.getMessage());
        }
    }

    /** 隐藏/显示桌面图标：纯本地状态 + 禁用 LAUNCHER 组件。 */
    private void doHideIcon() {
        boolean needHide = hideIcon.isChecked();
        SharedPreferences.Editor editor = local.edit();
        editor.putBoolean(Config.KEY_HIDE_ICON, needHide);
        editor.apply();

        int newState = needHide
                ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        PackageManager pm = getApplicationContext().getPackageManager();
        ComponentName componentName = new ComponentName(getApplicationContext(), MainActivity.class);
        pm.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP);
        toast(needHide ? "已隐藏桌面图标（仍可在模块管理器设置中打开）" : "已恢复桌面图标");
    }

    private void saveRules() {
        if (remote == null) {
            toast(getString(R.string.service_disconnected));
            return;
        }
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("保存规则")
                .setMessage("是否覆盖已存在的规则？")
                .setPositiveButton("确认", (dialog, which) -> doSaveRules())
                .setNegativeButton("取消", null)
                .show();
    }

    private void doSaveRules() {
        String rules = ruleE.getText().toString();
        String encoded = Config.encode(rules);
        SharedPreferences.Editor editor = remote.edit();
        editor.putString(Config.KEY_RULES, encoded);
        editor.apply();
        // apply() 先更新本地内存再异步提交，因此这里可立即读到新值
        if (encoded.equals(remote.getString(Config.KEY_RULES, ""))) {
            toast("保存成功！");
        } else {
            toast("保存失败！");
        }
    }

    /** 正则试测（纯本地）。 */
    public void checkRule(String ct, String[] rules) {
        boolean isMatch = false;
        try {
            for (String rule : rules) {
                if (Pattern.matches(rule, ct)) {
                    isMatch = true;
                    break;
                }
            }
            if (isMatch) {
                Toast.makeText(this, "匹配成功！", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "匹配失败！", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "正则语句出错了！", Toast.LENGTH_SHORT).show();
        }
    }
}
