
let games = [];
let activeFilter = "all";

const gamesContainer = document.getElementById("games-container");
const searchInput = document.getElementById("game-search");
const filterSelect = document.getElementById("game-filter");

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
  const value = String(url || "");
  return /^https:\/\/[^"'<>]+$/i.test(value) ? value : "";
}

function formatDate(value) {
  if (!value) return "Date unavailable";

  const date = new Date(
    String(value).replace(" ", "T").replace(/Z$/, "") + "Z"
  );

  if (Number.isNaN(date.getTime())) return "Date unavailable";

  return date.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric"
  });
}

function formatPoints(value) {
  return Number(value || 0).toLocaleString();
}

function getEarnedAchievements() {
  return games.flatMap(game =>
    (Array.isArray(game.achievements) ? game.achievements : [])
      .filter(achievement => achievement.earned)
      .map(achievement => ({
        ...achievement,
        gameTitle: game.title,
        gameConsole: game.console
      }))
  );
}

/* PLAYER PROFILE */

function renderProfile(data) {
  const profile = data.profile || {};
  const username = profile.username || data.username || "Player";

  const avatar = document.getElementById("avatar");
  const usernameElement = document.getElementById("username");
  const motto = document.getElementById("motto");
  const memberSince = document.getElementById("member-since");

  if (usernameElement) usernameElement.textContent = username;

  if (motto) {
    motto.textContent = profile.motto ||
      "Building a collection, one achievement at a time.";
  }

  if (memberSince) {
    memberSince.textContent = profile.memberSince
      ? "Member since " + formatDate(profile.memberSince)
      : "";
  }

  if (avatar) {
    const avatarUrl = safeImage(profile.avatar);

    if (avatarUrl) {
      avatar.src = avatarUrl;
      avatar.alt = username + " avatar";
      avatar.style.display = "block";

      avatar.onerror = function () {
        this.style.display = "none";
      };
    } else {
      avatar.style.display = "none";
    }
  }

  const hardcore = document.getElementById("hardcore-points");
  const softcore = document.getElementById("softcore-points");

  if (hardcore) {
    hardcore.textContent = formatPoints(profile.hardcorePoints);
  }

  if (softcore) {
    softcore.textContent = formatPoints(profile.softcorePoints);
  }
}

/* OVERALL PROGRESS */

function renderProgress(earned, total) {
  const percentage = total
    ? Math.round((earned / total) * 100)
    : 0;

  const percentElement = document.getElementById("completion-percent");
  const bar = document.getElementById("overall-progress");
  const text = document.getElementById("achievement-progress-text");

  if (percentElement) {
    percentElement.textContent = percentage + "%";
  }

  if (bar) {
    bar.style.width = percentage + "%";
  }

  if (text) {
    text.textContent =
      formatPoints(earned) + " of " +
      formatPoints(total) + " achievements earned";
  }
}

/* BACKGROUND PARTICLES */

function createParticles() {
  const container = document.getElementById("ambient-particles");
  if (!container) return;

  container.innerHTML = "";

  const count = window.matchMedia("(max-width: 600px)").matches
    ? 16
    : 30;

  for (let i = 0; i < count; i++) {
    const particle = document.createElement("span");

    particle.className = "ambient-particle";
    particle.style.left = Math.random() * 100 + "%";
    particle.style.animationDelay = Math.random() * 18 + "s";
    particle.style.animationDuration =
      12 + Math.random() * 16 + "s";

    particle.style.setProperty(
      "--particle-drift",
      Math.random() * 100 - 50 + "px"
    );

    const size = 2 + Math.random() * 4;

    particle.style.width = size + "px";
    particle.style.height = size + "px";

    container.appendChild(particle);
  }
}

/* RECENT ACHIEVEMENTS */

