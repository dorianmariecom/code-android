package com.codedorian

import dev.hotwire.core.turbo.webview.HotwireWebView
import dev.hotwire.navigation.destinations.HotwireDestinationDeepLink
import dev.hotwire.navigation.fragments.HotwireWebBottomSheetFragment

@HotwireDestinationDeepLink(uri = "hotwire://fragment/web/modal/sheet")
class WebModalSheetFragment : HotwireWebBottomSheetFragment() {
    private val imageSaver = WebImageSaver(this)

    override fun onWebViewAttached(webView: HotwireWebView) {
        super.onWebViewAttached(webView)
        imageSaver.attach(webView)
    }

    override fun onWebViewDetached(webView: HotwireWebView) {
        imageSaver.detach()
        super.onWebViewDetached(webView)
    }

    override fun onDestroyView() {
        imageSaver.detach()
        super.onDestroyView()
    }
}
