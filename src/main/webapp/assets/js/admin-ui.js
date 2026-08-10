(function () {
    'use strict';

    var DRAWER_BREAKPOINT = 980;

    function sidebar() {
        return document.getElementById('adminSidebar');
    }

    function drawerToggle() {
        return document.getElementById('adminMobileMenuButton');
    }

    function setDrawer(open) {
        document.body.classList.toggle('sidebar-open', open);
        var toggle = drawerToggle();
        if (toggle) {
            toggle.setAttribute('aria-expanded', String(open));
        }
        var adminSidebar = sidebar();
        if (adminSidebar) {
            adminSidebar.setAttribute('aria-hidden',
                    String(window.innerWidth <= DRAWER_BREAKPOINT && !open));
        }
        if (open && adminSidebar) {
            var firstLink = adminSidebar.querySelector('a, button');
            if (firstLink) {
                firstLink.focus();
            }
        }
    }

    window.toggleAdminDrawer = function () {
        setDrawer(!document.body.classList.contains('sidebar-open'));
    };

    window.closeAdminDrawer = function () {
        var wasOpen = document.body.classList.contains('sidebar-open');
        setDrawer(false);
        if (wasOpen && drawerToggle()) {
            drawerToggle().focus();
        }
    };

    window.goBackOrDashboard = function () {
        var topbar = document.querySelector('.topbar[data-dashboard-url]');
        var fallback = topbar ? topbar.getAttribute('data-dashboard-url') : '';
        if (document.referrer
                && document.referrer.indexOf(window.location.host) !== -1
                && document.referrer !== window.location.href) {
            history.back();
            return;
        }
        if (fallback) {
            window.location.href = fallback;
        }
    };

    window.performGlobalAdminSearch = function () {
        var input = document.getElementById('globalAdminSearchInput');
        if (!input) {
            return;
        }
        var query = input.value.toLocaleLowerCase('vi').trim();
        var selectors = [
            '[data-admin-search-item]',
            '.data-table tbody tr',
            '.table-data tbody tr',
            '.admin-data-table tbody tr',
            '.manage-item',
            '.item-row',
            '.film-admin-card',
            '.ent-room-card',
            '.appeal-card',
            '.notification-card'
        ];
        var seen = [];
        document.querySelectorAll(selectors.join(',')).forEach(function (item) {
            if (seen.indexOf(item) !== -1) {
                return;
            }
            seen.push(item);
            var matches = !query
                    || item.textContent.toLocaleLowerCase('vi').indexOf(query) !== -1;
            item.hidden = !matches;
        });
    };

    function enhanceDataTables() {
        document.querySelectorAll('.data-table, .table-data, .admin-data-table')
                .forEach(function (table) {
                    table.querySelectorAll('thead th').forEach(function (heading) {
                        if (!heading.hasAttribute('scope')) {
                            heading.setAttribute('scope', 'col');
                        }
                    });
                    table.querySelectorAll('tbody tr').forEach(function (row) {
                        row.setAttribute('data-admin-search-item', '');
                    });
                    if (table.parentElement
                            && !table.parentElement.classList.contains('admin-table-scroll')) {
                        var wrapper = document.createElement('div');
                        wrapper.className = 'admin-table-scroll';
                        wrapper.setAttribute('tabindex', '0');
                        wrapper.setAttribute('role', 'region');
                        wrapper.setAttribute('aria-label', 'Bảng dữ liệu có thể cuộn ngang');
                        table.parentNode.insertBefore(wrapper, table);
                        wrapper.appendChild(table);
                    }
                });
    }

    document.addEventListener('DOMContentLoaded', function () {
        enhanceDataTables();
        var overlay = document.querySelector('.admin-sidebar-overlay');
        if (overlay) {
            overlay.addEventListener('click', window.closeAdminDrawer);
        }

        var adminSidebar = sidebar();
        if (adminSidebar) {
            adminSidebar.querySelectorAll('a').forEach(function (link) {
                link.addEventListener('click', function () {
                    if (window.innerWidth <= DRAWER_BREAKPOINT) {
                        setDrawer(false);
                    }
                });
            });
        }

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape'
                    && document.body.classList.contains('sidebar-open')) {
                window.closeAdminDrawer();
            }
        });

        window.addEventListener('resize', function () {
            if (window.innerWidth > DRAWER_BREAKPOINT) {
                setDrawer(false);
                if (adminSidebar) {
                    adminSidebar.removeAttribute('aria-hidden');
                }
            } else if (adminSidebar) {
                adminSidebar.setAttribute('aria-hidden',
                        String(!document.body.classList.contains('sidebar-open')));
            }
        });

        if (window.innerWidth <= DRAWER_BREAKPOINT && adminSidebar) {
            adminSidebar.setAttribute('aria-hidden', 'true');
        }

        initLiveNotificationPoller();
    });

    function initLiveNotificationPoller() {
        var topbarBtn = document.getElementById('topbarNotificationBtn');
        if (!topbarBtn) return;

        var contextPath = topbarBtn.getAttribute('data-context-path') || '';
        if (!contextPath) {
            var href = topbarBtn.getAttribute('href') || '';
            var idx = href.indexOf('/admin/notifications');
            if (idx !== -1) {
                contextPath = href.substring(0, idx);
            } else {
                var pathName = window.location.pathname;
                var adminIdx = pathName.indexOf('/admin/');
                if (adminIdx !== -1) {
                    contextPath = pathName.substring(0, adminIdx);
                }
            }
        }

        var pollUrl = contextPath + '/admin/notifications?action=poll';
        var knownIds = {};
        var isFirstPoll = true;
        var POLL_INTERVAL = 8000;

        function updateBadges(unreadCount) {
            var topbarBadge = document.getElementById('topbarNotificationBadge');
            if (topbarBadge) {
                topbarBadge.textContent = unreadCount;
                topbarBadge.style.display = unreadCount > 0 ? 'inline-flex' : 'none';
            }

            var sidebarBadge = document.getElementById('sidebarNotificationBadge');
            if (sidebarBadge) {
                sidebarBadge.textContent = unreadCount;
                sidebarBadge.style.display = unreadCount > 0 ? 'inline-flex' : 'none';
            } else if (unreadCount > 0) {
                var sidebarToggle = document.querySelector('.sidebar-category-toggle[href*="/admin/notifications"]');
                if (sidebarToggle) {
                    var newBadge = document.createElement('span');
                    newBadge.className = 'sidebar-badge';
                    newBadge.id = 'sidebarNotificationBadge';
                    newBadge.style.cssText = 'margin-left:auto;background:#C81E1E;color:#fff;font-size:11px;font-weight:600;padding:2px 6px;border-radius:10px;line-height:1;display:inline-flex;align-items:center;justify-content:center;';
                    newBadge.textContent = unreadCount;
                    sidebarToggle.appendChild(newBadge);
                }
            }
        }

        function getSeveritySvg(severity) {
            if (severity === 'danger' || severity === 'error') {
                return '<svg viewBox="0 0 24 24" fill="none" stroke="#C81E1E" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:20px;height:20px;"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>';
            }
            if (severity === 'warning') {
                return '<svg viewBox="0 0 24 24" fill="none" stroke="#D97706" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:20px;height:20px;"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>';
            }
            if (severity === 'success') {
                return '<svg viewBox="0 0 24 24" fill="none" stroke="#059669" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:20px;height:20px;"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>';
            }
            return '<svg viewBox="0 0 24 24" fill="none" stroke="#2563EB" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:20px;height:20px;"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>';
        }

        function showToast(note) {
            var container = document.getElementById('adminToastContainer');
            if (!container) {
                container = document.createElement('div');
                container.id = 'adminToastContainer';
                container.className = 'admin-toast-container';
                container.setAttribute('aria-live', 'polite');
                container.setAttribute('aria-atomic', 'true');
                document.body.appendChild(container);
            }

            var toast = document.createElement('div');
            var sevClass = 'toast-' + (note.severity || 'info');
            toast.className = 'admin-toast-item ' + sevClass;

            var targetUrl = note.actionUrl ? note.actionUrl : (contextPath + '/admin/notifications');

            toast.innerHTML = 
                '<div class="admin-toast-icon">' + getSeveritySvg(note.severity) + '</div>' +
                '<div class="admin-toast-content">' +
                    '<div class="admin-toast-title">' + escapeHtml(note.title || 'Thông báo mới') + '</div>' +
                    '<div class="admin-toast-body">' + escapeHtml(note.message || '') + '</div>' +
                    '<div class="admin-toast-meta">' + escapeHtml(note.createdAtDisplay || '') + '</div>' +
                '</div>' +
                '<a href="' + escapeHtml(targetUrl) + '" class="admin-toast-action">Xem</a>' +
                '<button type="button" class="admin-toast-close" aria-label="Đóng">&times;</button>';

            var closeBtn = toast.querySelector('.admin-toast-close');
            if (closeBtn) {
                closeBtn.addEventListener('click', function () {
                    toast.classList.add('is-hiding');
                    setTimeout(function () {
                        if (toast.parentNode) toast.parentNode.removeChild(toast);
                    }, 300);
                });
            }

            container.appendChild(toast);

            setTimeout(function () {
                if (toast.parentNode && !toast.classList.contains('is-hiding')) {
                    toast.classList.add('is-hiding');
                    setTimeout(function () {
                        if (toast.parentNode) toast.parentNode.removeChild(toast);
                    }, 300);
                }
            }, 7000);
        }

        function escapeHtml(str) {
            if (!str) return '';
            return String(str)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#39;');
        }

        function poll() {
            fetch(pollUrl, {
                headers: { 'Accept': 'application/json' },
                cache: 'no-store'
            })
            .then(function (response) {
                if (!response.ok) throw new Error('Poll status ' + response.status);
                return response.json();
            })
            .then(function (data) {
                if (!data || !data.success) return;
                var unreadCount = data.unreadCount || 0;
                updateBadges(unreadCount);

                var list = data.notifications || [];
                var newCount = 0;
                list.forEach(function (note) {
                    if (!note || !note.id) return;
                    if (!knownIds[note.id]) {
                        if (!isFirstPoll && !note.isRead) {
                            showToast(note);
                            newCount++;
                        }
                        knownIds[note.id] = true;
                    }
                });

                isFirstPoll = false;
            })
            .catch(function (err) {
                // Silent fail on connection error
            });
        }

        // Initial poll after short delay
        setTimeout(poll, 1000);
        // Periodic interval poll
        setInterval(poll, POLL_INTERVAL);
    }
}());
