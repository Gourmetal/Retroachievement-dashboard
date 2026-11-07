
/* =========================================
   GOURME HUB — REAL GAME CASE SHELVES
   SteamGridDB artwork + physical case spines
   ========================================= */

document.addEventListener("DOMContentLoaded", () => {
  const shelf = document.getElementById("gh-cartridge-grid");
  const count = document.getElementById("gh-cartridge-count");
  const search = document.getElementById("gh-cartridge-search");
  const filter = document.getElementById("gh-cartridge-filter");
  const gameLibrary = document.getElementById("games-container");

  if (!shelf) return;

  const CASES_PER_SHELF = 10;

  function escapeHTML(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#039;"
    })[character]);
  }

  function safeImage(url) {
    const value = String(url || "").trim();
    return /^https:\/\/[^"'<>]+$/i.test(value) ? value : "";
  }

  function getDashboardGames() {
    if (typeof games !== "undefined" && Array.isArray(games)) {
      return games;
    }

    return [];
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
    const artwork = getArtwork(game);
    const status = getGameStatus(game);
    const progress = Math.max(
      0,
      Math.min(100, Number(game.progress) || 0)
    );

    const earned = Number(game.earned) || 0;
    const total = Number(game.total) || 0;

    const imageMarkup = artwork
      ? `
        <img
          class="gh-case-artwork"
          src="${escapeHTML(artwork)}"
          alt="${escapeHTML(title)} cover artwork"
          loading="lazy">
      `
      : `
        <span class="gh-case-no-art" aria-hidden="true">🎮</span>
      `;

    const statusText =
      status === "completed" ? "100% COMPLETE" :
      status === "in-progress" ? `${progress}% COMPLETE` :
      "NOT STARTED";

    return `
      <button
        type="button"
        class="gh-game-case ${getCaseClass(status)}"
        data-game-title="${escapeHTML(title)}"
        aria-label="Open ${escapeHTML(title)}">

        <span class="gh-case-plastic-edge"></span>

        <span class="gh-case-spine">

          <span class="gh-case-top">
            <span class="gh-case-platform">${escapeHTML(consoleShort)}</span>
            <span class="gh-case-number">${String(index + 1).padStart(3, "0")}</span>
          </span>

          <span class="gh-case-cover">
            ${imageMarkup}
            <span class="gh-case-cover-shine"></span>
          </span>

          <span class="gh-case-title">
            ${escapeHTML(title)}
          </span>

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

    shelf.querySelectorAll(".gh-case-artwork").forEach(image => {
      image.addEventListener("error", () => {
        const fallback = document.createElement("span");
        fallback.className = "gh-case-no-art";
        fallback.setAttribute("aria-hidden", "true");
        fallback.textContent = "🎮";
        image.replaceWith(fallback);
      }, { once: true });
    });
  }

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
        mainSearch.dispatchEvent(new Event("input", { bubbles: true }));
      }

      if (mainFilter && mainFilter.value !== "all") {
        mainFilter.value = "all";
        mainFilter.dispatchEvent(new Event("change", { bubbles: true }));
      }

      summary = findGameSummary();
    }

    if (summary) {
      summary.click();
    }
  }

  shelf.addEventListener("click", event => {
    const gameCase = event.target.closest(".gh-game-case");
    if (!gameCase) return;

    const title = gameCase.dataset.gameTitle;
    if (title) openGameFromShelf(title);
  });

  if (search) search.addEventListener("input", renderShelf);
  if (filter) filter.addEventListener("change", renderShelf);

  if (gameLibrary) {
    const observer = new MutationObserver(() => {
      if (getDashboardGames().length) renderShelf();
    });

    observer.observe(gameLibrary, {
      childList: true,
      subtree: true
    });
  }

  renderShelf();
});
