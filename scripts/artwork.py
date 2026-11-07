
import json
import os
import re
import time
import html
import urllib.parse
import urllib.request
from html.parser import HTMLParser

SGDB_ROOT = "https://www.steamgriddb.com/api/v2"
LAUNCHBOX_ROOT = "https://gamesdb.launchbox-app.com"

LAUNCHBOX_IMAGE_HOSTS = (
    "images.launchbox-app.com",
    "gamesdb-images.launchbox.gg"
)

# Increment this whenever the spine lookup method changes.
SPINE_CACHE_VERSION = 3


# --------------------------------------------------
# CACHE HELPERS
# --------------------------------------------------

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
    folder = os.path.dirname(path)

    if folder:
        os.makedirs(folder, exist_ok=True)

    with open(path, "w", encoding="utf-8") as file:
        json.dump(cache, file, indent=2, ensure_ascii=False)


def load_spine_cache(path):
    cache = load_cache(path)

    if cache.get("_cache_version") != SPINE_CACHE_VERSION:
        print("Resetting old LaunchBox spine cache...")

        cache = {
            "_cache_version": SPINE_CACHE_VERSION
        }

    return cache


# --------------------------------------------------
# HTTP HELPERS
# --------------------------------------------------

def fetch_json(url, headers=None):
    request = urllib.request.Request(
        url,
        headers=headers or {
            "User-Agent": "GourmeHub/1.0"
        }
    )

    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(
            response.read().decode("utf-8")
        )


def get_launchbox_html(url):
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 Chrome/120.0 Safari/537.36"
            ),
            "Accept": "text/html,application/xhtml+xml",
            "Accept-Language": "en-US,en;q=0.9"
        }
    )

    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode(
            "utf-8",
            errors="replace"
        )


def absolute_launchbox_url(url):
    if not url:
        return ""

    url = html.unescape(url.strip())

    if url.startswith("//"):
        return "https:" + url

    if url.startswith(("http://", "https://")):
        return url

    return urllib.parse.urljoin(LAUNCHBOX_ROOT, url)


# --------------------------------------------------
# STEAMGRIDDB
# --------------------------------------------------

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
        "~Hack~ Pokémon Emerald Seaglass":
            "Pokemon Emerald Seaglass",
        "~Homebrew~ Goodboy Galaxy":
            "Goodboy Galaxy"
    }

    search_title = aliases.get(title, title)

    try:
        search = sgdb_request(
            "search/autocomplete/"
            + urllib.parse.quote(search_title),
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
        print(
            f"SteamGridDB lookup failed for {title}: {error}"
        )

        return empty


# --------------------------------------------------
# LAUNCHBOX HTML PARSER
# --------------------------------------------------

class LaunchBoxHTMLParser(HTMLParser):

    VOID_TAGS = {
        "area",
        "base",
        "br",
        "col",
        "embed",
        "hr",
        "img",
        "input",
        "link",
        "meta",
        "param",
        "source",
        "track",
        "wbr"
    }

    def __init__(self):
        super().__init__(convert_charrefs=True)

        self.stack = []
        self.elements = []

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)

        node = {
            "tag": tag.lower(),
            "attrs": attributes,
            "text": [],
            "ancestors": list(self.stack)
        }

        self.elements.append(node)

        if tag.lower() not in self.VOID_TAGS:
            self.stack.append(node)

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)

        if tag.lower() not in self.VOID_TAGS:
            self.handle_endtag(tag)

    def handle_data(self, data):
        for node in self.stack:
            node["text"].append(data)

    def handle_endtag(self, tag):
        tag = tag.lower()

        for index in range(len(self.stack) - 1, -1, -1):
            if self.stack[index]["tag"] == tag:
                del self.stack[index:]
                break

    def get_links(self):
        links = []

        for node in self.elements:
            if node["tag"] != "a":
                continue

            href = node["attrs"].get("href", "")

            if not href:
                continue

            # HTMLParser collects child text into the anchor too.
            text = " ".join(node["text"]).strip()

            attribute_text = " ".join(
                str(value)
                for value in node["attrs"].values()
                if value
            )

            links.append({
                "href": href,
                "text": text,
                "attributes": attribute_text
            })

        return links


