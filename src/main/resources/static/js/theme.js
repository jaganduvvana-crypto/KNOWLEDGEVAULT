
"use strict";

document.addEventListener("DOMContentLoaded", function () {

    const themeToggle = document.getElementById("themeToggle");
    const root = document.documentElement;

    if (!themeToggle) {
        console.error("KNOWLEDGEVAULT theme button not found.");
        return;
    }

    // Load previously selected theme
    const savedTheme = localStorage.getItem("knowledgevault-theme");

    const initialTheme =
        savedTheme === "light" ? "light" : "dark";

    root.dataset.theme = initialTheme;

    // Update sun and moon icons
    function updateThemeButton() {

        if (root.dataset.theme === "dark") {

            // Sun icon: click to switch to light mode
            themeToggle.textContent = "\u2600\uFE0E";

            themeToggle.setAttribute(
                "aria-label",
                "Switch to light mode"
            );

            themeToggle.title = "Switch to light mode";

        } else {

            // Moon icon: click to switch to dark mode
            themeToggle.textContent = "\u263E\uFE0E";

            themeToggle.setAttribute(
                "aria-label",
                "Switch to dark mode"
            );

            themeToggle.title = "Switch to dark mode";
        }
    }

    // Set initial icon
    updateThemeButton();

    // Change theme when clicked
    themeToggle.addEventListener("click", function () {

        const currentTheme = root.dataset.theme;

        const newTheme =
            currentTheme === "dark" ? "light" : "dark";

        root.dataset.theme = newTheme;

        // Remember selected theme
        localStorage.setItem(
            "knowledgevault-theme",
            newTheme
        );

        updateThemeButton();

    });

});
