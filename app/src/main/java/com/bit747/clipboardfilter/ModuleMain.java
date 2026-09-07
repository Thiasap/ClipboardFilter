package com.bit747.clipboardfilter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * ClipboardFilter 的 libxposed API 102 入口类。
 *
 * <p>职责：hook 目标 App 进程内的 {@code ClipboardManager.setPrimaryClip(ClipData)}，
 * 把命中用户正则的剪贴板内容替换为空 {@code ClipData}，用于防止某些网站强制写入剪贴板。
 *
 * <p>规则与日志开关来自框架侧 Remote Preferences（group = {@link Config#GROUP}）：
 * 宿主进程内**只读**，模块 App 侧经 {@code XposedService.getRemotePreferences} 写入；
 * 宿主侧注册变更监听，配置改动即时生效，无需热重载。
 *
 * <p>生命周期：hook 一律安装在 {@link #onPackageReady}（真实 classloader 就绪、Application
 * 创建之前）；不在构造器/字段初始化中调用任何 {@code XposedInterface} 方法。
 */
public final class ModuleMain extends XposedModule {

    private static final String TAG = "ClipboardFilter";

    /** hook id：同一方法重复注册同 id 会原子替换，不会越积越多。 */
    private static final String HOOK_ID_SET_PRIMARY_CLIP = "clipboardfilter:setPrimaryClip";

    /** onModuleLoaded 记录的进程名（诊断用）。 */
    private String mProcessName = "";

    /** 当前进程里第一个被加载（即本进程主）的包名，日志里代替旧版 lpparam.packageName。 */
    private volatile String mHostPackage = "";

    /** 远程配置是否可用（可用才装 hook）。 */
    private volatile boolean mConfigReady = false;

    /** 已解码、按行拆好的规则数组。 */
    private volatile String[] mRules = new String[0];

    private volatile boolean mLogEnable = false;
    private volatile boolean mLogDetails = false;
    private volatile boolean mLogAll = false;

    /**
     * 配置变更监听器必须被强引用：SharedPreferences 的常见实现以 WeakHashMap 持有
     * listener，只传临时匿名对象会被 GC，导致配置改动收不到推送。
     */
    private SharedPreferences.OnSharedPreferenceChangeListener mConfigListener;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        mProcessName = param.getProcessName();
        // 指纹日志：一切排障的起点（框架名 + API 版本 + 能力位 + 进程名）
        log(Log.INFO, TAG, "loaded: process=" + mProcessName
                + ", systemServer=" + param.isSystemServer()
                + ", api=" + getApiVersion()
                + ", framework=" + getFrameworkName() + " " + getFrameworkVersion()
                + ", props=0x" + Long.toHexString(getFrameworkProperties()));
        loadConfig();
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        // 同一进程可能加载多个包，回调可能超出作用域 → 每个回调先过滤
        if (!param.isFirstPackage()) {
            return;
        }
        String pkg = param.getPackageName();
        if (pkg == null || Config.PACKAGE_NAME.equals(pkg)) {
            return; // 模块 App 自身不再被 hook，此分支仅是保险
        }
        mHostPackage = pkg;
        if (!mConfigReady) {
            // 配置源不可用（embedded 框架等），装 hook 也无规则可读，直接放弃
            log(Log.WARN, TAG, "remote config unavailable, skip hook in " + pkg);
            return;
        }
        installClipboardHook(param.getClassLoader());
    }

    // ---------- 配置加载（宿主进程内只读） ----------

    private void loadConfig() {
        if ((getFrameworkProperties() & PROP_CAP_REMOTE) == 0) {
            // 能力位为 0 时 remote API 会契约性抛 UnsupportedOperationException（embedded 框架）
            log(Log.WARN, TAG, "framework has no PROP_CAP_REMOTE; remote preferences may be unavailable");
        }
        try {
            SharedPreferences prefs = getRemotePreferences(Config.GROUP);
            mConfigListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    applyConfig(sharedPreferences);
                    log(Log.INFO, TAG, "config changed: " + key);
                }
            };
            prefs.registerOnSharedPreferenceChangeListener(mConfigListener);
            applyConfig(prefs);
            mConfigReady = true;
            log(Log.INFO, TAG, "remote config loaded: rules=" + mRules.length
                    + ", LogEnable=" + mLogEnable
                    + ", LogDetails=" + mLogDetails
                    + ", LogAll=" + mLogAll);
        } catch (UnsupportedOperationException e) {
            log(Log.WARN, TAG, "embedded framework: remote preferences unsupported, module disabled");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "load remote preferences failed", t);
        }
    }

    private void applyConfig(SharedPreferences prefs) {
        String raw = Config.decode(prefs.getString(Config.KEY_RULES, ""));
        mRules = Config.splitRules(raw);
        mLogEnable = prefs.getBoolean(Config.KEY_LOG_ENABLE, false);
        mLogDetails = prefs.getBoolean(Config.KEY_LOG_DETAILS, false);
        mLogAll = prefs.getBoolean(Config.KEY_LOG_ALL, false);
    }

    // ---------- Hook 安装与拦截 ----------

    private void installClipboardHook(ClassLoader classLoader) {
        try {
            // 三参 Class.forName：用目标进程的 classloader 解析（与宿主运行时同一份类）。
            // ClipboardManager 是框架类，任何 classloader 都会委托到 BootClassLoader。
            Class<?> clipboardManagerClass = Class.forName(
                    "android.content.ClipboardManager",
                    true,
                    classLoader);
            Method setPrimaryClip = clipboardManagerClass.getDeclaredMethod("setPrimaryClip", ClipData.class);

            hook(setPrimaryClip)
                    .setPriority(PRIORITY_DEFAULT)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .setId(HOOK_ID_SET_PRIMARY_CLIP)
                    .intercept(this::onSetPrimaryClip);
            log(Log.INFO, TAG, "hook installed: " + mHostPackage + " (" + mProcessName + ")");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "install hook failed in " + mHostPackage, t);
        }
    }

    /**
     * 拦截 {@code ClipboardManager.setPrimaryClip(ClipData)}。
     * 保留原调用（{@code chain.proceed()}）；命中规则时把第一个参数换成空 ClipData 再放行。
     *
     * @param chain hook 链（不能跨线程/跨回调复用）
     * @return 原方法为 void，返回值被框架忽略，统一返回 null
     * @throws Throwable 原方法或反射异常原样上抛（由框架按 ExceptionMode 处理）
     */
    private Object onSetPrimaryClip(XposedInterface.Chain chain) throws Throwable {
        Object[] args = chain.getArgs().toArray(); // getArgs() 是不可变 List，先拷贝
        if (args.length == 0 || !(args[0] instanceof ClipData)) {
            chain.proceed();
            return null;
        }
        ClipData clipData = (ClipData) args[0];
        if (clipData.getItemCount() <= 0) {
            chain.proceed();
            return null;
        }
        CharSequence text = clipData.getItemAt(0).getText();
        String clipStr = text == null ? "" : text.toString();
        if (clipStr.length() == 0) {
            chain.proceed();
            return null;
        }

        boolean matched = matchesRules(clipStr);

        // 主从分级日志：LogEnable 为总开关；LogAll 开启时未命中（未被过滤）的写入也记录；
        // LogDetails 开启时追加剪贴板内容。总开关关闭 = 完全静默，不输出任何日志。
        // 行文以"剪切板过滤器："前缀开头，走框架日志通道（log(int,String,String) == 旧 XposedBridge.log）。
        if (mLogEnable) {
            if (mLogAll || matched) {
                String msg = "剪切板过滤器：" + mHostPackage + " 写入了剪贴板。" + (matched ? "【已过滤】" : "");
                if (mLogDetails) {
                    msg = msg + "内容：" + clipStr;
                }
                log(Log.INFO, TAG, msg);
            }
        }

        if (matched) {
            // 命中规则：替换为空 ClipData 后放行，原方法照常执行（写入了空内容）
            args[0] = ClipData.newPlainText("", "");
            chain.proceed(args);
            return null;
        }

        chain.proceed();
        return null;
    }

    private boolean matchesRules(String clipStr) {
        String[] rules = mRules;
        if (rules == null || rules.length == 0) {
            return false;
        }
        try {
            for (String rule : rules) {
                if (rule == null || rule.length() == 0) {
                    continue;
                }
                if (Pattern.matches(rule, clipStr)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            // 某条正则非法：视为未命中，不阻断其它规则的判定
            log(Log.WARN, TAG, "invalid regex in rules", t);
        }
        return false;
    }
}
