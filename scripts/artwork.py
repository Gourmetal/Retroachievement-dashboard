
import json
import os
import time
import urllib.parse
import urllib.request
from html.parser import HTMLParser

SGDB_ROOT = "https://www.steamgriddb.com/api/v2"
LAUNCHBOX_ROOT = "https://gamesdb.launchbox-app.com"


def load_cache(path):
    if not os.path.exists(path):
        return {}

    try:
        with open(path, "r", encoding="utf-8") as file:
            data = json.load(file)
        return data if isinstance(data, dict) else {}
    except (OSError, json.JSONDecodeError):
        return {}


def save_cache(path, cache):
    os.makedirs(os.path.dirname(path), exist_ok=True)

    with open(path, "w", encoding="utf-8") as file:
        json.dump(cache, file, indent=2, ensure_ascii=False)


def fetch_json(url, headers=None):
    request = urllib.request.Request(
        url,
        headers=headers or {"User-Agent": "GourmeHub/1.0"}
    )

    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def sgdb_request(path, api_key):
    return fetch_json(
        f"{SGDB_ROOT}/{path}",
        {
            "Authorization": f"Bearer {api_key}",
            "User-Agent": "GourmeHub/1.0"
        }
    )


def get_steamgriddb_artwork(title, cache, api_key):
    empty = {
        "gameId": None,
        "grid": "",
        "hero": "",
        "logo": "",
        "icon": ""
    }

    if title in cache:
        return cache[title]

    if not api_key:
        return empty

    aliases = {
        "~Hack~ Pokémon Emerald Seaglass": "Pokemon Emerald Seaglass",
        "~Homebrew~ Goodboy Galaxy": "Goodboy Galaxy"
    }

    search_title = aliases.get(title, title)

    try:
        search = sgdb_request(
            "search/autocomplete/" + urllib.parse.quote(search_title),
            api_key
        )

        results = search.get("data", [])

        if not results:
            return empty

        game_id = results[0].get("id")

        artwork = {
            "gameId": game_id,
            "grid": "",
            "hero": "",
            "logo": "",
            "icon": ""
        }

        for key, endpoint in (
            ("grid", "grids"),
            ("hero", "heroes"),
            ("logo", "logos"),
            ("icon", "icons")
        ):
            try:
                result = sgdb_request(
                    f"{endpoint}/game/{game_id}",
                    api_key
                )

                images = result.get("data", [])

                if images:
                    artwork[key] = images[0].get("url", "")

            except Exception as error:
                print(
                    f"SteamGridDB {key} lookup failed "
                    f"for {title}: {error}"
                )

        cache[title] = artwork
        time.sleep(0.3)

        return artwork

    except Exception as error:
        print(f"SteamGridDB lookup failed for {title}: {error}")
        return empty


class LaunchBoxLinkParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self.current_href = None
        self.current_text = []

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            attributes = dict(attrs)
            self.current_href = attributes.get("href")
            self.current_text = []

    def handle_data(self, data):
        if self.current_href:
            self.current_text.append(data)

    def handle_endtag(self, tag):
        if tag == "a" and self.current_href:
            self.links.append((
                self.current_href,
                " ".join(self.current_text).strip()
            ))

            self.current_href = None
            self.current_text = []


def get_launchbox_html(url):
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (compatible; GourmeHub/1.0)",
            "Accept": "text/html"
        }
    )

    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8", errors="replace")


def absolute_launchbox_url(url):
    if not url:
        return ""

    if url.startswith(("http://", "https://")):
        return url

    return urllib.parse.urljoin(LAUNCHBOX_ROOT, url)


def get_launchbox_spine(title, cache):
    if title in cache:
        return cache[title]

    try:
        # Corrected LaunchBox search URL.
        search_url = (
            f"{LAUNCHBOX_ROOT}/games/results/?title="
            + urllib.parse.quote(title)
        )

        search_html = get_launchbox_html(search_url)

        search_parser = LaunchBoxLinkParser()
        search_parser.feed(search_html)

        title_words = [
            word.lower()
            for word in title.split()
            if len(word) > 2
        ][:4]

        candidates = []

        for href, label in search_parser.links:
            if "/games/details/" not in href:
                continue

            combined = f"{label} {href}".lower()

            if title_words and not all(
                word in combined for word in title_words
            ):
                continue

            candidates.append(absolute_launchbox_url(href))

        for detail_url in candidates[:8]:
            try:
                detail_html = get_launchbox_html(detail_url)

                detail_parser = LaunchBoxLinkParser()
                detail_parser.feed(detail_html)

                for href, label in detail_parser.links:
                    label = label.lower()

                    # Only accept North American box spines.
                    if "box - spine" not in label:
                        continue

                    if "north america" not in label:
                        continue

                    image_url = absolute_launchbox_url(href)

                    allowed_hosts = (
                        "https://images.launchbox-app.com/",
                        "https://gamesdb-images.launchbox.gg/"
                    )

                    if not image_url.startswith(allowed_hosts):
                        continue

                    cache[title] = image_url

                    print(
                        f"Found North American spine: {title}"
                    )

                    return image_url

            except Exception as error:
                print(
                    f"LaunchBox detail lookup failed "
                    f"for {title}: {error}"
                )

        print(f"No North American spine found for: {title}")
        return ""

    except Exception as error:
        print(f"LaunchBox lookup failed for {title}: {error}")
        return ""


def get_all_artwork(games):
    sgdb_cache_path = "data/steamgriddb-cache.json"
    spine_cache_path = "data/launchbox-spine-cache.json"

    sgdb_cache = load_cache(sgdb_cache_path)
    spine_cache = load_cache(spine_cache_path)

    sgdb_key = os.environ.get("SGDB_API_KEY", "")

    for index, game in enumerate(games):
        title = game.get("title", "")

        game["steamGridDB"] = get_steamgriddb_artwork(
            title,
            sgdb_cache,
            sgdb_key
        )

        game["launchBoxSpine"] = get_launchbox_spine(
            title,
            spine_cache
        )

        if index % 5 == 0:
            save_cache(sgdb_cache_path, sgdb_cache)
            save_cache(spine_cache_path, spine_cache)

    save_cache(sgdb_cache_path, sgdb_cache)
    save_cache(spine_cache_path, spine_cache)

    return games
        
