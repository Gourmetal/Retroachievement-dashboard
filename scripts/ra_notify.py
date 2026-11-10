
import os
import io
import json
import urllib.request
import urllib.parse
from pathlib import Path
from PIL import Image

USER = "Gourmetal"
API_KEY = os.environ["RA_API_KEY"]
TOPIC = os.environ["NTFY_TOPIC"]
RESEND_ID = os.getenv("RESEND_ID", "").strip()
RESEND_HOUR = os.getenv("RESEND_HOUR", "false").lower() == "true"

HISTORY = Path("data/notified-achievements.json")
GAMES = Path("data/games.json")

THEMES = {
    "banjo": ("🎒 BANJO ADVENTURE!", "#D69A35"),
    "zelda": ("🗡️ HYRULE ADVENTURE!", "#C5A43B"),
    "pokemon": ("⚡ POKÉDEX UPDATE!", "#F4C430"),
    "pokémon": ("⚡ POKÉDEX UPDATE!", "#F4C430"),
    "mario": ("🍄 MUSHROOM KINGDOM!", "#D83B32"),
    "luigi": ("🍄 MUSHROOM KINGDOM!", "#D83B32"),
    "yoshi": ("🍄 YOSHI ADVENTURE!", "#55A85A"),
    "wario": ("🟡 WARIO TIME!", "#D5B52C"),
    "metroid": ("🚀 GALACTIC MISSION!", "#7B9B45"),
    "samus": ("🚀 GALACTIC MISSION!", "#7B9B45"),
    "kirby": ("⭐ DREAM LAND!", "#D94B91"),
    "sonic": ("💨 GOTTA GO FAST!", "#147FC0"),
    "mega man": ("🤖 ROBOT MASTER DEFEATED!", "#287FC2"),
    "megaman": ("🤖 ROBOT MASTER DEFEATED!", "#287FC2"),
    "golden sun": ("☀️ GOLDEN SUN!", "#D9A52E"),
    "castlevania": ("🦇 CASTLE VICTORY!", "#713B9B"),
    "donkey kong": ("🍌 JUNGLE VICTORY!", "#A67C35"),
    "final fantasy": ("⚔️ FINAL FANTASY!", "#6A55A5"),
    "earthbound": ("🪐 PSI POWER!", "#E56B9B"),
    "tetris": ("🧩 PERFECT FIT!", "#38A6A6"),
    "crash bandicoot": ("🌀 CRASH CARNIVAL!", "#E87524"),
    "spyro": ("🐉 DRAGON VICTORY!", "#8054B8"),
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
            if str(game.get("gameId")) == str(game_id) and game.get("boxArt"):
                art = game["boxArt"]
                return (
                    "https://retroachievements.org" + art
                    if art.startswith("/")
                    else art
                )

        data = api("API_GetGame.php", {"i": game_id})
        art = data.get("ImageBoxArt") or data.get("ImageIcon")

        return (
            "https://retroachievements.org" + art
            if art and art.startswith("/")
            else art
        )

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

    banner, color = ("🎮 RETRO GAMING UPDATE!", None)

    for keyword, style in THEMES.items():
        if keyword in game.casefold():
            banner, color = style
            break

    art = artwork(item.get("GameID"))

    if not color and art:
        try:
            request = urllib.request.Request(
                art,
                headers={"User-Agent": "RetroNotifications"}
            )

            with urllib.request.urlopen(request, timeout=15) as response:
                image = Image.open(
                    io.BytesIO(response.read())
                ).convert("RGB")

            image.thumbnail((64, 64))

            color = "#{:02X}{:02X}{:02X}".format(
                *image.resize((1, 1)).getpixel((0, 0))
            )

        except Exception as error:
            print("Color sampling error:", error)

    color = color or "#667788"

    message = (
        f"{title}\n{description}\n\n"
        f"🎮 {game}\n"
        f"⭐ {points} points\n"
        f"{mode}\n"
        f"🎨 Theme: {color}\n"
        f"Achievement ID: {achievement_id}"
    )

    headers = {
        "Title": "Retro Gaming Update",
        "Priority": "default",
        "Tags": "video_game,trophy",
        "Color": color,
        "X-Click": (
            f"https://retroachievements.org/achievement/{achievement_id}"
        )
    }

    if art:
        headers["Attach"] = art

    request = urllib.request.Request(
        f"https://ntfy.sh/{TOPIC}",
        data=message.encode(),
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
        
