package com.codedorian

import android.Manifest
import android.os.Build
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _isRefreshInProgress = MutableStateFlow(false)
    val isRefreshInProgress: StateFlow<Boolean> = _isRefreshInProgress.asStateFlow()
    private var manualRefreshRequested = false

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
        super.onVisitCompleted(location, completedOffline)
        val activity = activity as? MainActivity ?: return
        logd("onVisitCompleted: location=$location completedOffline=$completedOffline")
        lastCompletedLocation = location
        attachWebViewHooks()
        activity.persistLastTabLocation(location)
        pendingScrollRestoreY =
            activity.consumePendingScrollRestore(location)
                ?: return
        logd("onVisitCompleted: pendingScrollRestoreY=$pendingScrollRestoreY")
        applyPendingScrollRestore(location)
    }

    override fun onVisitStarted(location: String) {
        super.onVisitStarted(location)
        if (manualRefreshRequested) {
            _isRefreshInProgress.value = true
            logd("onVisitStarted: manual refresh in progress location=$location")
        }
    }

    override fun onVisitRequestFinished(location: String) {
        super.onVisitRequestFinished(location)
        finishManualRefresh("onVisitRequestFinished location=$location")
    }

    override fun onVisitErrorReceived(
        location: String,
        error: dev.hotwire.core.turbo.errors.VisitError,
    ) {
        super.onVisitErrorReceived(location, error)
        finishManualRefresh("onVisitErrorReceived location=$location")
    }

    override fun onDestroyView() {
        finishManualRefresh("onDestroyView")
        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()
        logd("onPause: persist visible page state")
        persistVisiblePageState()
    }

    override fun onStop() {
        logd("onStop: persist visible page state")
        persistVisiblePageState()
        super.onStop()
    }

    private fun persistVisiblePageState() {
        if (webView == null) {
            logd("persistVisiblePageState: webView missing, trying to attach hooks")
            attachWebViewHooks()
        }
        val activity = activity as? MainActivity ?: return
        val location = webView?.url ?: lastCompletedLocation
        logd(
            "persistVisiblePageState: location=${location ?: "n/a"} lastCompleted=${lastCompletedLocation ?: "n/a"} webViewUrl=${webView?.url ?: "n/a"}",
        )
        if (!location.isNullOrBlank()) {
            activity.persistLastTabLocation(location)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                forceDark = WebSettings.FORCE_DARK_OFF
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setAlgorithmicDarkeningAllowed(false)
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(target, true)
        }
    }

    private fun attachWebViewHooks() {
        val candidate = findWebView(view)
        if (candidate == null) {
            logd("attachWebViewHooks: no webview yet")
            return
        }

        if (webView !== candidate) {
            webView = candidate
            configureWebView(webView)
            scrollListenerAttached = false
            logd("attachWebViewHooks: webview attached hash=${System.identityHashCode(candidate)}")
        } else {
            logd("attachWebViewHooks: reusing webview hash=${System.identityHashCode(candidate)}")
        }

        if (!scrollListenerAttached) {
            webView?.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val activity = activity as? MainActivity ?: return@setOnScrollChangeListener
                val location = webView?.url ?: lastCompletedLocation
                logd("onScrollChange: scrollY=$scrollY location=${location ?: "n/a"}")
                scheduleJsScrollPersist(activity, location)
            }
            scrollListenerAttached = true
            logd("attachWebViewHooks: scroll listener attached")
        }

        applyPendingScrollRestore(lastCompletedLocation)
    }

    private fun scheduleAttachWebViewHooks(remainingAttempts: Int = 20) {
        attachWebViewHooks()
        if (webView != null || remainingAttempts <= 0) return
        if (remainingAttempts == 1) logd("scheduleAttachWebViewHooks: webview still missing after retries")
        view?.postDelayed({ scheduleAttachWebViewHooks(remainingAttempts - 1) }, 120)
    }

    private fun scheduleJsScrollPersist(
        activity: MainActivity,
        location: String?,
    ) {
        if (jsScrollPersistScheduled) {
            logd("scheduleJsScrollPersist: skip because already scheduled")
            return
        }
        jsScrollPersistScheduled = true
        logd("scheduleJsScrollPersist: location=${location ?: "n/a"}")
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
        val targetWebView =
            webView ?: run {
                logd("persistScrollFromJs: skip because webView is null fromLifecycle=$fromLifecycle")
                return
            }
        targetWebView.evaluateJavascript(
            "(function(){return {" +
                "href:(window.location&&window.location.href)||''," +
                "y:(window.scrollY||document.documentElement.scrollTop||document.body.scrollTop||0)" +
                "};})()",
        ) { raw ->
            val parsed = runCatching { JSONObject(raw ?: "{}") }.getOrNull()
            if (parsed == null) {
                logd("persistScrollFromJs: failed to parse JS payload raw=${raw ?: "null"}")
            }
            val jsLocation = parsed?.optString("href", "")?.ifBlank { null } ?: location
            val scrollY = parsed?.optInt("y", 0) ?: 0
            logd(
                "persistScrollFromJs: fromLifecycle=$fromLifecycle jsLocation=${jsLocation ?: "n/a"} scrollY=$scrollY raw=${raw ?: "null"}",
            )
            val persistLocation =
                jsLocation
                    ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            if (jsLocation != null && persistLocation == null) {
                logd("persistScrollFromJs: ignore non-http location=$jsLocation")
            }
            if (fromLifecycle && scrollY == 0 && !persistLocation.isNullOrBlank()) {
                val saved = activity.savedScrollForLocation(persistLocation) ?: 0
                if (saved > 0) {
                    logd("persistScrollFromJs: skip zero overwrite for saved scroll=$saved location=$persistLocation")
                    return@evaluateJavascript
                }
            }
            if (!persistLocation.isNullOrBlank()) {
                activity.persistLastTabLocation(persistLocation)
            }
            activity.persistCurrentTabScroll(scrollY, persistLocation)
        }
    }

    private fun applyPendingScrollRestore(location: String?) {
        val targetWebView =
            webView ?: run {
                logd("applyPendingScrollRestore: skip because webView is null location=${location ?: "n/a"}")
                return
            }
        val pending =
            pendingScrollRestoreY ?: run {
                logd("applyPendingScrollRestore: skip because no pending scroll location=${location ?: "n/a"}")
                return
            }
        if (pending <= 0) {
            logd("applyPendingScrollRestore: clear non-positive pending value=$pending location=${location ?: "n/a"}")
            pendingScrollRestoreY = null
            return
        }
        logd("applyPendingScrollRestore: location=${location ?: "n/a"} targetY=$pending")
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

            targetWebView.evaluateJavascript(js) { result ->
                logd("applyPendingScrollRestore: js applied result=${result ?: "null"} targetY=$pending")
            }
            targetWebView.postDelayed(
                {
                    logd("applyPendingScrollRestore: clear pending restore")
                    pendingScrollRestoreY = null
                },
                2500,
            )
        }
    }

    fun reloadCurrentPage() {
        if (webView == null) {
            webView = findWebView(view)
            configureWebView(webView)
        }
        if (webView == null) {
            finishManualRefresh("reloadCurrentPage: webView missing")
            return
        }
        manualRefreshRequested = true
        _isRefreshInProgress.value = true
        webView?.reload()
    }

    private fun finishManualRefresh(reason: String) {
        if (!manualRefreshRequested && !_isRefreshInProgress.value) return
        manualRefreshRequested = false
        _isRefreshInProgress.value = false
        logd("finishManualRefresh: $reason")
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

    private fun logd(message: String) {
        Log.d(TAG, message)
    }

    companion object {
        private const val TAG = "WebFragment"
    }
}
