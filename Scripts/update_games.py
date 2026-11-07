
import json
import os
from datetime import datetime, timezone

from ra_api import fetch_retroachievements
from artwork import get_all_artwork


USERNAME = "Gourmetal"
OUTPUT_PATH = "data/games.json"


def save_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)

    with open(path, "w", encoding="utf-8") as file:
        json.dump(
            data,
            file,
            indent=2,
            ensure_ascii=False
        )


def main():
    api_key = os.environ.get("RA_API_KEY", "")

    if not api_key:
        raise RuntimeError(
            "Missing RA_API_KEY GitHub secret."
        )

    print(f"Fetching RetroAchievements data for {USERNAME}...")

    profile, games = fetch_retroachievements(
        api_key,
        USERNAME
    )

    print(f"Retrieved {len(games)} games.")

    print("Fetching game artwork and case spines...")

    games = get_all_artwork(games)

    achievements_earned = sum(
        game["earned"] for game in games
    )

    achievements_total = sum(
        game["total"] for game in games
    )

    output = {
        "username": USERNAME,
        "profile": profile,
        "lastUpdated": datetime.now(
            timezone.utc
        ).isoformat(),
        "games": games,
        "recentAchievements": [],
        "stats": {
            "gamesPlayed": len(games),
            "achievementsEarned": achievements_earned,
            "achievementsTotal": achievements_total
        }
    }

    save_json(OUTPUT_PATH, output)

    print("Dashboard data successfully updated.")
    print(f"Games: {len(games)}")
    print(
        f"Achievements: {achievements_earned}/"
        f"{achievements_total}"
    )


if __name__ == "__main__":
    main()
  
