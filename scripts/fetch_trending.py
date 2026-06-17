#!/usr/bin/env python3
"""
يبني data/trending.json ببيانات AliExpress حقيقية عبر RapidAPI (Aliexpress DataHub).

كل منتج يحمل بيانات حقيقية: العنوان، عدد الطلبات الفعلي (sales)، السعر، الصورة،
التقييم، والرابط المباشر.

المفتاح يُقرأ من متغير البيئة RAPIDAPI_KEY (GitHub Secret) — لا يُخزَّن في الكود.

ملاحظة صدق: AliExpress يوفّر عدد طلبات *تراكمي* (إجمالي) وليس لكل فترة زمنية.
لذلك الفلتر الزمني (يوم/أسبوع/شهر) يعيد ترتيب نفس المنتجات الحقيقية بمنظور مختلف:
 - day:   الأحدث + الأعلى تقييماً (نشاط حالي)
 - week:  توازن بين المبيعات والتقييم
 - month: الأعلى مبيعاً تراكمياً
الأسعار وأعداد الطلبات والصور كلها حقيقية في كل الفترات.
"""
import json
import os
import time
from datetime import datetime, timezone

import requests

RAPIDAPI_KEY = os.environ.get("RAPIDAPI_KEY", "").strip()
HOST = "aliexpress-datahub.p.rapidapi.com"
BASE = f"https://{HOST}/item_search_2"

# استعلامات حقيقية لكل قسم
SECTION_QUERIES = {
    "topSelling": ["wireless earbuds", "smart watch", "phone charger", "kitchen gadget"],
    "alibaba":    ["wholesale lot", "bulk items", "led strip wholesale"],
    "trending":   ["gadget 2025", "tiktok trending", "cool gadget"],
    "exclusive":  ["new arrival gadget", "innovative product", "unique gift"],
}

# ترتيب القسم على الخادم
SECTION_SORT = {
    "topSelling": "orders",
    "alibaba": "orders",
    "trending": "default",
    "exclusive": "default",
}


def api_search(query, sort, retries=3):
    """يرجّع قائمة عناصر AliExpress الحقيقية، مع إعادة محاولة عند الفشل المتقطّع."""
    headers = {"x-rapidapi-key": RAPIDAPI_KEY, "x-rapidapi-host": HOST}
    params = {"q": query, "page": "1", "sort": sort}
    for attempt in range(1, retries + 1):
        try:
            r = requests.get(BASE, headers=headers, params=params, timeout=35)
            data = r.json()
            items = data.get("result", {}).get("resultList", []) or []
            if items:
                return items
        except Exception as e:
            print(f"    [{query}] attempt {attempt} error: {e}")
        time.sleep(6)
    print(f"    [{query}] no results after {retries} attempts")
    return []


def to_product(raw, section):
    item = raw.get("item", raw)
    title = (item.get("title") or "").strip()
    if not title:
        return None
    sales = item.get("sales") or "0"
    try:
        sales_num = int(str(sales).replace(",", "").replace("+", "") or 0)
    except ValueError:
        sales_num = 0
    sku = item.get("sku", {}) or {}
    price = (sku.get("def", {}) or {}).get("promotionPrice") or (sku.get("def", {}) or {}).get("price")
    image = item.get("image") or ""
    if image.startswith("//"):
        image = "https:" + image
    url = item.get("itemUrl") or ""
    if url.startswith("//"):
        url = "https:" + url
    rating = item.get("averageStarRate") or 0
    is_new = section == "exclusive"
    return {
        "id": "ali_" + str(item.get("itemId", title[:10])),
        "name": title[:90],
        "description": f"منتج AliExpress حقيقي — {sales_num:,} طلب".replace(",", "٬") if sales_num else "منتج AliExpress حقيقي",
        "imageUrl": image,
        "price": str(price) if price is not None else "",
        "currency": "USD",
        "category": "AliExpress",
        "url": url or "https://www.aliexpress.com",
        "trendScore": max(45, min(99, 50 + sales_num // 200)),
        "salesCount": (f"{sales_num:,} طلب".replace(",", "٬")) if sales_num else ("جديد" if is_new else ""),
        "rating": float(rating) if rating else 0.0,
        "isNew": is_new,
        "source": "ALIEXPRESS",
        "trend": "UP",
        "interestScore": min(99, sales_num // 100),
        "_sales": sales_num,  # داخلي للترتيب
    }


def fetch_section(section):
    products, seen = [], set()
    for q in SECTION_QUERIES[section]:
        print(f"  fetching '{q}' ({section})...")
        for raw in api_search(q, SECTION_SORT[section]):
            p = to_product(raw, section)
            if p and p["name"] not in seen:
                seen.add(p["name"])
                products.append(p)
        time.sleep(3)
    return products


def rank(products, mode):
    """يعيد ترتيب نفس المنتجات الحقيقية حسب منظور الفترة الزمنية."""
    if mode == "month":
        key = lambda p: p["_sales"]
    elif mode == "week":
        key = lambda p: p["_sales"] * 0.6 + p["rating"] * 1000
    else:  # day
        key = lambda p: p["rating"] * 2000 + p["_sales"] * 0.2
    return sorted(products, key=key, reverse=True)


def strip_internal(products):
    out = []
    for p in products:
        q = {k: v for k, v in p.items() if not k.startswith("_")}
        out.append(q)
    return out


def main():
    if not RAPIDAPI_KEY:
        print("ERROR: RAPIDAPI_KEY not set — keeping existing data/trending.json")
        return

    print("Fetching REAL AliExpress data via RapidAPI...")
    sections = {sec: fetch_section(sec) for sec in SECTION_QUERIES}

    total = sum(len(v) for v in sections.values())
    if total == 0:
        print("ERROR: API returned no data — keeping existing file to avoid wiping real data.")
        return

    data = {"updatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")}
    for tf in ("day", "week", "month"):
        data[tf] = {
            sec: strip_internal(rank(sections[sec], tf)[:15])
            for sec in SECTION_QUERIES
        }

    os.makedirs("data", exist_ok=True)
    with open("data/trending.json", "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"Done -> data/trending.json ({total} real products)")


if __name__ == "__main__":
    main()
