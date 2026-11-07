
/* =========================================
   GOURME HUB — GAMEPLAY VIDEO GALLERY
   Muted autoplay, with player controls.
   ========================================= */

document.addEventListener("DOMContentLoaded", () => {
  const gallery = document.getElementById("gh-cartridge-grid");
  const count = document.getElementById("gh-cartridge-count");
  const search = document.getElementById("gh-cartridge-search");
  const filter = document.getElementById("gh-cartridge-filter");
  const gameLibrary = document.getElementById("games-container");

  if (!gallery) return;

  let videoData = {};

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
    return videoData[game.title] || {
      search: `${game.title} gameplay longplay`
    };
  }

  function getSearchURL(query) {
    return "https://www.youtube.com/results?search_query=" +
      encodeURIComponent(query);
  }

  /* VIDEO POPUP */

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

    const frame = modal.querySelector("iframe");
    if (frame) frame.src = "about:blank";
  }

  function openVideo(game) {
    if (!game) return;

    const info = getVideoInfo(game);
    const videoId = String(info.videoId || "").trim();
    const progress = getProgress(game);
    const earned = Number(game.earned) || 0;
    const total = Number(game.total) || 0;
    const image = getArtwork(game);

    let videoMarkup = "";

    if (/^[A-Za-z0-9_-]{11}$/.test(videoId)) {
      const embedURL =
        `https://www.youtube-nocookie.com/embed/${videoId}` +
        "?autoplay=1&mute=1&controls=1&playsinline=1&rel=0";

      videoMarkup = `
        <div class="gh-video-player">
          <iframe
            src="${embedURL}"
            title="${escapeHTML(game.title)} gameplay video"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            allowfullscreen
            loading="eager">
          </iframe>
        </div>

        <p class="gh-video-source">
          ${escapeHTML(info.label || "Gameplay video")}
        </p>

        <p class="gh-video-muted-note">
          Video starts muted. Use the player controls to turn sound on.
        </p>
      `;
    } else {
      const query = info.search || `${game.title} gameplay`;
      const searchURL = getSearchURL(query);

      videoMarkup = `
        <div class="gh-video-search-preview">
          <div class="gh-video-search-icon">▶</div>

          <h3>Video coming soon</h3>

          <p>
            A verified video ID has not been added for this game yet.
            You can still search for gameplay videos.
          </p>

          <a class="gh-video-watch-button"
            href="${escapeHTML(searchURL)}"
            target="_blank"
            rel="noopener noreferrer">
            Search gameplay videos on YouTube ↗
          </a>
        </div>
      `;
    }

    modalContent.innerHTML = `
      <div class="gh-video-heading">
        ${
          image
            ? `<img class="gh-video-game-art"
                src="${escapeHTML(image)}"
                alt="${escapeHTML(game.title)} artwork">`
            : `<div class="gh-video-game-art gh-video-art-fallback">🎮</div>`
        }

        <div class="gh-video-game-info">
          <p class="gh-video-eyebrow">GOURME HUB GAMEPLAY</p>

          <h2 id="gh-video-title">${escapeHTML(game.title)}</h2>

          <p>${escapeHTML(game.console || "Game console unavailable")}</p>

          <p class="gh-video-progress-text">
            ${earned} / ${total} achievements · ${progress}% complete
          </p>

          <div class="gh-video-progress">
            <span style="width:${progress}%"></span>
          </div>
        </div>
      </div>

      ${videoMarkup}

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

    modal.classList.add("open");
    modal.setAttribute("aria-hidden", "false");
    document.body.style.overflow = "hidden";
    closeButton.focus();
  }

  closeButton.addEventListener("click", closeVideo);

  modal.addEventListener("click", event => {
    if (event.target === modal) closeVideo();
  });

  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && modal.classList.contains("open")) {
      closeVideo();
    }
  });

  /* GAME CARDS */

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
        </p>`;
      if (count) count.textContent = "0 Games";
      return;
    }

    const searchTerm = search
      ? search.value.trim().toLowerCase()
      : "";

    const selectedFilter = filter ? filter.value : "all";

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
        </p>`;
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
    if (selectedGame) openVideo(selectedGame);
  });

  /* LOAD VIDEO DATA */

  async function loadVideoData() {
    try {
      const response = await fetch("data/game-videos.json", {
        cache: "no-store"
      });

      if (!response.ok) {
        throw new Error("Could not load game-videos.json");
      }

      const data = await response.json();

      if (data && typeof data === "object" && !Array.isArray(data)) {
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

  if (search) search.addEventListener("input", renderGallery);
  if (filter) filter.addEventListener("change", renderGallery);

  if (gameLibrary) {
    const observer = new MutationObserver(renderGallery);
    observer.observe(gameLibrary, {
      childList: true,
      subtree: true
    });
  }

  loadVideoData();
});
