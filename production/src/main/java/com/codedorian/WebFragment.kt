package com.codedorian

import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging
import dev.hotwire.navigation.destinations.HotwireDestinationDeepLink
import dev.hotwire.navigation.fragments.HotwireWebFragment
import kotlinx.coroutines.launch
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent

@HotwireDestinationDeepLink("hotwire://fragment/web")
class WebFragment : HotwireWebFragment() {
    private val viewModel = NotificationTokenViewModel()
    override fun onStart() {
        super.onStart()

        KeyboardVisibilityEvent.setEventListener(requireActivity()) { isOpen ->
            activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)?.isVisible = !isOpen
            toolbarForNavigation()?.isVisible = !isOpen
        }
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
}
