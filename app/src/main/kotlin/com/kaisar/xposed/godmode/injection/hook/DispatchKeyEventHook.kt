package com.kaisar.xposed.godmode.injection.hook

import android.animation.Animator
import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.appcompat.widget.TooltipCompat
import com.kaisar.xposed.godmode.GodModeApplication
import com.kaisar.xposed.godmode.injection.GodModeInjector
import com.kaisar.xposed.godmode.injection.GodModeInjector.Companion.injectModuleResources
import com.kaisar.xposed.godmode.injection.ViewController
import com.kaisar.xposed.godmode.injection.ViewHelper
import com.kaisar.xposed.godmode.injection.bridge.GodModeManager
import com.kaisar.xposed.godmode.injection.util.CommonUtils
import com.kaisar.xposed.godmode.injection.util.GmResources
import com.kaisar.xposed.godmode.injection.util.Logger
import com.kaisar.xposed.godmode.injection.util.Property.OnPropertyChangeListener
import com.kaisar.xposed.godmode.injection.weiget.MaskView
import com.kaisar.xposed.godmode.injection.weiget.ParticleView
import com.kaisar.xposed.godmode.injection.weiget.ParticleView.OnAnimationListener
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import xyz.xfqlittlefan.godmode.R
import java.lang.ref.WeakReference
import java.util.Locale

