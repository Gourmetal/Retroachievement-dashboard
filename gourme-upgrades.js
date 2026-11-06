/* =========================================
   GOURME HUB — INTERACTIVE UPGRADES
   ========================================= */

document.addEventListener("DOMContentLoaded", () => {

  /* =======================================
     SHARED HELPERS
     ======================================= */

  function ghEscape(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#039;"
    })[character]);
  }

  function ghSafeImage(url) {
    const value = String(url || "");
    return /^https:\/\/[^"'<>]+$/i.test(value) ? value : "";
  }

  function ghGetGames() {
    return typeof games !== "undefined" && Array.isArray(games)
      ? games
      : [];
  }

  function ghFormatDate(value) {
    if (!value) return "Date unavailable";

    const date = new Date(
      String(value).replace(" ", "T")
    );

    if (Number.isNaN(date.getTime())) {
      return "Date unavailable";
    }

    return date.toLocaleDateString(undefined, {
      month: "short",
      day: "numeric",
      year: "numeric"
    });
  }

  /* =======================================
     ROTATING GAMER STATUS
     ======================================= */

  const status = document.getElementById("gh-status");

  const taglines = [
    "Achievement Hunter",
    "Leveling Up One Unlock at a Time",
    "Collecting the Impossible",
    "Powered by Pixel Nostalgia"
  ];

  if (
    status &&
    !window.matchMedia("(prefers-reduced-motion: reduce)").matches
  ) {
    let index = 0;

    window.setInterval(() => {
      index = (index + 1) % taglines.length;
      status.textContent = taglines[index];
    }, 6500);
  }

  /* =======================================
     NEW ACHIEVEMENT ANIMATIONS
     ======================================= */

  const recent = document.getElementById("recent-achievements");

  if (recent) {
    const storageKey = "gourmeHubSeenAchievements";

    function getAchievementCards() {
      return Array.from(
        recent.querySelectorAll(".recent-item")
      );
    }

    function getAchievementKey(card) {
      return card.innerText.trim().replace(/\s+/g, " ");
    }

    let seen = [];

    try {
      seen = JSON.parse(
        localStorage.getItem(storageKey) || "[]"
      );

      if (!Array.isArray(seen)) {
        seen = [];
      }
    } catch (error) {
      seen = [];
    }

    function checkForNewAchievements() {
      const cards = getAchievementCards();
      const currentKeys = cards.map(getAchievementKey);

      cards.forEach(card => {
        const key = getAchievementKey(card);

        if (!seen.includes(key)) {
          card.classList.remove("gh-unlock");
          void card.offsetWidth;
          card.classList.add("gh-unlock");
        }
      });

      seen = Array.from(
        new Set([...seen, ...currentKeys])
      ).slice(-100);

      try {
        localStorage.setItem(
          storageKey,
          JSON.stringify(seen)
        );
      } catch (error) {
        // Continue if browser storage is unavailable.
      }
    }

    // Save the current achievements on first visit.
    try {
      if (!localStorage.getItem(storageKey)) {
        seen = getAchievementCards().map(getAchievementKey);

        localStorage.setItem(
          storageKey,
          JSON.stringify(seen)
        );
      }
    } catch (error) {
      // Continue if browser storage is unavailable.
    }

    const recentObserver = new MutationObserver(() => {
      checkForNewAchievements();
    });

    recentObserver.observe(recent, {
      childList: true,
      subtree: true
    });
  }

  /* =======================================
     ACHIEVEMENT SHOWCASE
     ======================================= */

  const achievementShowcase = document.getElementById(
    "achievement-showcase"
  );

  function ghGetEarnedAchievements() {
    return ghGetGames().flatMap(game => {
      const achievements = Array.isArray(game.achievements)
        ? game.achievements
        : [];

      return achievements
        .filter(achievement => achievement.earned)
        .map(achievement => ({
          ...achievement,
          gameTitle: game.title || "Unknown Game",
          gameConsole: game.console || ""
        }));
    });
  }

  function ghRenderAchievementShowcase() {
    if (!achievementShowcase) return;

    const earned = ghGetEarnedAchievements()
      .sort((a, b) => {
        const dateA = new Date(a.dateEarned || 0).getTime();
        const dateB = new Date(b.dateEarned || 0).getTime();

        return dateB - dateA;
      })
      .slice(0, 6);

    if (!earned.length) {
      achievementShowcase.innerHTML = `
        <p class="loading-message">
          Your earned achievements will appear here.
        </p>
      `;
      return;
    }

    achievementShowcase.innerHTML = earned.map(item => `
      <article class="gh-achievement-card">
        <h3>🏆 ${ghEscape(item.title)}</h3>

        <p>
          🎮 ${ghEscape(item.gameTitle)}
        </p>

        <p>
          ${ghEscape(item.description || "Achievement unlocked!")}
        </p>

        <span class="gh-achievement-points">
          ⭐ ${Number(item.points) || 0} points
        </span>

        <span class="gh-achievement-date">
          ${ghFormatDate(item.dateEarned)}
        </span>
      </article>
    `).join("");
  }

  /* =======================================
     THEME SWITCHER
     ======================================= */

  const themeButtons = document.querySelectorAll(
    "[data-theme-choice]"
  );

  const themeStorageKey = "gourmeHubTheme";

  const allowedThemes = [
    "midnight",
    "retro-neon"
  ];

  function ghSetTheme(theme) {
    if (!allowedThemes.includes(theme)) {
      theme = "midnight";
    }

    document.body.dataset.ghTheme = theme;

    themeButtons.forEach(button => {
      const isActive =
        button.dataset.themeChoice === theme;

      button.classList.toggle("active", isActive);

      button.setAttribute(
        "aria-pressed",
        String(isActive)
      );
    });

    try {
      localStorage.setItem(themeStorageKey, theme);
    } catch (error) {
      console.warn("Could not save theme preference.", error);
    }
  }

  let savedTheme = "midnight";

  try {
    savedTheme =
      localStorage.getItem(themeStorageKey) || "midnight";
  } catch (error) {
    savedTheme = "midnight";
  }

  ghSetTheme(savedTheme);

  themeButtons.forEach(button => {
    button.addEventListener("click", () => {
      ghSetTheme(button.dataset.themeChoice);
    });
  });

  /* =======================================
     GAME DETAIL POPUP
     ======================================= */

  const modalBackdrop = document.getElementById(
    "gh-game-modal"
  );

  const modalContent = document.getElementById(
    "gh-modal-content"
  );

  const closeButton = modalBackdrop
    ? modalBackdrop.querySelector(".gh-modal-close")
    : null;

  let previousFocus = null;

  function ghCloseModal() {
    if (!modalBackdrop) return;

    modalBackdrop.classList.remove("open");

    modalBackdrop.setAttribute(
      "aria-hidden",
      "true"
    );

    document.body.style.overflow = "";

    if (
      previousFocus &&
      typeof previousFocus.focus === "function"
    ) {
      previousFocus.focus();
    }
  }

  function ghOpenGame(game) {
    if (!game || !modalBackdrop || !modalContent) {
      return;
    }

    previousFocus = document.activeElement;

    const achievements = Array.isArray(game.achievements)
      ? game.achievements
      : [];

    const earned = Number(game.earned) || 0;
    const total = Number(game.total) || 0;

    const progress = Math.max(
      0,
      Math.min(100, Number(game.progress) || 0)
    );

    const artwork = game.steamGridDB || {};

    const image =
      ghSafeImage(artwork.hero) ||
      ghSafeImage(artwork.grid) ||
      ghSafeImage(game.boxArt);

    const sortedAchievements = [...achievements].sort(
      (a, b) => {
        if (a.earned !== b.earned) {
          return a.earned ? -1 : 1;
        }

        return String(a.title || "").localeCompare(
          String(b.title || "")
        );
      }
    );

    const achievementHTML = sortedAchievements.map(item => {
      const isEarned = Boolean(item.earned);

      return `
        <div class="gh-modal-achievement ${
          isEarned ? "earned" : "locked"
        }">

          <div>
            <strong>
              ${isEarned ? "🏆" : "🔒"}
              ${ghEscape(item.title)}
            </strong>

            <small>
              ${ghEscape(item.description || "No description available.")}
            </small>

            <small>
              ${isEarned ? "Earned" : "Locked"}
              ${item.hardcore ? " · Hardcore" : ""}
            </small>

            ${
              isEarned && item.dateEarned
                ? `<small>${ghFormatDate(item.dateEarned)}</small>`
                : ""
            }
          </div>

          <strong>
            ${Number(item.points) || 0} pts
          </strong>
        </div>
      `;
    }).join("");

    modalContent.innerHTML = `
      ${
        image
          ? `
            <img
              class="gh-modal-art"
              src="${ghEscape(image)}"
              alt="${ghEscape(game.title)} artwork">
          `
          : ""
      }

      <h2 class="gh-modal-title" id="gh-modal-title">
        ${ghEscape(game.title)}
      </h2>

      <p class="gh-modal-meta">
        🎮 ${ghEscape(game.console || "Unknown console")}
      </p>

      <p class="gh-modal-meta">
        ${earned} of ${total} achievements earned
      </p>

      <div
        class="gh-modal-progress"
        role="progressbar"
        aria-valuenow="${progress}"
        aria-valuemin="0"
        aria-valuemax="100"
        aria-label="Game completion">

        <div style="width: ${progress}%"></div>
      </div>

      <p class="gh-modal-meta">
        ${progress}% complete
        · ${ghEscape(game.mode || "Progress")}
      </p>

      <h3>Achievement List</h3>

      <div class="gh-modal-achievements">
        ${
          achievementHTML ||
          `
            <p class="loading-message">
              No achievement details available.
            </p>
          `
        }
      </div>
    `;

    modalBackdrop.classList.add("open");

    modalBackdrop.setAttribute(
      "aria-hidden",
      "false"
    );

    document.body.style.overflow = "hidden";

    if (closeButton) {
      closeButton.focus();
    }
  }

  if (closeButton) {
    closeButton.addEventListener(
      "click",
      ghCloseModal
    );
  }

  if (modalBackdrop) {
    modalBackdrop.addEventListener("click", event => {
      if (event.target === modalBackdrop) {
        ghCloseModal();
      }
    });
  }

  document.addEventListener("keydown", event => {
    if (
      event.key === "Escape" &&
      modalBackdrop &&
      modalBackdrop.classList.contains("open")
    ) {
      ghCloseModal();
    }
  });

  /* =======================================
     OPEN GAME POPUP FROM GAME LIBRARY
     ======================================= */

  const gameLibrary = document.getElementById(
    "games-container"
  );

  if (gameLibrary) {
    gameLibrary.addEventListener("click", event => {
      const summary = event.target.closest("summary");

      if (!summary || !gameLibrary.contains(summary)) {
        return;
      }

      const card = summary.closest("details.game-card");

      if (!card) return;

      const titleElement = summary.querySelector("h3");

      if (!titleElement) return;

      const selectedTitle = titleElement.textContent.trim();

      const selectedGame = ghGetGames().find(game =>
        String(game.title || "").trim() === selectedTitle
      );

      if (!selectedGame) return;

      // Open the popup instead of expanding the old details list.
      event.preventDefault();

      ghOpenGame(selectedGame);
    });
  }

  /* =======================================
     REFRESH SHOWCASE AFTER GAME DATA LOADS
     ======================================= */

  function ghRefreshShowcase() {
    if (ghGetGames().length) {
      ghRenderAchievementShowcase();
    }
  }

  const gameDataContainer = document.getElementById(
    "games-container"
  );

  if (gameDataContainer) {
    const gameObserver = new MutationObserver(() => {
      ghRefreshShowcase();
    });

    gameObserver.observe(gameDataContainer, {
      childList: true,
      subtree: true
    });
  }

  // Initial render.
  ghRefreshShowcase();

});
