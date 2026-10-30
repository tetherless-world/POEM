(() => {
    'use strict';

    const storageKey = 'poem-theme';
    const system = window.matchMedia('(prefers-color-scheme: dark)');
    let preference = readPreference();

    function readPreference() {
        try {
            const saved = localStorage.getItem(storageKey);
            return saved === 'dark' || saved === 'light' ? saved : null;
        } catch (_) {
            return null;
        }
    }

    function applyTheme() {
        const theme = preference || (system.matches ? 'dark' : 'light');
        document.documentElement.setAttribute('data-bs-theme', theme);
        const toggle = document.getElementById('theme-switch');
        const reset = document.getElementById('theme-system');
        const status = document.getElementById('theme-status');
        if (toggle) toggle.checked = theme === 'dark';
        if (reset) {
            reset.disabled = preference === null;
            reset.textContent = preference === null ? 'System' : 'Use system';
        }
        if (status) status.textContent = `${theme === 'dark' ? 'Dark' : 'Light'} mode${preference === null ? ', following system' : ', manual selection'}`;
    }

    function setPreference(value) {
        preference = value;
        try {
            if (value === null) localStorage.removeItem(storageKey);
            else localStorage.setItem(storageKey, value);
        } catch (_) {
            // Keep the switch usable when browser storage is unavailable.
        }
        applyTheme();
    }

    // Apply before styles load to avoid a flash of the wrong theme.
    applyTheme();
    system.addEventListener('change', applyTheme);
    window.addEventListener('storage', event => {
        if (event.key === storageKey || event.key === null) {
            preference = readPreference();
            applyTheme();
        }
    });
    document.addEventListener('DOMContentLoaded', () => {
        document.getElementById('theme-switch')?.addEventListener('change', event => {
            setPreference(event.target.checked ? 'dark' : 'light');
        });
        document.getElementById('theme-system')?.addEventListener('click', () => setPreference(null));
        applyTheme();
    });
})();
