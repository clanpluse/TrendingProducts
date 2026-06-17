package com.trending.products.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface EbayFindingApiService {

    // findPopularItems returns real bestselling/trending items on eBay right now
    @GET("services/search/FindingService/v1")
    suspend fun findPopularItems(
        @Query("OPERATION-NAME") operation: String = "findPopularItems",
        @Query("SERVICE-VERSION") version: String = "1.0.0",
        @Query("SECURITY-APPNAME") appId: String = ApiConfig.EBAY_APP_ID,
        @Query("RESPONSE-DATA-FORMAT") format: String = "JSON",
        @Query("paginationInput.entriesPerPage") limit: Int = 20,
        @Query("paginationInput.pageNumber") page: Int = 1
    ): Response<EbayPopularResponse>

    // findItemsByKeywords — search top items for a keyword (e.g. trending topic from Google)
    @GET("services/search/FindingService/v1")
    suspend fun findItemsByKeyword(
        @Query("OPERATION-NAME") operation: String = "findItemsByKeywords",
        @Query("SERVICE-VERSION") version: String = "1.0.0",
        @Query("SECURITY-APPNAME") appId: String = ApiConfig.EBAY_APP_ID,
        @Query("RESPONSE-DATA-FORMAT") format: String = "JSON",
        @Query("keywords") keywords: String,
        @Query("sortOrder") sortOrder: String = "BestMatch",
        @Query("paginationInput.entriesPerPage") limit: Int = 10,
        @Query("itemFilter(0).name") filterName: String = "ListingType",
        @Query("itemFilter(0).value") filterValue: String = "FixedPrice"
    ): Response<EbaySearchResponse>
}

// ─── Response models ──────────────────────────────────────────────────────────

data class EbayPopularResponse(
    val findPopularItemsResponse: List<FindPopularItemsResponse>?
)

data class FindPopularItemsResponse(
    val itemRecommendations: List<ItemRecommendations>?,
    val ack: List<String>?,
    val errorMessage: List<ErrorMessage>?
)

data class ItemRecommendations(
    val item: List<EbayPopularItem>?
)

data class EbayPopularItem(
    val itemId: List<String>?,
    val title: List<String>?,
    val galleryURL: List<String>?,
    val viewItemURL: List<String>?,
    val sellingStatus: List<EbaySellingStatus>?,
    val primaryCategory: List<EbayCategory>?,
    val shippingInfo: List<EbayShipping>?
)

data class EbaySellingStatus(
    val currentPrice: List<EbayPrice>?,
    val bidCount: List<String>?
)

data class EbayPrice(
    val `__value__`: String?,
    val `@currencyId`: String?
)

data class EbayCategory(
    val categoryId: List<String>?,
    val categoryName: List<String>?
)

data class EbayShipping(
    val shippingType: List<String>?
)

// Search response
data class EbaySearchResponse(
    val findItemsByKeywordsResponse: List<FindItemsByKeywordsResponse>?
)

data class FindItemsByKeywordsResponse(
    val searchResult: List<SearchResult>?,
    val ack: List<String>?
)

data class SearchResult(
    val item: List<EbaySearchItem>?,
    val `@count`: String?
)

data class EbaySearchItem(
    val itemId: List<String>?,
    val title: List<String>?,
    val galleryURL: List<String>?,
    val viewItemURL: List<String>?,
    val sellingStatus: List<EbaySellingStatus>?,
    val primaryCategory: List<EbayCategory>?,
    val condition: List<EbayCondition>?,
    val listingInfo: List<EbayListingInfo>?
)

data class EbayCondition(
    val conditionDisplayName: List<String>?
)

data class EbayListingInfo(
    val bestOfferEnabled: List<String>?,
    val buyItNowAvailable: List<String>?
)

data class ErrorMessage(
    val error: List<EbayError>?
)

data class EbayError(
    val message: List<String>?,
    val errorId: List<String>?
)
