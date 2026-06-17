package com.trending.products.data.repository

import com.trending.products.data.model.Product
import com.trending.products.data.model.ProductSource
import com.trending.products.data.model.TrendDirection
import com.trending.products.data.network.JsonProduct
import com.trending.products.data.network.RetrofitClient
import com.trending.products.data.network.TimeframeData
import com.trending.products.data.network.TrendingJsonData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** الأقسام الأربعة لفترة زمنية واحدة، جاهزة للعرض. */
data class CategoryProducts(
    val topSelling: List<Product>,
    val alibaba: List<Product>,
    val trending: List<Product>,
    val exclusive: List<Product>
)

class ProductRepository {

    /** يجلب كامل البيانات (3 فترات × 4 أقسام) مرة واحدة من GitHub. */
    suspend fun getTrendingData(): Result<TrendingJsonData> = withContext(Dispatchers.IO) {
        runCatching {
            val response = RetrofitClient.trendingDataApi.getTrendingData()
            if (response.isSuccessful) {
                response.body() ?: throw Exception("تعذّر جلب البيانات.")
            } else {
                throw Exception("تعذّر الاتصال بالخادم (${response.code()}).")
            }
        }
    }

    /** يحوّل بيانات فترة زمنية واحدة إلى أقسام جاهزة للعرض. */
    fun sliceTimeframe(data: TrendingJsonData?, timeframe: String): CategoryProducts {
        val tf: TimeframeData? = when (timeframe) {
            "week"  -> data?.week
            "month" -> data?.month
            else    -> data?.day
        }
        return CategoryProducts(
            topSelling = tf?.topSelling.orEmpty().mapNotNull { it.toProduct() },
            alibaba    = tf?.alibaba.orEmpty().mapNotNull { it.toProduct() },
            trending   = tf?.trending.orEmpty().mapNotNull { it.toProduct() },
            exclusive  = tf?.exclusive.orEmpty().mapNotNull { it.toProduct() }
        )
    }

    private fun JsonProduct.toProduct(): Product? {
        if (name.isNullOrBlank()) return null
        val src = when (source?.uppercase()) {
            "ALIEXPRESS"    -> ProductSource.ALIEXPRESS
            "ALIBABA"       -> ProductSource.ALIBABA
            "GOOGLE_TRENDS" -> ProductSource.GOOGLE_TRENDS
            "TIKTOK_SHOP"   -> ProductSource.TIKTOK_SHOP
            "MADE_IN_CHINA" -> ProductSource.MADE_IN_CHINA
            "EBAY"          -> ProductSource.EBAY
            else            -> ProductSource.AMAZON
        }
        val direction = when (trend?.uppercase()) {
            "DOWN"   -> TrendDirection.DOWN
            "STABLE" -> TrendDirection.STABLE
            else     -> TrendDirection.UP
        }
        return Product(
            id = id ?: name.hashCode().toString(),
            name = name,
            nameAr = name,
            description = description ?: "",
            descriptionAr = description ?: "",
            imageUrl = imageUrl ?: "",
            price = price ?: "",
            currency = currency ?: "USD",
            category = category ?: "",
            categoryAr = category ?: "",
            source = src,
            trendScore = trendScore ?: interestScore ?: 50,
            salesCount = salesCount ?: "",
            rating = rating ?: 0f,
            reviewCount = 0,
            url = url ?: "",
            tags = listOf(category ?: "", "ترند"),
            isNew = isNew ?: false,
            trendDirection = direction
        )
    }
}