class DispatchKeyEventHook : XC_MethodHook(), OnPropertyChangeListener<Boolean?>,
    OnSeekBarChangeListener {
    private val mViewNodes: MutableList<WeakReference<View>> = ArrayList()
    private var mCurrentViewIndex = 0
    private var mMaskView: MaskView? = null
    private var mNodeSelectorPanel: View? = null
    private var activity: Activity? = null
    private var seekbar: SeekBar? = null

    fun setActivity(a: Activity?) {
        activity = a
    }

    fun setDisplay(display: Boolean) {
        if (activity == null) return
        if (display) {
            showNodeSelectPanel(activity!!)
        } else {
            dismissNodeSelectPanel()
        }
    }

    override fun beforeHookedMethod(param: MethodHookParam) {
        if (GodModeInjector.switchProp.get() && !DispatchTouchEventHook.mDragging) {
            val activity = param.thisObject as Activity
            val event = param.args[0] as KeyEvent
            param.result = dispatchKeyEvent(activity, event)
        }
    }

    private fun dispatchKeyEvent(activity: Activity, keyEvent: KeyEvent): Boolean {
        Logger.d(GodModeApplication.TAG, keyEvent.toString())
        val action = keyEvent.action
        val keyCode = keyEvent.keyCode
        if (action == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (!mKeySelecting) {
                showNodeSelectPanel(activity)
            } else {
                //hide node select panel
                dismissNodeSelectPanel()
            }
        }
        return true
    }

    private fun showNodeSelectPanel(activity: Activity) {
        mViewNodes.clear()
        mCurrentViewIndex = 0
        //build view hierarchy tree
        mViewNodes.addAll(ViewHelper.buildViewNodes(activity.window.decorView))
        val container = activity.window.decorView as ViewGroup
        mMaskView = MaskView.makeMaskView(activity)
        mMaskView?.setMaskOverlay(OVERLAY_COLOR)
        mMaskView?.attachToContainer(container)
        try {
            injectModuleResources(activity.resources)
            val layoutInflater = LayoutInflater.from(activity)
            mNodeSelectorPanel = layoutInflater.inflate(
                GodModeInjector.moduleRes!!.getLayout(R.layout.layout_node_selector),
                container,
                false
            )
            seekbar = mNodeSelectorPanel?.findViewById(R.id.slider)
            seekbar?.max = mViewNodes.size - 1
            seekbar?.setOnSeekBarChangeListener(this)
            val btnBlock = mNodeSelectorPanel?.findViewById<View>(R.id.block)
            if (btnBlock != null) {
                TooltipCompat.setTooltipText(
                    btnBlock, GmResources.getText(R.string.accessibility_block)
                )
            }
            btnBlock?.setOnClickListener {
                try {
                    mNodeSelectorPanel?.alpha = 0f
                    val view = mViewNodes[mCurrentViewIndex].get()
                    Logger.d(GodModeApplication.TAG, "removed view = $view")
                    if (view != null) {
                        //hide overlay
                        mMaskView?.updateOverlayBounds(Rect())
                        val snapshot =
                            ViewHelper.snapshotView(ViewHelper.findTopParentViewByChildView(view))
                        val viewRule = ViewHelper.makeRule(view)
                        val particleView = ParticleView(activity)
                        particleView.setDuration(1000)
                        particleView.attachToContainer(container)
                        particleView.setOnAnimationListener(object : OnAnimationListener {
                            override fun onAnimationStart(animView: View, animation: Animator) {
                                viewRule.visibility = View.GONE
                                ViewController.applyRule(view, viewRule)
                            }

                            override fun onAnimationEnd(animView: View, animation: Animator) {
                                GodModeManager.getDefault()
                                    .writeRule(activity.packageName, viewRule, snapshot)
                                CommonUtils.recycleNullableBitmap(snapshot)
                                particleView.detachFromContainer()
                                mNodeSelectorPanel?.animate()?.alpha(1.0f)
                                    ?.setInterpolator(DecelerateInterpolator(1.0f))
                                    ?.setDuration(300)?.start()
                            }
                        })
                        particleView.boom(view)
                    }
                    mViewNodes.removeAt(mCurrentViewIndex--)
                    seekbar?.max = mViewNodes.size - 1
                } catch (e: Exception) {
                    Logger.e(GodModeApplication.TAG, "block fail", e)
                    Toast.makeText(
                        activity,
                        GmResources.getString(R.string.block_fail, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            val topContent = mNodeSelectorPanel?.findViewById<View>(R.id.topContent)
            mNodeSelectorPanel?.findViewById<View>(R.id.exchange)?.setOnClickListener {
                val display = activity.windowManager.defaultDisplay
                val width = display.width
                val targetWidth = width - width / 6
                if (topContent?.paddingRight == targetWidth) {
                    topContent.setPadding(4, 4, 12, 4)
                } else {
                    topContent?.setPadding(4, 4, targetWidth, 4)
                }
            }
            val btnUp = mNodeSelectorPanel?.findViewById<View>(R.id.Up)
            btnUp?.setOnClickListener { seekbarAdd() }
            val btnDown = mNodeSelectorPanel?.findViewById<View>(R.id.Down)
            btnDown?.setOnClickListener { seekbarReduce() }
            container.addView(mNodeSelectorPanel)
            mNodeSelectorPanel?.alpha = 0f
            mNodeSelectorPanel?.post {
                mNodeSelectorPanel?.translationX = mNodeSelectorPanel?.width?.div(2.0f) ?: 0f
                mNodeSelectorPanel?.animate()?.alpha(1f)?.translationX(0f)?.setDuration(300)
                    ?.setInterpolator(DecelerateInterpolator(1.0f))?.start()
            }
            mKeySelecting = true
            XposedHelpers.findAndHookMethod(Activity::class.java,
                "dispatchKeyEvent",
                KeyEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (GodModeInjector.switchProp.get() && !DispatchTouchEventHook.mDragging) {
                            val event = param.args[0] as KeyEvent
                            val action = event.action
                            val keyCode = event.keyCode
                            if (action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                                seekbarReduce()
                            } else if (action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                seekbarAdd()
                            }
                            param.result = true
                        }
                    }
                })
        } catch (e: Exception) {
            //god mode package uninstalled?
            Logger.e(GodModeApplication.TAG, "showNodeSelectPanel fail", e)
            mKeySelecting = false
        }
    }

    private fun seekbarAdd() {
        if (seekbar!!.progress == seekbar!!.max) {
            return
        }
        val progress = seekbar!!.progress + 1
        seekbar!!.progress = progress
        onProgressChanged(seekbar!!, progress, true)
    }

    private fun seekbarReduce() {
        if (seekbar!!.progress == 0) {
            return
        }
        val progress = seekbar!!.progress - 1
        seekbar!!.progress = progress
        onProgressChanged(seekbar!!, progress, true)
    }

    private fun dismissNodeSelectPanel() {
        if (mMaskView != null) mMaskView!!.detachFromContainer()
        mMaskView = null
        if (mNodeSelectorPanel != null) {
            val nodeSelectorPanel = mNodeSelectorPanel
            nodeSelectorPanel?.post {
                nodeSelectorPanel.animate().alpha(0f).translationX(nodeSelectorPanel.width / 2.0f)
                    .setDuration(250).setInterpolator(AccelerateInterpolator(1.0f)).withEndAction {
                        val parent = nodeSelectorPanel.parent as ViewGroup
                        parent.removeView(nodeSelectorPanel)
                    }.start()
            }
        }
        mNodeSelectorPanel = null
        mViewNodes.clear()
        mCurrentViewIndex = 0
        mKeySelecting = false
    }

    override fun onPropertyChange(enable: Boolean?) {
        if (mMaskView != null) {
            dismissNodeSelectPanel()
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            mCurrentViewIndex = progress
            val view = mViewNodes[mCurrentViewIndex].get()
            Logger.d(
                GodModeApplication.TAG,
                String.format(Locale.getDefault(), "progress=%d selected view=%s", progress, view)
            )
            if (view != null) {
                mMaskView!!.updateOverlayBounds(ViewHelper.getLocationInWindow(view))
            }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        mNodeSelectorPanel!!.alpha = 0.2f
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        mNodeSelectorPanel!!.alpha = 1f
    }

    companion object {
        private val OVERLAY_COLOR = Color.argb(150, 255, 0, 0)

        @JvmField
        @Volatile
        var mKeySelecting = false
    }
}