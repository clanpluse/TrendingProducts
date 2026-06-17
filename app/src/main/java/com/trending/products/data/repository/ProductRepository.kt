package com.trending.products.data.repository

import com.trending.products.data.model.Product
import com.trending.products.data.model.ProductSource
import com.trending.products.data.model.TrendDirection
import com.trending.products.data.network.AliExpressSigner
import com.trending.products.data.network.ApiConfig
import com.trending.products.data.network.RedditPost
import com.trending.products.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class ProductRepository {

    // ─── Tab 1: أكثر المنتجات مبيعاً (Reddit r/deals + r/BuyItForLife) ─────────

    suspend fun getTopSellingProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
        runCatching {
            val subreddits = listOf("deals", "BuyItForLife", "frugalmalefashion", "sales")
            val deferred = subreddits.map { sub ->
                async { fetchRedditProducts(sub, "day", 15, ProductSource.AMAZON) }
            }
            val all = deferred.awaitAll().flatten()
                .distinctBy { it.name.take(40) }
                .sortedByDescending { it.trendScore }
                .take(25)

            if (all.isEmpty()) throw Exception("تعذّر جلب البيانات. تحقق من الاتصال بالإنترنت.")
            all
        }
    }

    // ─── Tab 2: منتجات المصانع الصينية (AliExpress or Reddit r/Aliexpress) ──────

    suspend fun getChineseFactoryProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
        runCatching {
            if (ApiConfig.isAliExpressConfigured()) {
                val aliProducts = fetchAliExpressHotProducts()
                if (aliProducts.isNotEmpty()) return@runCatching aliProducts
            }
            val deferred = listOf(
                async { fetchRedditProducts("Aliexpress", "week", 20, ProductSource.ALIEXPRESS) },
                async { fetchRedditProducts("DHgate", "week", 10, ProductSource.ALIEXPRESS) },
                async { fetchRedditProducts("china_cq", "month", 10, ProductSource.ALIEXPRESS) }
            )
            val all = deferred.awaitAll().flatten()
                .distinctBy { it.name.take(40) }
                .sortedByDescending { it.trendScore }
                .take(25)

            if (all.isEmpty()) throw Exception("تعذّر جلب البيانات. تحقق من الاتصال بالإنترنت.")
            all
        }
    }

    // ─── Tab 3: جديد ورائج (Reddit r/shutupandtakemymoney + r/malelivingspace) ──

    suspend fun getHotNewProducts(): Result<List<Product>> = withContext(Dispatchers.IO) {
        runCatching {
            val deferred = listOf(
                async { fetchRedditProducts("shutupandtakemymoney", "week", 20, ProductSource.AMAZON) },
                async { fetchRedditProducts("Gadgets", "day", 15, ProductSource.AMAZON) },
                async { fetchRedditProducts("tech", "day", 10, ProductSource.AMAZON) }
            )
            val ali = async {
                if (ApiConfig.isAliExpressConfigured()) fetchAliExpressNewProducts()
                else emptyList()
            }

            val all = (deferred.awaitAll().flatten() + ali.await())
                .distinctBy { it.name.take(40) }
                .sortedByDescending { it.trendScore }
                .take(25)

            if (all.isEmpty()) throw Exception("تعذّر جلب المنتجات الجديدة.")
            all
        }
    }

    // ─── Reddit fetcher ───────────────────────────────────────────────────────────

    private suspend fun fetchRedditProducts(
        subreddit: String,
        time: String,
        limit: Int,
        source: ProductSource
    ): List<Product> {
        return try {
            val response = RetrofitClient.redditApi.getTopPosts(subreddit, time, limit)
            if (!response.isSuccessful) return emptyList()

            val posts = response.body()?.data?.children
                ?.mapNotNull { it.data }
                ?: return emptyList()

            posts
                .filter { post ->
                    val t = post.title ?: ""
                    post.is_self != true &&
                    t.isNotBlank() &&
                    !t.startsWith("Weekly") && !t.startsWith("Monthly") &&
                    (post.thumbnail?.startsWith("http") == true || post.url?.startsWith("http") == true)
                }
                .mapIndexed { i, post ->
                    val price = extractPrice(post.title ?: "")
                    val imageUrl = if (post.thumbnail?.startsWith("http") == true) post.thumbnail else ""
                    val score = post.score ?: 0
                    Product(
                        id = "reddit_${subreddit}_${post.id ?: i}",
                        name = cleanTitle(post.title ?: ""),
                        nameAr = cleanTitle(post.title ?: ""),
                        description = "رائج على Reddit — ${score.formatCount()} تصويت",
                        descriptionAr = "رائج على Reddit — ${score.formatCount()} تصويت",
                        imageUrl = imageUrl,
                        price = price,
                        currency = "USD",
                        category = subreddit,
                        categoryAr = mapSubredditAr(subreddit),
                        source = source,
                        trendScore = minOf(99, 50 + score / 200),
                        salesCount = "${score.formatCount()} تصويت",
                        rating = ((post.upvote_ratio ?: 0.8f) * 5f).coerceIn(0f, 5f),
                        reviewCount = score,
                        url = post.url ?: "https://www.reddit.com${post.permalink ?: ""}",
                        tags = listOf(mapSubredditAr(subreddit), "Reddit", "ترند"),
                        isNew = subreddit == "shutupandtakemymoney" || subreddit == "Gadgets",
                        trendDirection = TrendDirection.UP
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ─── AliExpress fetchers ──────────────────────────────────────────────────────

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

            response.body()
                ?.aliexpress_affiliate_hotproduct_query_response
                ?.products?.product
                ?.mapIndexed { i, p ->
                    val rating = (p.evaluate_rate?.replace("%", "")?.toFloatOrNull() ?: 0f) / 20f
                    Product(
                        id = "ali_hot_${p.product_id ?: i}",
                        name = p.product_title ?: "AliExpress Product",
                        nameAr = p.product_title ?: "منتج علي إكسبريس",
                        description = "منتج رائج من المصانع الصينية — ${p.lastest_volume ?: 0} طلب",
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
            val response = RetrofitClient.aliExpressApi.getNewProducts(timestamp = timestamp, sign = sign)
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
                        description = "منتج جديد من المصانع الصينية",
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

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private fun extractPrice(title: String): String {
        val regex = Regex("""\$(\d+(?:\.\d{1,2})?)""")
        return regex.find(title)?.groupValues?.get(1) ?: ""
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""\(.*?\)"""), "")
            .trim()
            .take(100)
    }

    private fun mapSubredditAr(subreddit: String): String = when (subreddit.lowercase()) {
        "deals" -> "عروض"
        "buyitforlife" -> "منتجات مدى الحياة"
        "frugalmalefashion" -> "موضة بأسعار معقولة"
        "sales" -> "تخفيضات"
        "aliexpress" -> "علي إكسبريس"
        "dhgate" -> "مصانع صينية"
        "china_cq" -> "منتجات صينية"
        "shutupandtakemymoney" -> "منتجات مبتكرة"
        "gadgets" -> "أدوات تقنية"
        "tech" -> "تكنولوجيا"
        else -> subreddit
    }

    private fun Int.formatCount(): String = when {
        this >= 1000 -> "${this / 1000}.${(this % 1000) / 100}k"
        else -> this.toString()
    }
}
