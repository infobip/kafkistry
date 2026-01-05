$(document).ready(function () {
    initSearchPage();
});

function initSearchPage() {
    const $searchInput = $('#search-input');
    const $searchBtn = $('#search-btn');
    const $applyFiltersBtn = $('#apply-filters-btn');

    // Search on button click
    $searchBtn.on('click', function() {
        performSearch();
    });

    // Search on Enter key
    $searchInput.on('keypress', function(e) {
        if (e.which === 13) {
            performSearch();
        }
    });

    // Apply category filters
    $applyFiltersBtn.on('click', function() {
        $('#filter-modal').modal('hide');
        performSearch();
    });
}

function performSearch() {
    const query = $('#search-input').val().trim();
    const maxResults = $('#max-results-input').val().trim();

    const selectedCategories = $('.category-filter:checked')
        .map(function() { return $(this).val(); })
        .get();

    let url = 'search?query=' + encodeURIComponent(query);
    if (selectedCategories.length > 0 && selectedCategories.length < $('.category-filter').length) {
        url += '&categories=' + selectedCategories.join(',');
    }
    url += '&maxResults=' + encodeURIComponent(maxResults);

    window.location.href = url;
}
