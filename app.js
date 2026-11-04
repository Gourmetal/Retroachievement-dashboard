const games = [];

let activeFilter = "all";

const gamesList = document.getElementById("games-list");
const searchInput = document.getElementById("game-search");
const filterButtons = document.querySelectorAll(".filter");

function renderGames() {
  const search = searchInput.value.toLowerCase();

  const filteredGames = games.filter(game => {
    const matchesSearch = game.title.toLowerCase().includes(search);

    const matchesFilter =
      activeFilter === "all" ||
      (activeFilter === "completed" && game.progress === 100) ||
      (activeFilter === "in-progress" &&
        game.progress > 0 &&
        game.progress < 100);

    return matchesSearch && matchesFilter;
  });

  if (filteredGames.length === 0) {
    gamesList.innerHTML = `
      <div class="loading">
        No games to display yet. Your RetroAchievements data
        will appear here once the account connection is set up.
      </div>
    `;
    return;
  }

  gamesList.innerHTML = filteredGames.map(game => `
    <article class="game-card">
      <h3>${escapeHTML(game.title)}</h3>
      <div class="game-meta">
        ${game.earned} / ${game.total} achievements ·
        ${game.progress}% complete
      </div>
      <div class="progress-track">
        <div class="progress-fill" style="width:${game.progress}%"></div>
      </div>
      <div class="game-meta">${escapeHTML(game.mode || "Progress")}</div>
    </article>
  `).join("");
}

function escapeHTML(value) {
  return String(value).replace(/[&<>"']/g, character => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  })[character]);
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

renderGames();
