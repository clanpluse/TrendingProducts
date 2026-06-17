package com.trending.products.data.model

data class Product(
    val id: String,
    val name: String,
    val nameAr: String,
    val description: String,
    val descriptionAr: String,
    val imageUrl: String,
    val price: String,
    val currency: String = "USD",
    val category: String,
    val categoryAr: String,
    val source: ProductSource,
    val trendScore: Int,
    val salesCount: String,
    val rating: Float,
    val reviewCount: Int,
    val url: String,
    val tags: List<String> = emptyList(),
    val isNew: Boolean = false,
    val trendDirection: TrendDirection = TrendDirection.UP
)

enum class ProductSource(val displayName: String, val displayNameAr: String) {
    ALIBABA("Alibaba", "علي بابا"),
    ALIEXPRESS("AliExpress", "علي إكسبريس"),
    AMAZON("Amazon", "أمازون"),
    TIKTOK_SHOP("TikTok Shop", "تيك توك شوب"),
    GOOGLE_TRENDS("Google Trends", "جوجل ترندز"),
    EBAY("eBay", "إيباي"),
    MADE_IN_CHINA("Made-in-China", "صُنع في الصين")
}

enum class TrendDirection {
    UP, DOWN, STABLE
}

enum class ProductCategory(val nameAr: String, val icon: String) {
    ALL("الكل", "🌐"),
    ELECTRONICS("إلكترونيات", "📱"),
    FASHION("أزياء وملابس", "👗"),
    HOME("منزل وديكور", "🏠"),
    BEAUTY("جمال وعناية", "💄"),
    TOYS("ألعاب وأطفال", "🧸"),
    SPORTS("رياضة ولياقة", "⚽"),
    AUTOMOTIVE("سيارات وقطع غيار", "🚗"),
    TOOLS("أدوات وصناعة", "🔧"),
    FOOD("طعام وصحة", "🍎")
}