function renderRecentAchievements() {
  const container = document.getElementById("recent-achievements");
  if (!container) return;

  const recent = getEarnedAchievements()
    .filter(item => item.dateEarned)
    .sort((a, b) =>
      new Date(b.dateEarned) - new Date(a.dateEarned)
    )
    .slice(0, 8);

  if (!recent.length) {
    container.innerHTML =
      '<p class="loading-message">Your recent achievements will appear here.</p>';
    return;
  }

  container.innerHTML = recent.map(item => `
    <article class="recent-item achievement-row achievement-earned">
      <div>
        <strong>🏆 ${escapeHTML(item.title)}</strong>
        <div class="game-meta">${escapeHTML(item.gameTitle)}</div>
        <div class="game-meta">${formatDate(item.dateEarned)}</div>
      </div>

      <span class="achievement-points">
        ${Number(item.points) || 0} pts
      </span>
    </article>
  `).join("");
}

/* FEATURED GAME SHOWCASE */

function renderShowcase() {
  const container = document.getElementById("game-showcase");
  if (!container) return;

  const featured = [...games]
    .sort((a, b) =>
      (Number(b.progress) || 0) - (Number(a.progress) || 0)
    )
    .slice(0, 4);

  if (!featured.length) {
    container.innerHTML =
      '<p class="loading-message">Your featured games will appear here.</p>';
    return;
  }

  container.innerHTML = featured.map(game => {
    const art = game.steamGridDB || {};

    const image = safeImage(art.hero) ||
      safeImage(art.grid) ||
      safeImage(game.boxArt);

    const logo = safeImage(art.logo);

    const progress = Math.max(
      0,
      Math.min(100, Number(game.progress) || 0)
    );

    return `
      <article class="game-card showcase-card">
        ${image ? `
          <img class="showcase-art"
               src="${escapeHTML(image)}"
               alt="${escapeHTML(game.title)} artwork"
               loading="lazy">
        ` : ""}

        <div class="showcase-info">
          ${logo ? `
            <img class="showcase-logo"
                 src="${escapeHTML(logo)}"
                 alt="${escapeHTML(game.title)} logo"
                 loading="lazy">
          ` : ""}

          <h3>${escapeHTML(game.title)}</h3>
          <p class="game-meta">${progress}% complete</p>
        </div>
      </article>
    `;
  }).join("");
}

/* GAME LIBRARY */

