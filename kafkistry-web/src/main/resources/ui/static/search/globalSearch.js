$(document).ready(function () {
    initGlobalSearch();
});

let searchTimeout;
const SEARCH_DELAY_MS = 300;
const MIN_QUERY_LENGTH = 2;
let previouslyActiveNavItem = null;

// Double Shift detection
let lastShiftPressTime = 0;
const DOUBLE_SHIFT_DELAY_MS = 500;

function toggleSearchDropdown() {
    const $searchContainer = $('#navbar-search-dropdown');
    if ($searchContainer.hasClass('show')) {
        closeSearchDropdown();
    } else {
        openSearchDropdown();
    }
}

function openSearchDropdown() {
    const $searchContainer = $('#navbar-search-dropdown');
    const $searchInput = $('#global-search-input');
    const $searchToggle = $('#global-search-toggle');

    // Remember currently active nav-item
    const $currentActive = $('.nav-link.active').not('#global-search-toggle');
    if ($currentActive.length > 0) {
        previouslyActiveNavItem = $currentActive.get(0);
        $currentActive.removeClass('active');
    }

    $searchContainer.addClass('show');
    $searchToggle.attr('aria-expanded', 'true');
    $searchToggle.addClass('active'); // Highlight nav-link when dropdown is open

    setTimeout(() => {
        $searchInput.focus();
    }, 50);
}

function closeSearchDropdown() {
    const $searchContainer = $('#navbar-search-dropdown');
    const $searchToggle = $('#global-search-toggle');
    const $resultsDropdown = $('#global-search-dropdown');

    $searchContainer.removeClass('show');
    $searchToggle.attr('aria-expanded', 'false');
    $searchToggle.removeClass('active'); // Remove highlight when dropdown closes
    $resultsDropdown.hide();

    // Restore previously active nav-item
    if (previouslyActiveNavItem) {
        $(previouslyActiveNavItem).addClass('active');
        previouslyActiveNavItem = null;
    }
}

function initGlobalSearch() {
    const $searchToggle = $('#global-search-toggle');
    const $searchContainer = $('#navbar-search-dropdown');
    const $searchInput = $('#global-search-input');
    const $dropdown = $('#global-search-dropdown');

    // Toggle dropdown when icon is clicked
    $searchToggle.on('click', function(e) {
        e.preventDefault();
        e.stopPropagation();
        toggleSearchDropdown();
    });

    // Prevent dropdown from closing when clicking inside
    $searchContainer.on('click', function(e) {
        e.stopPropagation();
    });

    // Prevent Bootstrap dropdown from handling keyboard events in search container
    $searchContainer.on('keydown', function(e) {
        // Handle Escape key to close dropdown
        if (e.which === 27) { // Escape
            e.stopPropagation();
            closeSearchDropdown();
            return;
        }
        // Stop propagation for arrow keys to prevent Bootstrap interference
        if (e.which === 38 || e.which === 40) {
            e.stopPropagation();
        }
    });

    // Keyboard shortcut: Cmd+K (Mac) or Ctrl+K (Windows/Linux)
    $(document).on('keydown', function(e) {
        // Double Shift to open search
        if (e.key === 'Shift') {
            const now = Date.now();
            if (now - lastShiftPressTime < DOUBLE_SHIFT_DELAY_MS) {
                // Double Shift detected
                e.preventDefault();
                openSearchDropdown();
                lastShiftPressTime = 0; // Reset after opening
            } else {
                lastShiftPressTime = now;
            }
            return;
        }

        if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
            e.preventDefault();
            const $searchContainer = $('#navbar-search-dropdown');
            if (!$searchContainer.hasClass('show')) {
                openSearchDropdown();
            } else {
                $searchInput.focus();
            }
        }

        // Close on Escape
        if (e.key === 'Escape') {
            closeSearchDropdown();
        }
    });

    // Debounced search on input
    $searchInput.on('input', function() {
        clearTimeout(searchTimeout);
        const query = $(this).val().trim();

        if (query.length < MIN_QUERY_LENGTH) {
            $dropdown.hide();
            return;
        }

        searchTimeout = setTimeout(function() {
            performQuickSearch(query);
        }, SEARCH_DELAY_MS);
    });

    // Navigate to full search page on Enter
    $searchInput.on('keypress', function(e) {
        if (e.which === 13) { // Enter key
            const query = $(this).val().trim();
            if (query.length >= MIN_QUERY_LENGTH) {
                navigateToSearchPage(query);
            }
        }
    });

    // Hide dropdown when clicking outside
    $(document).on('click', function(e) {
        if (!$(e.target).closest('#global-search-container').length) {
            closeSearchDropdown();
        }
    });

    // Close dropdown when navigating to a result
    $dropdown.on('click', 'a.search-result-item', function() {
        closeSearchDropdown();
    });

    // Keyboard navigation (arrow keys)
    $searchInput.on('keydown', function(e) {
        if (e.which === 40) { // Arrow down
            e.preventDefault();
            e.stopPropagation(); // Prevent Bootstrap dropdown from interfering
            $dropdown.find('a:first').focus();
        }
    });

    // Handle arrow key navigation within dropdown
    $dropdown.on('keydown', 'a', function(e) {
        if (e.which === 38) { // Arrow up
            e.preventDefault();
            e.stopPropagation(); // Prevent Bootstrap dropdown from interfering
            const $prev = $(this).prev('a');
            if ($prev.length) {
                $prev.focus();
            } else {
                $searchInput.focus();
            }
        } else if (e.which === 40) { // Arrow down
            e.preventDefault();
            e.stopPropagation(); // Prevent Bootstrap dropdown from interfering
            const $next = $(this).next('a');
            if ($next.length) {
                $next.focus();
            }
        }
    });
}

