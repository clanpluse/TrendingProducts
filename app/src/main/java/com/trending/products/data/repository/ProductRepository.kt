package com.trending.products.data.repository

import com.trending.products.data.model.Product
import com.trending.products.data.model.ProductSource
import com.trending.products.data.model.TrendDirection
import com.trending.products.data.network.AliExpressSigner
import com.trending.products.data.network.AmazonRssParser
import com.trending.products.data.network.ApiConfig
import com.trending.products.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.Request

class ProductRepository {

    // Amazon Best Sellers RSS — FREE, no key needed
    private val amazonCategories = listOf(
        "electronics"    to "إلكترونيات",
        "beauty"         to "جمال وعناية",
        "toys-and-games" to "ألعاب وأطفال",
        "sporting-goods" to "رياضة ولياقة",
        "home-garden"    to "منزل وديكور",
        "tools"          to "أدوات"
    )

    // ─── Tab 1: أكثر المنتجات مبيعاً (Amazon Best Sellers) ──────────────────

    suspend fun getTopSellingProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
        runCatching {
            // Fetch multiple Amazon categories in parallel
            val deferred = amazonCategories.map { (cat, catAr) ->
                async {
                    fetchAmazonBestSellers(cat, catAr)
                }
            }
            val allProducts = deferred.awaitAll().flatten()

            if (allProducts.isEmpty()) {
                throw Exception("تعذّر جلب البيانات من أمازون. تحقق من الاتصال بالإنترنت.")
            }

            // Mix categories: take top 3 from each category
            allProducts
                .groupBy { it.category }
                .flatMap { (_, products) -> products.take(3) }
                .sortedBy { it.trendScore * -1 }
                .take(20)
        }
    }

    // ─── Tab 2: منتجات المصانع الصينية (AliExpress) ──────────────────────────

    suspend fun getChineseFactoryProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
        runCatching {
            if (ApiConfig.isAliExpressConfigured()) {
                fetchAliExpressHotProducts()
            } else {
                // Fallback: Amazon Best Sellers with "Ships from China" context
                // + show setup message
                fetchAmazonBestSellers("electronics", "إلكترونيات") +
                fetchAmazonBestSellers("tools", "أدوات") +
                fetchAmazonBestSellers("home-garden", "منزل وديكور")
            }
        }
    }

    // ─── Tab 3: جديد ورائج (Amazon New Releases + AliExpress New) ────────────

    suspend fun getHotNewProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
        runCatching {
            val amazonNew = async { fetchAmazonNewReleases() }
            val aliNew = async {
                if (ApiConfig.isAliExpressConfigured()) fetchAliExpressNewProducts()
                else emptyList()
            }

            val combined = (amazonNew.await() + aliNew.await())
                .distinctBy { it.name.take(30) }
                .take(20)

            if (combined.isEmpty()) throw Exception("تعذّر جلب المنتجات الجديدة.")
            combined
        }
    }

    // ─── Amazon fetchers ──────────────────────────────────────────────────────

    private fun fetchAmazonBestSellers(category: String, categoryAr: String): List<Product> {
        return try {
            val url = "https://www.amazon.com/gp/rss/bestsellers/$category/"
            val response = fetch(url) ?: return emptyList()
            val body = response.body ?: return emptyList()
            val items = AmazonRssParser.parse(body.byteStream(), categoryAr)
            body.close()

            items.mapIndexed { i, item ->
                Product(
                    id = "amz_${category}_$i",
                    name = item.title,
                    nameAr = item.title,
                    description = item.description.ifEmpty { "Best seller on Amazon — rank #${item.rank}" },
                    descriptionAr = item.description.ifEmpty { "الأكثر مبيعاً على أمازون — المرتبة #${item.rank}" },
                    imageUrl = item.imageUrl,
                    price = item.price,
                    currency = "USD",
                    category = item.category,
                    categoryAr = categoryAr,
                    source = ProductSource.AMAZON,
                    trendScore = maxOf(10, 99 - (i * 4)),
                    salesCount = "المرتبة #${item.rank}",
                    rating = 0f,
                    reviewCount = 0,
                    url = item.url,
                    tags = listOf(categoryAr, "Amazon", "Best Seller"),
                    isNew = false,
                    trendDirection = TrendDirection.UP
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchAmazonNewReleases(): List<Product> {
        val newReleaseCategories = listOf(
            "electronics" to "إلكترونيات",
            "beauty" to "جمال وعناية",
            "toys-and-games" to "ألعاب وأطفال"
        )
        return newReleaseCategories.flatMap { (cat, catAr) ->
            try {
                val url = "https://www.amazon.com/gp/rss/new-releases/$cat/"
                val response = fetch(url) ?: return@flatMap emptyList()
                val body = response.body ?: return@flatMap emptyList()
                val items = AmazonRssParser.parse(body.byteStream(), catAr)
                body.close()
                items.take(4).mapIndexed { i, item ->
                    Product(
                        id = "amz_new_${cat}_$i",
                        name = item.title,
                        nameAr = item.title,
                        description = item.description.ifEmpty { "New release on Amazon" },
                        descriptionAr = item.description.ifEmpty { "إصدار جديد على أمازون" },
                        imageUrl = item.imageUrl,
                        price = item.price,
                        currency = "USD",
                        category = item.category,
                        categoryAr = catAr,
                        source = ProductSource.AMAZON,
                        trendScore = maxOf(10, 95 - (i * 5)),
                        salesCount = "جديد",
                        rating = 0f,
                        reviewCount = 0,
                        url = item.url,
                        tags = listOf(catAr, "Amazon", "New Release"),
                        isNew = true,
                        trendDirection = TrendDirection.UP
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // ─── AliExpress fetchers ──────────────────────────────────────────────────

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
            val response = RetrofitClient.aliExpressApi.getHotProducts(
                timestamp = timestamp,
                sign = sign
            )
            if (!response.isSuccessful) return emptyList()

            response.body()
                ?.aliexpress_affiliate_hotproduct_query_response
                ?.products?.product
                ?.mapIndexed { i, p ->
                    val rating = (p.evaluate_rate?.replace("%", "")?.toFloatOrNull() ?: 0f) / 20f
                    Product(
                        id = "ali_hot_${p.product_id ?: i}",
                        name = p.product_title ?: "AliExpress Product",
                        nameAr = p.product_title ?: "منتج علي إكسبريس",
                        description = "Hot product from Chinese factories — ${p.lastest_volume ?: 0} orders",
                        descriptionAr = "منتج رائج من المصانع الصينية — ${p.lastest_volume ?: 0} طلب",
                        imageUrl = p.product_main_image_url ?: "",
                        price = p.target_sale_price?.replace("US \$", "") ?: "0",
                        currency = "USD",
                        category = p.second_level_category_name ?: "General",
                        categoryAr = p.second_level_category_name ?: "عام",
                        source = ProductSource.ALIEXPRESS,
                        trendScore = minOf(99, 70 + (p.lastest_volume ?: 0) / 500),
                        salesCount = "${p.lastest_volume ?: 0} طلب",
                        rating = rating.coerceIn(0f, 5f),
                        reviewCount = 0,
                        url = p.product_detail_url ?: "https://www.aliexpress.com",
                        tags = listOf(p.second_level_category_name ?: "General", "AliExpress", "China"),
                        isNew = false,
                        trendDirection = TrendDirection.UP
                    )
                } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchAliExpressNewProducts(): List<Product> {
        return try {
            val timestamp = System.currentTimeMillis().toString()
            val params = mapOf(
                "method" to "aliexpress.affiliate.hotproduct.query",
                "app_key" to ApiConfig.ALIEXPRESS_APP_KEY,
                "sign_method" to "md5",
                "timestamp" to timestamp,
                "v" to "2.0",
                "fields" to "product_id,product_title,product_main_image_url,target_sale_price,evaluate_rate,lastest_volume,product_detail_url,second_level_category_name",
                "sort" to "SALE_PRICE_ASC",
                "page_no" to "1",
                "page_size" to "10",
                "target_currency" to "USD",
                "target_language" to "EN",
                "tracking_id" to "default"
            )
            val sign = AliExpressSigner.sign(ApiConfig.ALIEXPRESS_APP_SECRET, params)
            val response = RetrofitClient.aliExpressApi.getNewProducts(
                timestamp = timestamp,
                sign = sign
            )
            if (!response.isSuccessful) return emptyList()

            response.body()
                ?.aliexpress_affiliate_hotproduct_query_response
                ?.products?.product
                ?.mapIndexed { i, p ->
                    val rating = (p.evaluate_rate?.replace("%", "")?.toFloatOrNull() ?: 0f) / 20f
                    Product(
                        id = "ali_new_${p.product_id ?: i}",
                        name = p.product_title ?: "New AliExpress Product",
                        nameAr = p.product_title ?: "منتج جديد من علي إكسبريس",
                        description = "New product from Chinese factories",
                        descriptionAr = "منتج جديد من المصانع الصينية",
                        imageUrl = p.product_main_image_url ?: "",
                        price = p.target_sale_price?.replace("US \$", "") ?: "0",
                        currency = "USD",
                        category = p.second_level_category_name ?: "General",
                        categoryAr = p.second_level_category_name ?: "عام",
                        source = ProductSource.ALIEXPRESS,
                        trendScore = maxOf(10, 85 - (i * 5)),
                        salesCount = "${p.lastest_volume ?: 0} طلب",
                        rating = rating.coerceIn(0f, 5f),
                        reviewCount = 0,
                        url = p.product_detail_url ?: "https://www.aliexpress.com",
                        tags = listOf(p.second_level_category_name ?: "General", "AliExpress", "New"),
                        isNew = true,
                        trendDirection = TrendDirection.UP
                    )
                } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ─── HTTP helper ──────────────────────────────────────────────────────────

    private fun fetch(url: String): okhttp3.Response? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .header("Accept", "application/rss+xml, application/xml, text/xml")
                .build()
            val response = RetrofitClient.googleTrendsOkHttp.newCall(request).execute()
            if (response.isSuccessful) response else null
        } catch (e: Exception) {
            null
        }
    }
}
