#!/usr/bin/env python3
"""
يبني data/trending.json ببيانات AliExpress حقيقية عبر RapidAPI (Aliexpress DataHub)
باستخدام طريقة "ساعة AliExpress الخفية" لاستخراج تواريخ المنتجات وعمرها.

================== الطريقة العبقرية: Self-Calibrating ID-Clock ==================
رقم المنتج (itemId) في جيل "3256..." يرتفع بمعدّل شبه ثابت مع الزمن. لذلك:

 1) كل تشغيل نسجّل (التاريخ، أكبر itemId مرصود) في data/calibration.json.
 2) من نقطتين أو أكثر نحسب "سرعة الساعة" = كم وحدة itemId ترتفع في اليوم.
 3) عمر أي منتج (بالأيام) = (أكبر itemId حالي − itemId المنتج) ÷ سرعة الساعة.
 4) تاريخ الإطلاق التقديري = اليوم − العمر.
 5) سرعة البيع = إجمالي المبيعات ÷ العمر بالأيام (طلب/يوم حقيقي).
 6) الزخم الحالي = الزيادة في المبيعات منذ آخر لقطة ÷ الأيام بينهما (history).

النظام يعاير نفسه: كلما زاد عدد التشغيلات، زادت دقة التواريخ. قبل توفّر نقطتين
معايرة نستخدم تقديراً أولياً (SEED_ID_PER_DAY) ونعلّم التواريخ بأنها "تقديرية".

التصنيف الزمني الحقيقي:
 - day   = الأعلى زخماً حالياً (نمو فعلي منذ آخر تحديث)
 - week  = الأعلى سرعة بيع (طلب/يوم على مدى العمر)
 - month = الأعلى مبيعاً تراكمياً
الأقسام:
 - topSelling: الأعلى مبيعاً تراكمياً (sort=orders)
 - alibaba: جملة (sort=orders)
 - trending: شاب + زخم/سرعة عالية
 - exclusive: عمره < EXCLUSIVE_MAX_AGE يوم ومبيعاته < EXCLUSIVE_MAX_SALES
"""
import json
import os
import time
from datetime import datetime, timezone, timedelta

import requests

RAPIDAPI_KEY = os.environ.get("RAPIDAPI_KEY", "").strip()
HOST = "aliexpress-datahub.p.rapidapi.com"
BASE = f"https://{HOST}/item_search_2"

DATA_FILE = "data/trending.json"
HISTORY_FILE = "data/history.json"
CALIB_FILE = "data/calibration.json"

ID_GEN_PREFIX = "3256"          # الجيل الحالي من الأرقام
SEED_ID_PER_DAY = 2.7e9         # تقدير أولي لسرعة الساعة قبل المعايرة الذاتية
EXCLUSIVE_MAX_AGE = 60          # يوم
EXCLUSIVE_MAX_SALES = 800
TRENDING_MAX_AGE = 120          # يوم — "صاعد" يجب أن يكون شاباً

