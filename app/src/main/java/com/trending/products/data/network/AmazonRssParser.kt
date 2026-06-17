package com.trending.products.data.network

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Parses Amazon Best Sellers & New Releases RSS feeds.
 * Completely free — no API key needed.
 *
 * Feed URLs:
 *   Best Sellers (all):       amazon.com/gp/rss/bestsellers/
 *   Best Sellers Electronics: amazon.com/gp/rss/bestsellers/electronics/
 *   Best Sellers Beauty:      amazon.com/gp/rss/bestsellers/beauty/
 *   Best Sellers Toys:        amazon.com/gp/rss/bestsellers/toys-and-games/
 *   Best Sellers Sports:      amazon.com/gp/rss/bestsellers/sporting-goods/
 *   Best Sellers Home:        amazon.com/gp/rss/bestsellers/home-garden/
 *   New Releases:             amazon.com/gp/rss/new-releases/
 */
object AmazonRssParser {

    data class AmazonProduct(
        val title: String,
        val url: String,
        val imageUrl: String,
        val price: String,
        val rank: Int,
        val category: String,
        val description: String
    )

    fun parse(inputStream: InputStream, category: String): List<AmazonProduct> {
        val products = mutableListOf<AmazonProduct>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var inItem = false
        var title = ""
        var link = ""
        var description = ""
        var rank = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = try { parser.name ?: "" } catch (e: Exception) { "" }

            when (eventType) {
                XmlPullParser.START_TAG -> when {
                    tag == "item" -> {
                        inItem = true
                        rank++
                        title = ""; link = ""; description = ""
                    }
                    inItem && tag == "title" -> title = parser.nextText().trim()
                    inItem && tag == "link" -> link = parser.nextText().trim()
                    inItem && tag == "description" -> description = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> {
                    if (tag == "item" && inItem && title.isNotEmpty()) {
                        val imageUrl = extractImageUrl(description)
                        val price = extractPrice(description)
                        products.add(
                            AmazonProduct(
                                title = cleanTitle(title),
                                url = link,
                                imageUrl = imageUrl,
                                price = price,
                                rank = rank,
                                category = category,
                                description = cleanDescription(description)
                            )
                        )
                        inItem = false
                    }
                }
            }
            eventType = parser.next()
        }
        return products
    }

    private fun extractImageUrl(html: String): String {
        val imgRegex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        return imgRegex.find(html)?.groupValues?.get(1) ?: ""
    }

    private fun extractPrice(html: String): String {
        // Try to find price patterns like $12.99 or $1,299.00
        val priceRegex = Regex("""\$[\d,]+\.?\d*""")
        return priceRegex.find(html)?.value ?: "—"
    }

    private fun cleanTitle(title: String): String {
        // Remove rank prefix like "#1 Product Name"
        return title.replace(Regex("^#?\\d+\\.?\\s*"), "").trim()
    }

    private fun cleanDescription(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(150)
    }
}
