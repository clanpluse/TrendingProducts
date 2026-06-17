package com.trending.products.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface AliExpressApiService {

    // AliExpress Affiliate API — Hot Products endpoint
    // Docs: https://developers.aliexpress.com/en/doc.htm?docId=45800
    @GET("sync")
    suspend fun getHotProducts(
        @Query("method") method: String = "aliexpress.affiliate.hotproduct.query",
        @Query("app_key") appKey: String = ApiConfig.ALIEXPRESS_APP_KEY,
        @Query("sign_method") signMethod: String = "md5",
        @Query("timestamp") timestamp: String = System.currentTimeMillis().toString(),
        @Query("v") version: String = "2.0",
        @Query("fields") fields: String = "product_id,product_title,product_main_image_url,target_sale_price,evaluate_rate,lastest_volume,product_detail_url,second_level_category_name,target_original_price",
        @Query("sort") sort: String = "LAST_VOLUME_DESC",
        @Query("page_no") pageNo: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("target_currency") currency: String = "USD",
        @Query("target_language") language: String = "EN",
        @Query("tracking_id") trackingId: String = "default",
        @Query("sign") sign: String
    ): Response<AliExpressHotResponse>

    @GET("sync")
    suspend fun getNewProducts(
        @Query("method") method: String = "aliexpress.affiliate.hotproduct.query",
        @Query("app_key") appKey: String = ApiConfig.ALIEXPRESS_APP_KEY,
        @Query("sign_method") signMethod: String = "md5",
        @Query("timestamp") timestamp: String = System.currentTimeMillis().toString(),
        @Query("v") version: String = "2.0",
        @Query("fields") fields: String = "product_id,product_title,product_main_image_url,target_sale_price,evaluate_rate,lastest_volume,product_detail_url,second_level_category_name",
        @Query("sort") sort: String = "SALE_PRICE_ASC",
        @Query("page_no") pageNo: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("target_currency") currency: String = "USD",
        @Query("target_language") language: String = "EN",
        @Query("tracking_id") trackingId: String = "default",
        @Query("sign") sign: String
    ): Response<AliExpressHotResponse>
}

// ─── Response models ──────────────────────────────────────────────────────────

data class AliExpressHotResponse(
    val aliexpress_affiliate_hotproduct_query_response: AliHotQueryResponse?
)

data class AliHotQueryResponse(
    val products: AliProductsWrapper?,
    val total_record_count: Long?,
    val current_record_count: Int?,
    val resp_result: AliRespResult?
)

data class AliProductsWrapper(
    val product: List<AliProduct>?
)

data class AliProduct(
    val product_id: Long?,
    val product_title: String?,
    val product_main_image_url: String?,
    val target_sale_price: String?,
    val target_original_price: String?,
    val evaluate_rate: String?,
    val lastest_volume: Long?,
    val product_detail_url: String?,
    val second_level_category_name: String?
)

data class AliRespResult(
    val resp_code: Int?,
    val resp_msg: String?
)
