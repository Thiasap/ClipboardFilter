package com.bit747.clipboardfilter;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * 模块 App 进程的 Application。
 *
 * <p>现代 libxposed 架构里模块 App 自身不再被 hook；App 与框架通信的唯一通道是
 * {@code XposedService}——由框架向本 App 的 exported provider 推送 binder，
 * 因此这里只做一件事：在 {@link #onCreate} 注册 {@link XposedServiceHelper} 监听器
 * （只调一次），并把可能为空的 service 暴露给 UI。
 *
 * <p>规则与日志开关的**唯一可写端**就是经 {@code service.getRemotePreferences(Config.GROUP)}
 * 拿到的 RemotePreferences；被 hook 的宿主进程里只读。service 可能始终为 null
 * （未在框架下运行 / embedded 框架），UI 必须降级提示而不是崩溃。
 */
public final class CFApplication extends Application implements XposedServiceHelper.OnServiceListener {

    private static final String TAG = "ClipboardFilterUI";

    /** UI 状态变更监听（用于 MainActivity 感知 service 绑定/断开）。 */
    public interface ServiceStateListener {
        void onServiceStateChanged(XposedService service);
    }

    private static final CopyOnWriteArraySet<ServiceStateListener> STATE_LISTENERS =
            new CopyOnWriteArraySet<>();

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /** XposedService 的方法是同步 Binder 调用，全部放后台线程执行。 */
    private static final ExecutorService SERVICE_EXECUTOR = Executors.newSingleThreadExecutor();

    private static volatile XposedService sService;

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        sService = service;
        notifyStateChanged(service);
    }

    @Override
    public void onServiceDied(XposedService service) {
        Log.w(TAG, "XposedService died");
        if (sService == service) {
            sService = null;
        }
        notifyStateChanged(null);
    }

    /** 当前绑定的 service，未绑定/embedded 时为 null。 */
    public static XposedService getService() {
        return sService;
    }

    /**
     * 注册 service 状态监听。回调发生在任意线程（Binder 线程/主线程），
     * 实现方自行切到需要的线程。
     */
    public static void addServiceStateListener(ServiceStateListener listener, boolean notifyImmediately) {
        STATE_LISTENERS.add(listener);
        if (notifyImmediately) {
            listener.onServiceStateChanged(sService);
        }
    }

    public static void removeServiceStateListener(ServiceStateListener listener) {
        STATE_LISTENERS.remove(listener);
    }

    /** 把同步 Binder 调用放到后台线程执行。 */
    public static void runOnBackground(Runnable r) {
        SERVICE_EXECUTOR.execute(r);
    }

    /** 切回主线程（XposedService 回调与 Binder 线程可能非 UI 线程）。 */
    public static void runOnUiThread(Runnable r) {
        MAIN_HANDLER.post(r);
    }

    private void notifyStateChanged(XposedService service) {
        for (ServiceStateListener listener : STATE_LISTENERS) {
            try {
                listener.onServiceStateChanged(service);
            } catch (Throwable t) {
                Log.e(TAG, "service state listener failed", t);
            }
        }
    }
}
