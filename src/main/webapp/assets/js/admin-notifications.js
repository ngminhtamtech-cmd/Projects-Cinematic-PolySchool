(function () {
    'use strict';

    var activeFilter = 'all';
    var categoryFilter = document.getElementById('notificationCategoryFilter');
    var searchInput = document.getElementById('notificationSearchInput');
    var rows = Array.prototype.slice.call(document.querySelectorAll('.notification-row'));
    var resultCount = document.getElementById('notificationResultCount');
    var emptyState = document.getElementById('notificationFilteredEmpty');

    function applyFilters() {
        var category = categoryFilter ? categoryFilter.value : 'all';
        var searchQuery = searchInput ? searchInput.value.trim().toLowerCase() : '';
        var visible = 0;

        rows.forEach(function (row) {
            var matchesTab = activeFilter === 'all'
                || (activeFilter === 'unread' && row.dataset.read === 'false')
                || (activeFilter === 'room' && row.dataset.category === 'room');
            
            var matchesCategory = category === 'all' || row.dataset.category === category;

            var matchesSearch = true;
            if (searchQuery) {
                var titleEl = row.querySelector('.notification-title-cell strong');
                var copyEl = row.querySelector('.notification-copy');
                var titleText = titleEl ? titleEl.textContent.toLowerCase() : '';
                var copyText = copyEl ? copyEl.textContent.toLowerCase() : '';
                matchesSearch = titleText.indexOf(searchQuery) !== -1 || copyText.indexOf(searchQuery) !== -1;
            }

            var show = matchesTab && matchesCategory && matchesSearch;
            row.hidden = !show;
            if (show) visible += 1;
        });

        if (resultCount) {
            if (visible === 0) {
                resultCount.textContent = 'Hiển thị 0 thông báo';
            } else {
                resultCount.textContent = 'Hiển thị 1 đến ' + visible + ' của ' + rows.length + ' thông báo';
            }
        }
        if (emptyState) emptyState.hidden = visible !== 0 || rows.length === 0;
    }

    document.querySelectorAll('[data-notification-filter]').forEach(function (button) {
        button.addEventListener('click', function () {
            document.querySelectorAll('[data-notification-filter]').forEach(function (tab) {
                var selected = tab === button;
                tab.classList.toggle('is-active', selected);
                tab.setAttribute('aria-selected', selected ? 'true' : 'false');
            });
            activeFilter = button.dataset.notificationFilter;
            applyFilters();
        });
    });

    if (categoryFilter) categoryFilter.addEventListener('change', applyFilters);
    if (searchInput) searchInput.addEventListener('input', applyFilters);
})();
