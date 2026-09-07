package com.bit747.clipboardfilter;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 模块共享配置定义。
 *
 * <p>配置的唯一权威存储是框架侧的 Remote Preferences（group = {@link #GROUP}）：
 * 模块 App 进程通过 {@code XposedService.getRemotePreferences(group)} 可读可写，
 * 被 hook 的宿主进程通过 {@code XposedInterface.getRemotePreferences(group)} 只读并监听变更。
 *
 * <p>{@link #LOCAL_PREFS} 只保存与 hook 无关的 App 自身 UI 状态（例如隐藏桌面图标），
 * 不参与跨进程同步。
 */
public final class Config {

    /** Remote Preferences 的组名（沿用旧版 shared_prefs 文件名，便于一次性导入旧配置）。 */
    public static final String GROUP = "rules";

    /** 模块自身包名。不使用 BuildConfig，避免对 buildFeatures.buildConfig 的依赖。 */
    public static final String PACKAGE_NAME = "com.bit747.clipboardfilter";

    /** 换行分隔的正则规则集（存入前经过 {@link #encode}）。 */
    public static final String KEY_RULES = "rules";

    /** 是否已初始化过配置（缺失时写入默认规则或导入旧配置）。 */
    public static final String KEY_FIRST_OPEN = "isFirstOpen";

    public static final String KEY_LOG_ENABLE = "LogEnable";
    public static final String KEY_LOG_DETAILS = "LogDetails";
    public static final String KEY_LOG_ALL = "LogAll";

    /** 隐藏桌面图标：仅模块 App 自身使用，保存在本地偏好中。 */
    public static final String KEY_HIDE_ICON = "HideIcon";

    /** 旧版本（Legacy API 82）使用的本地 SharedPreferences 文件名，仅用于一次性导入。 */
    public static final String LEGACY_PREFS = "rules";

    /** 模块 App 本地偏好文件名（不跨进程）。 */
    public static final String LOCAL_PREFS = "ui";

    /** 首次打开时写入的默认规则（与旧版一致）。 */
    public static final String DEFAULT_RULES =
            "(1.fu:).*\n^(\\d+:/\\^).*.(\\^)$\n^(\\$\\w+@.?\\w+).*.(\\$)$";

    private Config() {
        // 工具类，禁止实例化
    }

    /**
     * 把规则串中的反斜杠翻倍后写入偏好，避免 XML 序列化时被吞掉。
     *
     * @param str 原始规则串，允许为 null
     * @return 编码后的规则串，永不为 null
     */
    public static String encode(String str) {
        if (str == null) {
            return "";
        }
        return str.replaceAll("\\\\", "\\\\\\\\").trim();
    }

    /**
     * {@link #encode} 的逆操作。
     *
     * @param str 编码后的规则串，允许为 null
     * @return 解码后的规则串，永不为 null
     */
    public static String decode(String str) {
        if (str == null) {
            return "";
        }
        return str.replaceAll("\\\\\\\\", "\\\\").trim();
    }

    /**
     * 把换行分隔的规则串拆成规则数组。
     *
     * @param rules 已解码的规则串
     * @return 规则数组，空输入返回长度为 0 的数组
     */
    public static String[] splitRules(String rules) {
        if (rules == null || rules.length() == 0) {
            return new String[0];
        }
        return rules.split("\n");
    }

    /**
     * 首次初始化远程配置：若存在旧版本（Legacy）的本地配置则导入，否则写入默认规则。
     *
     * <p>这是一次性迁移，不是双通道：导入完成后旧本地文件不再被读取或写入。
     *
     * @param ctx    模块 App 的 Context
     * @param remote 可写的 Remote Preferences
     */
    public static void initialize(Context ctx, SharedPreferences remote) {
        String rules = null;
        boolean logEnable = false;
        boolean logDetails = false;
        boolean logAll = false;

        SharedPreferences legacy = null;
        try {
            // 旧版在 Android 7+ 上 MODE_WORLD_READABLE 会抛异常并回退到 MODE_PRIVATE，
            // 因此这里直接用 MODE_PRIVATE 读取自己沙盒内的文件是安全的。
            legacy = ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            legacy = null;
        }

        if (legacy != null && legacy.contains(KEY_RULES)) {
            rules = legacy.getString(KEY_RULES, null);
            logEnable = legacy.getBoolean(KEY_LOG_ENABLE, false);
            logDetails = legacy.getBoolean(KEY_LOG_DETAILS, false);
            logAll = legacy.getBoolean(KEY_LOG_ALL, false);
        }

        if (rules == null || rules.length() == 0) {
            rules = encode(DEFAULT_RULES);
        }

        SharedPreferences.Editor editor = remote.edit();
        editor.putString(KEY_RULES, rules);
        editor.putBoolean(KEY_LOG_ENABLE, logEnable);
        editor.putBoolean(KEY_LOG_DETAILS, logDetails);
        editor.putBoolean(KEY_LOG_ALL, logAll);
        editor.putBoolean(KEY_FIRST_OPEN, false);
        editor.apply();
    }
}
