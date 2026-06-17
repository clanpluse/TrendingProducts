import requests
import json
import os
import re
from datetime import datetime, timezone

HEADERS = {"User-Agent": "TrendingProductsApp/1.0 (github.com/clanpluse/TrendingProducts)"}

def fetch_reddit(subreddit, time="day", limit=20):
    try:
        url = f"https://www.reddit.com/r/{subreddit}/top.json?t={time}&limit={limit}"
        r = requests.get(url, headers=HEADERS, timeout=15)
        if r.status_code != 200:
            return []
        posts = r.json().get("data", {}).get("children", [])
        results = []
        for p in posts:
            d = p.get("data", {})
            if d.get("is_self") or not d.get("title"):
                continue
            thumb = d.get("thumbnail", "")
            if not thumb.startswith("http"):
                thumb = ""
            title = re.sub(r'\[.*?\]|\(.*?\)', '', d.get("title", "")).strip()[:120]
            price = ""
            m = re.search(r'\$(\d+(?:\.\d{1,2})?)', d.get("title", ""))
            if m:
                price = m.group(1)
            results.append({
                "id": f"reddit_{subreddit}_{d.get('id','')}",
                "name": title,
                "description": f"رائج على Reddit — {d.get('score', 0):,} تصويت",
                "imageUrl": thumb,
                "price": price,
                "currency": "USD",
                "category": subreddit,
                "url": d.get("url", f"https://reddit.com{d.get('permalink','')}"),
                "trendScore": min(99, 50 + d.get("score", 0) // 200),
                "salesCount": f"{d.get('score', 0):,} تصويت",
                "rating": round(min(5.0, (d.get("upvote_ratio", 0.8) * 5)), 1),
                "isNew": subreddit in ["shutupandtakemymoney", "Gadgets", "tech"],
                "source": "REDDIT"
            })
        return results
    except Exception as e:
        print(f"Error fetching r/{subreddit}: {e}")
        return []

def main():
    print("Fetching trending products...")

    top_selling = []
    for sub in ["deals", "BuyItForLife", "frugalmalefashion", "sales"]:
        top_selling.extend(fetch_reddit(sub, "day", 20))
    seen = set()
    top_selling_dedup = []
    for p in sorted(top_selling, key=lambda x: x["trendScore"], reverse=True):
        key = p["name"][:40]
        if key not in seen:
            seen.add(key)
            top_selling_dedup.append(p)
    top_selling_dedup = top_selling_dedup[:25]

    chinese = []
    for sub in ["Aliexpress", "DHgate", "frugalmalefashion"]:
        chinese.extend(fetch_reddit(sub, "week", 20))
    seen2 = set()
    chinese_dedup = []
    for p in sorted(chinese, key=lambda x: x["trendScore"], reverse=True):
        key = p["name"][:40]
        if key not in seen2:
            seen2.add(key)
            chinese_dedup.append(p)
    chinese_dedup = chinese_dedup[:25]

    hot_new = []
    for sub in ["shutupandtakemymoney", "Gadgets", "tech", "gadgets"]:
        hot_new.extend(fetch_reddit(sub, "week", 20))
    seen3 = set()
    hot_new_dedup = []
    for p in sorted(hot_new, key=lambda x: x["trendScore"], reverse=True):
        key = p["name"][:40]
        if key not in seen3:
            seen3.add(key)
            hot_new_dedup.append(p)
    hot_new_dedup = hot_new_dedup[:25]

    data = {
        "updatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        "topSelling": top_selling_dedup,
        "chineseFactory": chinese_dedup,
        "hotNew": hot_new_dedup
    }

    os.makedirs("data", exist_ok=True)
    with open("data/trending.json", "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print(f"Done: {len(top_selling_dedup)} top, {len(chinese_dedup)} chinese, {len(hot_new_dedup)} new")

if __name__ == "__main__":
    main()
