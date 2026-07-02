/**
 * navbar.js — Auto-injects a mobile hamburger menu into any page's existing navbar.
 * Just include this file and it handles the rest automatically.
 */
(function () {
    document.addEventListener('DOMContentLoaded', function () {
        // Prevent horizontal scroll on all pages
        document.body.style.overflowX = 'hidden';

        const nav = document.querySelector('nav');
        if (!nav) return;

        const innerDiv = nav.querySelector('div');
        if (!innerDiv) return;

        // ── Inject mobile CSS ──────────────────────────────────────────────
        const style = document.createElement('style');
        style.textContent = `
            #mobile-nav-menu {
                display: none;
                flex-direction: column;
                background: #04192f;
                border-top: 1px solid rgba(255,255,255,0.1);
            }
            #mobile-nav-menu.open { display: flex; }
            #mobile-menu-btn { display: none; }
            @media (max-width: 767px) {
                #mobile-menu-btn { display: flex !important; }
            }
        `;
        document.head.appendChild(style);

        // ── Create hamburger button ────────────────────────────────────────
        const hamburger = document.createElement('button');
        hamburger.id = 'mobile-menu-btn';
        hamburger.style.cssText = 'flex-direction:column;gap:5px;padding:8px;background:none;border:none;cursor:pointer;';
        hamburger.innerHTML = `
            <span style="display:block;width:22px;height:2px;background:white;border-radius:2px;"></span>
            <span style="display:block;width:22px;height:2px;background:white;border-radius:2px;"></span>
            <span style="display:block;width:22px;height:2px;background:white;border-radius:2px;"></span>
        `;
        hamburger.addEventListener('click', function () {
            const menu = document.getElementById('mobile-nav-menu');
            if (menu) menu.classList.toggle('open');
        });
        innerDiv.appendChild(hamburger);

        // ── Build mobile menu from existing desktop links ──────────────────
        const desktopLinks = nav.querySelectorAll('ul a');
        if (desktopLinks.length === 0) return;

        const mobileMenu = document.createElement('div');
        mobileMenu.id = 'mobile-nav-menu';

        desktopLinks.forEach(function (link) {
            const label = link.textContent.trim();
            const a = document.createElement('a');
            a.href = link.href;
            a.textContent = label;

            if (label === 'Login') {
                a.style.cssText = 'display:block;margin:12px 16px 16px;padding:12px;background:#F5A623;color:white;font-weight:700;font-size:15px;border-radius:8px;text-align:center;text-decoration:none;';
            } else {
                a.style.cssText = 'display:block;padding:14px 24px;color:white;font-size:15px;font-weight:500;border-bottom:1px solid rgba(255,255,255,0.07);text-decoration:none;';
            }
            mobileMenu.appendChild(a);
        });

        nav.appendChild(mobileMenu);
    });
})();
