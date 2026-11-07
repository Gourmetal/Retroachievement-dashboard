
/* =========================================
   GOURME HUB — REAL GAME CASE SHELVES
   Manual spines first
   LaunchBox North American spines second
   SteamGridDB / RetroAchievements fallback
   ========================================= */

document.addEventListener("DOMContentLoaded", async () => {
  const shelf = document.getElementById("gh-cartridge-grid");
  const count = document.getElementById("gh-cartridge-count");
  const search = document.getElementById("gh-cartridge-search");
  const filter = document.getElementById("gh-cartridge-filter");
  const gameLibrary = document.getElementById("games-container");

  if (!shelf) return;

  const CASES_PER_SHELF = 10;
  let manualSpines = {};

  // Load custom artwork from its own file.
  // This file is separate from the automatic game-data updates.
  try {
    const response = await fetch("data/manual-spines.json", {
      cache: "no-store"
    });

    if (response.ok) {
      const data = await response.json();
      if (data && typeof data === "object" && !Array.isArray(data)) {
        manualSpines = data;
      }
    }
  } catch (error) {
    console.warn("Gourme Hub: manual spine file could not be loaded.", error);
  }

  function escapeHTML(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#039;"
    })[character]);
  }

  // Accept secure web URLs and safe repository-relative image paths.
  function safeImage(url) {
    const value = String(url || "").trim();

    if (/^https:\/\/[^"'<>]+$/i.test(value)) {
      return value;
    }

    if (
      value &&
      !value.startsWith("/") &&
      !value.includes("..") &&
      !/[\s"'<>\\]/.test(value) &&
      /^[a-z0-9_./-]+\.(png|jpe?g|webp|gif)$/i.test(value)
    ) {
      return value;
    }

    return "";
  }

  function getDashboardGames() {
    if (typeof games !== "undefined" && Array.isArray(games)) {
      return games;
    }

    return [];
  }

  // Manual image wins, then LaunchBox, then the existing cover fallback.
  function getSpine(game) {
    const manualImage = safeImage(manualSpines[game.title]);

    if (manualImage) {
      return {
        url: manualImage,
        source: "manual"
      };
    }

    const launchBoxImage = safeImage(game.launchBoxSpine);

    if (launchBoxImage) {
      return {
        url: launchBoxImage,
        source: "launchbox"
      };
    }

    return {
      url: "",
      source: ""
    };
  }

  function getArtwork(game) {
    const sgdb = game.steamGridDB || {};

    return (
      safeImage(sgdb.grid) ||
      safeImage(game.boxArt) ||
      ""
    );
  }

  function getGameStatus(game) {
    const progress = Math.max(
      0,
      Math.min(100, Number(game.progress) || 0)
    );

    if (progress >= 100) return "completed";
    if (progress > 0) return "in-progress";

    return "not-started";
  }

  function getConsoleShortName(consoleName) {
    const name = String(consoleName || "").toLowerCase();

    if (name.includes("game boy advance")) return "GBA";
    if (name.includes("game boy color")) return "GBC";
    if (name.includes("game boy")) return "GB";
    if (name.includes("nintendo 64")) return "N64";
    if (name.includes("super nintendo")) return "SNES";
    if (name.includes("nes") || name.includes("nintendo entertainment")) return "NES";
    if (name.includes("playstation 2")) return "PS2";
    if (name.includes("playstation")) return "PS";
    if (name.includes("genesis") || name.includes("mega drive")) return "GEN";
    if (name.includes("dreamcast")) return "DC";
    if (name.includes("arcade")) return "ARCADE";

    return String(consoleName || "GAME").slice(0, 10).toUpperCase();
  }

  function getCaseClass(status) {
    if (status === "completed") return "is-completed";
    if (status === "in-progress") return "is-in-progress";

    return "is-unstarted";
  }

  function createCase(game, index) {
    const title = String(game.title || "Unknown Game");
    const consoleName = String(game.console || "Unknown Console");
    const consoleShort = getConsoleShortName(consoleName);

    const spineData = getSpine(game);
    const spine = spineData.url;
    const artwork = getArtwork(game);
    const hasSpine = Boolean(spine);

    const status = getGameStatus(game);

    const progress = Math.max(
      0,
      Math.min(100, Number(game.progress) || 0)
    );

    const earned = Number(game.earned) || 0;
    const total = Number(game.total) || 0;

    const spineMarkup = hasSpine
      ? `
        <span class="gh-case-launchbox-wrap">
          <img
            class="gh-case-launchbox-spine"
            src="${escapeHTML(spine)}"
            alt="${escapeHTML(title)} game case spine"
            data-spine-source="${escapeHTML(spineData.source)}"
            loading="lazy">
        </span>
      `
      : "";

    const artworkMarkup = !hasSpine
      ? `
        <span class="gh-case-cover">
          ${
            artwork
              ? `
                <img
                  class="gh-case-artwork"
                  src="${escapeHTML(artwork)}"
                  alt="${escapeHTML(title)} cover artwork"
                  loading="lazy">
              `
              : `
                <span class="gh-case-no-art" aria-hidden="true">🎮</span>
              `
          }
          <span class="gh-case-cover-shine"></span>
        </span>
      `
      : "";

    const titleMarkup = !hasSpine
      ? `
        <span class="gh-case-title">
          ${escapeHTML(title)}
        </span>
      `
      : "";

    const statusText =
      status === "completed" ? "100% COMPLETE" :
      status === "in-progress" ? `${progress}% COMPLETE` :
      "NOT STARTED";

    return `
      <button
        type="button"
        class="gh-game-case ${getCaseClass(status)} ${hasSpine ? "has-launchbox-spine" : ""}"
        data-game-title="${escapeHTML(title)}"
        aria-label="Open ${escapeHTML(title)}">

        <span class="gh-case-plastic-edge"></span>

        <span class="gh-case-spine">

          ${spineMarkup}

          <span class="gh-case-top">
            <span class="gh-case-platform">${escapeHTML(consoleShort)}</span>
            <span class="gh-case-number">${String(index + 1).padStart(3, "0")}</span>
          </span>

          ${artworkMarkup}

          ${titleMarkup}

          <span class="gh-case-bottom">
            <span class="gh-case-progress">
              <span style="width:${progress}%"></span>
            </span>
            <span class="gh-case-status">${statusText}</span>
          </span>

        </span>

        <span class="gh-case-side-highlight"></span>
      </button>
    `;
  }

  function renderShelf() {
    const allGames = getDashboardGames();

    if (!allGames.length) {
      shelf.innerHTML = `
        <p class="gh-cartridge-empty">
          Your game collection will appear here when the library loads.
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
      const matchesSearch = title.includes(searchTerm);
      const status = getGameStatus(game);

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
      shelf.innerHTML = `
        <p class="gh-cartridge-empty">
          No games match your search.
        </p>
      `;
      return;
    }

    const rows = [];

    for (let i = 0; i < visibleGames.length; i += CASES_PER_SHELF) {
      const group = visibleGames.slice(i, i + CASES_PER_SHELF);

      const cases = group.map((game, groupIndex) =>
        createCase(game, i + groupIndex)
      ).join("");

      rows.push(`
        <div class="gh-case-shelf-row">

          <div class="gh-case-row-label">
            <span>GOURME HUB COLLECTION</span>
            <span>SHELF ${String(rows.length + 1).padStart(2, "0")}</span>
          </div>

          <div class="gh-case-row-track">
            ${cases}
          </div>

          <div class="gh-wood-shelf">
            <span></span>
          </div>

        </div>
      `);
    }

    shelf.innerHTML = rows.join("");

    // Replace broken images with a controller placeholder.
    shelf.querySelectorAll(
      ".gh-case-artwork, .gh-case-launchbox-spine"
    ).forEach(image => {
      image.addEventListener("error", () => {
        const fallback = document.createElement("span");

        fallback.className = "gh-case-no-art";
        fallback.setAttribute("aria-hidden", "true");
        fallback.textContent = "🎮";

        image.replaceWith(fallback);
      }, { once: true });
    });
  }

  // Preserve the existing game-detail popup behavior.
  function openGameFromShelf(title) {
    if (!gameLibrary) return;

    function findGameSummary() {
      return Array.from(
        gameLibrary.querySelectorAll("details.game-card summary")
      ).find(summary => {
        const heading = summary.querySelector("h3");
        return heading && heading.textContent.trim() === title;
      });
    }

    let summary = findGameSummary();

    if (!summary) {
      const mainSearch = document.getElementById("game-search");
      const mainFilter = document.getElementById("game-filter");

      if (mainSearch && mainSearch.value) {
        mainSearch.value = "";
        mainSearch.dispatchEvent(
          new Event("input", { bubbles: true })
        );
      }

      if (mainFilter && mainFilter.value !== "all") {
        mainFilter.value = "all";
        mainFilter.dispatchEvent(
          new Event("change", { bubbles: true })
        );
      }

      summary = findGameSummary();
    }

    if (summary) {
      summary.click();
    }
  }

  // Case click handling.
  shelf.addEventListener("click", event => {
    const gameCase = event.target.closest(".gh-game-case");
    if (!gameCase) return;

    const title = gameCase.dataset.gameTitle;
    if (title) openGameFromShelf(title);
  });

  // Search and filters.
  if (search) {
    search.addEventListener("input", renderShelf);
  }

  if (filter) {
    filter.addEventListener("change", renderShelf);
  }

  // Watch the main game library for updates.
  if (gameLibrary) {
    const observer = new MutationObserver(() => {
      if (getDashboardGames().length) {
        renderShelf();
      }
    });

    observer.observe(gameLibrary, {
      childList: true,
      subtree: true
    });
  }

  renderShelf();
});
                
