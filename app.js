let games = [];
let activeFilter = "all";

const gamesList = document.getElementById("games-list");
const searchInput = document.getElementById("game-search");
const filterButtons = document.querySelectorAll(".filter");

function escapeHTML(value) {
  return String(value ?? "").replace(/[&<>"']/g, c => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;",
    '"': "&quot;", "'": "&#039;"
  })[c]);
}

function renderGames() {
  const search = searchInput.value.trim().toLowerCase();

  const filtered = games.filter(game => {
    const matches = String(game.title || "").toLowerCase().includes(search);
    const progress = Number(game.progress) || 0;

    return matches &&
      (activeFilter === "all" ||
       (activeFilter === "completed" && progress >= 100) ||
       (activeFilter === "in-progress" && progress > 0 && progress < 100));
  });

  if (!filtered.length) {
    gamesList.innerHTML = '<div class="loading">No matching games found. Check that your data has been updated.</div>';
    return;
  }

  gamesList.innerHTML = filtered.map(game => {
    const progress = Math.max(0, Math.min(100, Number(game.progress) || 0));

    return `
      <article class="game-card">
        <h3>${escapeHTML(game.title)}</h3>
        <div class="game-meta">
          ${Number(game.earned) || 0} / ${Number(game.total) || 0}
          achievements · ${progress}% complete ·
          ${escapeHTML(game.console || "")}
        </div>
        <div class="progress-track">
          <div class="progress-fill" style="width:${progress}%"></div>
        </div>
        <div class="game-meta">${escapeHTML(game.mode || "Softcore progress")}</div>
      </article>
    `;
  }).join("");
}

async function loadDashboard() {
  try {
    const response = await fetch("data/games.json?v=" + Date.now());
    if (!response.ok) throw new Error("Game data unavailable");

    const data = await response.json();
    games = Array.isArray(data.games) ? data.games : [];

    const stats = data.stats || {};
    const earned = Number(stats.achievementsEarned) ||
      games.reduce((sum, g) => sum + (Number(g.earned) || 0), 0);
    const total = Number(stats.achievementsTotal) ||
      games.reduce((sum, g) => sum + (Number(g.total) || 0), 0);

    document.getElementById("games-played").textContent = games.length;
    document.getElementById("achievements-earned").textContent = earned;
    document.getElementById("achievements-total").textContent = total;
    document.getElementById("overall-progress").textContent =
      total ? Math.round(earned / total * 100) + "%" : "0%";

    renderGames();

    const recent = document.getElementById("recent-unlocks");
    if (recent) {
      recent.innerHTML = '<p class="loading">Recent achievement history will be added in a later update.</p>';
    }
  } catch (error) {
    gamesList.innerHTML =
      '<div class="loading">Could not load game data. Check the update workflow.</div>';
    console.error(error);
  }
}

searchInput.addEventListener("input", renderGames);

filterButtons.forEach(button => {
  button.addEventListener("click", () => {
    filterButtons.forEach(item => item.classList.remove("active"));
    button.classList.add("active");
    activeFilter = button.dataset.filter;
    renderGames();
  });
});

loadDashboard();
