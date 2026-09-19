"use client";

import { useEffect, useState } from "react";

const KEY = "gat-theme";

function currentTheme(): "light" | "dark" {
  const attr = document.documentElement.getAttribute("data-theme");
  if (attr === "light" || attr === "dark") return attr;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export default function ThemeToggle() {
  const [dark, setDark] = useState(false);

  useEffect(() => {
    setDark(currentTheme() === "dark");
  }, []);

  function toggle() {
    const next = currentTheme() === "dark" ? "light" : "dark";
    localStorage.setItem(KEY, next);
    document.documentElement.setAttribute("data-theme", next);
    setDark(next === "dark");
  }

  const label = dark ? "Switch to light mode" : "Switch to dark mode";
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={label}
      title={label}
      className="w-9 h-9 grid place-items-center rounded-lg border border-line text-ink hover:bg-background shrink-0"
    >
      {dark ? "☀️" : "🌙"}
    </button>
  );
}
