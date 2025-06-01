package com.codedorian

import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ButtonComponent(
    name: String,
    private val bridgeDelegate: BridgeDelegate<HotwireDestination>,
) : BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    @Serializable
    data class MessageData(
        @SerialName("title") val title: String,
    )

    private val buttonId = 1

    override fun onReceive(message: Message) {
        if (message.event == "connect") {
            removeButton()
            addButton(message)
        } else if (message.event == "disconnect") {
            removeButton()
        }
    }

    private fun addButton(message: Message) {
        val fragment = bridgeDelegate.destination.fragment
        if (fragment !is WebFragment && fragment !is WebModalSheetFragment) return

        val data = message.data<MessageData>() ?: return

        val composeView =
            ComposeView(fragment.requireContext()).apply {
                id = buttonId
                setContent {
                    ToolbarButton(
                        title = data.title,
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

        val toolbar =
            when (fragment) {
                is WebFragment -> fragment.toolbarForNavigation()
                is WebModalSheetFragment -> fragment.toolbarForNavigation()
                else -> null
            } ?: return

        toolbar.addView(composeView, layoutParams)
    }

    private fun removeButton() {
        val fragment = bridgeDelegate.destination.fragment
        if (fragment !is WebFragment && fragment !is WebModalSheetFragment) return

        val toolbar =
            when (fragment) {
                is WebFragment -> fragment.toolbarForNavigation()
                is WebModalSheetFragment -> fragment.toolbarForNavigation()
                else -> null
            } ?: return

        val button = toolbar.findViewById<ComposeView>(buttonId) ?: return
        toolbar.removeView(button)
    }

    @Composable
    private fun ToolbarButton(
        title: String,
        onClick: () -> Unit,
    ) {
        Button(
            modifier = Modifier.padding(horizontal = 8.dp),
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White,
                    ),
            onClick = onClick,
        ) {
            Text(title)
        }
    }
}
