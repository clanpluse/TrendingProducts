package com.trending.products.ui.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.trending.products.R
import com.trending.products.data.model.Product
import com.trending.products.data.model.ProductSource

class ProductAdapter : ListAdapter<Product, ProductAdapter.ProductViewHolder>(ProductDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_card, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProduct: ImageView = itemView.findViewById(R.id.ivProduct)
        private val tvProductName: TextView = itemView.findViewById(R.id.tvProductName)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvRating: TextView = itemView.findViewById(R.id.tvRating)
        private val tvSalesCount: TextView = itemView.findViewById(R.id.tvSalesCount)
        private val tvTrendScore: TextView = itemView.findViewById(R.id.tvTrendScore)
        private val tvNewBadge: TextView = itemView.findViewById(R.id.tvNewBadge)
        private val tvSource: TextView = itemView.findViewById(R.id.tvSource)
        private val chipGroupTags: ChipGroup = itemView.findViewById(R.id.chipGroupTags)
        private val btnViewProduct: Button = itemView.findViewById(R.id.btnViewProduct)

        fun bind(product: Product) {
            tvProductName.text = product.nameAr
            tvCategory.text = "● ${product.categoryAr}"
            tvDescription.text = product.descriptionAr
            tvPrice.text = "$${product.price}"
            tvRating.text = product.rating.toString()
            tvSalesCount.text = product.salesCount
            tvTrendScore.text = "${product.trendScore}%"
            tvSource.text = " ${product.source.displayNameAr} "
            tvNewBadge.visibility = if (product.isNew) View.VISIBLE else View.GONE

            Glide.with(itemView.context)
                .load(product.imageUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.darker_gray)
                .centerCrop()
                .into(ivProduct)

            setupTags(product.tags, itemView.context)

            btnViewProduct.setOnClickListener {
                openUrl(itemView.context, product.url)
            }
        }

        private fun setupTags(tags: List<String>, context: Context) {
            chipGroupTags.removeAllViews()
            tags.take(4).forEach { tag ->
                val chip = Chip(context).apply {
                    text = tag
                    textSize = 10f
                    isClickable = false
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                        context.getColor(R.color.background)
                    )
                    setTextColor(context.getColor(R.color.text_secondary))
                    chipStrokeWidth = 1f
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(
                        context.getColor(R.color.divider)
                    )
                }
                chipGroupTags.addView(chip)
            }
        }

        private fun openUrl(context: Context, url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                // URL not openable on this device
            }
        }
    }

    class ProductDiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Product, newItem: Product) = oldItem == newItem
    }
}
