package com.example.cosacloudspoofer;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookInit implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.oplus.cosa";

    /**
     * 只伪装这个接口。
     */
    private static final String SPOOF_API = "getApkAndPkgConfigByPackName";

    /**
     * 这个接口保持原机型，避免云控默认配置被污染。
     */
    private static final String KEEP_ORIGINAL_API = "getDeviceDefaultConfig";

    /**
     * 写死的目标代号。
     */
    private static final String FAKE_CODE = "24851";

    private static final Map<String, String> FAKE_HEADERS = new LinkedHashMap<>();

    static {
        /*
         * 常见短字段
         */
        FAKE_HEADERS.put("model", FAKE_CODE);
        FAKE_HEADERS.put("device", FAKE_CODE);
        FAKE_HEADERS.put("product", FAKE_CODE);
        FAKE_HEADERS.put("brand", "OPPO");
        FAKE_HEADERS.put("manufacturer", "OPPO");

        /*
         * 常见 Android property 风格字段
         */
        FAKE_HEADERS.put("ro.product.model", FAKE_CODE);
        FAKE_HEADERS.put("ro.product.device", FAKE_CODE);
        FAKE_HEADERS.put("ro.product.name", FAKE_CODE);
        FAKE_HEADERS.put("ro.product.brand", "OPPO");
        FAKE_HEADERS.put("ro.product.manufacturer", "OPPO");

        /*
         * 可能出现的 OPlus/ColorOS 字段名
         */
        FAKE_HEADERS.put("oplus_model", FAKE_CODE);
        FAKE_HEADERS.put("oplus_device", FAKE_CODE);
        FAKE_HEADERS.put("oplus_product", FAKE_CODE);
        FAKE_HEADERS.put("productName", FAKE_CODE);
        FAKE_HEADERS.put("deviceName", FAKE_CODE);
        FAKE_HEADERS.put("modelName", FAKE_CODE);
        FAKE_HEADERS.put("marketName", FAKE_CODE);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        log("Loaded target package: " + lpparam.packageName);

        hookOkHttp3(lpparam);
    }

    private void hookOkHttp3(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            ClassLoader cl = lpparam.classLoader;

            Class<?> okHttpClientClass = findClass("okhttp3.OkHttpClient", cl);
            Class<?> requestClass = findClass("okhttp3.Request", cl);

            if (okHttpClientClass == null || requestClass == null) {
                log("OkHttp3 classes not found in target process");
                return;
            }

            Method newCallMethod = okHttpClientClass.getDeclaredMethod("newCall", requestClass);

            XposedBridge.hookMethod(newCallMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object request = param.args[0];
                        if (request == null) {
                            return;
                        }

                        String url = getRequestUrl(request);
                        if (url == null || url.length() == 0) {
                            return;
                        }

                        /*
                         * getDeviceDefaultConfig 不修改。
                         */
                        if (url.contains(KEEP_ORIGINAL_API)) {
                            log("Keep original request: " + url);
                            return;
                        }

                        /*
                         * 只处理 getApkAndPkgConfigByPackName。
                         */
                        if (!url.contains(SPOOF_API)) {
                            return;
                        }

                        log("Spoof target request: " + url);

                        Object newRequest = rebuildRequestWithHeaders(request);

                        if (newRequest != null) {
                            param.args[0] = newRequest;
                            log("Request spoofed with code: " + FAKE_CODE);
                        } else {
                            log("Failed to rebuild request");
                        }
                    } catch (Throwable t) {
                        log("beforeHookedMethod error: " + t);
                    }
                }
            });

            log("Hooked okhttp3.OkHttpClient.newCall successfully");

        } catch (Throwable t) {
            log("hookOkHttp3 error: " + t);
        }
    }

    private Object rebuildRequestWithHeaders(Object request) {
        try {
            Method newBuilderMethod = request.getClass().getMethod("newBuilder");
            Object builder = newBuilderMethod.invoke(request);

            Method headerMethod = builder.getClass().getMethod("header", String.class, String.class);

            for (Map.Entry<String, String> entry : FAKE_HEADERS.entrySet()) {
                headerMethod.invoke(builder, entry.getKey(), entry.getValue());
                log("Set header: " + entry.getKey() + " = " + entry.getValue());
            }

            Method buildMethod = builder.getClass().getMethod("build");
            return buildMethod.invoke(builder);

        } catch (Throwable t) {
            log("rebuildRequestWithHeaders error: " + t);
            return null;
        }
    }

    private String getRequestUrl(Object request) {
        try {
            Method urlMethod = request.getClass().getMethod("url");
            Object urlObj = urlMethod.invoke(request);
            return String.valueOf(urlObj);
        } catch (Throwable t) {
            log("getRequestUrl error: " + t);
            return null;
        }
    }

    private Class<?> findClass(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void log(String msg) {
        XposedBridge.log("[CosaSpoofer24851] " + msg);
    }
}