# --------------------------------------------------
# TITLE MATCHING
# --------------------------------------------------

def clean_game_title(title):
    title = re.sub(
        r"\[Subset.*?\]",
        "",
        title,
        flags=re.IGNORECASE
    )

    title = re.sub(
        r"^~(?:Hack|Homebrew)~\s*",
        "",
        title,
        flags=re.IGNORECASE
    )

    title = title.lower()
    title = re.sub(r"[^a-z0-9]+", " ", title)

    return " ".join(title.split())


def title_match_score(search_title, candidate_title):
    wanted = set(
        clean_game_title(search_title).split()
    )

    found = set(
        clean_game_title(candidate_title).split()
    )

    if not wanted or not found:
        return 0

    common = wanted.intersection(found)

    return len(common) / len(wanted)


def get_game_detail_candidates(title, search_html):
    parser = LaunchBoxHTMLParser()
    parser.feed(search_html)

    results = []

    for link in parser.get_links():
        href = link["href"]

        if "/games/details/" not in href.lower():
            continue

        detail_url = absolute_launchbox_url(href)

        candidate_title = (
            link["text"]
            or link["attributes"]
        )

        score = title_match_score(
            title,
            candidate_title
        )

        if score < 0.45:
            continue

        results.append((score, detail_url))

    results.sort(
        key=lambda item: item[0],
        reverse=True
    )

    unique = []
    seen = set()

    for score, url in results:
        if url in seen:
            continue

        seen.add(url)
        unique.append(url)

    return unique


# --------------------------------------------------
# LAUNCHBOX IMAGE GALLERY
# --------------------------------------------------

def is_launchbox_image_url(url):
    parsed = urllib.parse.urlparse(url)

    if parsed.scheme not in ("http", "https"):
        return False

    if parsed.hostname not in LAUNCHBOX_IMAGE_HOSTS:
        return False

    return parsed.path.lower().endswith((
        ".jpg",
        ".jpeg",
        ".png",
        ".webp"
    ))


def is_north_american_spine_label(label):
    """
    Match the actual LaunchBox gallery label.

    Expected examples:
      Box - Spine (North America) - 186 x 978
      Box - Spine (North American) - 190 x 978
    """

    label = html.unescape(label).lower()

    label = re.sub(
        r"[_–—]",
        "-",
        label
    )

    has_spine = bool(
        re.search(
            r"\bbox\s*-\s*spine\b",
            label
        )
    )

    has_north_america = bool(
        re.search(
            r"\bnorth\s+america(?:n)?\b",
            label
        )
    )

    return has_spine and has_north_america


def get_spines_from_gallery(gallery_html, title):
    """
    Read the LaunchBox Images gallery.

    The gallery links each image with its actual image type
    and region label. We use that label rather than guessing
    from surrounding page content.
    """

    parser = LaunchBoxHTMLParser()
    parser.feed(gallery_html)

    matches = []

    for link in parser.get_links():

        label = " ".join((
            link.get("text", ""),
            link.get("attributes", "")
        ))

        if not is_north_american_spine_label(label):
            continue

        image_url = absolute_launchbox_url(
            link.get("href", "")
        )

        if not is_launchbox_image_url(image_url):
            continue

        matches.append(image_url)

    # Remove duplicate image URLs while preserving order.
    unique = []
    seen = set()

    for url in matches:
        if url in seen:
            continue

        seen.add(url)
        unique.append(url)

    if unique:
        print(
            f"Found {len(unique)} North American spine "
            f"image(s) in gallery: {title}"
        )

    return unique


def get_launchbox_gallery_url(detail_url):
    """
    Convert:
      /games/details/5681-game-name

    Into:
      /games/images/5681-game-name
    """

    parsed = urllib.parse.urlparse(detail_url)

    new_path = parsed.path.replace(
        "/games/details/",
        "/games/images/",
        1
    )

    return urllib.parse.urlunparse((
        parsed.scheme,
        parsed.netloc,
        new_path,
        "",
        "",
        ""
    ))


