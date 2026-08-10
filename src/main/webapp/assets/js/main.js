(function () {
    const header = document.querySelector('[data-site-header]');
    const navToggle = document.querySelector('[data-nav-toggle]');
    const navOverlay = document.querySelector('[data-nav-overlay]');
    const drawer = document.querySelector('[data-mobile-drawer]');

    function syncHeader() {
        if (!header) {
            return;
        }
        header.classList.toggle('is-compact', window.scrollY > 80);
    }

    function closeNav() {
        document.body.classList.remove('nav-open');
        if (navToggle) {
            navToggle.setAttribute('aria-expanded', 'false');
        }
    }

    function openNav() {
        document.body.classList.add('nav-open');
        if (navToggle) {
            navToggle.setAttribute('aria-expanded', 'true');
        }
    }

    window.addEventListener('scroll', syncHeader, { passive: true });
    syncHeader();

    if (navToggle && drawer) {
        navToggle.addEventListener('click', () => {
            if (document.body.classList.contains('nav-open')) {
                closeNav();
            } else {
                openNav();
            }
        });
    }

    if (navOverlay) {
        navOverlay.addEventListener('click', closeNav);
    }

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            closeNav();
        }
    });

    document.querySelectorAll('[data-close-nav]').forEach((link) => {
        link.addEventListener('click', closeNav);
    });

    const revealItems = document.querySelectorAll('.reveal');
    if ('IntersectionObserver' in window && revealItems.length) {
        const observer = new IntersectionObserver((entries) => {
            entries.forEach((entry) => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('is-visible');
                    observer.unobserve(entry.target);
                }
            });
        }, { threshold: 0.16 });
        revealItems.forEach((item, index) => {
            item.style.transitionDelay = `${Math.min(index * 60, 360)}ms`;
            observer.observe(item);
        });
    } else {
        revealItems.forEach((item) => item.classList.add('is-visible'));
    }

    document.querySelectorAll('.button, button').forEach((button) => {
        button.addEventListener('click', (event) => {
            const rect = button.getBoundingClientRect();
            const ripple = document.createElement('span');
            ripple.className = 'ripple';
            ripple.style.left = `${event.clientX - rect.left}px`;
            ripple.style.top = `${event.clientY - rect.top}px`;
            button.appendChild(ripple);
            ripple.addEventListener('animationend', () => ripple.remove());
        });
    });
})();
