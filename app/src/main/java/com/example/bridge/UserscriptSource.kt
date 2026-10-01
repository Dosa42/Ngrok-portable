package com.example.bridge

object UserscriptSource {
  val SCRIPT_CONTENT = """
// ==UserScript==
// @name         Proxy Redirect (Ngrok Agent Synced Bridge)
// @author       Schimon Jehudah, Adv. & Ngrok Agent Bridge
// @collaborator hacker09
// @collaborator Konf
// @homepageURL  https://greasyfork.org/scripts/465936-proxy-redirect
// @supportURL   https://greasyfork.org/scripts/465936-proxy-redirect/feedback
// @copyright    2023 - 2026, Schimon Jehudah (http://schimon.i2p)
// @license      AGPL-3.0-only; https://gnu.org/licenses/agpl-3.0.en.html
// @namespace    i2p.schimon.proxy-redirect
// @description  Redirect to privacy respecting proxy frontends with hardcoded synchronization bridge to Ngrok Agent Android App.
// @tag          proxy
// @run-at       document-start
// @version      26.08.24-ngrok-bridge
// @grant        GM.getValue
// @grant        GM.notification
// @grant        GM.registerMenuCommand
// @grant        GM.setValue
// @grant        GM.xmlHttpRequest
// @grant        GM_xmlhttpRequest
// @grant        GM_setValue
// @grant        GM_getValue
// @match        file:///*
// @match        *://*/*
// @connect      127.0.0.1
// @connect      localhost
// @connect      0011.lt
// @connect      076.ne.jp
// @connect      0ut0f.space
// @connect      1search.i2p
// @connect      1d4.us
// @connect      2syis2nnyytz6jnusnjurva4swlaizlnleiks5mjp46phuwjbdjqwgqd.onion
// @connect      40two.app
// @connect      42l.fr
// @connect      absturztau.be
// @connect      actionsack.com
// @connect      adminforge.de
// @connect      albony.xyz
// @connect      arancia.click
// @connect      artemislena.eu
// @connect      asynchronousexchange.com
// @connect      batsense.net
// @connect      bibliogram.art
// @connect      btdig.i2p
// @connect      btdig.com
// @connect      btdigggink2pdqzqrik3blmqemsbntpzwxottujilcdjfz56jumzfsyd.onion
// @connect      bus-hit.me
// @connect      cadence.moe
// @connect      catsarch.i2p
// @connect      catfluori.de
// @connect      cblgh.org
// @connect      censors.us
// @connect      chauvet.pro
// @connect      citizen4.eu
// @connect      cowfee.moe
// @connect      creller.net
// @connect      crewz.me
// @connect      cn.i2p
// @connect      datatunnel.xyz
// @connect      dc09.ru
// @connect      dcs0.hu
// @connect      domain.glass
// @connect      datura.network
// @connect      duckdns.org
// @connect      ducks.party
// @connect      dynabyte.ca
// @connect      ebnar.xyz
// @connect      envs.net
// @connect      esmail5pdn24shtvieloeedh7ehz3nrwcdivnfhfcedl7gf4kwddhkqd.onion
// @connect      etsi.me
// @connect      exarius.org
// @connect      f5.si
// @connect      facilmap.org
// @connect      farside.link
// @connect      fdn.fr
// @connect      flokinet.to
// @connect      flux.industries
// @connect      fmac.xyz
// @connect      froth.zone
// @connect      gatti.ninja
// @connect      ggc-project.de
// @connect      ggtyler.dev
// @connect      gnu.style
// @connect      go.metastem.su
// @connect      grimneko.de
// @connect      hostux.net
// @connect      hxvy0.gq
// @connect      hyperborea.cloud
// @connect      iket.me
// @connect      il.ax
// @connect      incogniweb.net
// @connect      incogsnoo.com
// @connect      invak.id
// @connect      jamiethalacker.dev
// @connect      jeikobu.net
// @connect      jewtube.i2p
// @connect      jing.rocks
// @connect      josias.dev
// @connect      jpope.org
// @connect      k62ptris7p72aborr4zoanee7xai6wguucveptwgxs5vbgt7qzpq.b32.i2p
// @connect      kamiokan.de
// @connect      kavin.rocks
// @connect      kittywi.re
// @connect      kylrth.com
// @connect      ledditqo2mxfvlgobxnlhrkq4dh34jss6evfkdkb2thlvy6dn4f4gpyd.onion
// @connect      libre.tw
// @connect      libredd.it
// @connect      libreddit.de
// @connect      libreddit.eu.org
// @connect      libreddit.hu
// @connect      libreddit.nl
// @connect      lingva.ml
// @connect      lqs5fjmajyp7rvp4qvyubwofzi6d4imua7vs237rkc4m5qogitqwrgyd.onion
// @connect      lr.n8pjl.ca
// @connect      lunar.icu
// @connect      melmac.space
// @connect      mint.lgbt
// @connect      moeyy.cn
// @connect      moomoo.me
// @connect      mywire.org
// @connect      mdosch.de
// @connect      monocles.de
// @connect      nadeko.net
// @connect      neet.works
// @connect      nerdvpn.de
// @connect      netlify.app
// @connect      neuters.de
// @connect      ngn.tf
// @connect      nitter.hu
// @connect      nitter.net
// @connect      nitter.one
// @connect      nixnet.services
// @connect      no-logs.com
// @connect      nogoo.me
// @connect      northboot.xyz
// @connect      nttr.stream
// @connect      oakleycord.dev
// @connect      ononoki.org
// @connect      ooguy.com
// @connect      openstreetmap.org
// @connect      osi.kr
// @connect      oversold.host
// @connect      owacon.moe
// @connect      owo.si
// @connect      pabloferreiro.es
// @connect      paulgo.io
// @connect      pavot.ca
// @connect      perditum.com
// @connect      phreedom.club
// @connect      piped.video
// @connect      poketube.fun
// @connect      priv.au
// @connect      privacy.com.de
// @connect      privadency.com
// @connect      private.coffee
// @connect      projectsegfau.lt
// @connect      procurx.pt
// @connect      prvcy.eu
// @connect      puffyan.us
// @connect      pussthecat.org
// @connect      qwik.i2p
// @connect      qwikxx2erhx6qrymued6ox2qkf2yeogjwypqvzoif4fqkljixasr6oid.onion
// @connect      r.nf
// @connect      r4fo.com
// @connect      rabbit-company.com
// @connect      rasp.fr
// @connect      reallyaweso.me
// @connect      resrv.org
// @connect      revvy.de
// @connect      riverside.rocks
// @connect      rtrace.io
// @connect      scribe.rip
// @connect      search.cdev.nexus
// @connect      search.im-in.space
// @connect      search.zdechov.net
// @connect      searx.ankha.ac
// @connect      searx.be
// @connect      searx.ee
// @connect      searx.fi
// @connect      searx.ninja
// @connect      searx.ru
// @connect      sethforprivacy.com
// @connect      simplifiedprivacy.com
// @connect      simplytranslate.org
// @connect      smnz.de
// @connect      snopyta.org
// @connect      sny.sh
// @connect      some-things.org
// @connect      sp-codes.de
// @connect      spike.codes
// @connect      spjmllawtheisznfs7uryhxumin26ssv2draj7oope3ok3wuhy43eoyd.onion
// @connect      strongthany.cc
// @connect      stuehieyr.com
// @connect      sugoma.tk
// @connect      syncpundit.com
// @connect      system72.dev
// @connect      teddit.i2p
// @connect      teddit.net
// @connect      tedditfyn6idalzso5wam5qd3kdtxoljjhbrbbx34q2xkcisvshuytad.onion
// @connect      theanonymouse.xyz
// @connect      tinfoil-hat.net
// @connect      tiekoetter.com
// @connect      tm4rwkeysv3zz3q5yacyr4rlmca2c4etkdobfvuqzt6vsfsu4weq.b32.i2p
// @connect      totaldarkness.net
// @connect      trom.tf
// @connect      tromdienste.de
// @connect      tux.land
// @connect      tux.pizza
// @connect      tuxcloud.net
// @connect      tyil.nl
// @connect      unixfox.eu
// @connect      unofficialbird.com
// @connect      userscripts-mirror.org
// @connect      voidnet.tech
// @connect      vojkovic.xyz
// @connect      voring.me
// @connect      walkx.org
// @connect      webheberg.info
// @connect      weblibre.org
// @connect      whatever.social
// @connect      whatevertinfoil.de
// @connect      wikiless.i2p
// @connect      wikiless.org
// @connect      winscloud.net
// @connect      xcancel.com
// @connect      yewtu.be
// @connect      yonalee.eu
// @connect      ytmous.i2p
// @connect      xanny.family
// @connect      yacy.iko.soy
// @connect      zackptg5.com
// @connect      zhaocloud.net
// @connect      zzls.i2p
// @connect      zzls.xyz
// @icon         data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjRtbSIgaGVpZ2h0PSI2NG1tIiB2aWV3Qm94PSIwIDAgNjQgNjQiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHRleHQgeG1sOnNwYWNlPSJwcmVzZXJ2ZSIgc3R5bGU9ImZvbnQtd2VpZ2h0OjQwMDtmb250LXNpemU6MTkycHg7bGluZS1oZWlnaHQ6MDt0ZXh0LWluZGVudDowO3RleHQtYWxpZ246c3RhcnQ7dGV4dC1kZWNvcmF0aW9uLXN0eWxlOnNvbGlkO3RleHQtZGVjb3JhdGlvbi1jb2xvcjojMDAwO3dyaXRpbmctbW9kZTpsci10YjtkaXJlY3Rpb246bHRyO3RleHQtb3JpZW50YXRpb246bWl4ZWQ7ZG9taW5hbnQtYmFzZWxpbmU6YXV0bztiYXNlbGluZS1zaGlmdDpiYXNlbGluZTt0ZXh0LWFuY2hvcjpzdGFydDtzaGFwZS1wYWRkaW5nOjA7c2hhcGUtbWFyZ2luOjA7aW5saW5lLXNpemU6MDtvcGFjaXR5OjE7ZmlsbDojMDAwO2ZpbGwtb3BhY2l0eToxO3N0cm9rZS13aWR0aDoxLjI3OTgyO3N0cm9rZS1saW5lY2FwOmJ1dHQ7c3Ryb2tlLWxpbmVqb2luOm1pdGVyO3N0cm9rZS1taXRlcmxpbWl0OjQ7c3Ryb2tlLWRhc2hvZmZzZXQ6MDtzdHJva2Utb3BhY2l0eToxO3N0b3AtY29sb3I6IzAwMDtzdG9wLW9wYWNpdHk6MSIgeD0iMTcuMDA1MjQ1IiB5PSIzMS42NTg0MDUiIHRyYW5zZm9ybT0idHJhbnNsYXRlKC00LjQzNjg1NjQgNDAuODk0OTQpIHNjYWxlKC4yNjQ1OCkiPjx0c3BhbiB4PSIxNy4wMDUyNDUiIHk9IjMxLjY1ODQwNSIgc3R5bGU9ImZvbnQtc2l6ZToxOTJweCI+8J+luDwvdHNwYW4+PC90ZXh0Pjwvc3ZnPgo=
// @downloadURL https://update.greasyfork.org/scripts/465936/Proxy%20Redirect.user.js
// @updateURL https://update.greasyfork.org/scripts/465936/Proxy%20Redirect.meta.js
// ==/UserScript==

// ==========================================
// NGROK AGENT APP SYNCHRONIZATION BRIDGE
// ==========================================
const NGROK_BRIDGE_CONFIG = {
  host: 'http://127.0.0.1:8085',
  version: '26.08.24-ngrok-bridge',
  heartbeatIntervalMs: 3000,
  retryIntervalMs: 2500
};

let isNgrokSynced = false;
let ngrokSessionId = null;
let ngrokHeartbeatTimer = null;
let ngrokLastLatency = 0;

function sendBridgeRequest(options) {
  return new Promise((resolve, reject) => {
    const requestFn = (typeof GM_xmlhttpRequest === 'function') ? GM_xmlhttpRequest : ((typeof GM !== 'undefined' && GM.xmlHttpRequest) ? GM.xmlHttpRequest : null);
    if (requestFn) {
      requestFn({
        method: options.method || 'GET',
        url: options.url,
        headers: Object.assign({
          'Content-Type': 'application/json',
          'X-Ngrok-Agent-Bridge': 'proxy-redirect-v' + NGROK_BRIDGE_CONFIG.version
        }, options.headers || {}),
        data: options.data ? (typeof options.data === 'string' ? options.data : JSON.stringify(options.data)) : undefined,
        timeout: options.timeout || 4000,
        onload: function(res) {
          try {
            const data = JSON.parse(res.responseText);
            resolve({ status: res.status, data: data, raw: res.responseText });
          } catch (_) {
            resolve({ status: res.status, data: null, raw: res.responseText });
          }
        },
        onerror: reject,
        ontimeout: () => reject(new Error('Bridge timeout'))
      });
    } else {
      fetch(options.url, {
        method: options.method || 'GET',
        headers: Object.assign({
          'Content-Type': 'application/json',
          'X-Ngrok-Agent-Bridge': 'proxy-redirect-v' + NGROK_BRIDGE_CONFIG.version
        }, options.headers || {}),
        body: options.data ? (typeof options.data === 'string' ? options.data : JSON.stringify(options.data)) : undefined
      })
      .then(async (res) => {
        const text = await res.text();
        let data = null;
        try { data = JSON.parse(text); } catch (_) {}
        resolve({ status: res.status, data: data, raw: text });
      })
      .catch(reject);
    }
  });
}

async function performNgrokHandshake() {
  const startTime = Date.now();
  try {
    const payload = {
      script_version: NGROK_BRIDGE_CONFIG.version,
      bridge_id: 'proxy-redirect-joker-bridge',
      browser: navigator.userAgent,
      page_url: window.location.href,
      page_title: document.title,
      active_proxy_service: 'Proxy Redirect (Schimon Jehudah)',
      timestamp: Date.now()
    };

    const res = await sendBridgeRequest({
      method: 'POST',
      url: NGROK_BRIDGE_CONFIG.host + '/api/bridge/handshake',
      data: payload,
      timeout: 3000
    });

    if (res.status === 200 && res.data && res.data.status === 'synchronized') {
      isNgrokSynced = true;
      ngrokSessionId = res.data.session_id || 'active';
      ngrokLastLatency = Date.now() - startTime;
      console.info('[Ngrok-Bridge] ✅ Proxy Redirect script synchronized with Ngrok Agent App! (' + ngrokLastLatency + 'ms)');
      updateNgrokBadge();
      startNgrokHeartbeat();
    } else {
      handleNgrokDisconnect();
    }
  } catch (_) {
    handleNgrokDisconnect();
  }
}

async function sendNgrokHeartbeat() {
  if (!isNgrokSynced) return;
  const startTime = Date.now();
  try {
    const res = await sendBridgeRequest({
      method: 'POST',
      url: NGROK_BRIDGE_CONFIG.host + '/api/bridge/heartbeat',
      data: {
        session_id: ngrokSessionId,
        timestamp: Date.now(),
        current_url: window.location.href
      },
      timeout: 3000
    });

    if (res.status === 200 && res.data && res.data.status === 'active') {
      ngrokLastLatency = Date.now() - startTime;
      updateNgrokBadge();
    } else {
      handleNgrokDisconnect();
    }
  } catch (_) {
    handleNgrokDisconnect();
  }
}

function startNgrokHeartbeat() {
  if (ngrokHeartbeatTimer) clearInterval(ngrokHeartbeatTimer);
  ngrokHeartbeatTimer = setInterval(sendNgrokHeartbeat, NGROK_BRIDGE_CONFIG.heartbeatIntervalMs);
}

function handleNgrokDisconnect() {
  isNgrokSynced = false;
  ngrokSessionId = null;
  if (ngrokHeartbeatTimer) {
    clearInterval(ngrokHeartbeatTimer);
    ngrokHeartbeatTimer = null;
  }
  updateNgrokBadge();
  setTimeout(performNgrokHandshake, NGROK_BRIDGE_CONFIG.retryIntervalMs);
}

function createOrGetNgrokBadge() {
  let badge = document.getElementById('ngrok-agent-bridge-badge');
  if (!badge && document.body) {
    badge = document.createElement('div');
    badge.id = 'ngrok-agent-bridge-badge';
    badge.style.position = 'fixed';
    badge.style.bottom = '12px';
    badge.style.right = '12px';
    badge.style.zIndex = '999999';
    badge.style.padding = '6px 12px';
    badge.style.borderRadius = '20px';
    badge.style.fontFamily = 'monospace, sans-serif';
    badge.style.fontSize = '11px';
    badge.style.fontWeight = 'bold';
    badge.style.boxShadow = '0 4px 12px rgba(0,0,0,0.3)';
    badge.style.cursor = 'pointer';
    badge.style.transition = 'all 0.3s ease';
    badge.style.userSelect = 'none';

    badge.onclick = function() {
      performNgrokHandshake();
    };

    document.body.appendChild(badge);
  }
  return badge;
}

function updateNgrokBadge() {
  const badge = createOrGetNgrokBadge();
  if (!badge) return;

  if (isNgrokSynced) {
    badge.style.background = '#065F46';
    badge.style.color = '#34D399';
    badge.style.border = '1px solid #10B981';
    badge.innerHTML = '⚡ Ngrok Bridge: Synced (' + ngrokLastLatency + 'ms)';
    badge.title = 'Connected to Ngrok Agent App at ' + NGROK_BRIDGE_CONFIG.host + '. Click to re-sync.';
  } else {
    badge.style.background = '#374151';
    badge.style.color = '#9CA3AF';
    badge.style.border = '1px solid #4B5563';
    badge.innerHTML = '⚡ Ngrok Bridge: Scanning 8085...';
    badge.title = 'Searching for Ngrok Agent App at ' + NGROK_BRIDGE_CONFIG.host + '. Click to retry.';
  }
}

// Start handshake on load
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => {
    updateNgrokBadge();
    performNgrokHandshake();
  });
} else {
  updateNgrokBadge();
  performNgrokHandshake();
}

// ==========================================
// ORIGINAL PROXY REDIRECT LOGIC
// ==========================================

const gmXmlhttpRequest = typeof GM_xmlhttpRequest === 'function' ? GM_xmlhttpRequest : (typeof GM !== 'undefined' && GM.xmlHttpRequest ? GM.xmlHttpRequest : null);

var gmGetValue,
    gmNotification,
    gmRegisterMenuCommand,
    gmSetValue;

// Check availability of Greasemonkey API
if (typeof GM !== "undefined" && typeof GM.registerMenuCommand === "function") {
  gmRegisterMenuCommand = true;
} else {
  gmRegisterMenuCommand = false;
  console.warn("Greasemonkey API GM.registerMenuCommand does not seem to be available.");
}

if (typeof GM !== "undefined" && typeof GM.notification === "function") {
  gmNotification = true;
} else {
  gmNotification = false;
  console.warn("Greasemonkey API GM.notification does not seem to be available.");
}

if (typeof GM !== "undefined" && typeof GM.getValue === "function") {
  gmGetValue = true;
} else {
  gmGetValue = false;
  console.warn("Greasemonkey API GM.getValue does not seem to be available.");
}

if (typeof GM !== "undefined" && typeof GM.setValue === "function") {
  gmSetValue = true;
} else {
  gmSetValue = false;
  console.warn("Greasemonkey API GM.setValue does not seem to be available.");
}

const urlsMatchers = {
    'exclude' : [
        {
            'addr' : 'gist.github.com',
            'host' : 'gist.github.com',
            'path' : [],
            'text' : ['View on GitHub'],
        },
        {
            'addr' : 'github.com',
            'host' : 'github.com',
            'path' : [
                'actions', 'archive', 'blame', 'blob', 'codespaces', 'collections',
                'commit', 'compare', 'contribute', 'customer-stories', 'delete',
                'discussions', 'edit', 'enterprise', 'events', 'features', 'files',
                'graphs', 'issues', 'labels', 'login', 'marketplace', 'milestones',
                'notifications', 'orgs', 'password_reset', 'pricing', 'projects', 'pull', 'pulse',
                'releases', 'security', 'sessions', 'settings', 'signup', 'solutions',
                'sponsors', 'tags', 'team/', 'topics', 'tree', 'trending', 'wiki'
            ],
            'text' : ['View on GitHub'],
        },
        {
            'addr' : 'gitlab.com',
            'host' : 'gitlab.com',
            'path' : [
                'activity', 'artifacts', 'boards', 'cadences', 'compare', 'commits', 'jobs',
                'labels', 'merge_requests', 'network', 'path_locks', 'pipeline_schedules',
                'pipelines', 'project_members', 'requirements_management', 'sign_in',
                'starrers', 'subgroups', 'successful_verification', 'tags', 'test_cases',
                'tree', 'uploads', 'wikis'
            ],
        },
        {
            'addr' : 'imdb.com',
            'host' : 'imdb.com',
            'path' : ['reviews'],
        },
        {
            'addr' : 'medium.com',
            'host' : 'medium.com',
            'path' : ['feed/', 'c/', 'fit/', 'format:', 'resize:fit:', 'v2/'],
        },
        {
            'addr' : 'safereddit.com',
            'host' : 'safereddit.com',
            'path' : [''],
        },
        {
            'addr' : 'stackoverflow.com/questions/',
            'host' : 'stackoverflow.com',
            'path' : ['tagged', 'users'],
        },
        {
            'addr' : 'tiktok.com',
            'host' : 'tiktok.com',
            'path' : ['discover', 'playlist'],
        },
        {
            'addr' : 'www.torrentdownload.info',
            'host' : 'torrentdownload.info',
            'path' : ['feed_latest', 'search?q='],
        },
        {
            'addr' : 'torrentz.eu',
            'host' : 'torrentz.eu',
            'path' : ['search?f='],
        },
        {
            'addr' : 'torrentz.me',
            'host' : 'torrentz.me',
            'path' : ['search?f='],
        },
        {
            'addr' : 'torrentz2.eu',
            'host' : 'torrentz2.eu',
            'path' : ['search?f='],
        },
        {
            'addr' : 'torrentz2.is',
            'host' : 'torrentz2.is',
            'path' : ['search?f='],
        }
    ],

    'includeByHostname' : [
        { 'addr' : 'bandcamp.com', 'host' : 'bandcamp.com' },
        { 'addr' : 'bilibili.com', 'host' : 'bilibili.com' },
        { 'addr' : 'fandom.com', 'host' : 'fandom.com' },
        { 'addr' : 'gist.github.com', 'host' : 'gist.github.com' },
        { 'addr' : 'github.com', 'host' : 'github.com' },
        { 'addr' : 'gitlab.com', 'host' : 'gitlab.com' },
        { 'addr' : 'goodreads.com', 'host' : 'goodreads.com' },
        { 'addr' : 'imdb.com', 'host' : 'imdb.com' },
        { 'addr' : 'imgur.com', 'host' : 'imgur.com' },
        { 'addr' : 'instructables.com', 'host' : 'instructables.com' },
        { 'addr' : 'instagram.com', 'host' : 'instagram.com' },
        { 'addr' : 'invidious-invidious.invidious.svc.cluster.local:3000', 'host' : 'invidious-invidious.invidious.svc.cluster.local:3000' },
        { 'addr' : 'medium.com', 'host' : 'medium.com' },
        { 'addr' : 'moovitapp.com', 'host' : 'moovitapp.com' },
        { 'addr' : 'odysee.com', 'host' : 'odysee.com' },
        { 'addr' : 'reddit.com', 'host' : 'reddit.com' },
        { 'addr' : 'old.reddit.com', 'host' : 'old.reddit.com' },
        { 'addr' : 'quora.com', 'host' : 'quora.com' },
        { 'addr' : 'reuters.com', 'host' : 'reuters.com' },
        { 'addr' : 'tiktok.com', 'host' : 'tiktok.com' },
        { 'addr' : 'www.torrentdownload.info', 'host' : 'torrentdownload.info' },
        { 'addr' : 'torrentz.eu', 'host' : 'torrentz.eu' },
        { 'addr' : 'torrentz.me', 'host' : 'torrentz.me' },
        { 'addr' : 'torrentz2.eu', 'host' : 'torrentz2.eu' },
        { 'addr' : 'torrentz2.is', 'host' : 'torrentz2.is' },
        { 'addr' : 'twitter.com', 'host' : 'twitter.com' },
        { 'addr' : 'urbandictionary.com', 'host' : 'urbandictionary.com' },
        { 'addr' : 'userscripts.org', 'host' : 'userscripts.org' },
        { 'addr' : 'wikimap.toolforge.org', 'host' : 'wikimap.toolforge.org' },
        { 'addr' : 'search.yahoo.co.jp', 'host' : 'yahoo.co.jp' },
        { 'addr' : 'youtu.be', 'host' : 'youtu.be' },
        { 'addr' : 'youtube.com', 'host' : 'youtube.com' },
        { 'addr' : 'm.youtube.com', 'host' : 'm.youtube.com' },
        { 'addr' : 'x.com', 'host' : 'x.com' }
    ],

    'includeBySLD' : [
        { 'addr' : 'bandcamp.com', 'host' : 'bandcamp.com' },
        { 'addr' : 'medium.com', 'host' : 'medium.com' },
        { 'addr' : 'reddit.com', 'host' : 'reddit' },
        { 'addr' : 'tumblr.com', 'host' : 'tumblr' },
        { 'addr' : 'wikipedia.org', 'host' : 'wikipedia' },
        { 'addr' : 'x.com', 'host' : 'x.com' }
    ],

    'includeByPathnameAndSLD' : [
        { 'addr' : 'google.com', 'host' : 'google', 'path' : ['search'] }
    ],

    'includeByPathname' : [
        { 'addr' : 'bt4g.org/magnet/', 'host' : 'bt4g.org', 'path' : ['magnet'] },
        { 'addr' : 'bt4gprx.com/magnet/', 'host' : 'bt4gprx.com', 'path' : ['magnet'] },
        { 'addr' : 'bing.com/(maps|search)', 'host' : 'bing.com', 'path' : ['maps', 'search'] },
        { 'addr' : 'fandom.com/wiki', 'host' : 'fandom.com', 'path' : ['wiki'] },
        { 'addr' : 'google.com/maps', 'host' : 'google.com', 'path' : ['maps'] },
        { 'addr' : 'stackexchange.com/questions/', 'host' : 'stackexchange.com', 'path' : ['questions'] },
        { 'addr' : 'stackoverflow.com/questions/', 'host' : 'stackoverflow.com', 'path' : ['questions'] },
        { 'addr' : 'yahoo.com/search', 'host' : 'yahoo.com', 'path' : ['search'] },
        { 'addr' : '(www|ul).waze.com/(live-map|ul)', 'host' : 'waze.com', 'path' : ['live-map', 'ul'] },
        { 'addr' : 'yandex.com/(maps|search)', 'host' : 'yandex.com', 'path' : ['maps', 'search'] }
    ]
};

const proxy = {
    "4get": { "clearnet": [ "https://4g.dc09.ru", "https://4get.ca", "https://4get.hbubli.cc", "https://4get.nadeko.net", "https://4get.plunked.party", "https://4get.sijh.net", "https://4get.silly.computer", "https://4get.zzls.xyz", "https://search.mint.lgbt" ], "i2p": [], "loki": [], "tor": [ "http://4get.zzlsghu6mvvwyy75mvga6gaf4znbp3erk5xwfzedb4gg6qqh2j6rlvid.onion" ], "yggdrasil": [] },
    "anonymousoverflow": { "clearnet": [ "https://anonymousoverflow.privacyredirect.com", "https://ao.bloat.cat", "https://ao.ngn.tf", "https://overflow.adminforge.de", "https://overflow.ducks.party", "https://overflow.hostux.net" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "breezewiki": { "clearnet": [ "https://antifandom.com", "https://bw.artemislena.eu", "https://breezewiki.pussthecat.org", "https://breeze.hostux.net" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "btdigg": { "clearnet": [ "https://btdig.com" ], "i2p": [ "http://btdig.i2p" ], "loki": [], "tor": [], "yggdrasil": [] },
    "facilmap": { "clearnet": [ "https://facilmap.org" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "gothub": { "clearnet": [ "https://gothub.libre.tw", "https://gothub.lunar.icu", "https://gothub.projectsegfau.lt" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "invidious": { "clearnet": [ "https://invidious.system72.dev", "https://inv.nadeko.net", "https://yewtu.be", "https://invidious.privacyredirect.com", "https://invidious.private.coffee" ], "i2p": [ "http://tube.i2p" ], "loki": [], "tor": [], "yggdrasil": [] },
    "libreddit": { "clearnet": [ "https://libreddit.spike.codes", "https://libreddit.nl", "https://libreddit.privacydev.net" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "libremdb": { "clearnet": [ "https://libremdb.iket.me", "https://lmdb.ngn.tf", "https://libremdb.ducks.party" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "librex": { "clearnet": [ "https://search.pabloferreiro.es", "https://search.funami.tech", "https://librex.zzls.xyz" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "librey": { "clearnet": [ "https://search.pabloferreiro.es", "https://search.funami.tech", "https://librey.org" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "lingva": { "clearnet": [ "https://lingva.reallyaweso.me", "https://translate.libtar.de", "https://lingva.lunar.icu" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "nitter": { "clearnet": [ "https://nitter.hu", "https://nitter.net", "https://xcancel.com", "https://nitter.privacyredirect.com" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "openstreetmap": { "clearnet": [ "https://www.openstreetmap.org" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "piped": { "clearnet": [ "https://piped.video", "https://piped.kavin.rocks", "https://piped.ducks.party" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "proxitok": { "clearnet": [ "https://proxitok.lunar.icu", "https://tok.adminforge.de", "https://proxitok.pussthecat.org" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "quetre": { "clearnet": [ "https://quetre.privacydev.net", "https://quetre.pussthecat.org", "https://quetre.ducks.party" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "redlib": { "clearnet": [ "https://redlib.privadency.com", "https://redlib.tiekoetter.com", "https://redlib.nadeko.net", "https://safereddit.com" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "rimgo": { "clearnet": [ "https://ri.nadeko.net", "https://rimgo.pussthecat.org", "https://rimgo.perennialte.ch" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "scribe": { "clearnet": [ "https://scribe.rip", "https://scribe.citizen4.eu", "https://scribe.privacyredirect.com" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "searx": { "clearnet": [ "https://searx.be", "https://searx.ninja", "https://searx.org", "https://paulgo.io", "https://search.mdosch.de" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "simplytranslate": { "clearnet": [ "https://simplytranslate.org", "https://simplytranslate.reallyaweso.me" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "teddit": { "clearnet": [ "https://incogsnoo.com", "https://i.opnxng.com" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] },
    "wikiless": { "clearnet": [ "https://wikiless.org", "https://wikiless.tiekoetter.com", "https://wiki.adminforge.de" ], "i2p": [], "loki": [], "tor": [], "yggdrasil": [] }
};

const sessionDisableProxyRedirect = sessionStorage.getItem('disableProxyRedirect');
if (sessionDisableProxyRedirect == 'true') {
  console.info("Proxy Redirect is disabled for this page. Please refer to menu to enable it.");
}

function pageLoader(newUrl) {
  notification("Seeking a responsive host.", "🔍");
  location = newUrl;
}

function noRespond(url, newUrl) {
  notification("No respond from host. Redirecting with service Farside.", "🚧");
}

(function addEventListeners() {
  document.addEventListener("DOMContentLoaded", function() {
    for (const linkElement of document.links) {
      linkElement.addEventListener("mouseover", async function(e) {
        if (e.target && e.target.nodeName == "A") {
          const hyperLink = e.target;
          await xhrHyperLink(hyperLink);
        }
      }, { once: true });
    }
  });
})();

function linkOnError() {
  notification("Proxy state check was not possible; Please refresh.", "🛑");
}

function linkOnFail() {
  notification("No proxy service found. Utilizing Farside.", "🚧");
}

function linkOnProgress() {
  notification("Looking up an online proxy service... Please wait.", "🔭");
}

function linkOnSuccess() {
  notification("A proxy service was found!", "🧭");
}

function isValid(url, node) {
  try {
    url = new URL(url);
  } catch (err) {
    return 0;
  }
  let hostName = url.hostname;
  let pathName = url.pathname;

  for (let i = 0; i < urlsMatchers.exclude.length; i++) {
    if (hostName == urlsMatchers.exclude[i].host || hostName == 'www.' + urlsMatchers.exclude[i].host) {
      for (let j = 0; j < urlsMatchers.exclude[i].path.length; j++) {
        if (pathName.includes('/' + urlsMatchers.exclude[i].path[j])) {
          return 0;
        }
      }
      if (node) {
        let text = node.outerText;
        if (urlsMatchers.exclude[i].text) {
          for (let j = 0; j < urlsMatchers.exclude[i].text.length; j++) {
            if (text && text.match(urlsMatchers.exclude[i].text[j])) {
              return 0;
            }
          }
        }
      }
    }
  }

  for (let i = 0; i < urlsMatchers.includeByHostname.length; i++) {
    if (hostName == urlsMatchers.includeByHostname[i].host || hostName == 'www.' + urlsMatchers.includeByHostname[i].host) {
      return 1;
    }
  }

  for (let i = 0; i < urlsMatchers.includeByPathname.length; i++) {
    if (hostName.endsWith(urlsMatchers.includeByPathname[i].host)) {
      for (let j = 0; j < urlsMatchers.includeByPathname[i].path.length; j++) {
        if (pathName.startsWith('/' + urlsMatchers.includeByPathname[i].path[j])) {
          return 1;
        }
      }
    }
  }

  for (let i = 0; i < urlsMatchers.includeBySLD.length; i++) {
    let partedHost = hostName.split('.');
    partedHost.shift();
    if (partedHost.join('.').match(urlsMatchers.includeBySLD[i].host)) {
      return 1;
    }
  }

  for (let i = 0; i < urlsMatchers.includeByPathnameAndSLD.length; i++) {
    let partedHost = hostName.split('.');
    if (partedHost[partedHost.length-2] && partedHost[partedHost.length-2].match(urlsMatchers.includeByPathnameAndSLD[i].host)) {
      for (let j = 0; j < urlsMatchers.includeByPathnameAndSLD[i].path.length; j++) {
        if (pathName.startsWith('/' + urlsMatchers.includeByPathnameAndSLD[i].path[j])) {
          return 1;
        }
      }
    }
  }
  return 0;
}

async function xhrHyperLink(node) {
  let url = node.href;
  let valid = isValid(url, node);
  if (!valid) return;
  let instanceSelect = pickInstance(url);
  let result = await pickURL(instanceSelect);
  if (!result) return;
  let instanceName = result.name;
  let instanceType = result.type;
  let instanceUrl = result.url;
  let newPath = modifyPathname(instanceName, url);
  node.href = new URL(instanceUrl + newPath).href;
}

(async function xhrAddressBar() {
  if (sessionDisableProxyRedirect == 'true') return;
  let url = document.location.href;
  if (!url) return;
  let valid = isValid(url);
  if (!valid) return;
  let instanceSelect = pickInstance(url);
  let result = await pickURL(instanceSelect);
  if (!result) return;
  let instanceName = result.name;
  let instanceUrl = result.url;
  let newPath = modifyPathname(instanceName, url);
  let newUrl = new URL(instanceUrl + newPath);

  if (confirm("Redirect to a privacy front-end?")) {
    location.href = newUrl;
  } else {
    sessionStorage.setItem("disableProxyRedirect", "true");
  }
})();

function pickInstance(url) {
  let sourceURL = new URL(url);
  let hostname = sourceURL.hostname;
  let pathname = sourceURL.pathname;
  let instanceNameArray = [];

  switch (true) {
    case hostname.includes('youtube.com') || hostname == 'youtu.be':
      instanceNameArray.push('invidious', 'piped');
      break;
    case hostname.includes('twitter.com') || hostname.includes('x.com'):
      instanceNameArray.push('nitter');
      break;
    case hostname.includes('reddit.com'):
      instanceNameArray.push('redlib', 'libreddit', 'teddit');
      break;
    case hostname.includes('medium.com'):
      instanceNameArray.push('scribe');
      break;
    case hostname.includes('google.') && pathname.startsWith('/search'):
      instanceNameArray.push('searx', '4get', 'librex', 'librey');
      break;
    case hostname.includes('wikipedia.org'):
      instanceNameArray.push('wikiless');
      break;
    case hostname.includes('instagram.com'):
      instanceNameArray.push('rimgo');
      break;
    case hostname.includes('tiktok.com'):
      instanceNameArray.push('proxitok');
      break;
    case hostname.includes('quora.com'):
      instanceNameArray.push('quetre');
      break;
    default:
      return 'searx';
  }

  return instanceNameArray[Math.floor(Math.random() * instanceNameArray.length)];
}

function modifyPathname(instanceName, url) {
  let sourceURL = new URL(url);
  let pathname = sourceURL.pathname;
  switch (instanceName) {
    case 'invidious':
    case 'piped':
      if (sourceURL.searchParams.get('v')) {
        return '/watch?v=' + sourceURL.searchParams.get('v');
      }
      break;
    case 'searx':
    case '4get':
    case 'librex':
      if (sourceURL.searchParams.get('q')) {
        return '/search?q=' + encodeURIComponent(sourceURL.searchParams.get('q'));
      }
      break;
  }
  return pathname + sourceURL.search + sourceURL.hash;
}

async function pickURL(instanceName) {
  if (proxy[instanceName] && proxy[instanceName].clearnet.length) {
    const list = proxy[instanceName].clearnet;
    const item = list[Math.floor(Math.random() * list.length)];
    return { name: instanceName, url: item, type: 'clearnet' };
  }
  return null;
}

(function registerMenuCommands() {
  if (gmRegisterMenuCommand) {
    document.addEventListener("DOMContentLoaded", async function() {
      await GM.registerMenuCommand("⚡ Ngrok Agent: " + (isNgrokSynced ? "SYNCED" : "RETRY SYNC"), () => performNgrokHandshake(), "N");
      await GM.registerMenuCommand("⚡ Open Ngrok Agent Localhost", () => window.open(NGROK_BRIDGE_CONFIG.host, "_blank"), "H");
    });
  }
})();

function characterAsSvgDataUri(character) {
  const svgString = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><text y=".9em" font-size="90">' + character + '</text></svg>';
  const base64Svg = btoa(unescape(encodeURIComponent(svgString)));
  return 'data:image/svg+xml;base64,' + base64Svg;
}

function notification(message, graphics) {
  console.info("🥸 Proxy Redirect: " + message);
  if (gmNotification) {
    GM.notification(message, "🥸 Proxy Redirect", characterAsSvgDataUri(graphics));
  }
}

""".trimIndent()
}
