package com.trending.products.data.repository

import com.trending.products.data.model.Product
import com.trending.products.data.model.ProductSource
import com.trending.products.data.model.TrendDirection
import com.trending.products.data.network.AliExpressSigner
import com.trending.products.data.network.ApiConfig
import com.trending.products.data.network.JsonProduct
import com.trending.products.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProductRepository {

    suspend fun getTopSellingProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
        runCatching {
            val data = fetchGithubData()
            val products = data?.topSelling?.mapNotNull { it.toProduct() }
            if (!products.isNullOrEmpty()) return@runCatching products
            throw Exception("تعذّر جلب البيانات. تحقق من الاتصال بالإنترنت.")
        }
    }

    suspend fun getChineseFactoryProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
        runCatching {
            if (ApiConfig.isAliExpressConfigured()) {
                val ali = fetchAliExpressHotProducts()
                if (ali.isNotEmpty()) return@runCatching ali
            }
            val data = fetchGithubData()
            val products = data?.chineseFactory?.mapNotNull { it.toProduct() }
            if (!products.isNullOrEmpty()) return@runCatching products
            throw Exception("تعذّر جلب بيانات مصانع الصين.")
        }
    }

    suspend fun getHotNewProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
        runCatching {
            val data = fetchGithubData()
            val products = data?.hotNew?.mapNotNull { it.toProduct() }
            if (!products.isNullOrEmpty()) return@runCatching products
            throw Exception("تعذّر جلب المنتجات الجديدة.")
        }
    }

    private suspend fun fetchGithubData() = try {
        val response = RetrofitClient.trendingDataApi.getTrendingData()
        if (response.isSuccessful) response.body() else null
    } catch (e: Exception) {
        null
    }

    private fun JsonProduct.toProduct(): Product? {
        if (name.isNullOrBlank()) return null
        val src = when (source) {
            "ALIEXPRESS" -> ProductSource.ALIEXPRESS
            else -> ProductSource.AMAZON
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
            trendScore = trendScore ?: 50,
            salesCount = salesCount ?: "",
            rating = rating ?: 0f,
            reviewCount = 0,
            url = url ?: "",
            tags = listOf(category ?: "", "ترند"),
            isNew = isNew ?: false,
            trendDirection = TrendDirection.UP
        )
    }

    private suspend fun fetchAliExpressHotProducts(): List<Product> {
        return try {
            val timestamp = System.currentTimeMillis().toString()
            val params = mapOf(
                "method" to "aliexpress.affiliate.hotproduct.query",
                "app_key" to ApiConfig.ALIEXPRESS_APP_KEY,
                "sign_method" to "md5",
                "timestamp" to timestamp,
                "v" to "2.0",
                "fields" to "product_id,product_title,product_main_image_url,target_sale_price,evaluate_rate,lastest_volume,product_detail_url,second_level_category_name",
                "sort" to "LAST_VOLUME_DESC",
                "page_no" to "1",
                "page_size" to "20",
                "target_currency" to "USD",
                "target_language" to "EN",
                "tracking_id" to "default"
            )
            val sign = AliExpressSigner.sign(ApiConfig.ALIEXPRESS_APP_SECRET, params)
            val response = RetrofitClient.aliExpressApi.getHotProducts(timestamp = timestamp, sign = sign)
            if (!response.isSuccessful) return emptyList()
            response.body()?.aliexpress_affiliate_hotproduct_query_response?.products?.product
                ?.mapIndexed { i, p ->
                    val rating = (p.evaluate_rate?.replace("%", "")?.toFloatOrNull() ?: 0f) / 20f
                    Product(
                        id = "ali_${p.product_id ?: i}",
                        name = p.product_title ?: "AliExpress Product",
                        nameAr = p.product_title ?: "منتج علي إكسبريس",
                        description = "منتج رائج — ${p.lastest_volume ?: 0} طلب",
                        descriptionAr = "منتج رائج — ${p.lastest_volume ?: 0} طلب",
                        imageUrl = p.product_main_image_url ?: "",
                        price = p.target_sale_price?.replace("US \$", "") ?: "",
                        currency = "USD",
                        category = p.second_level_category_name ?: "General",
                        categoryAr = p.second_level_category_name ?: "عام",
                        source = ProductSource.ALIEXPRESS,
                        trendScore = minOf(99, 70 + (p.lastest_volume ?: 0) / 500),
                        salesCount = "${p.lastest_volume ?: 0} طلب",
                        rating = rating.coerceIn(0f, 5f),
                        reviewCount = 0,
                        url = p.product_detail_url ?: "https://www.aliexpress.com",
                        tags = listOf(p.second_level_category_name ?: "General", "AliExpress"),
                        isNew = false,
                        trendDirection = TrendDirection.UP
                    )
                } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
