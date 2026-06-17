package com.trending.products.data.network

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Parses Google Trends Daily RSS feed.
 * Feed URL: https://trends.google.com/trends/trendingsearches/daily/rss?geo=US
 * No API key required.
 */
object GoogleTrendsParser {

    data class TrendingTopic(
        val title: String,
        val trafficVolume: String,     // e.g. "2,000,000+"
        val description: String,
        val imageUrl: String,
        val newsUrl: String,
        val pubDate: String
    )

    fun parse(inputStream: InputStream): List<TrendingTopic> {
        val topics = mutableListOf<TrendingTopic>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var inItem = false

        var title = ""
        var traffic = ""
        var description = ""
        var imageUrl = ""
        var newsUrl = ""
        var pubDate = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = try { parser.name ?: "" } catch (e: Exception) { "" }

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        tagName == "item" -> {
                            inItem = true
                            title = ""; traffic = ""; description = ""
                            imageUrl = ""; newsUrl = ""; pubDate = ""
                        }
                        inItem && tagName == "title" -> title = parser.nextText().trim()
                        inItem && tagName == "ht:approx_traffic" -> traffic = parser.nextText().trim()
                        inItem && tagName == "description" -> description = parser.nextText()
                            .replace(Regex("<[^>]*>"), "").trim()
                        inItem && tagName == "ht:picture" -> imageUrl = parser.nextText().trim()
                        inItem && tagName == "link" -> newsUrl = parser.nextText().trim()
                        inItem && tagName == "pubDate" -> pubDate = parser.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName == "item" && inItem && title.isNotEmpty()) {
                        topics.add(TrendingTopic(title, traffic, description, imageUrl, newsUrl, pubDate))
                        inItem = false
                    }
                }
            }
            eventType = parser.next()
        }
        return topics
    }
}
