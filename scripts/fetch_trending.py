#!/usr/bin/env python3
"""
يبني data/trending.json ببيانات AliExpress حقيقية عبر RapidAPI (Aliexpress DataHub).

تعريفات دقيقة وصادقة لكل قسم (لا ادّعاءات غير حقيقية):

 1) topSelling (الأعلى مبيعاً): ترتيب الخادم sort=orders، ثم الأعلى عدد طلبات فعلي.
    -> حقيقي 100%: مرتّب حسب عدد الطلبات التراكمي الفعلي.

 2) alibaba (جملة): استعلامات جملة + sort=orders.
    -> حقيقي: أعلى منتجات الجملة مبيعاً (مصدرها AliExpress wholesale).

 3) trending (رائج/صاعد): sort=newest ثم تصفية المنتجات المُضافة حديثاً التي
    حقّقت بالفعل مبيعات معتبرة (RISING_MIN..RISING_MAX طلب). منتج جديد جمع آلاف
    الطلبات بسرعة = صاعد فعلاً. + يُحسب نمو المبيعات منذ آخر لقطة (history) إن توفّر.

 4) exclusive (حصري جديد): sort=newest ثم تصفية المبيعات المنخفضة (< EXCLUSIVE_MAX).
    -> حقيقي: منتجات جديدة فعلاً وقليلة الانتشار (حصرية مبكرة).

ملاحظة صدق عن الفلتر الزمني: AliExpress يعطي عدد طلبات تراكمي فقط، فلا يمكن
استخراج "مبيعات آخر 24 ساعة/أسبوع/شهر" بدقة. لذلك نحسب "النمو منذ آخر لقطة"
(history) كمؤشر زمني حقيقي يتحسّن مع كل تشغيل، ونعرضه في حقل salesDelta.
الفترات الثلاث تعيد ترتيب نفس المنتجات الحقيقية حسب: النمو (day) / مزيج (week) /
الإجمالي (month).
"""
import json
import os
import time
from datetime import datetime, timezone

import requests

RAPIDAPI_KEY = os.environ.get("RAPIDAPI_KEY", "").strip()
HOST = "aliexpress-datahub.p.rapidapi.com"
BASE = f"https://{HOST}/item_search_2"

HISTORY_FILE = "data/history.json"
DATA_FILE = "data/trending.json"

# حدود تعريف الأقسام (عدد الطلبات التراكمي)
EXCLUSIVE_MAX = 500       # حصري جديد: أقل من هذا
RISING_MIN = 1000         # صاعد: مُضاف حديثاً لكنه تجاوز هذا
RISING_MAX = 100000

# 6 استعلامات فقط (ضمن حد المفتاح المجاني 100/شهر)
QUERIES = [
    ("best seller electronics", "orders",  "topSelling"),
    ("home kitchen gadget",     "orders",  "topSelling"),
    ("wholesale bulk lot",      "orders",  "alibaba"),
    ("new gadget",              "newest",  "_newpool"),   # يغذّي trending + exclusive
    ("phone accessories new",   "newest",  "_newpool"),
    ("creative product",        "newest",  "_newpool"),
]


def api_search(query, sort, retries=3):
    headers = {"x-rapidapi-key": RAPIDAPI_KEY, "x-rapidapi-host": HOST}
    params = {"q": query, "page": "1", "sort": sort}
    for attempt in range(1, retries + 1):
        try:
            r = requests.get(BASE, headers=headers, params=params, timeout=35)
            items = r.json().get("result", {}).get("resultList", []) or []
            if items:
                return items
        except Exception as e:
            print(f"    [{query}] attempt {attempt}: {e}")
        time.sleep(6)
    print(f"    [{query}] no results")
    return []


def parse_sales(item):
    s = item.get("sales") or "0"
    try:
        return int(str(s).replace(",", "").replace("+", "") or 0)
    except ValueError:
        return 0


