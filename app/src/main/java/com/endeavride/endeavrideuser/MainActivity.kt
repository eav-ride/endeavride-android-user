package com.endeavride.endeavrideuser

import android.os.Bundle
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.endeavride.endeavrideuser.databinding.ActivityMainBinding
import com.endeavride.endeavrideuser.ui.login.LoginFragment
import com.endeavride.endeavrideuser.ui.login.LoginViewModel
import com.endeavride.endeavrideuser.ui.login.LoginViewModelFactory
import dagger.hilt.android.AndroidEntryPoint

class MainActivity : AppCompatActivity() {

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityMainBinding
    private lateinit var mapFragment: MapsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapFragment = MapsFragment()

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)
        navView.visibility = View.GONE

        loginViewModel = ViewModelProvider(this, LoginViewModelFactory())
            .get(LoginViewModel::class.java)

        loginViewModel.loggedInUser.observe(this,
            Observer { loggedInUser ->
                loggedInUser ?: return@Observer
                if (loggedInUser.displayName == "") {
                    navController.navigate(R.id.navigation_login)
                }
            })
        loginViewModel.loadUserInfoIfAvailable()

        val currentBackStackEntry = navController.currentBackStackEntry!!
        val savedStateHandle = currentBackStackEntry.savedStateHandle
        savedStateHandle.getLiveData<Boolean>(LoginFragment.LOGIN_SUCCESSFUL)
            .observe(currentBackStackEntry, Observer { success ->
            })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == MapsFragment.LOCATION_PERMISSION_REQUEST_CODE) {
            mapFragment.onRequestPermissionsResult(requestCode,
                permissions as Array<String>, grantResults)
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}