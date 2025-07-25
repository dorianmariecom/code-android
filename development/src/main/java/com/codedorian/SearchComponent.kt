package com.codedorian

import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class SearchComponent(
    name: String,
    private val bridgeDelegate: BridgeDelegate<HotwireDestination>,
) : BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    private val searchViewId = AppConfig.searchViewId

    override fun onReceive(message: Message) {
        when (message.event) {
            "connect" -> {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(AppConfig.searchDelay)
                    addSearchView()
                }
            }
            "disconnect" -> removeSearchView()
        }
    }

    @Serializable
    private data class QueryMessageData(
        val query: String?,
    )

    private fun addSearchView() {
        val fragment = bridgeDelegate.destination.fragment as? WebFragment ?: return
        val toolbar = fragment.toolbarForNavigation() ?: return
        val existingSearchView = toolbar.findViewById<SearchView>(searchViewId)
        if (existingSearchView != null) return

        val context = fragment.requireContext()
        val searchView =
            SearchView(context).apply {
                id = searchViewId
                isFocusable = true
                isFocusableInTouchMode = true

                setOnSearchClickListener {
                    layoutParams =
                        Toolbar.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                }

                setOnCloseListener {
                    layoutParams =
                        Toolbar
                            .LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ).apply { gravity = Gravity.END }
                    false
                }

                setOnQueryTextListener(
                    object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean {
                            replyTo("connect", QueryMessageData(query))
                            return true
                        }

                        override fun onQueryTextChange(newText: String?): Boolean {
                            replyTo("connect", QueryMessageData(newText))
                            return true
                        }
                    },
                )
            }

        val layoutParams =
            Toolbar
                .LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply { gravity = Gravity.END }

        toolbar.addView(searchView, layoutParams)
    }

    private fun removeSearchView() {
        val fragment = bridgeDelegate.destination.fragment as? WebFragment ?: return
        val toolbar = fragment.toolbarForNavigation() ?: return
        val existingSearchView = toolbar.findViewById<SearchView>(searchViewId)
        toolbar.removeView(existingSearchView)
    }
}
