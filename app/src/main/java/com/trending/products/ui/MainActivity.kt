package com.trending.products.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.trending.products.R
import com.trending.products.viewmodel.ProductViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: ProductViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SetupActivity.isSetupDone(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        SetupActivity.loadSavedKeys(this)
        setContentView(R.layout.activity_main)

        val tabLayout   = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager   = findViewById<ViewPager2>(R.id.viewPager)
        val btnRefresh  = findViewById<ImageButton>(R.id.btnRefresh)
        val tvUpdated   = findViewById<TextView>(R.id.tvLastUpdated)
        val chipGroup   = findViewById<ChipGroup>(R.id.chipGroupTime)

        viewPager.adapter = ProductPagerAdapter(this)
        viewPager.offscreenPageLimit = 3

        // تبويبا "علي بابا" و"الأعلى مبيعاً" مخفيّان مؤقتاً
        val tabs = listOf("📈 رائج", "🆕 الأكثر مبيعاً الجديد", "💎 حصري جديد")
        TabLayoutMediator(tabLayout, viewPager) { tab, pos -> tab.text = tabs[pos] }.attach()

        // فلتر الفترة الزمنية
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val tf = when (checkedIds.firstOrNull()) {
                R.id.chipWeek  -> "week"
                R.id.chipMonth -> "month"
                else           -> "day"
            }
            viewModel.setTimeframe(tf)
        }

        btnRefresh.setOnClickListener { viewModel.loadAll() }
        btnRefresh.setOnLongClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
            true
        }

        viewModel.lastUpdated.observe(this) { tvUpdated.text = "آخر تحديث: $it" }
    }
}

class ProductPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    // تبويبا "علي بابا" و"الأعلى مبيعاً" مخفيّان مؤقتاً — 3 تبويبات
    override fun getItemCount() = 3
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> ProductListFragment.newInstance(ProductListFragment.TAB_TRENDING)
        1 -> ProductListFragment.newInstance(ProductListFragment.TAB_NEW_BEST)
        2 -> ProductListFragment.newInstance(ProductListFragment.TAB_EXCLUSIVE)
        else -> ProductListFragment.newInstance(ProductListFragment.TAB_TRENDING)
    }
}
