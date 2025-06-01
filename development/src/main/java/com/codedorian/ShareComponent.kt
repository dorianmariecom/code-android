package com.codedorian

import android.content.Intent
import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class ShareComponent(
    name: String,
    private val bridgeDelegate: BridgeDelegate<HotwireDestination>
) : BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    private val buttonId = 3

    override fun onReceive(message: Message) {
        when (message.event) {
            "connect" -> {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(50)
                    addButton(message)
                }
            }
            "disconnect" -> removeButton()
        }
    }

    private fun addButton(message: Message) {
        val fragment = bridgeDelegate.destination.fragment as? WebFragment ?: return
        val data = message.data<MessageData>() ?: return

        removeButton()

        val composeView = ComposeView(fragment.requireContext()).apply {
            id = buttonId
            setContent {
                ToolbarButton(
                    onClick = { share(data.url) })
            }
        }

        val layoutParams = Toolbar.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.END }

        val toolbar = fragment.toolbarForNavigation() ?: return
        toolbar.addView(composeView, layoutParams)
    }

    private fun removeButton() {
        val fragment = bridgeDelegate.destination.fragment as? WebFragment ?: return
        val toolbar = fragment.toolbarForNavigation() ?: return
        val button = toolbar.findViewById<ComposeView>(buttonId) ?: return
        toolbar.removeView(button)
    }

    private fun share(url: String) {
        val fragment = bridgeDelegate.destination.fragment as? WebFragment ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        fragment.requireActivity().startActivity(Intent.createChooser(intent, "Share via"))
    }

    @Serializable
    data class MessageData(
        val url: String
    )
}

@Composable
private fun ToolbarButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.Black
        )
    ) {
        Text(
            text = "share",
            fontFamily = FontFamily(Font(R.font.material_symbols)),
            fontSize = 28.sp,
            style = TextStyle(fontFeatureSettings = "liga")
        )
    }
}