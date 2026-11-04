
let games = [];
let activeFilter = "all";

const gamesList = document.getElementById("games-list");
const searchInput = document.getElementById("game-search");
const filterButtons = document.querySelectorAll(".filter");

function escapeHTML(value) {
  return String(value ?? "").replace(/[&<>"']/g, c => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  })[c]);
}

function formatDate(value) {
  if (!value) return "Date unavailable";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Date unavailable";

  return date.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric"
  });
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

function renderProfile(data, earned, total) {
  const username = data.username || "RetroAchievements Player";
  const usernameElement = document.getElementById("profile-username");
  const nameElement = document.getElementById("profile-name");
  const avatar = document.getElementById("profile-avatar");

  if (usernameElement) usernameElement.textContent = username;
  if (nameElement) nameElement.textContent = "Player Overview";
  if (avatar) avatar.textContent = "🏆";

  const percentage = total
    ? Math.round((earned / total) * 100)
    : 0;

  const ring = document.getElementById("overall-ring");
  const ringPercentage = document.getElementById("ring-percentage");
  const ringEarned = document.getElementById("ring-earned");
  const ringTotal = document.getElementById("ring-total");

  if (ring) {
    ring.style.background =
      `conic-gradient(#ff9b14 ${percentage}%, #292929 ${percentage}% 100%)`;
  }

  if (ringPercentage) ringPercentage.textContent = percentage + "%";
  if (ringEarned) ringEarned.textContent = earned;
  if (ringTotal) ringTotal.textContent = total;
}

function renderRecentAchievements() {
  const container = document.getElementById("recent-unlocks");
  if (!container) return;

  const recent = getEarnedAchievements()
    .filter(achievement => achievement.dateEarned)
    .sort((a, b) =>
      new Date(b.dateEarned) - new Date(a.dateEarned)
    )
    .slice(0, 8);

  if (!recent.length) {
    container.innerHTML =
      '<p class="loading">Your recent unlocks will appear here.</p>';
    return;
  }

  container.innerHTML = recent.map(achievement => `
    <div class="achievement-row achievement-earned">
      <div>
        <strong>🏆 ${escapeHTML(achievement.title)}</strong>
        <div class="game-meta">
          ${escapeHTML(achievement.gameTitle)}
        </div>
        <div class="game-meta">
          ${formatDate(achievement.dateEarned)}
          ${achievement.hardcore ? " · Hardcore" : ""}
        </div>
      </div>
      <span class="achievement-points">
        ${Number(achievement.points) || 0} pts
      </span>
    </div>
  `).join("");
}

function renderShowcase() {
  const container = document.getElementById("achievement-showcase");
  if (!container) return;

  const featured = getEarnedAchievements()
    .sort((a, b) =>
      (Number(b.points) || 0) - (Number(a.points) || 0)
    )
    .slice(0, 6);

  if (!featured.length) {
    container.innerHTML =
      '<p class="loading">Earn achievements to build your showcase!</p>';
    return;
  }

  container.innerHTML = featured.map(achievement => `
    <div class="achievement-row achievement-earned">
      <div>
        <strong>⭐ ${escapeHTML(achievement.title)}</strong>
        <div class="game-meta">
          ${escapeHTML(achievement.gameTitle)}
        </div>
      </div>
      <span class="achievement-points">
        ${Number(achievement.points) || 0} pts
      </span>
    </div>
  `).join("");
}

function renderGames() {
  const search = searchInput.value.trim().toLowerCase();

  const filtered = games.filter(game => {
    const matches = String(game.title || "")
      .toLowerCase()
      .includes(search);

    const progress = Number(game.progress) || 0;

    return matches &&
      (activeFilter === "all" ||
       (activeFilter === "completed" && progress >= 100) ||
       (activeFilter === "in-progress" &&
        progress > 0 && progress < 100));
  });

  if (!filtered.length) {
    gamesList.innerHTML =
      '<div class="loading">No matching games found.</div>';
    return;
  }

  gamesList.innerHTML = filtered.map(game => {
    const progress = Math.max(
      0,
      Math.min(100, Number(game.progress) || 0)
    );

    const achievements = Array.isArray(game.achievements)
      ? game.achievements
      : [];

    const cover = game.boxArt
      ? `<img
           class="game-cover"
           src="${escapeHTML(game.boxArt)}"
           alt="${escapeHTML(game.title)} cover art"
           loading="lazy"
           onerror="this.style.display='none'"
         >`
      : `<div class="game-cover-placeholder">🎮</div>`;

    const achievementRows = achievements.length
      ? achievements.map(achievement => `
          <div class="achievement-row ${
            achievement.earned
              ? "achievement-earned"
              : "achievement-locked"
          }">
            <div>
              <strong>${escapeHTML(achievement.title)}</strong>
              <div class="game-meta">
                ${escapeHTML(achievement.description || "")}
              </div>
              <div class="game-meta">
                ${achievement.earned ? "✓ Earned" : "🔒 Locked"}
                ${achievement.hardcore ? " · Hardcore" : ""}
              </div>
            </div>
            <span class="achievement-points">
              ${Number(achievement.points) || 0} pts
            </span>
          </div>
        `).join("")
      : '<p class="loading">No achievement details available.</p>';

    return `
      <details class="game-card">
        <summary>
          <div class="game-summary-layout">
            ${cover}

            <div class="game-summary-info">
              <h3>${escapeHTML(game.title)}</h3>

              <div class="game-meta">
                ${Number(game.earned) || 0} /
                ${Number(game.total) || 0}
                achievements · ${progress}% complete
              </div>

              <div class="game-meta">
                ${escapeHTML(game.console || "")}
              </div>

              <div class="progress-track">
                <div
                  class="progress-fill"
                  style="width:${progress}%"
                ></div>
              </div>

              <div class="game-meta">
                ${escapeHTML(game.mode || "Softcore progress")}
              </div>

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

async function loadDashboard() {
  try {
    const response = await fetch(
      "data/games.json?v=" + Date.now()
    );

    if (!response.ok) {
      throw new Error("Game data unavailable");
    }

    const data = await response.json();
    games = Array.isArray(data.games) ? data.games : [];

    const stats = data.stats || {};

    const earned = Number(stats.achievementsEarned) ||
      games.reduce(
        (sum, game) => sum + (Number(game.earned) || 0),
        0
      );

    const total = Number(stats.achievementsTotal) ||
      games.reduce(
        (sum, game) => sum + (Number(game.total) || 0),
        0
      );

    document.getElementById("games-played").textContent =
      games.length;

    document.getElementById("achievements-earned").textContent =
      earned;

    document.getElementById("achievements-total").textContent =
      total;

    document.getElementById("overall-progress").textContent =
      total ? Math.round(earned / total * 100) + "%" : "0%";

    renderProfile(data, earned, total);
    renderRecentAchievements();
    renderShowcase();
    renderGames();

  } catch (error) {
    gamesList.innerHTML =
      '<div class="loading">Could not load game data. Check the update workflow.</div>';

    console.error(error);
  }
}

searchInput.addEventListener("input", renderGames);

filterButtons.forEach(button => {
  button.addEventListener("click", () => {
    filterButtons.forEach(item =>
      item.classList.remove("active")
    );

    button.classList.add("active");
    activeFilter = button.dataset.filter;

    renderGames();
  });
});

loadDashboard();