function renderGames() {
  if (!gamesContainer) return;

  const search = searchInput
    ? searchInput.value.trim().toLowerCase()
    : "";

  const filtered = games.filter(game => {
    const title = String(game.title || "").toLowerCase();
    const matchesSearch = title.includes(search);
    const progress = Number(game.progress) || 0;

    const matchesFilter =
      activeFilter === "all" ||
      (activeFilter === "completed" && progress >= 100) ||
      (activeFilter === "in-progress" &&
        progress > 0 && progress < 100) ||
      (activeFilter === "not-started" && progress === 0);

    return matchesSearch && matchesFilter;
  });

  const count = document.getElementById("game-count");

  if (count) {
    count.textContent = filtered.length + " Games";
  }

  if (!filtered.length) {
    gamesContainer.innerHTML =
      '<p class="loading-message">No matching games found.</p>';
    return;
  }

  gamesContainer.innerHTML = filtered.map(game => {
    const progress = Math.max(
      0,
      Math.min(100, Number(game.progress) || 0)
    );

    const achievements = Array.isArray(game.achievements)
      ? game.achievements
      : [];

    const art = game.steamGridDB || {};

    const cover = safeImage(art.grid) ||
      safeImage(game.boxArt);

    const hero = safeImage(art.hero);
    const logo = safeImage(art.logo);
    const icon = safeImage(art.icon);

    const earned = Number(game.earned) || 0;
    const total = Number(game.total) || 0;

    const achievementRows = achievements.length
      ? achievements.map(item => `
          <div class="achievement-row ${
            item.earned ? "achievement-earned" : "achievement-locked"
          }">
            <div>
              <strong>${escapeHTML(item.title)}</strong>

              <div class="game-meta">
                ${escapeHTML(item.description || "")}
              </div>

              <div class="game-meta">
                ${item.earned ? "✓ Earned" : "🔒 Locked"}
                ${item.hardcore ? " · Hardcore" : ""}
              </div>
            </div>

            <span class="achievement-points">
              ${Number(item.points) || 0} pts
            </span>
          </div>
        `).join("")
      : '<p class="loading-message">No achievement details available.</p>';

    return `
      <details class="game-card">
        ${hero ? `
          <div class="game-hero">
            <img src="${escapeHTML(hero)}"
                 alt=""
                 loading="lazy">
          </div>
        ` : ""}

        <summary>
          <div class="game-summary-layout">
            ${cover ? `
              <img class="game-cover"
                   src="${escapeHTML(cover)}"
                   alt="${escapeHTML(game.title)} cover art"
                   loading="lazy"
                   onerror="this.style.display='none'">
            ` : `
              <div class="game-cover-placeholder">🎮</div>
            `}

            <div class="game-summary-info">
              ${logo ? `
                <img class="game-logo"
                     src="${escapeHTML(logo)}"
                     alt="${escapeHTML(game.title)} logo"
                     loading="lazy">
              ` : ""}

              <h3>${escapeHTML(game.title)}</h3>

              <div class="game-meta">
                ${escapeHTML(game.console || "")}
              </div>

              <div class="game-meta">
                ${earned} / ${total} achievements
              </div>

              <div class="game-progress-layout">
                <div class="game-progress-ring"
                     style="--game-progress:${progress}%">
                  <div class="game-ring-center">
                    <strong>${progress}%</strong>
                  </div>
                </div>

                <div class="game-progress-text">
                  <strong>${earned} of ${total}</strong>
                  <span>Achievements unlocked</span>
                  <span>${escapeHTML(game.mode || "Progress")}</span>
                </div>
              </div>

              ${icon ? `
                <img class="game-icon"
                     src="${escapeHTML(icon)}"
                     alt=""
                     loading="lazy">
              ` : ""}

              <div class="game-meta">
                Tap to view achievements ▾
              </div>
            </div>
          </div>
        </summary>

        <div class="achievement-list">
          ${achievementRows}
        </div>
      </details>
    `;
  }).join("");
}

/* LOAD DASHBOARD DATA */

async function loadDashboard() {
  try {
    const response = await fetch("data/games.json?v=" + Date.now());

    if (!response.ok) {
      throw new Error("Game data unavailable");
    }

    const data = await response.json();

    games = Array.isArray(data.games) ? data.games : [];

    const stats = data.stats || {};

    const earned = Number(stats.achievementsEarned) ||
      games.reduce((sum, game) =>
        sum + (Number(game.earned) || 0), 0);

    const total = Number(stats.achievementsTotal) ||
      games.reduce((sum, game) =>
        sum + (Number(game.total) || 0), 0);

    const gamesPlayed = document.getElementById("games-played");
    const achievements = document.getElementById("total-achievements");
    const updated = document.getElementById("last-updated");

    if (gamesPlayed) {
      gamesPlayed.textContent = games.length;
    }

    if (achievements) {
      achievements.textContent = formatPoints(earned);
    }

    if (updated) {
      updated.textContent = data.lastUpdated
        ? "Last updated: " + formatDate(data.lastUpdated)
        : "";
    }

    renderProfile(data);
    renderProgress(earned, total);
    renderRecentAchievements();
    renderShowcase();
    renderGames();

  } catch (error) {
    console.error(error);

    if (gamesContainer) {
      gamesContainer.innerHTML =
        '<p class="loading-message">Could not load game data. Check the update workflow.</p>';
    }
  }
}

/* SEARCH AND FILTER */

if (searchInput) {
  searchInput.addEventListener("input", renderGames);
}

if (filterSelect) {
  filterSelect.addEventListener("change", () => {
    activeFilter = filterSelect.value;
    renderGames();
  });
}

/* STARTUP */

