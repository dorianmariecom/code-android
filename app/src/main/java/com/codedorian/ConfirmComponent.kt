package com.codedorian

import androidx.appcompat.app.AlertDialog
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.Serializable

class ConfirmComponent(
    name: String,
    private val bridgeDelegate: BridgeDelegate<HotwireDestination>,
) : BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    override fun onReceive(message: Message) {
        when (message.event) {
            "show" -> {
                val fragment = bridgeDelegate.destination.fragment as? WebFragment ?: return
                val data = message.data<MessageData>() ?: return

                activeDialog?.dismiss()

                val dialog =
                    AlertDialog
                        .Builder(fragment.requireContext())
                        .setTitle(data.title)
                        .setMessage(data.description)
                        .setCancelable(true)
                        .setNegativeButton(data.cancel, null)
                        .setPositiveButton(data.confirm) { _, _ ->
                            replyTo(message.event)
                        }.create()

                dialog.setOnDismissListener {
                    if (activeDialog === dialog) activeDialog = null
                }

                dialog.setOnShowListener {
                    if (data.destructive) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                            fragment.requireContext().getColor(android.R.color.holo_red_dark),
                        )
                    }
                }

                activeDialog = dialog
                dialog.show()
            }

            "disconnect" -> {
                activeDialog?.dismiss()
                activeDialog = null
            }
        }
    }

    @Serializable
    private data class MessageData(
        val title: String,
        val description: String?,
        val destructive: Boolean,
        val confirm: String,
        val cancel: String,
    )

    companion object {
        private var activeDialog: AlertDialog? = null
    }
}
