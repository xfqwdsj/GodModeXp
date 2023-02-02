package com.kaisar.xposed.godmode.injection

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.XModuleResources
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.kaisar.xposed.godmode.GodModeApplication
import com.kaisar.xposed.godmode.injection.bridge.GodModeManager
import com.kaisar.xposed.godmode.injection.bridge.ManagerObserver
import com.kaisar.xposed.godmode.injection.hook.ActivityLifecycleHook
import com.kaisar.xposed.godmode.injection.hook.DispatchKeyEventHook
import com.kaisar.xposed.godmode.injection.hook.DisplayPropertiesHook
import com.kaisar.xposed.godmode.injection.hook.EventHandlerHook
import com.kaisar.xposed.godmode.injection.hook.SystemPropertiesHook
import com.kaisar.xposed.godmode.injection.hook.SystemPropertiesStringHook
import com.kaisar.xposed.godmode.injection.util.Logger
import com.kaisar.xposed.godmode.injection.util.PackageManagerUtils
import com.kaisar.xposed.godmode.injection.util.Property
import com.kaisar.xposed.godmode.rule.ActRules
import com.kaisar.xposed.godmode.service.GodModeManagerService
import com.kaisar.xservicemanager.XServiceManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.IXposedHookZygoteInit.StartupParam
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import xyz.xfqlittlefan.godmode.R
import java.io.File

/**
 * Created by jrsen on 17-10-13.
 */
