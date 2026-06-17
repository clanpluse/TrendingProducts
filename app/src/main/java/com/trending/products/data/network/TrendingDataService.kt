package com.trending.products.data.network

import retrofit2.Response
import retrofit2.http.GET

interface TrendingDataService {
    @GET("clanpluse/TrendingProducts/main/data/trending.json")
    suspend fun getTrendingData(): Response<TrendingJsonData>
}

data class TrendingJsonData(
    val updatedAt: String?,
    val topSelling: List<JsonProduct>?,
    val chineseFactory: List<JsonProduct>?,
    val hotNew: List<JsonProduct>?
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
    val source: String?
)
