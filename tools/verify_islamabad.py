#!/usr/bin/env python3
"""Compare archived 24/48-hour temperature forecasts with OPIS measurements."""

import argparse
import csv
import hashlib
import json
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path
from statistics import mean
from urllib.parse import urlencode
from urllib.request import urlopen


MODELS = (
    "ecmwf_ifs", "icon_seamless", "ncep_gfs_seamless",
    "google_weathernext2_ensemble_mean", "best_match",
)
START = "2026-09-01"
END = "2026-11-10"
SPLIT = "2026-10-16T00:00"
STATION = "OPIS"


def inputs():
    obs = [
        ("station", STATION), ("station", "OPRN"), ("data", "tmpf"),
        ("data", "p01i"), ("data", "wxcodes"), ("year1", 2026), ("month1", 9),
        ("day1", 1), ("year2", 2026), ("month2", 11), ("day2", 11),
        ("tz", "Etc/UTC"), ("format", "onlycomma"), ("latlon", "yes"),
        ("missing", "M"), ("trace", "T"), ("report_type", 1), ("report_type", 2),
    ]
    urls = {"iem-islamabad.csv": "https://mesonet.agron.iastate.edu/cgi-bin/request/asos.py?" + urlencode(obs)}
    for model in MODELS:
        params = {
            "latitude": 33.5491, "longitude": 72.8256, "models": model,
            "hourly": "temperature_2m_previous_day1,temperature_2m_previous_day2",
            "start_date": START, "end_date": END, "timezone": "GMT",
        }
        urls[f"runs-{model}.json"] = "https://previous-runs-api.open-meteo.com/v1/forecast?" + urlencode(params)
    return urls


def observations(path):
    values = defaultdict(set)
    with path.open(newline="") as stream:
        for row in csv.DictReader(stream):
            stamp = row["valid"].replace(" ", "T")
            if row["station"] != STATION or not stamp.endswith(":00"):
                continue
            try:
                temperature = (float(row["tmpf"]) - 32) * 5 / 9
            except ValueError:
                continue
            if -100 <= temperature <= 70:
                values[stamp].add(temperature)
    return {stamp: next(iter(samples)) for stamp, samples in values.items() if len(samples) == 1}


def complete_local_days(stamps):
    days = defaultdict(list)
    for stamp in stamps:
        day = (datetime.fromisoformat(stamp) + timedelta(hours=5)).date()
        days[day].append(stamp)
    return [hours for hours in days.values() if len(hours) == 24]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input_dir", type=Path)
    parser.add_argument("--download", action="store_true")
    args = parser.parse_args()
    folder = args.input_dir
    urls = inputs()
    if args.download:
        folder.mkdir(parents=True, exist_ok=True)
        for name, url in urls.items():
            with urlopen(url, timeout=45) as response:
                (folder / name).write_bytes(response.read(2_000_000))
    measured = observations(folder / "iem-islamabad.csv")
    forecasts = {}
    for model in MODELS:
        response = json.loads((folder / f"runs-{model}.json").read_text())
        hourly = response["hourly"]
        forecasts[model] = {
            stamp: (day1, day2)
            for stamp, day1, day2 in zip(hourly["time"], hourly["temperature_2m_previous_day1"],
                                         hourly["temperature_2m_previous_day2"], strict=True)
            if day1 is not None and day2 is not None and -100 <= day1 <= 70 and -100 <= day2 <= 70
        }
    common = sorted(set(measured).intersection(*(set(series) for series in forecasts.values())))
    training = [stamp for stamp in common if stamp < SPLIT]
    holdout = [stamp for stamp in common if stamp >= SPLIT]
    if len(training) < 700 or len(holdout) < 400:
        raise ValueError("Insufficient matched station measurements for the comparison")
    days = complete_local_days(holdout)
    scores = {}
    for model, series in forecasts.items():
        scores[model] = {
            "training_mae_c": [round(mean(abs(series[t][i] - measured[t]) for t in training), 3) for i in (0, 1)],
            "holdout_mae_c": [round(mean(abs(series[t][i] - measured[t]) for t in holdout), 3) for i in (0, 1)],
            "holdout_daily_high_mae_c": round(mean(
                abs(max(series[t][i] for t in ts) - max(measured[t] for t in ts))
                for ts in days for i in (0, 1)
            ), 3),
            "holdout_daily_low_mae_c": round(mean(
                abs(min(series[t][i] for t in ts) - min(measured[t] for t in ts))
                for ts in days for i in (0, 1)
            ), 3),
        }
    ranked = sorted((m for m in MODELS if m != "best_match"),
                    key=lambda model: mean(scores[model]["training_mae_c"]))
    chosen = ranked[0]
    result = {
        "station": STATION, "station_coordinates": [33.5491, 72.8256],
        "start_utc": START, "end_utc": END, "holdout_start_utc": SPLIT,
        "lead_hours": [24, 48], "training_hours": len(training), "holdout_hours": len(holdout),
        "complete_holdout_days_pk": len(days), "training_ranked_models": ranked,
        "chosen_model": chosen,
        "best_match_equals_ifs": all(forecasts["best_match"][t] == forecasts["ecmwf_ifs"][t] for t in common),
        "scores": scores,
        "inputs": {
            name: {"url": url, "sha256": hashlib.sha256((folder / name).read_bytes()).hexdigest()}
            for name, url in urls.items()
        },
    }
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
