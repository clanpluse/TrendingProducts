package com.trending.products.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// AliExpress Affiliate API (free tier available)
interface AliExpressApiService {
    @GET("aliexpress/affiliate/hotproduct/query")
    suspend fun getHotProducts(
        @Query("app_key") appKey: String,
        @Query("fields") fields: String = "product_id,product_title,product_main_image_url,target_sale_price,evaluate_rate,lastest_volume,product_detail_url,second_level_category_name",
        @Query("sort") sort: String = "SALE_PRICE_ASC",
        @Query("page_no") pageNo: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("target_currency") currency: String = "USD",
        @Query("target_language") language: String = "EN"
    ): Response<AliExpressResponse>
}

data class AliExpressResponse(
    val aliexpress_affiliate_hotproduct_query_response: HotProductQueryResponse?
)

data class HotProductQueryResponse(
    val products: ProductsWrapper?,
    val total_record_count: Int?,
    val current_record_count: Int?
)

data class ProductsWrapper(
    val product: List<AliExpressProduct>?
)

data class AliExpressProduct(
    val product_id: Long,
    val product_title: String,
    val product_main_image_url: String,
    val target_sale_price: String,
    val evaluate_rate: String,
    val lastest_volume: Int,
    val product_detail_url: String,
    val second_level_category_name: String
)
