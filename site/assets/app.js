/* 雫的域名盒 · 官网交互 */
(function () {
  "use strict";

  var root = document.documentElement;
  var THEME_KEY = "sdb-theme";

  /* ------------------------------ 主题 ------------------------------ */
  function applyTheme(t) {
    root.setAttribute("data-theme", t);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute("content", t === "dark" ? "#0C1019" : "#3E6FD9");
  }

  function readStoredTheme() {
    try { return localStorage.getItem(THEME_KEY); } catch (e) { return null; }
  }

  function storeTheme(t) {
    try { localStorage.setItem(THEME_KEY, t); } catch (e) { /* 隐私模式忽略 */ }
  }

  var stored = readStoredTheme();
  var prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;

  // 支持 ?theme=dark / ?theme=light 强制指定（方便分享特定配色）
  var forced = (function () {
    var m = /[?&]theme=(dark|light)\b/i.exec(location.search);
    return m ? m[1].toLowerCase() : null;
  })();

  applyTheme(forced || stored || (prefersDark ? "dark" : "light"));

  var themeBtn = document.getElementById("themeBtn");
  if (themeBtn) {
    themeBtn.addEventListener("click", function () {
      var next = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
      applyTheme(next);
      storeTheme(next);
    });
  }

  // 用户没手动选过时，跟随系统变化
  if (window.matchMedia) {
    var mq = window.matchMedia("(prefers-color-scheme: dark)");
    var onSys = function (e) {
      if (!readStoredTheme()) applyTheme(e.matches ? "dark" : "light");
    };
    if (mq.addEventListener) mq.addEventListener("change", onSys);
    else if (mq.addListener) mq.addListener(onSys);
  }

  /* ------------------------------ 导航 ------------------------------ */
  var nav = document.getElementById("nav");
  var onScroll = function () {
    if (!nav) return;
    if (window.scrollY > 8) nav.classList.add("is-scrolled");
    else nav.classList.remove("is-scrolled");
  };
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  var menuBtn = document.getElementById("menuBtn");
  var navLinks = document.getElementById("navLinks");
  if (menuBtn && navLinks) {
    menuBtn.addEventListener("click", function () {
      navLinks.classList.toggle("is-open");
    });
    navLinks.addEventListener("click", function (e) {
      if (e.target.tagName === "A") navLinks.classList.remove("is-open");
    });
  }

  /* --------------------------- 界面截图 Tab --------------------------- */
  var tabs = document.querySelectorAll(".tab");
  var panes = {
    android: document.getElementById("shotsAndroid"),
    windows: document.getElementById("shotsWindows")
  };

  Array.prototype.forEach.call(tabs, function (tab) {
    tab.addEventListener("click", function () {
      var key = tab.getAttribute("data-tab");
      Array.prototype.forEach.call(tabs, function (t) {
        t.classList.toggle("is-active", t === tab);
      });
      Object.keys(panes).forEach(function (k) {
        var el = panes[k];
        if (!el) return;
        if (k === key) el.classList.remove("is-hidden");
        else el.classList.add("is-hidden");
      });
    });
  });

  /* --------------------------- 卡片进场动画 --------------------------- */
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (!reduce && "IntersectionObserver" in window) {
    var targets = document.querySelectorAll(".card, .shot, .dl-card, .notice, .faq details");
    Array.prototype.forEach.call(targets, function (el) {
      el.style.opacity = "0";
      el.style.transform = "translateY(18px)";
      el.style.transition = "opacity .5s ease, transform .5s ease";
    });

    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        var el = entry.target;
        var idx = parseInt(el.getAttribute("data-i") || "0", 10);
        setTimeout(function () {
          el.style.opacity = "1";
          el.style.transform = "translateY(0)";
        }, Math.min(idx, 5) * 55);
        io.unobserve(el);
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.06 });

    var counters = {};
    Array.prototype.forEach.call(targets, function (el) {
      var parent = el.parentElement;
      var key = parent ? (parent.className || "x") : "x";
      counters[key] = (counters[key] || 0) + 1;
      el.setAttribute("data-i", String(counters[key] - 1));
      io.observe(el);
    });
  }

  /* --------------------------- FAQ 手风琴 --------------------------- */
  var faqs = document.querySelectorAll(".faq details");
  Array.prototype.forEach.call(faqs, function (d) {
    d.addEventListener("toggle", function () {
      if (!d.open) return;
      Array.prototype.forEach.call(faqs, function (other) {
        if (other !== d) other.open = false;
      });
    });
  });
})();
