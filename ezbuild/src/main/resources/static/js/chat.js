/* AI chat: one module for the floating widget and the full /assistant page. */
(function () {
  'use strict';
  const { $, $$, api, money, esc, toast, addMany } = window.EZ;
  const page = $('[data-chat-page]');
  const root = page || $('[data-chat-panel]');
  if (!root) return;
  const log = $('[data-chat-log]', root);
  const form = $('[data-chat-form]', root);
  const input = $('input', form);
  const sugg = $('[data-chat-suggest]', root);
  let sessionId = null;
  try { sessionId = sessionStorage.getItem('ezb-chat'); } catch (_) { sessionId = null; }
  let busy = false;

  const scroll = () => { log.scrollTop = log.scrollHeight; };

  // Minimal safe Markdown for assistant replies: escape HTML first, then **bold**, headings and bullets.
  function md(text) {
    return esc(text || '')
      .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
      .replace(/^#{1,4}\s*(.+)$/gm, '<b>$1</b>')
      .replace(/^\s*[-*]\s+/gm, '• ');
  }

  function bubble(role, text) {
    const el = document.createElement('div');
    el.className = 'msg ' + role;
    if (role === 'assistant') el.innerHTML = md(text); else el.textContent = text;
    log.appendChild(el);
    scroll();
    return el;
  }

  function buildCard(b) {
    const el = document.createElement('div');
    el.className = 'build-card';
    const ok = b.compat && b.compat.ok;
    const warnings = (b.compat && b.compat.warnings) || [];
    el.innerHTML = `
      <div class="bc-head"><span class="pill dark">${esc(b.tier)}</span><span class="pill ${ok ? 'ok' : 'bad'}">${ok ? (warnings.length ? 'Compatible*' : 'Compatible') : 'Check'}</span></div>
      <div class="bc-total">${money(b.total)} ${b.budget ? `<small class="muted" style="font-weight:500;font-size:.8rem">· ${money(b.budget - b.total)} left of ${money(b.budget)}</small>` : ''}</div>
      ${b.parts.map((p) => `<div class="bc-part"><img src="/media/p/${esc(p.productId)}.webp" alt="" data-fallback="/media/c/${esc(String(p.category).toLowerCase())}.svg">
        <div><div class="c">${esc(String(p.category).toLowerCase())}</div><a href="/products/${esc(p.productId)}" target="_blank" rel="noopener">${esc(p.name)}</a></div><b>${money(p.price)}</b></div>`).join('')}
      ${warnings.length ? `<small class="muted">* ${esc(warnings.join(' '))}</small>` : ''}
      <div class="bc-actions">
        <button class="btn sky sm" type="button" data-bc-cart>Add all to cart</button>
        <a class="btn ghost sm" href="/builder?ids=${b.parts.map((p) => encodeURIComponent(p.productId)).join(',')}">Open in builder</a>
        <button class="btn ghost sm" type="button" data-bc-save>Save</button>
      </div>`;
    $('[data-bc-cart]', el).addEventListener('click', () => addMany(b.parts.map((p) => p.productId)).catch((e) => toast(e.message, 'err')));
    $('[data-bc-save]', el).addEventListener('click', async () => {
      try {
        await api('POST', '/api/builds', { name: b.tier + ' build ' + money(b.total), source: 'AI', productIds: b.parts.map((p) => p.productId) });
        toast('Build saved to your account');
      } catch (e) { toast(e.message, 'err'); }
    });
    log.appendChild(el);
    scroll();
  }

  function suggestions(list) {
    sugg.innerHTML = (list || []).map((s) => `<span class="chip plain" data-say="${esc(s)}">${esc(s)}</span>`).join('');
  }

  function render(reply) {
    bubble('assistant', reply.text);
    (reply.builds || []).forEach(buildCard);
    suggestions(reply.suggestions);
    if (reply.degraded) $('.chat-head .dot', root)?.classList.add('off');
  }

  async function send(text) {
    if (busy || !text.trim()) return;
    busy = true;
    bubble('user', text.trim());
    input.value = '';
    suggestions([]);
    const typing = document.createElement('div');
    typing.className = 'msg assistant typing';
    typing.innerHTML = '<i></i><i></i><i></i>';
    log.appendChild(typing);
    scroll();
    try {
      const reply = await api('POST', '/api/chat', { sessionId, message: text.trim() });
      sessionId = reply.sessionId;
      try { sessionStorage.setItem('ezb-chat', sessionId); } catch (_) { /* ignore */ }
      typing.remove();
      render(reply);
    } catch (e) {
      typing.remove();
      bubble('assistant', e.status === 429 ? 'You are sending messages a bit fast. Try again in a minute.' : (e.message || 'Something went wrong.'));
    } finally { busy = false; input.focus(); }
  }

  async function loadSession(id) {
    log.innerHTML = '';
    try {
      const msgs = await api('GET', '/api/chat/sessions/' + encodeURIComponent(id));
      sessionId = id;
      msgs.forEach((m) => { bubble(m.role, m.text); (m.builds || []).forEach(buildCard); });
      const last = msgs[msgs.length - 1];
      suggestions(last ? last.suggestions : []);
    } catch (_) { sessionId = null; greet(); }
  }

  function greet() {
    bubble('assistant', 'Hi! I\'m the EZBuild assistant. Tell me your budget and what the PC is for, or name parts you want, and I\'ll plan compatible builds from our stock.');
    suggestions(['Gaming PC 1 lakh', 'Office PC 50k', 'Editing PC 1.5 lakh', 'amar 80 hajar takar gaming pc lagbe']);
  }

  form.addEventListener('submit', (e) => { e.preventDefault(); send(input.value); });
  sugg.addEventListener('click', (e) => { const c = e.target.closest('[data-say]'); if (c) send(c.dataset.say); });

  // floating widget open/close
  $$('[data-chat-toggle]').forEach((b) => b.addEventListener('click', () => {
    const panel = $('[data-chat-panel]');
    panel.classList.toggle('open');
    if (panel.classList.contains('open')) { if (!log.children.length) (sessionId ? loadSession(sessionId) : greet()); input.focus(); }
  }));

  // full page: session list, new chat, ?q= prefill
  if (page) {
    $$('[data-session]').forEach((a) => a.addEventListener('click', (e) => {
      e.preventDefault();
      $$('[data-session]').forEach((x) => x.classList.remove('on'));
      a.classList.add('on');
      loadSession(a.dataset.session);
    }));
    $('[data-new-chat]')?.addEventListener('click', () => {
      sessionId = null;
      try { sessionStorage.removeItem('ezb-chat'); } catch (_) { /* ignore */ }
      log.innerHTML = '';
      greet();
    });
    if (input.value) { greet(); send(input.value); } else if (sessionId) loadSession(sessionId); else greet();
  }
})();
