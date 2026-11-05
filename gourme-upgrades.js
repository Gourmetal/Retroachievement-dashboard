/* =================================
   GOURME HUB — INTERACTIVE UPGRADES
   ================================= */

document.addEventListener("DOMContentLoaded", () => {
  const status = document.getElementById("gh-status");

  // Rotating gamer status
  const taglines = [
    "Achievement Hunter",
    "Leveling Up One Unlock at a Time",
    "Collecting the Impossible",
    "Powered by Pixel Nostalgia"
  ];

  if (status && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    let index = 0;

    window.setInterval(() => {
      index = (index + 1) % taglines.length;
      status.textContent = taglines[index];
    }, 6500);
  }

  // Animate newly detected achievements
  const recent = document.getElementById("recent-achievements");

  if (!recent) return;

  const storageKey = "gourmeHubSeenAchievements";

  function getAchievementCards() {
    return Array.from(recent.querySelectorAll(".recent-item"));
  }

  function getAchievementKey(card) {
    return card.innerText.trim().replace(/\s+/g, " ");
  }

  let seen = [];

  try {
    seen = JSON.parse(localStorage.getItem(storageKey) || "[]");
    if (!Array.isArray(seen)) seen = [];
  } catch {
    seen = [];
  }

  const initialCards = getAchievementCards();

  // First visit: save existing cards without animating them
  if (!localStorage.getItem(storageKey)) {
    seen = initialCards.map(getAchievementKey);
    localStorage.setItem(storageKey, JSON.stringify(seen));
  }

  function checkForNewAchievements() {
    const cards = getAchievementCards();
    const currentKeys = cards.map(getAchievementKey);

    cards.forEach((card) => {
      const key = getAchievementKey(card);

      if (!seen.includes(key)) {
        card.classList.remove("gh-unlock");
        void card.offsetWidth;
        card.classList.add("gh-unlock");
      }
    });

    seen = Array.from(new Set([...seen, ...currentKeys])).slice(-100);
    localStorage.setItem(storageKey, JSON.stringify(seen));
  }

  const observer = new MutationObserver(() => {
    checkForNewAchievements();
  });

  observer.observe(recent, {
    childList: true,
    subtree: true
  });
});
