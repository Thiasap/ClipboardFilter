# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# =====================================================================
# libxposed API 102 三件套（缺一不可）。
# 当前 minifyEnabled=false 时这些规则处于闲置状态；
# 一旦开启 release 混淆，以下三条必须全部生效，否则：
#   - 缺 -adaptresourcefilecontents：debug 正常、release 完全失效
#     （入口类被 R8 改名后 META-INF/xposed/java_init.list 未同步重写）
#   - 缺 -keep：入口类被 shrink/构造器丢失 → NoSuchMethodException
#   - 缺 -dontwarn：annotation 是 compileOnly，R8 报 missing class 警告
# =====================================================================
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
