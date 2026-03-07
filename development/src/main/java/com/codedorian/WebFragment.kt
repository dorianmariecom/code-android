package com.codedorian

import android.Manifest
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging
import dev.hotwire.navigation.destinations.HotwireDestinationDeepLink
import dev.hotwire.navigation.fragments.HotwireWebFragment
import kotlinx.coroutines.launch
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import org.json.JSONObject

@HotwireDestinationDeepLink("hotwire://fragment/web")
class WebFragment : HotwireWebFragment() {
    private val viewModel = NotificationTokenViewModel()
    private var webView: WebView? = null
    private var lastCompletedLocation: String? = null
    private var scrollListenerAttached = false
    private var pendingScrollRestoreY: Int? = null
    private var jsScrollPersistScheduled = false

    override fun onStart() {
        super.onStart()
        scheduleAttachWebViewHooks()

        KeyboardVisibilityEvent.setEventListener(requireActivity()) { isOpen ->
            activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)?.isVisible = !isOpen
            toolbarForNavigation()?.isVisible = !isOpen
        }
    }

    override fun onVisitCompleted(
        location: String,
        completedOffline: Boolean,
    ) {
        val activity = activity as? MainActivity ?: return
        lastCompletedLocation = location
        attachWebViewHooks()
        activity.persistLastTabLocation(location, navigator)
        pendingScrollRestoreY =
            activity.consumePendingScrollRestore(location)
                ?: return
        applyPendingScrollRestore(location)
    }

    override fun onPause() {
        super.onPause()
        persistVisiblePageState()
    }

    override fun onStop() {
        persistVisiblePageState()
        super.onStop()
    }

    private fun persistVisiblePageState() {
        if (webView == null) {
            attachWebViewHooks()
        }
        val activity = activity as? MainActivity ?: return
        val location = webView?.url ?: lastCompletedLocation
        if (!location.isNullOrBlank()) {
            activity.persistLastTabLocation(location, navigator)
        }
        persistScrollFromJs(location, activity, fromLifecycle = true)
    }

    private val contract = ActivityResultContracts.RequestPermission()
    private val requestPermissionLauncher =
        registerForActivityResult(contract) { isGranted ->
            if (isGranted) registerForTokenChanges()
        }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            requestPermissionLauncher.launch(permission)
        } else {
            registerForTokenChanges()
        }
    }

    private fun registerForTokenChanges() {
        val firebase = FirebaseMessaging.getInstance()
        firebase.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.registerToken(task.result)
                }
            }
        }
    }

    private fun configureWebView(webView: WebView?) {
        val target = webView ?: return
        target.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(target, true)
        }
    }

    private fun attachWebViewHooks() {
        val candidate = findWebView(view)
        if (candidate == null) {
            return
        }

        if (webView !== candidate) {
            webView = candidate
            configureWebView(webView)
            scrollListenerAttached = false
        }

        if (!scrollListenerAttached) {
            webView?.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val activity = activity as? MainActivity ?: return@setOnScrollChangeListener
                val location = webView?.url ?: lastCompletedLocation
                scheduleJsScrollPersist(activity, location)
            }
            scrollListenerAttached = true
        }

        applyPendingScrollRestore(lastCompletedLocation)
    }

    private fun scheduleAttachWebViewHooks(remainingAttempts: Int = 20) {
        attachWebViewHooks()
        if (webView != null || remainingAttempts <= 0) return
        view?.postDelayed({ scheduleAttachWebViewHooks(remainingAttempts - 1) }, 120)
    }

    private fun scheduleJsScrollPersist(
        activity: MainActivity,
        location: String?,
    ) {
        if (jsScrollPersistScheduled) return
        jsScrollPersistScheduled = true
        webView?.postDelayed(
            {
                jsScrollPersistScheduled = false
                persistScrollFromJs(location, activity, fromLifecycle = false)
            },
            120,
        )
    }

    private fun persistScrollFromJs(
        location: String?,
        activity: MainActivity,
        fromLifecycle: Boolean,
    ) {
        val targetWebView = webView ?: return
        targetWebView.evaluateJavascript(
            "(function(){return {" +
                "href:(window.location&&window.location.href)||''," +
                "y:(window.scrollY||document.documentElement.scrollTop||document.body.scrollTop||0)" +
                "};})()",
        ) { raw ->
            val parsed = runCatching { JSONObject(raw ?: "{}") }.getOrNull()
            val jsLocation = parsed?.optString("href", "")?.ifBlank { null } ?: location
            val scrollY = parsed?.optInt("y", 0) ?: 0
            if (fromLifecycle && scrollY == 0 && !jsLocation.isNullOrBlank()) {
                val saved = activity.savedScrollForLocation(jsLocation) ?: 0
                if (saved > 0) {
                    return@evaluateJavascript
                }
            }
            if (!jsLocation.isNullOrBlank()) {
                activity.persistLastTabLocation(jsLocation, navigator)
            }
            activity.persistCurrentTabScroll(scrollY, navigator, jsLocation)
        }
    }

    private fun applyPendingScrollRestore(location: String?) {
        val targetWebView = webView ?: return
        val pending = pendingScrollRestoreY ?: return
        if (pending <= 0) {
            pendingScrollRestoreY = null
            return
        }
        targetWebView.post {
            val js =
                """
                (function() {
                  var targetY = $pending;
                  if (window.__codeDorianScrollRestore && window.__codeDorianScrollRestore.cancel) {
                    window.__codeDorianScrollRestore.cancel();
                  }

                  window.history.scrollRestoration = 'manual';
                  var cancelled = false;
                  var attempts = 0;
                  var stableHits = 0;
                  var maxAttempts = 180;
                  var listeners = [];

                  function currentY() {
                    return window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0;
                  }

                  function maxScrollY() {
                    var root = document.scrollingElement || document.documentElement || document.body;
                    if (!root) return targetY;
                    return Math.max(0, root.scrollHeight - window.innerHeight);
                  }

                  function on(name, fn) {
                    window.addEventListener(name, fn, { passive: true });
                    listeners.push([name, fn]);
                  }

                  function cleanup() {
                    listeners.forEach(function(pair) { window.removeEventListener(pair[0], pair[1]); });
                    listeners = [];
                  }

                  function tick() {
                    if (cancelled) return;
                    attempts += 1;

                    var cappedTarget = Math.min(targetY, maxScrollY());
                    window.scrollTo(0, cappedTarget);
                    var y = currentY();

                    if (Math.abs(y - cappedTarget) <= 2) {
                      stableHits += 1;
                    } else {
                      stableHits = 0;
                    }

                    if (stableHits >= 3 || attempts >= maxAttempts) {
                      cleanup();
                      return;
                    }

                    requestAnimationFrame(tick);
                  }

                  var retrigger = function() {
                    stableHits = 0;
                    tick();
                  };

                  on('load', retrigger);
                  on('pageshow', retrigger);
                  on('resize', retrigger);
                  document.addEventListener('turbo:load', retrigger);

                  tick();

                  window.__codeDorianScrollRestore = {
                    cancel: function() {
                      cancelled = true;
                      cleanup();
                      document.removeEventListener('turbo:load', retrigger);
                    }
                  };
                })();
                """.trimIndent()

            targetWebView.evaluateJavascript(js, null)
            targetWebView.postDelayed({ pendingScrollRestoreY = null }, 2500)
        }
    }

    fun reloadCurrentPage() {
        if (webView == null) {
            webView = findWebView(view)
            configureWebView(webView)
        }
        webView?.reload()
    }

    private fun findWebView(root: View?): WebView? {
        if (root == null) return null
        if (root is WebView) return root
        if (root !is ViewGroup) return null

        for (index in 0 until root.childCount) {
            val found = findWebView(root.getChildAt(index))
            if (found != null) return found
        }

        return null
    }
}
