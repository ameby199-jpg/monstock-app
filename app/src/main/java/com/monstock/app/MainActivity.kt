package com.monstock.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.monstock.app.databinding.ActivityMainBinding
import com.monstock.app.ui.IngredientsFragment
import com.monstock.app.ui.LoginFragment
import com.monstock.app.ui.ReportsFragment
import com.monstock.app.ui.SalesFragment
import com.monstock.app.ui.SellFragment
import com.monstock.app.ui.StockFragment
import com.monstock.app.util.EmployeeSession
import com.monstock.app.util.ThemePrefs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemePrefs.getSelectedTheme(this).styleRes)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_stock -> StockFragment()
                R.id.nav_sell -> SellFragment()
                R.id.nav_sales -> SalesFragment()
                R.id.nav_ingredients -> IngredientsFragment()
                R.id.nav_reports -> ReportsFragment()
                else -> StockFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            // Le code personnel est redemandé à chaque ouverture de l'app, avant tout accès.
            EmployeeSession.logout(this)
            showLogin()
        }
    }

    private fun showLogin() {
        binding.bottomNav.visibility = android.view.View.GONE
        val loginFragment = LoginFragment()
        loginFragment.onLoginSuccess = { showMainUi() }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, loginFragment)
            .commit()
    }

    private fun showMainUi() {
        binding.bottomNav.visibility = android.view.View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, SellFragment())
            .commit()
    }
}