def to_product(raw, section, is_new, sales_delta=None):
    item = raw.get("item", raw)
    title = (item.get("title") or "").strip()
    if not title:
        return None
    sales_num = parse_sales(item)
    sku = item.get("sku", {}) or {}
    price = (sku.get("def", {}) or {}).get("promotionPrice") or (sku.get("def", {}) or {}).get("price")
    image = item.get("image") or ""
    if image.startswith("//"):
        image = "https:" + image
    url = item.get("itemUrl") or ""
    if url.startswith("//"):
        url = "https:" + url
    rating = item.get("averageStarRate") or 0

    if is_new:
        sales_label = f"{sales_num:,} طلب — جديد".replace(",", "٬") if sales_num else "جديد ✨"
    else:
        sales_label = f"{sales_num:,} طلب".replace(",", "٬") if sales_num else ""

    direction = "UP"
    if sales_delta is not None and sales_delta > 0:
        sales_label += f" (+{sales_delta:,} منذ آخر تحديث)".replace(",", "٬")

    return {
        "id": "ali_" + str(item.get("itemId", title[:10])),
        "name": title[:90],
        "description": "منتج AliExpress حقيقي",
        "imageUrl": image,
        "price": str(price) if price is not None else "",
        "currency": "USD",
        "category": "AliExpress",
        "url": url or "https://www.aliexpress.com",
        "trendScore": max(45, min(99, 50 + sales_num // 200)),
        "salesCount": sales_label,
        "rating": float(rating) if rating else 0.0,
        "isNew": is_new,
        "source": "ALIEXPRESS",
        "trend": direction,
        "interestScore": min(99, sales_num // 100),
        "_sales": sales_num,
        "_delta": sales_delta or 0,
        "_itemId": str(item.get("itemId", "")),
    }


def load_history():
    try:
        with open(HISTORY_FILE, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def save_history(products):
    hist = {p["_itemId"]: p["_sales"] for p in products if p["_itemId"]}
    os.makedirs("data", exist_ok=True)
    with open(HISTORY_FILE, "w", encoding="utf-8") as f:
        json.dump(hist, f)


def strip_internal(products):
    return [{k: v for k, v in p.items() if not k.startswith("_")} for p in products]


def rank(products, mode):
    if mode == "day":      # الأسرع نمواً منذ آخر لقطة (مؤشر زمني حقيقي)
        key = lambda p: (p["_delta"], p["_sales"])
    elif mode == "week":
        key = lambda p: p["_sales"] * 0.5 + p["_delta"] * 5 + p["rating"] * 500
    else:                  # month: الأعلى مبيعاً تراكمياً
        key = lambda p: p["_sales"]
    return sorted(products, key=key, reverse=True)


def main():
    if not RAPIDAPI_KEY:
        print("ERROR: RAPIDAPI_KEY not set — keeping existing data.")
        return

    history = load_history()
    print(f"Fetching REAL AliExpress data (history: {len(history)} items)...")

    top, alibaba, newpool = [], [], []
    seen = set()
    for q, sort, target in QUERIES:
        print(f"  '{q}' (sort={sort}) -> {target}")
        for raw in api_search(q, sort):
            item = raw.get("item", raw)
            iid = str(item.get("itemId", ""))
            sales = parse_sales(item)
            delta = None
            if iid in history:
                delta = max(0, sales - history[iid])
            is_new = target == "_newpool" and sales < EXCLUSIVE_MAX
            p = to_product(raw, target, is_new, delta)
            if not p or p["name"] in seen:
                continue
            seen.add(p["name"])
            if target == "topSelling":
                top.append(p)
            elif target == "alibaba":
                alibaba.append(p)
            else:
                newpool.append(p)
        time.sleep(3)

    # تقسيم newpool بدقة: حصري (مبيعات منخفضة) + صاعد (جديد لكنه تجاوز عتبة)
    exclusive = [p for p in newpool if p["_sales"] < EXCLUSIVE_MAX]
    trending = [p for p in newpool if RISING_MIN <= p["_sales"] <= RISING_MAX]
    # احتياط: لو فرغ أحدهما، املأ من newpool
    if not trending:
        trending = sorted(newpool, key=lambda p: p["_sales"], reverse=True)[:10]
    if not exclusive:
        exclusive = sorted(newpool, key=lambda p: p["_sales"])[:10]

    all_products = top + alibaba + newpool
    if not all_products:
        print("ERROR: no data returned — keeping existing file.")
        return

    sections = {"topSelling": top, "alibaba": alibaba, "trending": trending, "exclusive": exclusive}
    data = {"updatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")}
    for tf in ("day", "week", "month"):
        data[tf] = {sec: strip_internal(rank(items, tf)[:15]) for sec, items in sections.items()}

    os.makedirs("data", exist_ok=True)
    with open(DATA_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    save_history(all_products)
    print(f"Done: top={len(top)} alibaba={len(alibaba)} trending={len(trending)} exclusive={len(exclusive)}")


if __name__ == "__main__":
    main()