function performQuickSearch(query) {
    const $dropdown = $('#global-search-dropdown');
    $dropdown.html('<div class="search-loading">Searching...</div>').show();

    $.ajax({
        url: 'api/search/quick',
        method: 'GET',
        data: {
            query: query,
            maxResults: 10
        },
        success: function(results) {
            renderSearchDropdown(results, query);
        },
        error: function(xhr) {
            $dropdown.html('<div class="search-error">Search failed. Please try again.</div>');
        }
    });
}

function renderSearchDropdown(results, query) {
    const $dropdown = $('#global-search-dropdown');

    if (results.totalResults === 0) {
        $dropdown.html(
            '<div class="search-no-results">' +
            'No results found for "' + escapeHtml(query) + '"' +
            '</div>'
        );
        $dropdown.show();
        return;
    }

    let html = '';

    // Render results as flat list with match highlighting
    results.results.forEach(function(result) {
        html += '<a href="' + escapeHtml(result.url) + '" class="search-result-item">';
        html += '<div class="search-result-title">' +
                highlightMatches(result.title, result.matches.titleMatches) +
                ' <span class="search-result-category">' + escapeHtml(result.category.displayName) + '</span>' +
                '</div>';
        if (result.subtitle) {
            html += '<div class="search-result-subtitle">' +
                    highlightMatches(result.subtitle, result.matches.subtitleMatches) +
                    '</div>';
        }
        if (result.description) {
            html += '<div class="search-result-description">' +
                    highlightMatches(result.description, result.matches.descriptionMatches) +
                    '</div>';
        }
        html += '</a>';
    });

    // Add "View all results" link
    html += '<div class="search-footer">';
    html += '<a href="' + getSearchPageUrl(query) + '" class="btn btn-sm btn-link">';
    html += 'View all results →';
    html += '</a>';
    html += '</div>';

    $dropdown.html(html).show();
}

/**
 * Highlight matched terms in text using the matches array from SearchResultItem.
 * Each matched term is wrapped in a <mark> tag for highlighting.
 */
function highlightMatches(text, matches) {
    if (!text) return '';
    if (!matches || matches.length === 0) {
        return escapeHtml(text);
    }

    // Escape HTML first
    let result = escapeHtml(text);

    // Replace each matched term with highlighted version (case-insensitive)
    matches.forEach(function(match) {
        const regex = new RegExp('(' + escapeRegex(match) + ')', 'gi');
        result = result.replace(regex, '<mark class="search-highlight">$1</mark>');
    });

    return result;
}

function escapeHtml(text) {
    if (!text) return '';
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return String(text).replace(/[&<>"']/g, function(m) { return map[m]; });
}

function escapeRegex(text) {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function navigateToSearchPage(query) {
    window.location.href = getSearchPageUrl(query);
}

function getSearchPageUrl(query) {
    return urlFor("search.showSearch", {query: query});
    //return 'search?query=' + encodeURIComponent(query);
}
