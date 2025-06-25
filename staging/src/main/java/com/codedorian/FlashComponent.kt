package com.codedorian

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import androidx.core.graphics.toColorInt
import android.graphics.drawable.GradientDrawable

class FlashComponent(
    name: String,
    private val bridgeDelegate: BridgeDelegate<HotwireDestination>,
) : BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    @Serializable
    data class MessageData(
        @SerialName("message") val message: String,
        @SerialName("type") val type: String,
    )

    override fun onReceive(message: Message) {
        if (message.event == "connect") {
            val data = message.data<MessageData>() ?: return
            val fragment = bridgeDelegate.destination.fragment
            if (fragment !is WebFragment && fragment !is WebModalSheetFragment) return
            val activity = fragment.activity as? MainActivity ?: return
            val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

            val borderColor = (if (data.type == "alert") "#dc2626" else "#16a34a").toColorInt()

            val container = FrameLayout(activity).apply {
                val drawable = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = 8f
                    setStroke(1, borderColor)
                }
                background = drawable
                backgroundTintList = null
            }

            // Create TextView
            val label = TextView(activity).apply {
                text = data.message
                setTextColor(borderColor)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(16, 8, 16, 8)
                backgroundTintList = null
            }

            container.addView(label)
            val layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 250
            }
            rootView.addView(container, layoutParams)

            Handler(Looper.getMainLooper()).postDelayed({
                rootView.removeView(container)
            }, 5000)
        }
    }
}