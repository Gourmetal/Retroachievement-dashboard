
import json
import os
import time
import urllib.parse
import urllib.request

RA_ROOT = "https://retroachievements.org"
RA_API_ROOT = "https://retroachievements.org/API"
USERNAME = "Gourmetal"


def ra_request(endpoint, params, api_key):
    params = dict(params)
    params["y"] = api_key

    query = urllib.parse.urlencode(params)
    url = f"{RA_API_ROOT}/{endpoint}?{query}"

    request = urllib.request.Request(
        url,
        headers={"User-Agent": "GourmeHub/1.0"}
    )

    with urllib.request.urlopen(request, timeout=30) as response:
        data = json.loads(response.read().decode("utf-8"))

    if isinstance(data, dict) and data.get("Error"):
        raise RuntimeError(data["Error"])

    return data


def absolute_ra_url(path):
    if not path:
        return ""

    if path.startswith("http://") or path.startswith("https://"):
        return path

    return RA_ROOT + (path if path.startswith("/") else "/" + path)


def get_profile(api_key, username):
    data = ra_request(
        "API_GetUserSummary.php",
        {"u": username, "g": 0, "a": 0},
        api_key
    )

    return {
        "username": username,
        "avatar": absolute_ra_url(data.get("UserPic", "")),
        "memberSince": data.get("MemberSince", ""),
        "motto": data.get("Motto", ""),
        "rank": data.get("Rank", 0),
        "totalRanked": data.get("TotalRanked", 0),
        "hardcorePoints": data.get("TotalPoints", 0),
        "softcorePoints": data.get("TotalSoftcorePoints", 0),
        "truePoints": data.get("TotalTruePoints", 0)
    }


def get_completion_games(api_key, username):
    all_games = []
    offset = 0
    page_size = 500

    while True:
        data = ra_request(
            "API_GetUserCompletionProgress.php",
            {
                "u": username,
                "c": page_size,
                "o": offset
            },
            api_key
        )

        results = data.get("Results", []) if isinstance(data, dict) else data

        if not results:
            break

        all_games.extend(results)
        offset += len(results)

        total = data.get("Total", 0) if isinstance(data, dict) else 0

        if len(results) < page_size:
            break

        if total and offset >= int(total):
            break

    return all_games


def get_game_details(api_key, username, game_row):
    game_id = game_row.get("GameID", game_row.get("ID"))

    if not game_id:
        return None

    data = ra_request(
        "API_GetGameInfoAndUserProgress.php",
        {"u": username, "g": game_id},
        api_key
    )

    raw_achievements = data.get("Achievements", {})
    if isinstance(raw_achievements, dict):
        achievement_items = raw_achievements.items()
    else:
        achievement_items = enumerate(raw_achievements)

    achievements = []

    for achievement_id, achievement in achievement_items:
        date_earned = achievement.get("DateEarned", "")
        date_hardcore = achievement.get("DateEarnedHardcore", "")

        achievements.append({
            "id": achievement.get("ID", achievement_id),
            "badgeName": achievement.get("BadgeName", ""),
            "title": achievement.get("Title", "Untitled achievement"),
            "description": achievement.get("Description", ""),
            "points": int(achievement.get("Points") or 0),
            "earned": bool(date_earned),
            "dateEarned": date_earned,
            "hardcore": bool(date_hardcore),
            "dateEarnedHardcore": date_hardcore
        })

    achievements.sort(
        key=lambda item: (
            not item["earned"],
            item["title"].lower()
        )
    )

    earned = sum(1 for item in achievements if item["earned"])
    hardcore_earned = sum(1 for item in achievements if item["hardcore"])

    total = int(
        data.get("NumAchievements")
        or game_row.get("MaxPossible")
        or len(achievements)
    )

    if not total:
        total = len(achievements)

    progress = round((earned / total) * 100, 2) if total else 0

    return {
        "gameId": int(game_id),
        "title": data.get("Title") or game_row.get("Title", "Unknown game"),
        "console": data.get("ConsoleName") or game_row.get("ConsoleName", ""),
        "earned": earned,
        "hardcoreEarned": hardcore_earned,
        "total": total,
        "progress": progress,
        "mode": "Softcore",
        "lastPlayed": (
            game_row.get("MostRecentAward")
            or game_row.get("LastPlayed")
            or ""
        ),
        "boxArt": absolute_ra_url(
            data.get("ImageBoxArt") or data.get("ImageIcon", "")
        ),
        "achievements": achievements
    }


def fetch_retroachievements(api_key, username=USERNAME):
    if not api_key:
        raise RuntimeError("The RA_API_KEY secret is missing.")

    profile = get_profile(api_key, username)
    game_rows = get_completion_games(api_key, username)

    games = []

    for index, game_row in enumerate(game_rows):
        game = get_game_details(api_key, username, game_row)

        if game:
            games.append(game)

        if index < len(game_rows) - 1:
            time.sleep(0.25)

    games.sort(key=lambda item: item["title"].lower())

    return profile, games
      
