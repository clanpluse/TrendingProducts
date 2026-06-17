package com.trending.products.data.network

import retrofit2.Response
import retrofit2.http.GET

interface TrendingDataService {
    @GET("clanpluse/TrendingProducts/main/data/trending.json")
    suspend fun getTrendingData(): Response<TrendingJsonData>
}

/**
 * البنية الجديدة: 3 فترات زمنية (يوم / أسبوع / شهر)،
 * كل فترة تحتوي 4 أقسام منفصلة لتفادي التداخل عند القراءة.
 */
data class TrendingJsonData(
    val updatedAt: String?,
    val day: TimeframeData?,
    val week: TimeframeData?,
    val month: TimeframeData?
)

data class TimeframeData(
    val topSelling: List<JsonProduct>?,   // الأعلى مبيعاً فعلياً
    val alibaba: List<JsonProduct>?,      // علي بابا ومنافسوه (جملة B2B)
    val trending: List<JsonProduct>?,     // الأعلى اهتماماً في البحث
    val exclusive: List<JsonProduct>?     // حصري جديد: مبيعات قليلة + اهتمام صاعد
)

data class JsonProduct(
    val id: String?,
    val name: String?,
    val description: String?,
    val imageUrl: String?,
    val price: String?,
    val currency: String?,
    val category: String?,
    val url: String?,
    val trendScore: Int?,
    val salesCount: String?,
    val rating: Float?,
    val isNew: Boolean?,
    val source: String?,
    val trend: String?,          // UP / DOWN / STABLE — اتجاه الاهتمام
    val interestScore: Int?      // درجة اهتمام البحث (Google Trends)
)
