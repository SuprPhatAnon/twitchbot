/**
 * Common JavaScript for Twitch Bot UI
 */

// --- Theme Management ---
function initTheme() {
    const savedTheme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-bs-theme', savedTheme);
    const themeToggle = document.getElementById('theme-toggle');
    if (themeToggle) {
        themeToggle.innerText = savedTheme === 'dark' ? '☀️' : '🌙';
    }
}

function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-bs-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-bs-theme', newTheme);
    localStorage.setItem('theme', newTheme);
    const themeToggle = document.getElementById('theme-toggle');
    if (themeToggle) {
        themeToggle.innerText = newTheme === 'dark' ? '☀️' : '🌙';
    }
}

// --- User & Authorization ---
async function fetchAndStoreApiKey() {
    try {
        const response = await fetch('/api/users/me/api-key');
        if (response.ok) {
            const apiKey = await response.text();
            if (apiKey && !apiKey.startsWith('••••')) {
                localStorage.setItem('apiKey', apiKey);
                return apiKey;
            }
        }
    } catch (error) {
        console.error('Error fetching API key:', error);
    }
    return null;
}

function getApiKey() {
    return localStorage.getItem('apiKey');
}

async function getCurrentUser() {
    try {
        const response = await fetch('/api/users/me');
        if (response.status === 401 || response.status === 403) {
            return null;
        }
        return await response.json();
    } catch (error) {
        console.error('Error fetching user:', error);
        return null;
    }
}

function hasRole(user, role) {
    return user && user.roles && user.roles.includes(role);
}

async function updateNavigation() {
    const user = await getCurrentUser();
    if (!user) {
        // Not logged in or session expired
        const authOverlay = document.getElementById('auth-overlay');
        if (authOverlay) {
            authOverlay.style.display = 'flex';
        } else {
             // If no overlay but we are on a protected page, redirect to login
             if (window.location.pathname !== '/login.html' && window.location.pathname !== '/player.html') {
                 window.location.href = '/login.html';
             }
        }
        return;
    }

    // Update navigation items and also fetch API key if needed
    fetchAndStoreApiKey();

    // Update visibility of nav items based on roles
    const navItems = document.querySelectorAll('[data-role]');
    navItems.forEach(item => {
        const requiredRole = item.getAttribute('data-role');
        if (hasRole(user, 'ROLE_ADMIN') || hasRole(user, requiredRole)) {
            item.style.display = '';
        } else {
            item.style.display = 'none';
        }
    });

    // Update username displays
    const usernameDisplay = document.getElementById('display-username');
    if (usernameDisplay) usernameDisplay.innerText = user.username;
}

// --- Status Bar Updates ---
async function refreshStatusBar() {
    try {
        const response = await fetch('/api/twitch-config/full-status');
        if (!response.ok) return;
        const data = await response.json();
        
        // Update Stream Status
        const streamStatus = document.getElementById('stat-stream');
        if (streamStatus) {
            streamStatus.innerText = data.streamOnline ? 'ONLINE' : 'OFFLINE';
            streamStatus.className = 'status-value ' + (data.streamOnline ? 'status-online' : 'status-offline');
        }

        // Update Twitch Connection (Streamer)
        const twitchStatus = document.getElementById('stat-twitch');
        if (twitchStatus) {
            const connected = data.streamerConnected;
            twitchStatus.innerText = connected ? 'CONNECTED' : 'DISCONNECTED';
            twitchStatus.className = 'status-value ' + (connected ? 'status-online' : 'status-offline');
        }

        // Update Bot Connection
        const botStatus = document.getElementById('stat-bot');
        if (botStatus) {
            const connected = data.botConnected;
            botStatus.innerText = connected ? 'CONNECTED' : 'DISCONNECTED';
            botStatus.className = 'status-value ' + (connected ? 'status-online' : 'status-offline');
        }

        // Update Queue Size
        const queueSize = document.getElementById('stat-queue');
        if (queueSize) {
            // This might be updated via WebSocket too, but we sync here just in case
        }
    } catch (error) {
        console.error('Error refreshing status bar:', error);
    }
}

// --- Common UI Helpers ---
function copyToClipboard(text, buttonEl) {
    navigator.clipboard.writeText(text).then(() => {
        const originalText = buttonEl.innerText;
        buttonEl.innerText = 'Copied!';
        buttonEl.classList.replace('btn-outline-secondary', 'btn-success');
        setTimeout(() => {
            buttonEl.innerText = originalText;
            buttonEl.classList.replace('btn-success', 'btn-outline-secondary');
        }, 2000);
    });
}

// --- Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    updateNavigation();
    if (document.querySelector('.status-bar')) {
        refreshStatusBar();
        setInterval(refreshStatusBar, 30000);
    }
});
