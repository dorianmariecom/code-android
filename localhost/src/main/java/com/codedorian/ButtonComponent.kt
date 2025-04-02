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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import dev.hotwire.navigation.fragments.HotwireFragment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ButtonComponent(
    name: String,
    private val bridgeDelegate: BridgeDelegate<HotwireDestination>,
) : BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    private val buttonId = 1
    private val fragment: HotwireFragment
        get() = bridgeDelegate.destination.fragment as HotwireFragment

    override fun onReceive(message: Message) {
        if (message.event == "connect") {
            removeButton()
            addButton(message)
        } else if (message.event == "disconnect") {
            removeButton()
        }
    }

    private fun addButton(message: Message) {
        val data = message.data<MessageData>() ?: return

        val composeView =
            ComposeView(fragment.requireContext()).apply {
                id = buttonId
                setContent {
                    ToolbarButton(
                        title = data.title,
                        image = data.image,
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

        val toolbar = fragment.toolbarForNavigation()
        toolbar?.addView(composeView, layoutParams)
    }

    private fun removeButton() {
        val toolbar = fragment.toolbarForNavigation()
        val button = toolbar?.findViewById<ComposeView>(buttonId)
        toolbar?.removeView(button)
    }
}

@Composable
private fun ToolbarButton(
    title: String,
    image: String,
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
        if (image.isEmpty()) {
            Text(title)
        } else {
            Text(
                text = image,
                fontFamily = FontFamily(Font(R.font.material_symbols)),
                style = TextStyle(fontFeatureSettings = "liga"),
            )
        }
    }
}

@Serializable
data class MessageData(
    @SerialName("title") val title: String,
    @SerialName("image") val image: String,
)
