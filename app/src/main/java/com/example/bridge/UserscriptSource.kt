package com.example.bridge

import android.content.Context
import java.io.InputStream

object UserscriptSource {
  const val SCRIPT_FILENAME = "Proxy-Redirect.user.js"

  val SCRIPT_CONTENT = """
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
// @description  Redirect to privacy respecting proxy frontends & fully synchronized with Ngrok Agent Android App, Phone Telemetry & Reverse Proxy Engine.
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
  let activeBridgeHost = 'http://127.0.0.1:8085';
  let isBridgeSynced = false;
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

  // 1. Handshake with Android App / Phone Gateway
  function initBridgeHandshake() {
    let portIndex = 0;

    function tryPort(port) {
      const targetBase = `http://127.0.0.1:${'$'}{port}`;
      const startMs = Date.now();
      makeRequest({
        method: 'POST',
        url: `${'$'}{targetBase}/api/bridge/handshake`,
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
              console.log(`[Proxy Redirect] Synced with Android Ngrok Agent on ${'$'}{targetBase}`);
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
      if (!isBridgeSynced) return;
      makeRequest({
        method: 'POST',
        url: `${'$'}{activeBridgeHost}/api/bridge/heartbeat`,
        headers: { 'Content-Type': 'application/json' },
        data: JSON.stringify({
          session_id: sessionId,
          current_url: window.location.href
        }),
        timeout: 4000
      });
    }, 5000);
  }

  // 2. Perform Intelligent Privacy Redirection
  function checkAndPerformRedirection() {
    const currentHost = window.location.hostname.toLowerCase();
    const currentPath = window.location.pathname;

    for (const [key, service] of Object.entries(proxyServices)) {
      if (!service.enabled) continue;
      const matched = service.domains.some(d => currentHost === d || currentHost.endsWith('.' + d));
      if (matched) {
        let targetInstance = service.selected.replace(/\/+${'$'}/, '');
        // Ignore if already on proxy target
        if (window.location.origin === targetInstance) return;

        let targetUrl = `${'$'}{targetInstance}${'$'}{currentPath}${'$'}{window.location.search}${'$'}{window.location.hash}`;
        targetUrl = stripTracking(targetUrl);

        console.info(`[Proxy Redirect] Forwarding ${'$'}{currentHost} to ${'$'}{targetUrl}`);
        window.location.replace(targetUrl);
        return;
      }
    }
  }

  function stripTracking(urlStr) {
    try {
      const u = new URL(urlStr);
      const trackingParams = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'si', 'fbclid', 'gclid', 'ref', 'igshid', 'feature'];
      trackingParams.forEach(p => u.searchParams.delete(p));
      return u.toString();
    } catch (_) {
      return urlStr;
    }
  }

  // 3. In-Browser Floating Reverse Proxy & Phone Gateway HUD
  function renderInBrowserHud(latencyMs) {
    const existingHud = document.getElementById('ngrok-proxy-hud');
    if (existingHud) existingHud.remove();

    const host = window.location.hostname;
    let matchingServiceKey = null;
    for (const [k, s] of Object.entries(proxyServices)) {
      if (s.domains.some(d => host.includes(d))) {
        matchingServiceKey = k;
        break;
      }
    }

    const hud = document.createElement('div');
    hud.id = 'ngrok-proxy-hud';
    hud.style.cssText = `
      position: fixed;
      bottom: 16px;
      right: 16px;
      z-index: 2147483647;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 12px;
      color: #E2E8F0;
      background: #0B1120;
      border: 1px solid ${'$'}{isBridgeSynced ? '#10B981' : '#F59E0B'};
      border-radius: 12px;
      padding: 10px 14px;
      box-shadow: 0 8px 24px rgba(0,0,0,0.6);
      display: flex;
      flex-direction: column;
      gap: 6px;
      max-width: 330px;
      transition: all 0.2s ease;
    `;

    const statusBadge = isBridgeSynced
      ? `<span style="color: #10B981; font-weight: bold;">🟢 Synced (${'$'}{latencyMs || 10}ms)</span>`
      : `<span style="color: #F59E0B; font-weight: bold;">🟡 Standalone</span>`;

    let phoneInfoHtml = '';
    if (phoneTelemetry) {
      const bat = phoneTelemetry.battery ? `${'$'}{phoneTelemetry.battery.percent}%` : '100%';
      phoneInfoHtml = `
        <div style="font-size:10px; color:#94A3B8; background:#1E293B; border-radius:6px; padding:3px 6px;">
          📱 ${'$'}{phoneTelemetry.manufacturer || 'Android'} ${'$'}{phoneTelemetry.device_model || 'Device'} &bull; 🔋 ${'$'}{bat}
        </div>
      `;
    }

    let actionBtnHtml = '';
    if (matchingServiceKey) {
      const s = proxyServices[matchingServiceKey];
      actionBtnHtml = `
        <div style="display:flex; justify-content:space-between; align-items:center; margin-top:4px;">
          <span>${'$'}{s.name}:</span>
          <button id="ngrok-hud-redirect-btn" style="background:#0284C7; color:white; border:none; border-radius:6px; padding:4px 8px; font-size:11px; cursor:pointer; font-weight:bold;">
            Redirect ➔
          </button>
        </div>
      `;
    }

    hud.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <span style="font-weight:bold; color:#38BDF8;">⚡ Phone Gateway & Proxy</span>
        <button id="ngrok-hud-close" style="background:none; border:none; color:#94A3B8; cursor:pointer; font-size:14px;">✕</button>
      </div>
      <div style="font-size:11px;">Status: ${'$'}{statusBadge}</div>
      ${'$'}{phoneInfoHtml}
      ${'$'}{actionBtnHtml}
      <div style="display:flex; gap:6px; margin-top:4px;">
        <button id="ngrok-hud-proxy-route" style="flex:1; background:#334155; color:#F8FAFC; border:none; border-radius:6px; padding:4px; font-size:10px; cursor:pointer;">
          Route via Ngrok
        </button>
        <button id="ngrok-hud-open-app" style="flex:1; background:#065F46; color:#A7F3D0; border:none; border-radius:6px; padding:4px; font-size:10px; cursor:pointer; font-weight:bold;">
          Phone Portal
        </button>
      </div>
    `;

    function attachListeners() {
      document.body.appendChild(hud);

      const closeBtn = document.getElementById('ngrok-hud-close');
      if (closeBtn) {
        closeBtn.onclick = () => hud.remove();
      }

      const redirectBtn = document.getElementById('ngrok-hud-redirect-btn');
      if (redirectBtn && matchingServiceKey) {
        redirectBtn.onclick = () => {
          const s = proxyServices[matchingServiceKey];
          window.location.href = s.selected + window.location.pathname + window.location.search;
        };
      }

      const proxyRouteBtn = document.getElementById('ngrok-hud-proxy-route');
      if (proxyRouteBtn) {
        proxyRouteBtn.onclick = () => {
          const encoded = encodeURIComponent(window.location.href);
          window.location.href = `${'$'}{activeBridgeHost}/proxy?url=${'$'}{encoded}`;
        };
      }

      const openAppBtn = document.getElementById('ngrok-hud-open-app');
      if (openAppBtn) {
        openAppBtn.onclick = () => {
          window.open(`${'$'}{activeBridgeHost}/`, '_blank');
        };
      }
    }

    if (document.body) {
      attachListeners();
    } else {
      window.addEventListener('DOMContentLoaded', attachListeners);
    }
  }

  // Register Greasemonkey Menu Commands
  if (typeof GM_registerMenuCommand !== 'undefined') {
    GM_registerMenuCommand("📱 Open Phone Remote Portal", () => {
      window.open(`${'$'}{activeBridgeHost}/`, '_blank');
    });
    GM_registerMenuCommand("🔄 Re-sync with Android Ngrok Agent", () => {
      initBridgeHandshake();
    });
  }

  // Initial Execution
  checkAndPerformRedirection();
  initBridgeHandshake();
})();
"""
}
