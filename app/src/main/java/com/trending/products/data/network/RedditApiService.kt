package com.trending.products.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RedditApiService {

    @GET("r/{subreddit}/top.json")
    suspend fun getTopPosts(
        @Path("subreddit") subreddit: String,
        @Query("t") time: String = "day",
        @Query("limit") limit: Int = 25
    ): Response<RedditResponse>
}

data class RedditResponse(
    val data: RedditData?
)

data class RedditData(
    val children: List<RedditChild>?
)

data class RedditChild(
    val data: RedditPost?
)

data class RedditPost(
    val id: String?,
    val title: String?,
    val url: String?,
    val thumbnail: String?,
    val score: Int?,
    val permalink: String?,
    val subreddit: String?,
    val selftext: String?,
    val is_self: Boolean?,
    val upvote_ratio: Float?
)
