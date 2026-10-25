package io.github.kepsilent.noclearbtn;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

public class NoClearBtnHook extends XposedModule {

    private static final String TAG = "NoClearBtn";
    private static Field btn;

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String p = param.getPackageName();
        if (!("com.android.launcher".equals(p) || "com.oppo.launcher".equals(p))) return;
        try {
            Class<?> panel = Class.forName("com.oplus.quickstep.views.OplusClearAllPanelView", false, param.getClassLoader());
            btn = findFieldByType(panel, "com.android.launcher.views.PressFeedbackButton");
            Log.i(TAG, "init pkg=" + p + " panel=" + (panel != null) + " btn=" + (btn != null));
            if (btn == null) return;
            btn.setAccessible(true);
            Method m = findMethod(panel, "inflateIfNeeded", false);
            if (m == null) m = findMethod(panel, "onFinishInflate", false);
            if (m != null) {
                Log.i(TAG, "hook inflate: " + m.getName());
                hook(m).intercept(c -> {
                    Object r = c.proceed();
                    hide(c.getThisObject());
                    return r;
                });
            }
            Method a = findMethod(panel, "setAlpha", true);
            if (a != null) {
                Log.i(TAG, "hook setAlpha");
                hook(a).intercept(c -> {
                    Object r = c.proceed();
                    hide(c.getThisObject());
                    return r;
                });
            }
        } catch (Throwable t) {
            Log.i(TAG, "init fail: " + t);
        }
    }

    private static Field findFieldByType(Class<?> c, String t) {
        for (Class<?> cur = c; cur != null; cur = cur.getSuperclass())
            for (Field f : cur.getDeclaredFields())
                if (f.getType().getName().equals(t)) return f;
        return null;
    }

    private static Method findMethod(Class<?> c, String n, boolean flt) {
        Method fb = null;
        for (Class<?> cur = c; cur != null; cur = cur.getSuperclass())
            for (Method m : cur.getDeclaredMethods()) {
                if (!m.getName().equals(n)) continue;
                if (flt) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == float.class) return m;
                } else if (m.getParameterCount() == 0) return m;
                if (fb == null) fb = m;
            }
        return fb;
    }

    private static void hide(Object o) {
        try {
            View v = (View) btn.get(o);
            if (v != null) v.setVisibility(View.GONE);
        } catch (Throwable t) {
            Log.i(TAG, "hide fail: " + t);
        }
    }
}
