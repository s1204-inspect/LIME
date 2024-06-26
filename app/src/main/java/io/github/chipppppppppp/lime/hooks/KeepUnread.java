package io.github.chipppppppppp.lime.hooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.chipppppppppp.lime.LimeOptions;
import io.github.chipppppppppp.lime.R;

public class KeepUnread implements IHook {
    static boolean keepUnread = false;

    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (limeOptions.removeKeepUnread.checked) return;

        XposedBridge.hookAllConstructors(
                loadPackageParam.classLoader.loadClass("jp.naver.line.android.common.view.listview.PopupListView"),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        ViewGroup viewGroup = (ViewGroup) param.thisObject;
                        Context context = viewGroup.getContext();

                        SharedPreferences prefs = context.getSharedPreferences(Constants.MODULE_NAME + "-options", Context.MODE_PRIVATE);

                        Context moduleContext = context.getApplicationContext().createPackageContext(Constants.MODULE_NAME, Context.CONTEXT_IGNORE_SECURITY);
                        String textKeepUnread = moduleContext.getResources().getString(R.string.switch_keep_unread);

                        RelativeLayout container = new RelativeLayout(context);
                        RelativeLayout.LayoutParams containerParams = new RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                        container.setLayoutParams(containerParams);

                        GradientDrawable background = new GradientDrawable();
                        background.setShape(GradientDrawable.RECTANGLE);
                        background.setColor(Color.parseColor("#06C755"));
                        background.setCornerRadii(new float[]{100, 100, 80, 30, 100, 100, 80, 30});

                        container.setBackground(background);

                        TextView label = new TextView(context);
                        label.setText(textKeepUnread);
                        label.setTextSize(18);
                        label.setTextColor(Color.WHITE);
                        label.setId(View.generateViewId());
                        RelativeLayout.LayoutParams labelParams = new RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                        labelParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                        labelParams.setMargins(40, 0, 0, 0);
                        container.addView(label, labelParams);

                        Switch switchView = new Switch(context);
                        RelativeLayout.LayoutParams switchParams = new RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                        switchParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                        switchParams.setMargins(0, 0, 40, 0);
                        keepUnread = prefs.getBoolean("keep_unread", false);
                        switchView.setChecked(keepUnread);
                        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            keepUnread = isChecked;
                            prefs.edit().putBoolean("keep_unread", isChecked).apply();
                        });

                        container.addView(switchView, switchParams);

                        ((ListView) viewGroup.getChildAt(0)).addFooterView(container);
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                loadPackageParam.classLoader.loadClass(Constants.MARK_AS_READ_HOOK.className),
                Constants.MARK_AS_READ_HOOK.methodName,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (keepUnread) {
                            param.setResult(null);
                        }
                    }
                }
        );
    }
}
