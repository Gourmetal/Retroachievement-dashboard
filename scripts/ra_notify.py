
import os
import json
import urllib.request
import urllib.parse
from pathlib import Path

USER = "Gourmetal"
API_KEY = os.environ["RA_API_KEY"]
TOPIC = os.environ["NTFY_TOPIC"]
RESEND_ID = os.getenv("RESEND_ID", "").strip()
RESEND_HOUR = os.getenv("RESEND_HOUR", "false").lower() == "true"

HISTORY = Path("data/notified-achievements.json")
GAMES = Path("data/games.json")

THEMES = {
    "banjo": "🎒 BANJO ADVENTURE!",
    "zelda": "🗡️ HYRULE ADVENTURE!",
    "pokemon": "⚡ POKÉDEX UPDATE!",
    "pokémon": "⚡ POKÉDEX UPDATE!",
    "mario": "🍄 MUSHROOM KINGDOM!",
    "luigi": "🍄 MUSHROOM KINGDOM!",
    "yoshi": "🍄 YOSHI ADVENTURE!",
    "wario": "🟡 WARIO TIME!",
    "metroid": "🚀 GALACTIC MISSION!",
    "samus": "🚀 GALACTIC MISSION!",
    "kirby": "⭐ DREAM LAND!",
    "sonic": "💨 GOTTA GO FAST!",
    "mega man": "🤖 ROBOT MASTER DEFEATED!",
    "megaman": "🤖 ROBOT MASTER DEFEATED!",
    "golden sun": "☀️ GOLDEN SUN!",
    "castlevania": "🦇 CASTLE VICTORY!",
    "donkey kong": "🍌 JUNGLE VICTORY!",
    "final fantasy": "⚔️ FINAL FANTASY!",
    "earthbound": "🪐 PSI POWER!",
    "tetris": "🧩 PERFECT FIT!",
    "crash bandicoot": "🌀 CRASH CARNIVAL!",
    "spyro": "🐉 DRAGON VICTORY!",
}


def api(endpoint, params):
    params["y"] = API_KEY
    url = (
        "https://retroachievements.org/API/"
        + endpoint
        + "?"
        + urllib.parse.urlencode(params)
    )

    request = urllib.request.Request(
        url,
        headers={"User-Agent": "RetroNotifications"}
    )

    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode())


def recent():
    return api(
        "API_GetUserRecentAchievements.php",
        {"u": USER, "m": 60}
    )


def artwork(game_id):
    try:
        library = json.loads(GAMES.read_text()) if GAMES.exists() else {}

        for game in library.get("games", []):
            if str(game.get("gameId")) == str(game_id):
                art = game.get("boxArt")

                if art:
                    return (
                        "https://retroachievements.org" + art
                        if art.startswith("/")
                        else art
                    )

        data = api("API_GetGame.php", {"i": game_id})
        art = data.get("ImageBoxArt") or data.get("ImageIcon")

        if art and art.startswith("/"):
            return "https://retroachievements.org" + art

        return art

    except Exception as error:
        print("Artwork error:", error)
        return None


def send(item):
    game = item.get("GameTitle", "Retro game")
    title = item.get("Title", "Achievement unlocked!")
    achievement_id = item.get("AchievementID", "")
    points = item.get("Points", 0)
    description = item.get("Description", "")

    mode = (
        "🔥 Hardcore"
        if str(item.get("HardcoreMode", 0)) == "1"
        else "🎮 Softcore"
    )

    banner = "🎮 RETRO GAMING UPDATE!"

    for keyword, themed_banner in THEMES.items():
        if keyword in game.casefold():
            banner = themed_banner
            break

    art = artwork(item.get("GameID"))

    message = (
        f"{banner}\n\n"
        f"🏆 {title}\n"
        f"{description}\n\n"
        f"🎮 {game}\n"
        f"⭐ {points} points\n"
        f"{mode}\n\n"
        f"🔗 View achievement:\n"
        f"https://retroachievements.org/achievement/{achievement_id}"
    )

    headers = {
        "Title": "Retro Gaming Update",
        "Priority": "default",
        "Tags": "video_game,trophy",
        "Click": f"https://retroachievements.org/achievement/{achievement_id}"
    }

    if art:
        headers["Attach"] = art

    request = urllib.request.Request(
        f"https://ntfy.sh/{TOPIC}",
        data=message.encode("utf-8"),
        headers=headers,
        method="POST"
    )

    with urllib.request.urlopen(request, timeout=30) as response:
        print(f"Sent {achievement_id}: HTTP {response.status}")


def main():
    if RESEND_ID:
        data = api(
            "API_GetAchievementUnlocks.php",
            {"a": RESEND_ID, "c": 100, "o": 0}
        )

        achievement = data.get("Achievement", {})
        game = data.get("Game", {})
        unlocks = data.get("Unlocks", [])

        unlock = next(
            (
                u for u in unlocks
                if u.get("User", "").casefold() == USER.casefold()
            ),
            None
        )

        if not achievement.get("ID") or not game.get("ID") or not unlock:
            raise RuntimeError(
                "Achievement or player unlock not found."
            )

        send({
            "AchievementID": achievement["ID"],
            "Title": achievement.get("Title"),
            "Description": achievement.get("Description"),
            "Points": achievement.get("Points", 0),
            "GameID": game["ID"],
            "GameTitle": game.get("Title"),
            "HardcoreMode": unlock.get("HardcoreMode", 0)
        })

        return

    items = recent()

    if RESEND_HOUR:
        for item in reversed(items):
            send(item)

        print(f"Resent {len(items)} recent achievements.")
        return

    HISTORY.parent.mkdir(parents=True, exist_ok=True)

    seen = (
        set(json.loads(HISTORY.read_text()))
        if HISTORY.exists()
        else set()
    )

    new = [
        item for item in items
        if item.get("AchievementID")
        and str(item["AchievementID"]) not in seen
    ]

    for item in reversed(new):
        send(item)
        seen.add(str(item["AchievementID"]))

    HISTORY.write_text(
        json.dumps(sorted(seen), indent=2) + "\n"
    )

    print(f"Sent {len(new)} new notification(s).")


if __name__ == "__main__":
    main()
    
