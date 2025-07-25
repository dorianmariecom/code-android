package com.codedorian

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

class RefreshComponent(
    name: String,
    private val bridgeDelegate: BridgeDelegate<HotwireDestination>,
) : BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    private val buttonId = AppConfig.refreshButtonId

    override fun onReceive(message: Message) {
        when (message.event) {
            "connect" -> {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(AppConfig.refreshDelay)
                    removeButton()
                    addButton(message)
                }
            }
            "disconnect" -> removeButton()
        }
    }

    private fun addButton(message: Message) {
        val fragment = bridgeDelegate.destination.fragment as? WebFragment ?: return
        val toolbar = fragment.toolbarForNavigation() ?: return

        val composeView =
            ComposeView(fragment.requireContext()).apply {
                id = buttonId
                setContent {
                    ToolbarButton(
                        onClick = { replyTo(message.event) },
                    )
                }
            }

        val layoutParams =
            Toolbar
                .LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.END }

        toolbar.addView(composeView, layoutParams)
    }

    private fun removeButton() {
        val fragment = bridgeDelegate.destination.fragment as? WebFragment ?: return
        val toolbar = fragment.toolbarForNavigation() ?: return
        val button = toolbar.findViewById<ComposeView>(buttonId) ?: return
        toolbar.removeView(button)
    }
}

@Composable
private fun ToolbarButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.Black,
            ),
    ) {
        Text(
            text = "refresh",
            fontFamily = FontFamily(Font(R.font.material_symbols)),
            fontSize = 28.sp,
            style = TextStyle(fontFeatureSettings = "liga"),
        )
    }
}