class GodModeInjector : IXposedHookLoadPackage, IXposedHookZygoteInit {
    // Injector Res
    override fun initZygote(startupParam: StartupParam) {
        modulePath = startupParam.modulePath
        moduleRes = XModuleResources.createInstance(modulePath, null)
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        if (R.string.res_inject_success ushr 24 == 0x7f) {
            XposedBridge.log("package id must NOT be 0x7f, reject loading...")
            return
        }
        if (!loadPackageParam.isFirstApplication) {
            return
        }
        Companion.loadPackageParam = loadPackageParam
        val packageName = loadPackageParam.packageName
        if (packageName == "android") { //Run in system process
            Logger.d(GodModeApplication.TAG, "inject GodModeManagerService as system service.")
            XServiceManager.initForSystemServer()
            XServiceManager.registerService("godmode") { GodModeManagerService(it) }
        } else { //Run in other application processes
            XposedHelpers.findAndHookMethod(Activity::class.java,
                "onCreate",
                Bundle::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        //Volume key select old
                        val activity = param.thisObject as Activity
                        dispatchKeyEventHook.setActivity(activity)
                        injectModuleResources(activity.resources)
                        super.afterHookedMethod(param)
                    }
                })
            registerHook()
            val gmManager = GodModeManager.getDefault()
            gmManager.addObserver(loadPackageParam.packageName, ManagerObserver())
            switchProp.set(gmManager.isInEditMode)
            actRuleProp.set(gmManager.getRules(loadPackageParam.packageName))
        }
    }

    private fun registerHook() {
        //hook activity#lifecycle block view
        val lifecycleHook = ActivityLifecycleHook()
        actRuleProp.addOnPropertyChangeListener(lifecycleHook)
        XposedHelpers.findAndHookMethod(Activity::class.java, "onPostResume", lifecycleHook)
        XposedHelpers.findAndHookMethod(Activity::class.java, "onDestroy", lifecycleHook)

//        DisplayPropertiesHook displayPropertiesHook = new DisplayPropertiesHook();
//        switchProp.addOnPropertyChangeListener(displayPropertiesHook);
//        XposedHelpers.findAndHookConstructor(View.class, Context.class, displayPropertiesHook);

        // Hook debug layout
        try {
            if (Build.VERSION.SDK_INT < 29) {
                val systemPropertiesHook = SystemPropertiesHook()
                switchProp.addOnPropertyChangeListener(systemPropertiesHook)
                XposedHelpers.findAndHookMethod(
                    "android.os.SystemProperties",
                    ClassLoader.getSystemClassLoader(),
                    "native_get_boolean",
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    systemPropertiesHook
                )
            } else {
                val systemPropertiesStringHook = SystemPropertiesStringHook()
                switchProp.addOnPropertyChangeListener(systemPropertiesStringHook)
                XposedBridge.hookAllMethods(
                    XposedHelpers.findClass(
                        "android.os.SystemProperties", ClassLoader.getSystemClassLoader()
                    ), "native_get", systemPropertiesStringHook
                )
                val displayPropertiesHook = DisplayPropertiesHook()
                switchProp.addOnPropertyChangeListener(displayPropertiesHook)
                XposedHelpers.findAndHookMethod(
                    "android.sysprop.DisplayProperties",
                    ClassLoader.getSystemClassLoader(),
                    "debug_layout",
                    displayPropertiesHook
                )
            }

            //Disable show layout margin bound
            XposedHelpers.findAndHookMethod(
                ViewGroup::class.java,
                "onDebugDrawMargins",
                Canvas::class.java,
                Paint::class.java,
                XC_MethodReplacement.DO_NOTHING
            )

            //Disable GM component show layout bounds
            val disableDebugDraw: XC_MethodHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    if (ViewHelper.TAG_GM_CMP == view.tag) {
                        param.result = null
                    }
                }
            }
            XposedHelpers.findAndHookMethod(
                ViewGroup::class.java, "onDebugDraw", Canvas::class.java, disableDebugDraw
            )
            XposedHelpers.findAndHookMethod(
                View::class.java, "debugDrawFocus", Canvas::class.java, disableDebugDraw
            )
        } catch (e: Throwable) {
            Logger.e(GodModeApplication.TAG, "Hook debug layout error", e)
        }
        val eventHandlerHook = EventHandlerHook()
        switchProp.addOnPropertyChangeListener(eventHandlerHook)
        //Volume key select
        //XposedHelpers.findAndHookMethod(Activity.class, "dispatchKeyEvent", KeyEvent.class, eventHandlerHook);
        //Drag view support
        XposedHelpers.findAndHookMethod(
            View::class.java, "dispatchTouchEvent", MotionEvent::class.java, eventHandlerHook
        )
    }

    internal enum class State {
        UNKNOWN, ALLOWED, BLOCKED
    }

    companion object {
        @JvmField
        val switchProp = Property<Boolean>()
        val actRuleProp = Property<ActRules>()
        var loadPackageParam: LoadPackageParam? = null

        @JvmField
        var moduleRes: Resources? = null
        private var state = State.UNKNOWN
        private val dispatchKeyEventHook = DispatchKeyEventHook()
        private var modulePath: String? = null

        @JvmStatic
        fun notifyEditModeChanged(enable: Boolean) {
            if (state == State.UNKNOWN) {
                state = if (checkBlockList(
                        loadPackageParam!!.packageName
                    )
                ) State.BLOCKED else State.ALLOWED
            }
            if (state == State.ALLOWED) {
                switchProp.set(enable)
            }
            dispatchKeyEventHook.setDisplay(enable)
        }

        @JvmStatic
        fun notifyViewRulesChanged(actRules: ActRules) {
            actRuleProp.set(actRules)
        }

        /**
         * Inject resources into hook software - Code from qnotified
         *
         * @param res Inject software resources
         */
        @JvmStatic
        fun injectModuleResources(res: Resources?) {
            if (res == null) {
                return
            }
            try {
                res.getString(R.string.res_inject_success)
                return
            } catch (ignored: Resources.NotFoundException) {
            }
            try {
                val sModulePath = modulePath ?: throw RuntimeException(
                    "get module path failed, loader=" + GodModeInjector::class.java.classLoader
                )
                val assets = res.assets
                @SuppressLint("DiscouragedPrivateApi") val addAssetPath =
                    AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
                addAssetPath.isAccessible = true
                val cookie = addAssetPath.invoke(assets, sModulePath) as Int
                try {
                    Logger.i(
                        GodModeApplication.TAG,
                        "injectModuleResources: " + res.getString(R.string.res_inject_success)
                    )
                } catch (e: Resources.NotFoundException) {
                    Logger.e(
                        GodModeApplication.TAG,
                        "Fatal: injectModuleResources: test injection failure!"
                    )
                    Logger.e(
                        GodModeApplication.TAG,
                        "injectModuleResources: cookie=" + cookie + ", path=" + sModulePath + ", loader=" + GodModeInjector::class.java.classLoader
                    )
                    var length: Long = -1
                    var read = false
                    var exist = false
                    var isDir = false
                    try {
                        val f = File(sModulePath)
                        exist = f.exists()
                        isDir = f.isDirectory
                        length = f.length()
                        read = f.canRead()
                    } catch (e2: Throwable) {
                        Logger.e(GodModeApplication.TAG, "Open module error", e2)
                    }
                    Logger.e(
                        GodModeApplication.TAG,
                        "sModulePath: exists = " + exist + ", isDirectory = " + isDir + ", canRead = " + read + ", fileLength = " + length
                    )
                }
            } catch (e: Exception) {
                Logger.e(GodModeApplication.TAG, "Inject module resources error", e)
            }
        }

        private fun checkBlockList(packageName: String): Boolean {
            if (TextUtils.equals("com.android.systemui", packageName)) {
                return true
            }
            try {
                //检查是否为launcher应用
                val homeIntent = Intent(Intent.ACTION_MAIN)
                homeIntent.addCategory(Intent.CATEGORY_HOME)
                var resolveInfoList: List<ResolveInfo>? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PackageManagerUtils.queryIntentActivities(
                            homeIntent, null, PackageManager.MATCH_ALL, 0
                        )
                    } else {
                        PackageManagerUtils.queryIntentActivities(homeIntent, null, 0, 0)
                    }
                //            Logger.d(TAG, "launcher apps:" + resolveInfoList);
                if (resolveInfoList != null) {
                    for (resolveInfo in resolveInfoList) {
                        if (!TextUtils.equals(
                                "com.android.settings", packageName
                            ) && TextUtils.equals(resolveInfo.activityInfo.packageName, packageName)
                        ) {
                            return true
                        }
                    }
                }

                //检查是否为键盘应用
                val keyboardIntent = Intent("android.view.InputMethod")
                resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PackageManagerUtils.queryIntentServices(
                        keyboardIntent, null, PackageManager.MATCH_ALL, 0
                    )
                } else {
                    PackageManagerUtils.queryIntentServices(keyboardIntent, null, 0, 0)
                }
                //            Logger.d(TAG, "keyboard apps:" + resolveInfoList);
                if (resolveInfoList != null) {
                    for (resolveInfo in resolveInfoList) {
                        if (TextUtils.equals(resolveInfo.serviceInfo.packageName, packageName)) {
                            return true
                        }
                    }
                }

                //检查是否为无界面应用
                val packageInfo = PackageManagerUtils.getPackageInfo(
                    packageName, PackageManager.GET_ACTIVITIES, 0
                )
                if (packageInfo?.activities != null && packageInfo.activities.isEmpty()) {
//                Logger.d(TAG, "no user interface app:" + resolveInfoList);
                    return true
                }
            } catch (t: Throwable) {
                Logger.e(GodModeApplication.TAG, "checkWhiteListPackage crash", t)
            }
            return false
        }
    }
}