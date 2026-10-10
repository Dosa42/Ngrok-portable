// ==UserScript==
// @name         Proxy Redirect (Ngrok Agent Synced Bridge & Phone Remote Gateway)
// @author       Schimon Jehudah, Adv. & Ngrok Agent Reverse Proxy Engine
// @collaborator hacker09
// @collaborator Konf
// @homepageURL  https://greasyfork.org/scripts/465936-proxy-redirect
// @supportURL   https://greasyfork.org/scripts/465936-proxy-redirect/feedback
// @downloadURL  http://127.0.0.1:8085/Proxy-Redirect.user.js
// @updateURL    http://127.0.0.1:8085/Proxy-Redirect.user.js
// @version      26.08.24
// @description  Redirect to privacy respecting proxy frontends & fully synchronized with Ngrok Agent Android App, Phone Telemetry, Native Local WebSocket (Zero CORS) & Reverse Proxy Engine.
// @match        *://*/*
// @exclude      *://127.0.0.1:*/*
// @exclude      *://localhost:*/*
// @connect      127.0.0.1
// @connect      localhost
// @connect      *
// @grant        GM.xmlHttpRequest
// @grant        GM_xmlhttpRequest
// @grant        GM.setValue
// @grant        GM.getValue
// @grant        GM.notification
// @grant        GM.registerMenuCommand
// @grant        GM_registerMenuCommand
// @run-at       document-start
// @noframes
// ==/UserScript==