# --------------------------------------------------
# LAUNCHBOX GAME LOOKUP
# --------------------------------------------------

def get_launchbox_spine(title, cache):

    cached = cache.get(title)

    if (
        isinstance(cached, str)
        and is_launchbox_image_url(cached)
    ):
        return cached

    search_urls = [
        (
            f"{LAUNCHBOX_ROOT}/games/results/?title="
            + urllib.parse.quote_plus(title)
        ),
        (
            f"{LAUNCHBOX_ROOT}/games/results?title="
            + urllib.parse.quote_plus(title)
        )
    ]

    detail_candidates = []
    seen_details = set()

    # Find the LaunchBox game detail page.
    for search_url in search_urls:
        try:
            print(
                f"Searching LaunchBox for: {title}"
            )

            search_html = get_launchbox_html(search_url)

            found = get_game_detail_candidates(
                title,
                search_html
            )

            for detail_url in found:
                if detail_url not in seen_details:
                    seen_details.add(detail_url)
                    detail_candidates.append(detail_url)

            if detail_candidates:
                break

        except Exception as error:
            print(
                f"LaunchBox search failed for {title}: {error}"
            )

    if not detail_candidates:
        print(
            f"No matching LaunchBox game page found: {title}"
        )

        return ""

    # Visit each matching game's actual Images gallery.
    for detail_url in detail_candidates[:12]:

        gallery_url = get_launchbox_gallery_url(
            detail_url
        )

        try:
            print(
                f"Opening LaunchBox image gallery: {title}"
            )

            gallery_html = get_launchbox_html(
                gallery_url
            )

            spine_images = get_spines_from_gallery(
                gallery_html,
                title
            )

            if spine_images:
                # Use the first exact North American spine match.
                spine = spine_images[0]

                cache[title] = spine

                print(
                    f"Selected North American spine: {title}"
                )

                return spine

            print(
                f"No Box - Spine (North America) "
                f"entry in gallery: {title}"
            )

            time.sleep(0.2)

        except Exception as error:
            print(
                f"LaunchBox gallery lookup failed "
                f"for {title}: {error}"
            )

    print(
        f"No North American spine found for: {title}"
    )

    return ""


# --------------------------------------------------
# MAIN ARTWORK UPDATE
# --------------------------------------------------

def get_all_artwork(games):

    sgdb_cache_path = "data/steamgriddb-cache.json"
    spine_cache_path = "data/launchbox-spine-cache.json"

    sgdb_cache = load_cache(sgdb_cache_path)
    spine_cache = load_spine_cache(spine_cache_path)

    sgdb_key = os.environ.get("SGDB_API_KEY", "")

    total = len(games)

    for index, game in enumerate(games):

        title = game.get("title", "")

        print(
            f"Processing artwork {index + 1}/{total}: {title}"
        )

        # Keep SteamGridDB artwork as the fallback.
        game["steamGridDB"] = get_steamgriddb_artwork(
            title,
            sgdb_cache,
            sgdb_key
        )

        # Retrieve only exact LaunchBox gallery spine matches.
        game["launchBoxSpine"] = get_launchbox_spine(
            title,
            spine_cache
        )

        # Save progress every five games.
        if (index + 1) % 5 == 0:
            save_cache(
                sgdb_cache_path,
                sgdb_cache
            )

            save_cache(
                spine_cache_path,
                spine_cache
            )

    # Final cache saves.
    save_cache(
        sgdb_cache_path,
        sgdb_cache
    )

    save_cache(
        spine_cache_path,
        spine_cache
    )

    found_spines = sum(
        1
        for game in games
        if game.get("launchBoxSpine")
    )

    print("")
    print("Artwork update complete.")
    print(f"Games processed: {total}")
    print(
        f"North American LaunchBox spines found: {found_spines}"
    )

    return games
