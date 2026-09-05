package com.monstock.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.monstock.app.databinding.ActivityMainBinding
import com.monstock.app.ui.IngredientsFragment
import com.monstock.app.ui.ReportsFragment
import com.monstock.app.ui.SalesFragment
import com.monstock.app.ui.SellFragment
import com.monstock.app.ui.StockFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, StockFragment())
                .commit()
        }

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
    }
}
