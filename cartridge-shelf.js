
/* =========================================
   GOURME HUB — GAMEPLAY VIDEO GALLERY
   Exact GBASnaps filename matching
   Muted autoplay with player controls
   ========================================= */

document.addEventListener("DOMContentLoaded", () => {
  const gallery = document.getElementById("gh-cartridge-grid");
  const count = document.getElementById("gh-cartridge-count");
  const search = document.getElementById("gh-cartridge-search");
  const filter = document.getElementById("gh-cartridge-filter");
  const gameLibrary = document.getElementById("games-container");

  if (!gallery) return;

  let videoData = {};
  let gbasnapsFilesPromise = null;

  function escapeHTML(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#039;"
    })[character]);
  }

  function getGames() {
    return typeof games !== "undefined" && Array.isArray(games)
      ? games
      : [];
  }

  function getArtwork(game) {
    const artwork = game.steamGridDB || {};
    const candidates = [artwork.hero, artwork.grid, game.boxArt];

    return candidates.find(url =>
      typeof url === "string" &&
      /^https:\/\/[^"'<>]+$/i.test(url)
    ) || "";
  }

  function getProgress(game) {
    return Math.max(0, Math.min(100, Number(game.progress) || 0));
  }

  function getStatus(game) {
    const progress = getProgress(game);

    if (progress >= 100) return "completed";
    if (progress > 0) return "in-progress";

    return "not-started";
  }

  function getStatusLabel(game) {
    const status = getStatus(game);

    if (status === "completed") return "Completed";
    if (status === "in-progress") return "In progress";

    return "Not started";
  }

  function getVideoInfo(game) {
    return videoData[game.title] || {};
  }

  function normalizeTitle(value) {
    return String(value || "")
      .toLowerCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/\[[^\]]*\]/g, " ")
      .replace(/~hack~|~homebrew~/gi, " ")
      .replace(/[^a-z0-9]+/g, " ")
      .trim()
      .replace(/\s+/g, " ");
  }

  function getSearchTitle(game) {
    return String(game.title || "")
      .replace(/\[[^\]]*\]/g, " ")
      .replace(/~hack~|~homebrew~/gi, "")
      .trim();
  }

  /*
   * GBASnaps DIRECTORY SEARCH
   *
   * Reads the actual file listing from the
   * GBASnaps Archive.org download item.
   * No general Archive.org search is used.
   */

  function normalizeArchiveFilename(filename) {
    return normalizeTitle(
      String(filename || "")
        .replace(/\.mp4$/i, "")
        .replace(/\([^)]*\)/g, " ")
    );
  }

  function getGameFilenameTerms(game) {
    const normalized = normalizeTitle(getSearchTitle(game));

    const stopWords = new Set([
      "the", "and", "of", "in", "a", "an",
      "full", "gameplay", "longplay", "walkthrough",
      "playthrough", "video", "game", "usa", "europe",
      "japan", "gba", "gbc", "gb", "gameboy",
      "boy", "version"
    ]);

    return normalized
      .split(" ")
      .filter(word => word.length > 0 && !stopWords.has(word));
  }

  /*
   * VERIFIED EXACT TITLE ALIASES
   *
   * These prevent similar game names and sequels
   * from being selected accidentally.
   */

  const exactFilenameAliases = {
    "pokemon emerald version": [
      "pokemon emerald version"
    ],

    "pokemon leafgreen version": [
      "pokemon leafgreen version"
    ],

    "sonic advance": [
      "sonic advance"
    ],

    "summon night swordcraft story": [
      "summon night swordcraft story"
    ],

    "super mario advance": [
      "super mario advance"
    ]
  };

  /*
   * These games intentionally have no automatic match.
   * They are hacks or homebrew and must not receive
   * an unrelated official-game video.
   */

  const noAutomaticVideoTitles = new Set([
    "goodboy galaxy",
    "pokemon emerald seaglass"
  ]);

  async function getGBASnapsFiles() {
    if (!gbasnapsFilesPromise) {
      gbasnapsFilesPromise = (async () => {
        const response = await fetch(
          "https://archive.org/metadata/gbasnaps",
          { cache: "no-store" }
        );

        if (!response.ok) {
          throw new Error("Could not load the GBASnaps directory listing");
        }

        const metadata = await response.json();
        const files = metadata?.files || [];

        return files.filter(file => {
          const name = String(file.name || "");

          return (
            /\.mp4$/i.test(name) &&
            !/thumb|thumbnail|sample|preview|trailer|small/i.test(name) &&
            (!file.source || file.source === "original")
          );
        });
      })().catch(error => {
        gbasnapsFilesPromise = null;
        throw error;
      });
    }

    return gbasnapsFilesPromise;
  }

  async function findArchiveVideo(game) {
    const gameKey = normalizeTitle(getSearchTitle(game));

    // Never guess a video for ROM hacks or homebrew.
    if (noAutomaticVideoTitles.has(gameKey)) {
      return null;
    }

    const files = await getGBASnapsFiles();
    const aliases = exactFilenameAliases[gameKey];

    let selected = null;

    /*
     * Exact alias matching.
     *
     * For example:
     * Pokemon Emerald Version
     * matches:
     * Pokemon - Emerald Version (USA, Europe).mp4
     *
     * Parenthetical region information is ignored.
     * Additional title words or sequel numbers are not.
     */

    if (aliases) {
      const normalizedAliases = aliases.map(alias =>
        normalizeTitle(alias)
      );

      selected = files.find(file => {
        const filename = String(file.name || "").split("/").pop();
        const normalizedFilename = normalizeArchiveFilename(filename);

        return normalizedAliases.includes(normalizedFilename);
      }) || null;

    } else {
      /*
       * Conservative matching for other games.
       *
       * Every meaningful title word must match.
       * Extra sequel numbers are rejected.
       */

      const wantedWords = getGameFilenameTerms(game);

      if (!wantedWords.length) return null;

      const wantedNumbers = wantedWords.filter(word =>
        /^\d+$/.test(word)
      );

      const matches = files.map(file => {
        const filename = String(file.name || "").split("/").pop();
        const normalizedFilename = normalizeArchiveFilename(filename);
        const filenameWords = new Set(normalizedFilename.split(" "));

        const hasAllWords = wantedWords.every(word =>
          filenameWords.has(word)
        );

        const filenameNumbers = normalizedFilename
          .split(" ")
          .filter(word => /^\d+$/.test(word));

        const hasConflictingNumber = filenameNumbers.some(number =>
          !wantedNumbers.includes(number)
        );

        return {
          file,
          hasAllWords,
          hasConflictingNumber
        };
      });

      const match = matches.find(item =>
        item.hasAllWords && !item.hasConflictingNumber
      );

      selected = match ? match.file : null;
    }

    if (!selected) return null;

    const filename = String(selected.name || "");

    const encodedFilename = filename
      .split("/")
      .map(encodeURIComponent)
      .join("/");

    return {
      title: filename.split("/").pop(),
      identifier: "gbasnaps",
      url: "https://archive.org/download/gbasnaps/" + encodedFilename
    };
  }

  /*
   * VIDEO POPUP
   */

  const modal = document.createElement("div");

  modal.id = "gh-video-modal";
  modal.className = "gh-video-modal";
  modal.setAttribute("aria-hidden", "true");

  modal.innerHTML = `
    <div class="gh-video-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="gh-video-title">

      <button type="button"
        class="gh-video-close"
        aria-label="Close video preview">×</button>

      <div id="gh-video-content"></div>
    </div>
  `;

  document.body.appendChild(modal);

  const modalContent = modal.querySelector("#gh-video-content");
  const closeButton = modal.querySelector(".gh-video-close");

  function closeVideo() {
    modal.classList.remove("open");
    modal.setAttribute("aria-hidden", "true");

    document.body.style.overflow = "";

    const player = modal.querySelector("video");

    if (player) {
      player.pause();
      player.removeAttribute("src");

      player.querySelectorAll("source").forEach(source => {
        source.remove();
      });

      player.load();
    }
  }

  function createHeading(game) {
    const progress = getProgress(game);
    const earned = Number(game.earned) || 0;
    const total = Number(game.total) || 0;
    const image = getArtwork(game);

    return `
      <div class="gh-video-heading">

        ${
          image
            ? `<img class="gh-video-game-art"
                src="${escapeHTML(image)}"
                alt="${escapeHTML(game.title)} artwork">`
            : `<div class="gh-video-game-art gh-video-art-fallback">🎮</div>`
        }

        <div class="gh-video-game-info">

          <p class="gh-video-eyebrow">
            GOURME HUB GAMEPLAY
          </p>

          <h2 id="gh-video-title">
            ${escapeHTML(game.title)}
          </h2>

          <p>
            ${escapeHTML(game.console || "Game console unavailable")}
          </p>

          <p class="gh-video-progress-text">
            ${earned} / ${total} achievements · ${progress}% complete
          </p>

          <div class="gh-video-progress">
            <span style="width:${progress}%"></span>
          </div>

        </div>
      </div>
    `;
  }

  async function openVideo(game) {
    if (!game) return;

    const heading = createHeading(game);

    modalContent.innerHTML = heading + `
      <div class="gh-video-search-preview">

        <div class="gh-video-search-icon">▶</div>

        <h3>Finding gameplay video…</h3>

        <p>
          Checking the actual GBASnaps download directory
          for this game's video file.
        </p>

      </div>

      <p class="gh-video-footer">
        ${escapeHTML(getStatusLabel(game))} · RetroAchievements collection
      </p>
    `;

    modal.classList.add("open");
    modal.setAttribute("aria-hidden", "false");

    document.body.style.overflow = "hidden";

    closeButton.focus();

    try {
      const info = getVideoInfo(game);
      let match = null;

      /*
       * Manually supplied Archive links take priority.
       * Supports both /download/ and /0/items/ URLs.
       */

      if (
        typeof info.archiveVideo === "string" &&
        /^https:\/\/(?:[\w.-]+\.)?archive\.org\/(?:download|0\/items)\//i.test(
          info.archiveVideo
        )
      ) {
        match = {
          title: info.label || game.title,
          url: info.archiveVideo
        };
      } else {
        match = await findArchiveVideo(game);
      }

      if (!modal.classList.contains("open")) return;

      if (match) {
        modalContent.innerHTML = heading + `

          <div class="gh-video-player">

            <video
              controls
              autoplay
              muted
              playsinline
              preload="metadata">

              <source
                src="${escapeHTML(match.url)}"
                type="video/mp4">

              Your browser does not support HTML5 video.

            </video>

          </div>

          <p class="gh-video-source">
            Internet Archive · ${escapeHTML(match.title)}
          </p>

          <p class="gh-video-muted-note">
            Video starts muted. Use the player volume control to turn sound on.
          </p>

          <p class="gh-video-footer">
            ${escapeHTML(getStatusLabel(game))} · RetroAchievements collection
          </p>
        `;

        const artworkImage =
          modalContent.querySelector("img.gh-video-game-art");

        if (artworkImage) {
          artworkImage.addEventListener("error", () => {
            const fallback = document.createElement("div");

            fallback.className =
              "gh-video-game-art gh-video-art-fallback";

            fallback.textContent = "🎮";

            artworkImage.replaceWith(fallback);
          }, { once: true });
        }

      } else {
        modalContent.innerHTML = heading + `

          <div class="gh-video-search-preview">

            <div class="gh-video-search-icon">▶</div>

            <h3>No matching GBASnaps video found</h3>

            <p>
              We couldn't find an exact matching MP4 filename in the
              GBASnaps download directory.
              No unrelated video was selected.
            </p>

            <a class="gh-video-watch-button"
              href="https://archive.org/download/gbasnaps"
              target="_blank"
              rel="noopener noreferrer">

              Open GBASnaps directory ↗

            </a>

          </div>

          <p class="gh-video-footer">
            ${escapeHTML(getStatusLabel(game))} · RetroAchievements collection
          </p>
        `;
      }

    } catch (error) {
      console.warn(
        "Gourme Hub: GBASnaps lookup failed.",
        error
      );

      if (!modal.classList.contains("open")) return;

      modalContent.innerHTML = heading + `

        <div class="gh-video-search-preview">

          <div class="gh-video-search-icon">!</div>

          <h3>Video lookup unavailable</h3>

          <p>
            Internet Archive couldn't be reached right now.
            Please try again later.
          </p>

        </div>

        <p class="gh-video-footer">
          ${escapeHTML(getStatusLabel(game))} · RetroAchievements collection
        </p>
      `;
    }
  }

  closeButton.addEventListener("click", closeVideo);

  modal.addEventListener("click", event => {
    if (event.target === modal) {
      closeVideo();
    }
  });

  document.addEventListener("keydown", event => {
    if (
      event.key === "Escape" &&
      modal.classList.contains("open")
    ) {
      closeVideo();
    }
  });

  /*
   * GAME CARDS
   */

  function createGameCard(game, index) {
    const title = String(game.title || "Unknown Game");
    const image = getArtwork(game);
    const progress = getProgress(game);
    const status = getStatus(game);
    const earned = Number(game.earned) || 0;
    const total = Number(game.total) || 0;

    const imageMarkup = image
      ? `<img class="gh-gallery-art"
          src="${escapeHTML(image)}"
          alt="${escapeHTML(title)} cover artwork"
          loading="lazy">`
      : `<div class="gh-gallery-art gh-gallery-art-fallback">🎮</div>`;

    return `
      <button type="button"
        class="gh-gallery-card ${status}"
        data-game-index="${index}"
        aria-label="Open gameplay preview for ${escapeHTML(title)}">

        <span class="gh-gallery-image-wrap">

          ${imageMarkup}

          <span class="gh-gallery-play">▶</span>

        </span>

        <span class="gh-gallery-card-copy">

          <strong class="gh-gallery-title">
            ${escapeHTML(title)}
          </strong>

          <span class="gh-gallery-console">
            ${escapeHTML(game.console || "Unknown console")}
          </span>

          <span class="gh-gallery-achievements">
            🏆 ${earned} / ${total} achievements
          </span>

          <span class="gh-gallery-progress">
            <span style="width:${progress}%"></span>
          </span>

          <span class="gh-gallery-status">
            ${escapeHTML(getStatusLabel(game))} · ${progress}%
          </span>

        </span>
      </button>
    `;
  }

  function renderGallery() {
    const allGames = getGames();

    if (!allGames.length) {
      gallery.innerHTML = `
        <p class="gh-cartridge-empty">
          Your games will appear here when the library loads.
        </p>
      `;

      if (count) count.textContent = "0 Games";

      return;
    }

    const searchTerm = search
      ? search.value.trim().toLowerCase()
      : "";

    const selectedFilter = filter
      ? filter.value
      : "all";

    const visibleGames = allGames.filter(game => {
      const title = String(game.title || "").toLowerCase();
      const consoleName = String(game.console || "").toLowerCase();

      const matchesSearch =
        title.includes(searchTerm) ||
        consoleName.includes(searchTerm);

      const status = getStatus(game);

      const matchesFilter =
        selectedFilter === "all" ||
        selectedFilter === status;

      return matchesSearch && matchesFilter;
    });

    if (count) {
      count.textContent =
        `${visibleGames.length} / ${allGames.length} Games`;
    }

    if (!visibleGames.length) {
      gallery.innerHTML = `
        <p class="gh-cartridge-empty">
          No games match your search.
        </p>
      `;

      return;
    }

    gallery.innerHTML = `
      <div class="gh-video-gallery-grid">

        ${visibleGames.map(game =>
          createGameCard(game, allGames.indexOf(game))
        ).join("")}

      </div>
    `;

    gallery.querySelectorAll(".gh-gallery-art").forEach(image => {
      image.addEventListener("error", () => {
        const fallback = document.createElement("div");

        fallback.className =
          "gh-gallery-art gh-gallery-art-fallback";

        fallback.textContent = "🎮";

        image.replaceWith(fallback);
      }, { once: true });
    });
  }

  gallery.addEventListener("click", event => {
    const card = event.target.closest(".gh-gallery-card");

    if (!card || !gallery.contains(card)) return;

    const index = Number(card.dataset.gameIndex);

    if (!Number.isInteger(index)) return;

    const selectedGame = getGames()[index];

    if (selectedGame) {
      openVideo(selectedGame);
    }
  });

  /*
   * LOAD VIDEO DATA
   */

  async function loadVideoData() {
    try {
      const response = await fetch("data/game-videos.json", {
        cache: "no-store"
      });

      if (!response.ok) {
        throw new Error("Could not load game-videos.json");
      }

      const data = await response.json();

      if (
        data &&
        typeof data === "object" &&
        !Array.isArray(data)
      ) {
        videoData = data;
      }

    } catch (error) {
      console.warn(
        "Gourme Hub: video data could not be loaded.",
        error
      );
    }

    renderGallery();
  }

  if (search) {
    search.addEventListener("input", renderGallery);
  }

  if (filter) {
    filter.addEventListener("change", renderGallery);
  }

  if (gameLibrary) {
    const observer = new MutationObserver(renderGallery);

    observer.observe(gameLibrary, {
      childList: true,
      subtree: true
    });
  }

  loadVideoData();
});