createParticles();
loadDashboard();
             <div class="game-progress-layout">
                <div class="game-progress-ring"
                     style="--game-progress:${progress}%">
                  <div class="game-ring-center">
                    <strong>${progress}%</strong>
                  </div>
                </div>

                <div class="game-progress-text">
                  <strong>${earned} of ${total}</strong>
                  <span>Achievements unlocked</span>
                  <span>${escapeHTML(game.mode || "Progress")}</span>
                </div>
              </div>

              ${icon ? `
                <img class="game-icon"
                     src="${escapeHTML(icon)}"
                     alt=""
                     loading="lazy">
              ` : ""}

              <div class="game-meta">Tap to view achievements ▾</div>
            </div>
          </div>
        </summary>

        <div class="achievement-list">
          ${achievementRows}
        </div>
      </details>
    `;
  }).join("");
}

/* LOAD DASHBOARD DATA */

async function loadDashboard() {
  try {
    const response = await fetch("data/games.json?v=" + Date.now());

    if (!response.ok) {
      throw new Error("Game data unavailable");
    }

    const data = await response.json();
    games = Array.isArray(data.games) ? data.games : [];

    const stats = data.stats || {};

    const earned = Number(stats.achievementsEarned) ||
      games.reduce((sum, game) =>
        sum + (Number(game.earned) || 0), 0);

    const total = Number(stats.achievementsTotal) ||
      games.reduce((sum, game) =>
        sum + (Number(game.total) || 0), 0);

    const gamesPlayed = document.getElementById("games-played");
    const achievements = document.getElementById("total-achievements");
    const updated = document.getElementById("last-updated");

    if (gamesPlayed) gamesPlayed.textContent = games.length;
    if (achievements) achievements.textContent = formatPoints(earned);

    if (updated) {
      updated.textContent = data.lastUpdated
        ? "Last updated: " + formatDate(data.lastUpdated)
        : "";
    }

    renderProfile(data);
    renderProgress(earned, total);
    renderRecentAchievements();
    renderShowcase();
    renderGames();

  } catch (error) {
    console.error(error);

    if (gamesContainer) {
      gamesContainer.innerHTML =
        '<p class="loading-message">Could not load game data. Check the update workflow.</p>';
    }
  }
}

/* SEARCH AND FILTER */

if (searchInput) {
  searchInput.addEventListener("input", renderGames);
}

if (filterSelect) {
  filterSelect.addEventListener("change", () => {
    activeFilter = filterSelect.value;
    renderGames();
  });
}

/* STARTUP */

createParticles();
loadDashboard();
se.ok) {
      throw new Error("Game data unavailable");
    }

    const data = await response.json();

    games = Array.isArray(data.games) ? data.games : [];

    const stats = data.stats || {};

    const earned = Number(stats.achievementsEarned) ||
      games.reduce((sum, game) => sum + (Number(game.earned) || 0), 0);

    const total = Number(stats.achievementsTotal) ||
      games.reduce((sum, game) => sum + (Number(game.total) || 0), 0);

    const gamesPlayed = document.getElementById("games-played");
    const achievementsEarned = document.getElementById("achievements-earned");
    const achievementsTotal = document.getElementById("achievements-total");
    const overallProgress = document.getElementById("overall-progress");

    if (gamesPlayed) gamesPlayed.textContent = games.length;
    if (achievementsEarned) achievementsEarned.textContent = earned;
    if (achievementsTotal) achievementsTotal.textContent = total;

    if (overallProgress) {
      overallProgress.textContent =
        total ? Math.round((earned / total) * 100) + "%" : "0%";
    }

    renderProfile(data);
    renderProgress(earned, total);
    renderRecentAchievements();
    renderShowcase();
    renderGames();

  } catch (error) {
    if (gamesList) {
      gamesList.innerHTML =
        '<div class="loading">Could not load game data. Check the update workflow.</div>';
    }

    console.error(error);
  }
}

/* SEARCH AND FILTERS */

if (searchInput) {
  searchInput.addEventListener("input", renderGames);
}

filterButtons.forEach(button => {
  button.addEventListener("click", () => {
    filterButtons.forEach(item => item.classList.remove("active"));
    button.classList.add("active");
    activeFilter = button.dataset.filter;
    renderGames();
  });
});

/* STARTUP */

createParticles();
loadDashboard();
