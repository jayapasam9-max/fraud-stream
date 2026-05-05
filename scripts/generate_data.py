#!/usr/bin/env python3
"""
Generate a synthetic credit card transaction dataset with ~2% labeled fraud.

Usage:
    python scripts/generate_data.py --rows 50000 --out sample-data/transactions.csv

Fraud patterns injected:
    - Velocity bursts (5+ tx in 60s on same card)
    - Geo-impossible (NYC then Tokyo within 30 min)
    - High-risk MCC (crypto/gambling)
    - Amount deviation (10x normal spend)
    - Round-amount bursts ($100, $500, $1000 in succession)
"""
import argparse
import csv
import random
import uuid
from datetime import datetime, timedelta, timezone

CITIES = [
    ("New York",   40.7128,  -74.0060, "US"),
    ("Los Angeles", 34.0522, -118.2437, "US"),
    ("Chicago",    41.8781,  -87.6298, "US"),
    ("Dallas",     32.7767,  -96.7970, "US"),
    ("Tokyo",      35.6762,  139.6503, "JP"),
    ("London",     51.5074,   -0.1278, "GB"),
    ("Lagos",       6.5244,    3.3792, "NG"),
]
NORMAL_MCCS  = ["5411", "5812", "5541", "5912", "5311", "4814"]
RISKY_MCCS   = ["6051", "7995", "5933", "7273"]
MERCHANTS    = ["Walmart", "Starbucks", "Shell", "CVS", "Target", "Amazon", "Uber"]


def gen_normal(card_id, account_id, base_time, home_city):
    city = home_city if random.random() < 0.85 else random.choice(CITIES)
    return {
        "tx_id":     str(uuid.uuid4()),
        "card_id":   card_id,
        "account_id": account_id,
        "amount":    round(random.lognormvariate(3.5, 0.8), 2),
        "merchant":  random.choice(MERCHANTS),
        "mcc":       random.choice(NORMAL_MCCS),
        "country":   city[3],
        "lat":       city[1],
        "lon":       city[2],
        "timestamp": base_time.isoformat(),
        "is_fraud":  0,
    }


def inject_velocity_burst(card_id, account_id, base_time, home_city):
    """5 transactions in 45 seconds."""
    out = []
    for i in range(5):
        t = base_time + timedelta(seconds=i * 9)
        tx = gen_normal(card_id, account_id, t, home_city)
        tx["amount"] = round(random.uniform(20, 200), 2)
        tx["is_fraud"] = 1
        out.append(tx)
    return out


def inject_geo_impossible(card_id, account_id, base_time, home_city):
    far = random.choice([c for c in CITIES if c[3] != home_city[3]])
    return [
        {**gen_normal(card_id, account_id, base_time, home_city), "is_fraud": 1},
        {**gen_normal(card_id, account_id, base_time + timedelta(minutes=12),
                      far), "is_fraud": 1},
    ]


def inject_high_risk_mcc(card_id, account_id, base_time, home_city):
    tx = gen_normal(card_id, account_id, base_time, home_city)
    tx["mcc"] = random.choice(RISKY_MCCS)
    tx["amount"] = round(random.uniform(500, 5000), 2)
    tx["is_fraud"] = 1
    return [tx]


def inject_round_amount_burst(card_id, account_id, base_time, home_city):
    out = []
    for i, amt in enumerate([100, 500, 1000]):
        t = base_time + timedelta(minutes=i * 2)
        tx = gen_normal(card_id, account_id, t, home_city)
        tx["amount"] = amt
        tx["is_fraud"] = 1
        out.append(tx)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rows", type=int, default=50000)
    ap.add_argument("--out",  default="sample-data/transactions.csv")
    ap.add_argument("--fraud-rate", type=float, default=0.02)
    args = ap.parse_args()

    n_accounts = max(100, args.rows // 100)
    accounts = [(f"ACC{i:06d}", f"CARD{i:06d}", random.choice(CITIES))
                for i in range(n_accounts)]

    rows = []
    t = datetime.now(timezone.utc) - timedelta(days=1)

    while len(rows) < args.rows:
        acc, card, home = random.choice(accounts)
        if random.random() < args.fraud_rate:
            pattern = random.choice([
                inject_velocity_burst, inject_geo_impossible,
                inject_high_risk_mcc, inject_round_amount_burst
            ])
            rows.extend(pattern(card, acc, t, home))
        else:
            rows.append(gen_normal(card, acc, t, home))
        t += timedelta(seconds=random.uniform(0.1, 2.0))

    rows = rows[:args.rows]
    with open(args.out, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    fraud = sum(r["is_fraud"] for r in rows)
    print(f"Wrote {len(rows)} rows to {args.out} ({fraud} labeled fraud, {fraud/len(rows)*100:.1f}%)")


if __name__ == "__main__":
    main()
