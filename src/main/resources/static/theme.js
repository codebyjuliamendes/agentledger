// Theme bootstrap: runs in <head> so the saved choice applies before first paint.
// Without a saved choice the CSS follows prefers-color-scheme.
(function () {
  'use strict';
  var KEY = 'agentledger-theme';
  var root = document.documentElement;

  function saved() {
    try {
      var value = localStorage.getItem(KEY);
      return value === 'light' || value === 'dark' ? value : null;
    } catch (e) {
      return null;
    }
  }

  function effective() {
    return root.dataset.theme ||
      (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  }

  function syncButton() {
    var button = document.getElementById('theme-toggle');
    if (!button) return;
    var dark = effective() === 'dark';
    button.setAttribute('aria-pressed', String(dark));
  }

  var initial = saved();
  if (initial) root.dataset.theme = initial;

  document.addEventListener('DOMContentLoaded', function () {
    var button = document.getElementById('theme-toggle');
    if (!button) return;
    syncButton();
    button.addEventListener('click', function () {
      var next = effective() === 'dark' ? 'light' : 'dark';
      root.dataset.theme = next;
      try { localStorage.setItem(KEY, next); } catch (e) { /* storage unavailable */ }
      syncButton();
    });
    if (window.matchMedia) {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', syncButton);
    }
  });
})();
