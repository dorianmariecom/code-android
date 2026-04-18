package com.codedorian

import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
                    addButton()
                }
            }

            "disconnect" -> {
                removeButton()
            }
        }
    }

    private fun addButton() {
        val fragment = activeFragment() ?: return
        val toolbar = fragment.toolbarForNavigation() ?: return

        val composeView =
            ComposeView(fragment.requireContext()).apply {
                id = buttonId
                setContent {
                    ToolbarButton(
                        isRefreshing = fragment.isRefreshInProgress.collectAsState().value,
                        onClick = { fragment.reloadCurrentPage() },
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
        val fragment = activeFragment() ?: return
        val toolbar = fragment.toolbarForNavigation() ?: return
        val button = toolbar.findViewById<ComposeView>(buttonId) ?: return
        toolbar.removeView(button)
    }

    private fun activeFragment(): WebFragment? =
        (bridgeDelegate.destination.fragment as? WebFragment)?.takeIf {
            it.isAdded && it.context != null
        }
}

@Composable
private fun ToolbarButton(
    isRefreshing: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = {
            if (isRefreshing) return@Button
            onClick()
        },
        enabled = !isRefreshing,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.Black,
            ),
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                color = Color.Black,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = "refresh",
                fontFamily = FontFamily(Font(R.font.material_symbols)),
                fontSize = 28.sp,
                style = TextStyle(fontFeatureSettings = "liga"),
            )
        }
    }
}