(function () {
  'use strict';

  const NGROK_LOCAL_PORTS = [8085, 8083, 8080, 3000, 5000, 8000];
  const WS_LOCAL_PORTS = [8086, 8085, 8087, 8088];
  let activeBridgeHost = 'http://127.0.0.1:8085';
  let isBridgeSynced = false;
  let isWebSocketConnected = false;
  let wsClient = null;
  let sessionId = null;
  let syncedProxyConfig = null;
  let phoneTelemetry = null;

  // Default Fallback Proxy Services
  let proxyServices = {
    "youtube": {
      "name": "YouTube",
      "domains": ["youtube.com", "youtu.be", "m.youtube.com"],
      "selected": "https://yewtu.be",
      "enabled": true
    },
    "twitter": {
      "name": "Twitter / X",
      "domains": ["twitter.com", "x.com"],
      "selected": "https://nitter.privacydev.net",
      "enabled": true
    },
    "reddit": {
      "name": "Reddit",
      "domains": ["reddit.com", "old.reddit.com", "www.reddit.com"],
      "selected": "https://redlib.tux.pizza",
      "enabled": true
    },
    "tiktok": {
      "name": "TikTok",
      "domains": ["tiktok.com", "www.tiktok.com"],
      "selected": "https://proxitok.pabloferreiro.es",
      "enabled": true
    },
    "instagram": {
      "name": "Instagram",
      "domains": ["instagram.com", "www.instagram.com"],
      "selected": "https://proxigram.lunar.icu",
      "enabled": true
    },
    "medium": {
      "name": "Medium",
      "domains": ["medium.com"],
      "selected": "https://scribe.rip",
      "enabled": true
    },
    "wikipedia": {
      "name": "Wikipedia",
      "domains": ["wikipedia.org", "en.wikipedia.org"],
      "selected": "https://wikiless.org",
      "enabled": true
    },
    "imgur": {
      "name": "Imgur",
      "domains": ["imgur.com", "i.imgur.com"],
      "selected": "https://rimgo.privacydev.net",
      "enabled": true
    },
    "search": {
      "name": "Google Search",
      "domains": ["google.com", "google.de"],
      "selected": "https://search.privacydev.net",
      "enabled": false
    }
  };

  // Safe Cross-Engine HTTP Request
  function makeRequest(details) {
    if (typeof GM_xmlhttpRequest !== 'undefined') {
      return GM_xmlhttpRequest(details);
    } else if (typeof GM !== 'undefined' && GM.xmlHttpRequest) {
      return GM.xmlHttpRequest(details);
    } else {
      return fetch(details.url, {
        method: details.method || 'GET',
        headers: details.headers,
        body: details.data
      }).then(res => res.text()).then(text => details.onload && details.onload({ responseText: text, status: 200 }));
    }
  }

  // 1. Native Local WebSocket Connection (Zero CORS Blockers)
  function initWebSocketConnection() {
    let wsPortIdx = 0;

    function tryWsPort(port) {
      try {
        const wsUrl = `ws://127.0.0.1:${port}/ws`;
        console.log(`[Proxy Redirect] Attempting native local WebSocket: ${wsUrl}`);
        const ws = new WebSocket(wsUrl);

        ws.onopen = function () {
          console.log(`[Proxy Redirect] ✅ Native Local WebSocket Connected on port ${port} (No CORS)`);
          isWebSocketConnected = true;
          isBridgeSynced = true;
          wsClient = ws;

          // Send immediate handshake over WebSocket
          ws.send(JSON.stringify({
            action: 'bridge_handshake',
            version: '26.08.24',
            browser: navigator.userAgent.includes('Lemur') ? 'Lemur Browser' : 'Tampermonkey Browser',
            url: window.location.href,
            title: document.title || 'Web Page'
          }));

          // Request telemetry
          ws.send(JSON.stringify({ action: 'get_phone_status' }));
          ws.send(JSON.stringify({ action: 'get_proxy_rules' }));
          renderInBrowserHud(5);
        };

        ws.onmessage = function (event) {
          try {
            const data = JSON.parse(event.data);
            if (data.type === 'PHONE_STATUS' && data.data) {
              phoneTelemetry = data.data;
              renderInBrowserHud(2);
            } else if (data.type === 'PROXY_CONFIG' && data.config) {
              applySyncedProxyConfig(data.config);
            } else if (data.type === 'WEBSOCKET_CONNECTED') {
              sessionId = data.sessionId;
            }
          } catch (e) {
            console.warn('[Proxy Redirect] WS frame parse error:', e);
          }
        };

        ws.onerror = function () {
          wsPortIdx++;
          if (wsPortIdx < WS_LOCAL_PORTS.length) {
            tryWsPort(WS_LOCAL_PORTS[wsPortIdx]);
          } else {
            // Fallback to HTTP polling if WebSocket is not active
            initBridgeHandshake();
          }
        };

        ws.onclose = function () {
          isWebSocketConnected = false;
        };
      } catch (e) {
        initBridgeHandshake();
      }
    }

    tryWsPort(WS_LOCAL_PORTS[0]);
  }

  // 2. HTTP Companion Fallback Handshake
  function initBridgeHandshake() {
    let portIndex = 0;

    function tryPort(port) {
      const targetBase = `http://127.0.0.1:${port}`;
      const startMs = Date.now();
      makeRequest({
        method: 'POST',
        url: `${targetBase}/api/bridge/handshake`,
        headers: { 'Content-Type': 'application/json' },
        data: JSON.stringify({
          script_version: '26.08.24',
          browser: navigator.userAgent.includes('Lemur') ? 'Lemur Browser' : 'Tampermonkey Browser',
          page_url: window.location.href,
          page_title: document.title || 'Web Page'
        }),
        timeout: 2500,
        onload: function (response) {
          try {
            const data = JSON.parse(response.responseText);
            if (data.status === 'synchronized') {
              isBridgeSynced = true;
              activeBridgeHost = targetBase;
              sessionId = data.session_id;
              phoneTelemetry = data.phone_telemetry || null;
              if (data.proxy_config) {
                applySyncedProxyConfig(data.proxy_config);
              }
              console.log(`[Proxy Redirect] Synced with Android Ngrok Agent on ${targetBase}`);
              renderInBrowserHud(Date.now() - startMs);
              startHeartbeatLoop();
            }
          } catch (e) {
            nextPort();
          }
        },
        onerror: nextPort,
        ontimeout: nextPort
      });
    }

    function nextPort() {
      portIndex++;
      if (portIndex < NGROK_LOCAL_PORTS.length) {
        tryPort(NGROK_LOCAL_PORTS[portIndex]);
      } else {
        renderInBrowserHud(null);
      }
    }

    tryPort(NGROK_LOCAL_PORTS[0]);
  }

  function applySyncedProxyConfig(cfg) {
    syncedProxyConfig = cfg;
    if (cfg.services && Array.isArray(cfg.services)) {
      cfg.services.forEach(s => {
        if (proxyServices[s.id]) {
          proxyServices[s.id].selected = s.selected_instance;
          proxyServices[s.id].enabled = s.is_enabled;
        }
      });
    }
    checkAndPerformRedirection();
  }

  function startHeartbeatLoop() {
    setInterval(() => {
      if (!isBridgeSynced || isWebSocketConnected) return;
      makeRequest({
        method: 'POST',
        url: `${activeBridgeHost}/api/bridge/heartbeat`,
        headers: { 'Content-Type': 'application/json' },
        data: JSON.stringify({
          session_id: sessionId,
          current_url: window.location.href
        }),
        timeout: 4000
      });
    }, 5000);
  }

  // 3. Perform Intelligent Privacy Redirection
  function checkAndPerformRedirection() {
    const currentHost = window.location.hostname.toLowerCase();
    const currentPath = window.location.pathname;

    for (const [key, service] of Object.entries(proxyServices)) {
      if (!service.enabled) continue;
      const matched = service.domains.some(d => currentHost === d || currentHost.endsWith('.' + d));
      if (matched) {
        let targetInstance = service.selected.replace(/\/+$/, '');
        if (window.location.origin === targetInstance) return;

        let targetUrl = `${targetInstance}${currentPath}${window.location.search}${window.location.hash}`;
        targetUrl = stripTracking(targetUrl);

        console.info(`[Proxy Redirect] Forwarding ${currentHost} to ${targetUrl}`);
        window.location.replace(targetUrl);
        break;
      }
    }
  }

  function stripTracking(urlStr) {
    try {
      const url = new URL(urlStr);
      const trackingParams = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'fbclid', 'gclid', 'si', 'feature'];
      trackingParams.forEach(p => url.searchParams.delete(p));
      return url.toString();
    } catch (_) {
      return urlStr;
    }
  }

  // 4. In-Browser Floating HUD Widget
  function renderInBrowserHud(latency) {
    if (document.getElementById('ngrok-agent-hud')) return;

    const hud = document.createElement('div');
    hud.id = 'ngrok-agent-hud';
    hud.style.cssText = `
      position: fixed;
      bottom: 16px;
      right: 16px;
      z-index: 2147483647;
      background: rgba(18, 18, 18, 0.94);
      color: #ffffff;
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: 12px;
      padding: 8px 12px;
      font-family: system-ui, -apple-system, sans-serif;
      font-size: 11px;
      box-shadow: 0 4px 16px rgba(0,0,0,0.4);
      display: flex;
      align-items: center;
      gap: 8px;
      backdrop-filter: blur(8px);
      transition: all 0.2s ease;
    `;

    const statusDot = document.createElement('span');
    statusDot.style.cssText = `
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: ${isBridgeSynced ? '#4ade80' : '#f87171'};
      box-shadow: 0 0 6px ${isBridgeSynced ? '#4ade80' : '#f87171'};
    `;

    const statusText = document.createElement('span');
    const wsLabel = isWebSocketConnected ? ' • ⚡ WS Live' : '';
    const phoneLabel = phoneTelemetry?.battery_pct ? ` • 🔋 ${phoneTelemetry.battery_pct}%` : '';
    statusText.textContent = isBridgeSynced
      ? `Ngrok Agent Synced (${latency || 0}ms)${wsLabel}${phoneLabel}`
      : 'Ngrok Agent Offline';
    statusText.style.fontWeight = '600';

    const actionBtn = document.createElement('button');
    actionBtn.textContent = 'App GUI';
    actionBtn.style.cssText = `
      background: #3b82f6;
      color: white;
      border: none;
      border-radius: 6px;
      padding: 3px 8px;
      font-size: 10px;
      font-weight: bold;
      cursor: pointer;
    `;
    actionBtn.onclick = () => window.open(activeBridgeHost, '_blank');

    hud.appendChild(statusDot);
    hud.appendChild(statusText);
    hud.appendChild(actionBtn);

    if (document.body) {
      document.body.appendChild(hud);
    } else {
      window.addEventListener('DOMContentLoaded', () => document.body && document.body.appendChild(hud));
    }
  }

  // Initialize Native WebSocket First (Zero CORS blockers), falling back to HTTP handshake
  initWebSocketConnection();
})();
