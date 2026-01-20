(function() {
    'use strict';

    const THEME_KEY = 'kafkistry-theme';
    const THEME_LIGHT = 'light';
    const THEME_DARK = 'dark';
    const THEME_AUTO = 'auto';

    // Theme change listeners
    const themeChangeListeners = [];

    /**
     * Gets the stored theme preference or returns 'auto' as default
     */
    function getStoredTheme() {
        return localStorage.getItem(THEME_KEY) || THEME_AUTO;
    }

    /**
     * Saves the theme preference to localStorage
     */
    function setStoredTheme(theme) {
        localStorage.setItem(THEME_KEY, theme);
    }

    /**
     * Detects the system's preferred color scheme
     */
    function getPreferredTheme() {
        const storedTheme = getStoredTheme();

        if (storedTheme !== THEME_AUTO) {
            return storedTheme;
        }

        // Check system preference
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            return THEME_DARK;
        }

        return THEME_LIGHT;
    }

    /**
     * Notifies all registered theme change listeners
     */
    function notifyThemeChangeListeners(theme, resolvedTheme) {
        themeChangeListeners.forEach(listener => {
            try {
                listener(theme, resolvedTheme);
            } catch (error) {
                console.error('Error in theme change listener:', error);
            }
        });
    }

    /**
     * Applies the theme to the document
     */
    function applyTheme(theme) {
        const resolvedTheme = theme === THEME_AUTO
            ? (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? THEME_DARK : THEME_LIGHT)
            : theme;
        console.log(`Applying '${theme}' theme which resolves to '${resolvedTheme}' on UI`);
        document.documentElement.setAttribute('data-bs-theme', resolvedTheme);

        // Update theme selector UI if it exists
        updateThemeSelector(theme);

        // Notify all listeners about the theme change
        notifyThemeChangeListeners(theme, resolvedTheme);
    }

    /**
     * Updates the theme selector button/dropdown state
     */
    function updateThemeSelector(theme) {
        const selector = document.getElementById('theme-selector');
        if (!selector) return;

        // Update active state on dropdown items
        const dropdownItems = selector.querySelectorAll('.dropdown-item');
        dropdownItems.forEach(item => {
            const itemTheme = item.getAttribute('data-theme');
            if (itemTheme === theme) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });

        // Update icon in button
        const themeIcon = document.getElementById('theme-icon');
        if (themeIcon) {
            let icon = '';
            switch(theme) {
                case THEME_LIGHT:
                    icon = '☀️';
                    break;
                case THEME_DARK:
                    icon = '🌙';
                    break;
                case THEME_AUTO:
                    icon = '🔄';
                    break;
            }
            themeIcon.textContent = icon;
        }
    }

    /**
     * Initializes theme on page load (before content renders)
     */
    function initTheme() {
        const theme = getPreferredTheme();
        applyTheme(theme);
    }

    /**
     * Sets up theme change listeners
     */
    function initThemeListeners() {
        // Listen for system theme changes when auto mode is active
        if (window.matchMedia) {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
                if (getStoredTheme() === THEME_AUTO) {
                    applyTheme(THEME_AUTO);
                }
            });
        }

        // Theme selector click handlers
        $(document).on('click', '[data-theme-selector]', function(e) {
            e.preventDefault();
            const theme = $(this).attr('data-theme');
            setStoredTheme(theme);
            applyTheme(theme);
        });
    }

    // Apply theme immediately (before DOM ready to prevent flash)
    initTheme();

    // Initialize listeners when DOM is ready
    $(document).ready(function() {
        initThemeListeners();
    });

    // Expose API for manual control
    window.KafkistryTheme = {
        setTheme: function(theme) {
            setStoredTheme(theme);
            applyTheme(theme);
        },
        getTheme: getStoredTheme,
        getCurrentTheme: getPreferredTheme,
        /**
         * Register a listener to be notified when the theme changes
         * @param {Function} listener - Callback function (theme, resolvedTheme) => void
         * @returns {Function} Unregister function to remove the listener
         */
        addThemeChangeListener: function(listener) {
            if (typeof listener !== 'function') {
                throw new Error('Theme change listener must be a function');
            }
            themeChangeListeners.push(listener);

            // Return unregister function
            return function() {
                const index = themeChangeListeners.indexOf(listener);
                if (index > -1) {
                    themeChangeListeners.splice(index, 1);
                }
            };
        }
    };
})();
