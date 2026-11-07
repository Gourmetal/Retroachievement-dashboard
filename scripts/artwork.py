
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

SPINE_CACHE_VERSION = 2


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

    # Old cache entries may contain screenshots or incorrect artwork.
    # Reset them once when upgrading to this scraper version.
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

            if href:
                link_text = " ".join(node["text"]).strip()

                links.append({
                    "href": href,
                    "text": link_text,
                    "attrs": node["attrs"]
                })

        return links

    def get_image_candidates(self):
        candidates = []

        for node in self.elements:
            tag = node["tag"]
            attrs = node["attrs"]

            # Only inspect actual image elements.
            # Do not treat ordinary links or page backgrounds as images.
            if tag not in ("img", "source"):
                continue

            urls = []

            for key in (
                "src",
                "data-src",
                "data-original",
                "data-lazy-src",
                "data-url"
            ):
                if attrs.get(key):
                    urls.append(attrs[key])

            # Collect image URLs from srcset.
            srcset = attrs.get("srcset", "")

            if srcset:
                for item in srcset.split(","):
                    item = item.strip()

                    if item:
                        urls.append(item.split()[0])

            # Build separate local contexts.
            # Do not combine the entire page's text into one context.
            contexts = []

            image_context = " ".join(
                str(value)
                for value in attrs.values()
                if value
            )

            if image_context:
                contexts.append(image_context)

            # Only inspect the nearest four ancestors.
            # This prevents labels elsewhere on the page from being
            # incorrectly associated with an image.
            nearby_ancestors = node["ancestors"][-4:]

            for parent in reversed(nearby_ancestors):
                parts = []

                parts.extend(
                    str(value)
                    for value in parent["attrs"].values()
                    if value
                )

                parts.extend(parent["text"])

                context = " ".join(parts).strip()

                if context:
                    contexts.append(context)

            for url in urls:
                url = html.unescape(str(url).strip())

                if not url:
                    continue

                candidates.append({
                    "url": url,
                    "contexts": contexts
                })

        return candidates


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

        link_text = link["text"]

        if not link_text:
            link_text = (
                link["attrs"].get("title", "")
                or link["attrs"].get("aria-label", "")
            )

        score = title_match_score(
            title,
            link_text
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
# NORTH AMERICAN SPINE DETECTION
# --------------------------------------------------

def is_launchbox_image_url(url):
    parsed = urllib.parse.urlparse(url)

    if parsed.scheme not in ("http", "https"):
        return False

    if parsed.hostname not in LAUNCHBOX_IMAGE_HOSTS:
        return False

    # Reject detail pages and other non-image URLs.
    image_path = parsed.path.lower()

    return image_path.endswith((
        ".jpg",
        ".jpeg",
        ".png",
        ".webp"
    ))


def has_spine_type(context):
    context = html.unescape(context).lower()

    context = re.sub(
        r"[_–—]",
        "-",
        context
    )

    patterns = (
        r"\bbox\s*-\s*spine\b",
        r"\bbox\s+spine\b",
        r"\bmedia\s+type\s*[:=]\s*box\s*-\s*spine\b"
    )

    return any(
        re.search(pattern, context)
        for pattern in patterns
    )


def has_north_american_region(context):
    context = html.unescape(context).lower()

    context = re.sub(
        r"[_–—]",
        "-",
        context
    )

    region_patterns = (
        r"\bnorth\s+america\b",
        r"\bnorth\s+american\b",
        r"\bunited\s+states\b",
        r"\busa\b",
        r"\bntsc\s*-\s*u\b",
        r"\bntsc-u\b"
    )

    return any(
        re.search(pattern, context)
        for pattern in region_patterns
    )


def candidate_is_north_american_spine(candidate):
    contexts = candidate.get("contexts", [])

    # Require both the spine type and region to appear in the
    # same nearby context. Never mix labels from separate page areas.
    for context in contexts:
        if (
            has_spine_type(context)
            and has_north_american_region(context)
        ):
            return True

    return False


def find_north_american_spine(detail_html, title):
    parser = LaunchBoxHTMLParser()
    parser.feed(detail_html)

    candidates = parser.get_image_candidates()

    for candidate in candidates:
        image_url = absolute_launchbox_url(
            candidate["url"]
        )

        if not is_launchbox_image_url(image_url):
            continue

        if not candidate_is_north_american_spine(candidate):
            continue

        print(
            f"Verified North American spine: {title}"
        )

        return image_url

    return ""


# --------------------------------------------------
# LAUNCHBOX GAME LOOKUP
# --------------------------------------------------

def get_launchbox_spine(title, cache):

    cached = cache.get(title)

    # Only reuse URLs from the current cache version.
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

    for detail_url in detail_candidates[:12]:
        try:
            detail_html = get_launchbox_html(detail_url)

            spine = find_north_american_spine(
                detail_html,
                title
            )

            if spine:
                cache[title] = spine
                return spine

            time.sleep(0.2)

        except Exception as error:
            print(
                f"LaunchBox detail lookup failed "
                f"for {title}: {error}"
            )

    print(
        f"No verified North American spine found for: {title}"
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

        # SteamGridDB artwork remains the fallback.
        game["steamGridDB"] = get_steamgriddb_artwork(
            title,
            sgdb_cache,
            sgdb_key
        )

        # Only verified LaunchBox North American spines are accepted.
        game["launchBoxSpine"] = get_launchbox_spine(
            title,
            spine_cache
        )

        # Save progress periodically.
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
    print(f"Verified North American spines found: {found_spines}")

    return games
            