QUERIES = [
    ("best seller electronics", "orders", "topSelling"),
    ("home kitchen gadget",     "orders", "topSelling"),
    ("wholesale bulk lot",      "orders", "alibaba"),
    ("new gadget",              "newest", "_pool"),
    ("phone accessories new",   "newest", "_pool"),
    ("creative product",        "newest", "_pool"),
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


def load_json(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


# ---------------- معايرة ساعة الـ ID ----------------
def update_calibration(current_max_id):
    calib = load_json(CALIB_FILE, {"points": []})
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    # نقطة واحدة لكل يوم (نحدّث آخر نقطة لو نفس اليوم)
    pts = [p for p in calib["points"] if p["date"] != today]
    pts.append({"date": today, "maxId": current_max_id})
    pts.sort(key=lambda p: p["date"])
    calib["points"] = pts[-30:]  # نحتفظ بآخر 30 نقطة
    os.makedirs("data", exist_ok=True)
    with open(CALIB_FILE, "w", encoding="utf-8") as f:
        json.dump(calib, f, ensure_ascii=False, indent=2)
    return calib["points"]


def compute_id_per_day(points):
    """سرعة الساعة = (فرق أكبر ID) ÷ (فرق الأيام) بين أبعد نقطتين."""
    if len(points) < 2:
        return SEED_ID_PER_DAY, False
    first, last = points[0], points[-1]
    d0 = datetime.strptime(first["date"], "%Y-%m-%d")
    d1 = datetime.strptime(last["date"], "%Y-%m-%d")
    days = (d1 - d0).days
    delta_id = last["maxId"] - first["maxId"]
    if days <= 0 or delta_id <= 0:
        return SEED_ID_PER_DAY, False
    return delta_id / days, True


def estimate_age_days(item_id, current_max_id, id_per_day):
    if item_id <= 0 or not str(item_id).startswith(ID_GEN_PREFIX):
        return None
    age = (current_max_id - item_id) / id_per_day
    return max(0.0, age)


# ---------------- بناء المنتج ----------------
def to_product(raw, section, age_days, calibrated, sales_delta, days_between):
    item = raw.get("item", raw)
    title = (item.get("title") or "").strip()
    if not title:
        return None
    sales = parse_sales(item)
    sku = item.get("sku", {}) or {}
    price = (sku.get("def", {}) or {}).get("promotionPrice") or (sku.get("def", {}) or {}).get("price")
    image = item.get("image") or ""
    if image.startswith("//"):
        image = "https:" + image
    url = item.get("itemUrl") or ""
    if url.startswith("//"):
        url = "https:" + url
    rating = item.get("averageStarRate") or 0

    velocity = (sales / age_days) if age_days and age_days > 0 else 0.0
    momentum = (sales_delta / days_between) if (sales_delta and days_between > 0) else 0.0

    # تاريخ الإطلاق وعمره — يُعرض فقط بعد اكتمال المعايرة الذاتية (دقيق)
    launch_date = ""
    age_label = ""
    if age_days is not None and calibrated:
        d = datetime.now(timezone.utc) - timedelta(days=age_days)
        launch_date = d.strftime("%Y-%m-%d")
        if age_days < 30:
            age_label = f"عمره {int(age_days)} يوم"
        elif age_days < 365:
            age_label = f"عمره {int(age_days/30)} شهر"
        else:
            age_label = f"عمره {age_days/365:.1f} سنة"

    is_new = section == "exclusive"
    if is_new:
        sales_label = (f"{sales:,} طلب — {age_label}".replace(",", "٬")
                       if age_label else f"{sales:,} طلب — جديد".replace(",", "٬"))
    else:
        parts = [f"{sales:,} طلب".replace(",", "٬")]
        if calibrated and velocity >= 1:
            parts.append(f"{int(velocity)}/يوم")
        sales_label = " • ".join(parts)

    return {
        "id": "ali_" + str(item.get("itemId", title[:10])),
        "name": title[:90],
        "description": (age_label + " — منتج AliExpress حقيقي") if age_label else "منتج AliExpress حقيقي",
        "imageUrl": image,
        "price": str(price) if price is not None else "",
        "currency": "USD",
        "category": "AliExpress",
        "url": url or "https://www.aliexpress.com",
        "trendScore": max(45, min(99, 50 + sales // 200)),
        "salesCount": sales_label,
        "rating": float(rating) if rating else 0.0,
        "isNew": is_new,
        "source": "ALIEXPRESS",
        "trend": "UP" if momentum > 0 else "STABLE",
        "interestScore": min(99, sales // 100),
        "launchDate": launch_date,
        "_sales": sales,
        "_age": age_days if age_days is not None else 9999,
        "_velocity": velocity,
        "_momentum": momentum,
        "_itemId": str(item.get("itemId", "")),
    }


def strip_internal(products):
    return [{k: v for k, v in p.items() if not k.startswith("_")} for p in products]


def rank(products, mode):
    if mode == "day":          # الزخم الحالي (نمو فعلي حديث)؛ احتياطياً السرعة
        key = lambda p: (p["_momentum"], p["_velocity"], p["_sales"])
    elif mode == "week":       # متوسط سرعة البيع على العمر
        key = lambda p: (p["_velocity"], p["_sales"])
    else:                      # month: الإجمالي التراكمي
        key = lambda p: p["_sales"]
    return sorted(products, key=key, reverse=True)


def main():
    if not RAPIDAPI_KEY:
        print("ERROR: RAPIDAPI_KEY not set — keeping existing data.")
        return

    history = load_json(HISTORY_FILE, {})
    hist_date = history.get("_date")
    days_between = 3.5
    if hist_date:
        try:
            d0 = datetime.strptime(hist_date, "%Y-%m-%d")
            days_between = max(0.5, (datetime.now(timezone.utc).replace(tzinfo=None) - d0).days or 0.5)
        except Exception:
            pass

    print(f"Fetching REAL data (history items: {len(history)-1 if history else 0})...")
    raw_by_target = {"topSelling": [], "alibaba": [], "_pool": []}
    all_ids = []
    for q, sort, target in QUERIES:
        print(f"  '{q}' (sort={sort}) -> {target}")
        for raw in api_search(q, sort):
            raw_by_target[target].append(raw)
            iid = str((raw.get("item", raw)).get("itemId", ""))
            if iid.isdigit():
                all_ids.append(int(iid))
        time.sleep(3)

    if not all_ids:
        print("ERROR: no data — keeping existing file.")
        return

    # معايرة ساعة الـ ID
    gen_ids = [i for i in all_ids if str(i).startswith(ID_GEN_PREFIX)]
    current_max_id = max(gen_ids) if gen_ids else max(all_ids)
    points = update_calibration(current_max_id)
    id_per_day, calibrated = compute_id_per_day(points)
    print(f"  ID-clock: {id_per_day:.3e} id/day | calibrated={calibrated} | points={len(points)}")

    def build(raw, section):
        item = raw.get("item", raw)
        iid = str(item.get("itemId", ""))
        sales = parse_sales(item)
        age = estimate_age_days(int(iid) if iid.isdigit() else 0, current_max_id, id_per_day)
        prev = history.get(iid)
        delta = max(0, sales - prev) if isinstance(prev, (int, float)) else None
        return to_product(raw, section, age, calibrated, delta, days_between)

    seen = set()
    top, alibaba, pool = [], [], []
    for raw in raw_by_target["topSelling"]:
        p = build(raw, "topSelling")
        if p and p["name"] not in seen:
            seen.add(p["name"]); top.append(p)
    for raw in raw_by_target["alibaba"]:
        p = build(raw, "alibaba")
        if p and p["name"] not in seen:
            seen.add(p["name"]); alibaba.append(p)
    for raw in raw_by_target["_pool"]:
        p = build(raw, "_pool")
        if p and p["name"] not in seen:
            seen.add(p["name"]); pool.append(p)

    # احتياط: لو فرغ "الأعلى مبيعاً" (تقطّع API)، نملؤه من أعلى مبيعات المجمّع
    if not top:
        top = sorted(pool, key=lambda p: p["_sales"], reverse=True)[:15]
    if not alibaba:
        alibaba = sorted(pool, key=lambda p: p["_sales"], reverse=True)[:15]

    # تصنيف دقيق بالعمر الحقيقي
    exclusive = [p for p in pool if p["_age"] <= EXCLUSIVE_MAX_AGE and p["_sales"] < EXCLUSIVE_MAX_SALES]
    trending = [p for p in pool if p["_age"] <= TRENDING_MAX_AGE and p["_sales"] >= EXCLUSIVE_MAX_SALES]
    if not exclusive:
        exclusive = sorted(pool, key=lambda p: p["_age"])[:10]
    if not trending:
        trending = sorted(pool, key=lambda p: (p["_momentum"], p["_velocity"]), reverse=True)[:10]
    for p in exclusive:
        p["isNew"] = True

    sections = {"topSelling": top, "alibaba": alibaba, "trending": trending, "exclusive": exclusive}
    data = {
        "updatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        "calibrated": calibrated,
    }
    for tf in ("day", "week", "month"):
        data[tf] = {sec: strip_internal(rank(items, tf)[:15]) for sec, items in sections.items()}

    with open(DATA_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    # حفظ اللقطة (للزخم القادم)
    new_hist = {"_date": datetime.now(timezone.utc).strftime("%Y-%m-%d")}
    for p in top + alibaba + pool:
        if p["_itemId"]:
            new_hist[p["_itemId"]] = p["_sales"]
    with open(HISTORY_FILE, "w", encoding="utf-8") as f:
        json.dump(new_hist, f)

    print(f"Done: top={len(top)} alibaba={len(alibaba)} trending={len(trending)} exclusive={len(exclusive)} | calibrated={calibrated}")


if __name__ == "__main__":
    main()
