package com.trending.products.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.trending.products.R
import com.trending.products.data.model.Product
import com.trending.products.ui.adapter.ProductAdapter
import com.trending.products.viewmodel.ProductViewModel
import com.trending.products.viewmodel.UiState

class ProductListFragment : Fragment() {

    companion object {
        private const val ARG_TAB = "tab_type"
        const val TAB_TOP_SELLING = 0
        const val TAB_ALIBABA = 1
        const val TAB_TRENDING = 2
        const val TAB_EXCLUSIVE = 3

        fun newInstance(tabType: Int) = ProductListFragment().apply {
            arguments = Bundle().apply { putInt(ARG_TAB, tabType) }
        }
    }

    private val viewModel: ProductViewModel by activityViewModels()
    private lateinit var adapter: ProductAdapter
    private var tabType = 0

    private lateinit var recyclerView: RecyclerView
    private lateinit var shimmer: ShimmerFrameLayout
    private lateinit var errorLayout: LinearLayout
    private lateinit var tvError: TextView
    private lateinit var btnRetry: Button
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabType = arguments?.getInt(ARG_TAB) ?: 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_product_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        shimmer = view.findViewById(R.id.shimmerLayout)
        errorLayout = view.findViewById(R.id.errorLayout)
        tvError = view.findViewById(R.id.tvError)
        btnRetry = view.findViewById(R.id.btnRetry)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)

        adapter = ProductAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        swipeRefresh.setColorSchemeColors(requireContext().getColor(R.color.primary))
        swipeRefresh.setOnRefreshListener { viewModel.loadAll() }
        btnRetry.setOnClickListener { viewModel.loadAll() }

        observeData()
    }

    private fun observeData() {
        val liveData = when (tabType) {
            TAB_TOP_SELLING -> viewModel.topSelling
            TAB_ALIBABA     -> viewModel.alibaba
            TAB_TRENDING    -> viewModel.trending
            TAB_EXCLUSIVE   -> viewModel.exclusive
            else            -> viewModel.topSelling
        }
        liveData.observe(viewLifecycleOwner) { state ->
            swipeRefresh.isRefreshing = false
            when (state) {
                is UiState.Loading -> showLoading()
                is UiState.Success -> showProducts(state.data)
                is UiState.Error   -> showError(state.message)
            }
        }
    }

    private fun showLoading() {
        shimmer.visibility = View.VISIBLE
        shimmer.startShimmer()
        recyclerView.visibility = View.GONE
        errorLayout.visibility = View.GONE
    }

    private fun showProducts(products: List<Product>) {
        shimmer.stopShimmer()
        shimmer.visibility = View.GONE
        errorLayout.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        adapter.submitList(products)
    }

    private fun showError(message: String) {
        shimmer.stopShimmer()
        shimmer.visibility = View.GONE
        recyclerView.visibility = View.GONE
        errorLayout.visibility = View.VISIBLE
        tvError.text = message
    }
}
