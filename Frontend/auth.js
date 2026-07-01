/**
 * auth.js — Simple login token helper used by all dashboard pages.
 *
 * ── How JWT auth works on the frontend (for interviews) ──────────────────────
 *
 * 1. User logs in → backend returns a JWT token
 * 2. We store the token in localStorage (like a key card)
 * 3. Every API call attaches the token in the "Authorization" header
 * 4. Backend reads the header, verifies the token, lets the request through
 * 5. If the token is missing or expired → backend returns 401 → user is redirected to login
 *
 * ── Security notes ───────────────────────────────────────────────────────────
 * - The token expires after 8 hours (server-enforced)
 * - The token is signed with a secret key only the server knows
 * - If someone steals the token, it only works for 8 hours max
 * - Passwords are NEVER stored in the frontend, only the token
 * - The server secret (JWT_SECRET) is only set as an env variable on Railway,
 *   never in any code file or GitHub
 */

// ── authFetch: use this instead of fetch() for all API calls ─────────────────
// It automatically adds the JWT token to every request header.
// Usage: const res = await authFetch(`${API_BASE}/api/something`);
//        Works exactly like fetch() — same options, same Response object.
async function authFetch(url, options = {}) {
    const token = localStorage.getItem('authToken');

    options.headers = {
        'Content-Type': 'application/json',
        ...options.headers,   // allow caller to override headers
    };

    if (token) {
        // Attach the token so the backend knows who you are
        options.headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(url, options);

    // If the server says "token expired / not logged in", send to login page
    if (response.status === 401) {
        localStorage.clear();
        window.location.href = 'Login.html';
        return response; // won't reach here after redirect
    }

    return response;
}

// ── handleLogout: signs out the user ─────────────────────────────────────────
// Clears the stored token and sends back to the login page.
function handleLogout() {
    localStorage.clear();
    window.location.href = 'Login.html';
}
