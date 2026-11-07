
/* =========================================
   GOURME HUB — VIRTUAL CARTRIDGE SHELF
   ========================================= */

document.addEventListener("DOMContentLoaded", () => {

  const shelf = document.getElementById("gh-cartridge-grid");
  const count = document.getElementById("gh-cartridge-count");
  const search = document.getElementById("gh-cartridge-search");
  const filter = document.getElementById("gh-cartridge-filter");
  const gameLibrary = document.getElementById("games-container");

  if (!shelf) return;

  let allGames = [];

  /* SAFE TEXT */

  function escapeHTML(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#039;"
    })[character]);
  }

  /* SAFE IMAGE URL */

  function safeImage(url) {
    const value = String(url || "").trim();

    return /^https:\/\/[^"'<>]+$/i.test(value)
      ? value
      : "";
  }

  /* GET GAMES FROM EXISTING DASHBOARD */

  function getDashboardGames() {
    if (typeof games !== "undefined" && Array.isArray(games)) {
      return games;
    }

    return [];
  }

  /* GET GAME ARTWORK */

  function getGameArtwork(game) {
    const artwork = game.steamGridDB || {};

    return (
      safeImage(artwork.grid) ||
      safeImage(game.boxArt) ||
      safeImage(artwork.hero) ||
      ""
    );
  }

  /* GAME STATUS */

  function getGameStatus(game) {
    const progress = Math.max(
      0,
      Math.min(100, Number(game.progress) || 0)
    );

    if (progress >= 100) {
      return "completed";
    }

    if (progress > 0) {
      return "in-progress";
    }

    return "not-started";
  }

  function getStatusLabel(status) {
    if (status === "completed") return "✓ Completed";
    if (status === "in-progress") return "In Progress";
    return "Not Started";
  }

  /* BUILD ONE CARTRIDGE */

  function createCartridge(game) {
    const title = String(game.title || "Unknown Game");
    const consoleName = String(game.console || "Unknown Console");

    const progress = Math.max(
      0,
      Math.min(100, Number(game.progress) || 0)
    );

    const status = getGameStatus(game);
    const image = getGameArtwork(game);

    const earned = Number(game.earned) || 0;
    const total = Number(game.total) || 0;

    const imageHTML = image
      ? `
        <img
          src="${escapeHTML(image)}"
          alt="${escapeHTML(title)} cartridge artwork"
          loading="lazy">
      `
      : `
        <span class="gh-cartridge-placeholder" aria-hidden="true">
          🎮
        </span>
      `;

    return `
      <button
        type="button"
        class="gh-cartridge is-${status}"
        data-game-title="${escapeHTML(title)}"
        aria-label="Open ${escapeHTML(title)} game details">

        <span class="gh-cartridge-art">
          ${imageHTML}
        </span>

        <span class="gh-cartridge-label">
          <strong>${escapeHTML(title)}</strong>
        </span>

        <span class="gh-cartridge-console">
          ${escapeHTML(consoleName)}
        </span>

        <span
          class="gh-cartridge-progress"
          role="progressbar"
          aria-valuenow="${progress}"
          aria-valuemin="0"
          aria-valuemax="100"
          aria-label="${escapeHTML(title)} completion">

          <span style="width:${progress}%"></span>
        </span>

        <span class="gh-cartridge-status">
          ${getStatusLabel(status)}
        </span>

        <span class="gh-cartridge-console">
          ${earned} / ${total} achievements
        </span>

      </button>
    `;
  }

  /* RENDER THE SHELF */

  function renderShelf() {
    allGames = getDashboardGames();

    if (!allGames.length) {
      shelf.innerHTML = `
        <p class="gh-cartridge-empty">
          Your cartridges will appear here when your game library loads.
        </p>
      `;

      if (count) {
        count.textContent = "0 Cartridges";
      }

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
        `${visibleGames.length} / ${allGames.length} Cartridges`;
    }

    if (!visibleGames.length) {
      shelf.innerHTML = `
        <p class="gh-cartridge-empty">
          No cartridges match your search.
        </p>
      `;

      return;
    }

    shelf.innerHTML = visibleGames
      .map(createCartridge)
      .join("");

    /* USE A FALLBACK IF COVER ART FAILS */

    shelf.querySelectorAll(".gh-cartridge-art img").forEach(image => {
      image.addEventListener("error", () => {
        const placeholder = document.createElement("span");

        placeholder.className = "gh-cartridge-placeholder";
        placeholder.setAttribute("aria-hidden", "true");
        placeholder.textContent = "🎮";

        image.replaceWith(placeholder);
      }, { once: true });
    });
  }

  /* OPEN THE EXISTING GAME DETAIL POPUP */

  function openGameFromShelf(title) {
    if (!gameLibrary) return;

    let summaries = Array.from(
      gameLibrary.querySelectorAll("details.game-card summary")
    );

    let matchingSummary = summaries.find(summary => {
      const heading = summary.querySelector("h3");

      return heading &&
        heading.textContent.trim() === title;
    });

    /*
      If the game is hidden by the main library search/filter,
      clear those controls so the existing popup can be opened.
    */

    if (!matchingSummary) {
      const mainSearch = document.getElementById("game-search");
      const mainFilter = document.getElementById("game-filter");

      if (mainSearch && mainSearch.value) {
        mainSearch.value = "";
        mainSearch.dispatchEvent(new Event("input", {
          bubbles: true
        }));
      }

      if (mainFilter && mainFilter.value !== "all") {
        mainFilter.value = "all";
        mainFilter.dispatchEvent(new Event("change", {
          bubbles: true
        }));
      }

      summaries = Array.from(
        gameLibrary.querySelectorAll("details.game-card summary")
      );

      matchingSummary = summaries.find(summary => {
        const heading = summary.querySelector("h3");

        return heading &&
          heading.textContent.trim() === title;
      });
    }

    if (matchingSummary) {
      matchingSummary.click();
    }
  }

  /* CARTRIDGE CLICK HANDLER */

  shelf.addEventListener("click", event => {
    const cartridge = event.target.closest(".gh-cartridge");

    if (!cartridge) return;

    const title = cartridge.dataset.gameTitle;

    if (title) {
      openGameFromShelf(title);
    }
  });

  /* SEARCH AND FILTER */

  if (search) {
    search.addEventListener("input", renderShelf);
  }

  if (filter) {
    filter.addEventListener("change", renderShelf);
  }

  /* WATCH FOR THE MAIN GAME LIBRARY TO LOAD */

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

  /* INITIAL RENDER */

  renderShelf();

});
