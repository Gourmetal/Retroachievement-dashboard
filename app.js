
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

  const date = new Date(value.replace(" ", "T") + "Z");

  if (Number.isNaN(date.getTime())) {
    return "Date unavailable";
  }

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
  const username = profile.username || data.username || "RetroAchievements Player";

  const usernameElement = document.getElementById("profile-username");
  const nameElement = document.getElementById("profile-name");
  const avatar = document.getElementById("profile-avatar");
  const motto = document.getElementById("profile-motto");
  const rank = document.getElementById("profile-rank");
  const memberSince = document.getElementById("profile-member-since");

  if (usernameElement) usernameElement.textContent = username;
  if (nameElement) nameElement.textContent = username;

  if (avatar && profile.avatar) {
    avatar.alt = username + " avatar";

    if (avatar.getAttribute("src") !== profile.avatar) {
      avatar.src = profile.avatar;
    }

    avatar.onerror = function () {
      this.style.display = "none";
      this.parentElement.textContent = "🏆";
    };
  }

  if (motto) {
    motto.textContent = profile.motto ||
      "Building a collection, one achievement at a time.";
  }

  if (rank) {
    rank.textContent = profile.rank
      ? "Rank: #" + formatPoints(profile.rank)
      : "Rank: Not available";
  }

  if (memberSince) {
    memberSince.textContent = profile.memberSince
      ? "Member since: " + formatDate(profile.memberSince)
      : "Member since: Not available";
  }

  const hardcore = document.getElementById("profile-hardcore-points");
  const softcore = document.getElementById("profile-softcore-points");
  const truePoints = document.getElementById("profile-true-points");

  if (hardcore) hardcore.textContent = formatPoints(profile.hardcorePoints);
  if (softcore) softcore.textContent = formatPoints(profile.softcorePoints);
  if (truePoints) truePoints.textContent = formatPoints(profile.truePoints);
}

/* OVERALL PROGRESS */

function renderProgress(earned, total) {
  const percentage = total
    ? Math.round((earned / total) * 100)
    : 0;

  const ring = document.getElementById("overall-ring");
  const ringPercentage = document.getElementById("ring-percentage");
  const ringEarned = document.getElementById("ring-earned");
  const ringTotal = document.getElementById("ring-total");

  if (ring) {
    ring.style.setProperty("--progress", percentage + "%");
  }

  if (ringPercentage) ringPercentage.textContent = percentage + "%";
  if (ringEarned) ringEarned.textContent = earned;
  if (ringTotal) ringTotal.textContent = total;
}

/* BACKGROUND PARTICLES */

function createParticles() {
  const container = document.getElementById("ambient-particles");
  if (!container) return;

  container.innerHTML = "";

  const particleCount = window.matchMedia("(max-width: 600px)").matches
    ? 16
    : 30;

  for (let i = 0; i < particleCount; i++) {
    const particle = document.createElement("span");
    particle.className = "ambient-particle";

    particle.style.left = Math.random() * 100 + "%";
    particle.style.animationDelay = (Math.random() * 18) + "s";
    particle.style.animationDuration = (12 + Math.random() * 16) + "s";
    particle.style.setProperty(
      "--particle-drift",
      (Math.random() * 100 - 50) + "px"
    );

    const size = 2 + Math.random() * 4;
    particle.style.width = size + "px";
    particle.style.height = size + "px";

    container.appendChild(particle);
  }
}

/* RECENT ACHIEVEMENTS */

function renderRecentAchievements() {
  const container = document.getElementById("recent-unlocks");
  if (!container) return;

  const recent = getEarnedAchievements()
    .filter(achievement => achievement.dateEarned)
    .sort((a, b) =>
      new Date(b.dateEarned.replace(" ", "T") + "Z") -
      new Date(a.dateEarned.replace(" ", "T") + "Z")
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
        <div class="game-meta">${escapeHTML(achievement.gameTitle)}</div>
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

/* ACHIEVEMENT SHOWCASE */

function renderShowcase() {
  const container = document.getElementById("achievement-showcase");
  if (!container) return;

  const featured = getEarnedAchievements()
    .sort((a, b) =>
      (Number(b.points) || 0) -
      (Number(a.points) || 0)
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
        <div class="game-meta">${escapeHTML(achievement.gameTitle)}</div>
      </div>
      <span class="achievement-points">
        ${Number(achievement.points) || 0} pts
      </span>
    </div>
  `).join("");
}

/* GAME COLLECTION WITH INDIVIDUAL PROGRESS RINGS */

function renderGames() {
  if (!gamesList) return;

  const search = searchInput
    ? searchInput.value.trim().toLowerCase()
    : "";

  const filtered = games.filter(game => {
    const matches = String(game.title || "")
      .toLowerCase()
      .includes(search);

    const progress = Number(game.progress) || 0;

    return matches &&
      (
        activeFilter === "all" ||
        (activeFilter === "completed" && progress >= 100) ||
        (activeFilter === "in-progress" &&
          progress > 0 && progress < 100)
      );
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

    const earned = Number(game.earned) || 0;
    const total = Number(game.total) || 0;

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
                ${earned} / ${total} achievements
              </div>

              <div class="game-meta">
                ${escapeHTML(game.console || "")}
              </div>

              <div class="game-progress-layout">
                <div class="game-progress-ring"
                     style="--game-progress: ${progress}%">
                  <div class="game-ring-center">
                    <strong>${progress}%</strong>
                  </div>
                </div>

                <div class="game-progress-text">
                  <strong>${earned} of ${total}</strong>
                  <span>Achievements unlocked</span>
                  <span>${escapeHTML(game.mode || "Softcore progress")}</span>
                </div>
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

/* LOAD DASHBOARD DATA */

async function loadDashboard() {
  try {
    const response = await fetch(
      "data/games.json?v=" + Date.now()
    );

    if (!response.ok) {
      throw new Error("Game data unavailable");
    }

    const data = await response.json();

    games = Array.isArray(data.games)
      ? data.games
      : [];

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

    const gamesPlayed = document.getElementById("games-played");
    const achievementsEarned =
      document.getElementById("achievements-earned");
    const achievementsTotal =
      document.getElementById("achievements-total");
    const overallProgress =
      document.getElementById("overall-progress");

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
    filterButtons.forEach(item =>
      item.classList.remove("active")
    );

    button.classList.add("active");
    activeFilter = button.dataset.filter;

    renderGames();
  });
});

/* STARTUP */

createParticles();
loadDashboard();
t = total;

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

if (searchInput) {
  searchInput.addEventListener("input", renderGames);
}

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
s="loading">Could not load game data. Check the update workflow.</div>';

    console.error(error);
  }
}

if (searchInput) {
  searchInput.addEventListener("input", renderGames);
}

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
