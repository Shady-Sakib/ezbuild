/* Core helpers: API calls (session -> JWT bridge), toasts, cart drawer, wishlist, search suggestions. */
(function () {
  'use strict';

  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => Array.from(root.querySelectorAll(s));
  const meta = (n) => { const m = document.querySelector(`meta[name="${n}"]`); return m ? m.content : null; };
  const signedIn = meta('signed-in') === 'true';
  const money = (n) => '৳' + Number(n || 0).toLocaleString('en-US');
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const placeholder = '/media/c/part.svg';

  // -- JWT bridge: token kept in memory only, never localStorage --
  let token = null;
  let tokenExpires = 0;
  async function getToken(force) {
    if (!force && token && Date.now() < tokenExpires - 30000) return token;
    const headers = {};
    const h = meta('_csrf_header'), t = meta('_csrf');
    if (h && t) headers[h] = t;
    const res = await fetch('/auth/token', { method: 'POST', headers, credentials: 'same-origin' });
    if (!res.ok) throw new Error('auth');
    const data = await res.json();
    token = data.accessToken;
    tokenExpires = Date.now() + data.expiresIn * 1000;
    return token;
  }

  async function api(method, url, body, retry = true) {
    const headers = { 'Accept': 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (signedIn) headers['Authorization'] = 'Bearer ' + await getToken();
    const res = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    if (res.status === 401 && signedIn && retry) { await getToken(true); return api(method, url, body, false); }
    if (res.status === 204) return null;
    const data = await res.json().catch(() => ({}));
    if (!res.ok) { const err = new Error(data.message || 'Request failed'); err.status = res.status; err.data = data; throw err; }
    return data;
  }

  // -- toasts --
  function toast(message, type = 'ok') {
    const box = $('[data-toasts]');
    if (!box) return;
    const el = document.createElement('div');
    el.className = 'toast ' + type;
    el.textContent = message;
    box.appendChild(el);
    setTimeout(() => { el.style.transition = 'opacity .25s'; el.style.opacity = '0'; setTimeout(() => el.remove(), 260); }, 2600);
  }

  // -- cart --
  function setCount(n) {
    $$('[data-cart-count]').forEach((b) => { b.textContent = n; b.style.display = n > 0 ? '' : 'none'; });
  }

  function renderDrawer(cart) {
    const items = $('[data-drawer-items]');
    if (!items) return;
    $('[data-drawer-subtotal]').textContent = money(cart.subtotal);
    setCount(cart.count);
    if (!cart.lines.length) { items.innerHTML = '<div class="empty">Your cart is empty.</div>'; return; }
    items.innerHTML = cart.lines.map((l) => `
      <div class="line" data-line="${esc(l.productId)}">
        <img src="/media/p/${esc(l.productId)}.webp" alt="" data-fallback="${placeholder}">
        <div><a href="/products/${esc(l.productId)}">${esc(l.name)}</a><br>
          <small class="muted">${money(l.unitPrice)} × </small>
          <select data-line-qty style="width:62px;min-height:30px;padding:2px 6px">${Array.from({ length: 10 }, (_, i) => `<option ${i + 1 === l.qty ? 'selected' : ''}>${i + 1}</option>`).join('')}</select>
        </div>
        <div style="text-align:right"><b>${money(l.unitPrice * l.qty)}</b><br><button class="link" data-line-remove style="background:none;border:0;cursor:pointer;font-size:.8rem">Remove</button></div>
      </div>`).join('');
  }

  async function openDrawer() {
    const drawer = $('#cart-drawer');
    if (!drawer) { location.href = '/sign-in'; return; }
    drawer.hidden = false;
    void drawer.offsetWidth; // force layout while off-screen so it slides in
    drawer.classList.add('open');
    $('[data-overlay]').classList.add('open');
    try { renderDrawer(await api('GET', '/api/cart')); } catch (e) { toast('Could not load your cart', 'err'); }
  }
  function closeDrawer() {
    const drawer = $('#cart-drawer');
    if (drawer && drawer.classList.contains('open')) {
      drawer.classList.remove('open');
      setTimeout(() => { if (!drawer.classList.contains('open')) drawer.hidden = true; }, 280);
    }
    $('[data-overlay]')?.classList.remove('open');
    $('#filters')?.classList.remove('open');
  }

  async function addToCart(productId, qty, button) {
    if (!signedIn) { location.href = '/sign-in'; return; }
    button?.classList.add('loading');
    try {
      const cart = await api('POST', '/api/cart/items', { productId, qty });
      setCount(cart.count);
      const line = (cart.lines || []).find((l) => l.productId === productId);
      toast(line ? `Added to cart: ${line.name}` : 'Added to cart');
      renderDrawer(cart);
    } catch (e) {
      toast(e.message, 'err');
    } finally { button?.classList.remove('loading'); }
  }

  async function addMany(ids) {
    if (!signedIn) { location.href = '/sign-in'; return; }
    const cart = await api('POST', '/api/cart/build', { productIds: ids });
    setCount(cart.count);
    toast('Build added to cart');
    openDrawer();
  }

  // -- event delegation --
  document.addEventListener('click', async (e) => {
    const add = e.target.closest('[data-add]');
    if (add) {
      e.preventDefault();
      const qtyInput = add.closest('div')?.parentElement?.querySelector('[data-qty] input');
      addToCart(add.dataset.add, qtyInput ? Math.max(1, Math.min(10, +qtyInput.value || 1)) : 1, add);
      return;
    }
    if (e.target.closest('[data-cart-open]')) { openDrawer(); return; }
    if (e.target.closest('[data-drawer-close]') || e.target.matches('[data-overlay]')) { closeDrawer(); return; }

    const remove = e.target.closest('[data-line-remove]');
    if (remove) {
      const row = remove.closest('[data-line]');
      const name = row.querySelector('a')?.textContent || 'Item';
      try {
        renderDrawer(await api('DELETE', '/api/cart/items/' + encodeURIComponent(row.dataset.line)));
        toast(`Removed from cart: ${name}`);
      } catch (err) { toast(err.message, 'err'); }
      return;
    }

    const wish = e.target.closest('[data-wish]');
    if (wish) {
      if (!signedIn) { location.href = '/sign-in'; return; }
      try {
        const r = await api('POST', '/api/wishlist/' + encodeURIComponent(wish.dataset.wish));
        wish.textContent = r.inWishlist ? '♥ Saved' : '♡ Wishlist';
        toast(r.inWishlist ? 'Saved to wishlist' : 'Removed from wishlist');
      } catch (err) { toast(err.message, 'err'); }
      return;
    }

    const cmp = e.target.closest('[data-compare]');
    if (cmp) {
      e.preventDefault();
      let list = [];
      try { list = JSON.parse(localStorage.getItem('ezb-compare') || '[]'); } catch (_) { list = []; }
      list = [cmp.dataset.compare, ...list.filter((x) => x !== cmp.dataset.compare)].slice(0, 3);
      try { localStorage.setItem('ezb-compare', JSON.stringify(list)); } catch (_) { /* private mode */ }
      location.href = '/compare?ids=' + list.join(',');
      return;
    }

    const toggle = e.target.closest('[data-dropdown-toggle]');
    $$('[data-dropdown]').forEach((d) => { if (!toggle || !d.contains(toggle)) d.classList.remove('open'); });
    if (toggle) { toggle.closest('[data-dropdown]').classList.toggle('open'); return; }

    const copy = e.target.closest('[data-copy]');
    if (copy) {
      const url = location.origin + copy.dataset.copy;
      try { await navigator.clipboard.writeText(url); toast('Link copied'); } catch (_) { prompt('Copy this link', url); }
      return;
    }
    if (e.target.closest('[data-print]')) { window.print(); return; }

    const qtyBtn = e.target.closest('[data-qty] button');
    if (qtyBtn) {
      const input = qtyBtn.parentElement.querySelector('input');
      input.value = Math.max(1, Math.min(10, (+input.value || 1) + (qtyBtn.hasAttribute('data-inc') ? 1 : -1)));
    }
  });

  document.addEventListener('change', async (e) => {
    if (e.target.matches('[data-line-qty]')) {
      const id = e.target.closest('[data-line]').dataset.line;
      try {
        renderDrawer(await api('PATCH', '/api/cart/items/' + encodeURIComponent(id), { qty: +e.target.value }));
        toast('Cart updated');
      } catch (err) { toast(err.message, 'err'); }
    }
    if (e.target.matches('[data-autosubmit]')) e.target.form.submit();
    if (e.target.matches('[data-select-all]')) $$('[data-row]').forEach((c) => { c.checked = e.target.checked; });
  });

  document.addEventListener('submit', (e) => {
    const msg = e.target.getAttribute('data-confirm');
    if (msg && !confirm(msg)) e.preventDefault();
  });

  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeDrawer(); });

  // success flash messages (after a form post) show as a toast instead of a banner
  $$('.flash.success').forEach((f) => { toast(f.textContent.trim()); f.remove(); });

  // broken/missing product image -> category placeholder (CSP blocks inline onerror)
  document.addEventListener('error', (e) => {
    const img = e.target;
    if (img.tagName === 'IMG' && !img.dataset.failed) {
      img.dataset.failed = '1';
      img.src = img.dataset.fallback || placeholder;
    }
  }, true);

  // -- header search suggestions (debounced) --
  const input = $('[data-suggest]');
  const box = $('#suggest');
  if (input && box) {
    let timer = null, active = -1, last = '';
    input.addEventListener('input', () => {
      clearTimeout(timer);
      const q = input.value.trim();
      if (q.length < 2) { box.classList.remove('open'); return; }
      timer = setTimeout(async () => {
        if (q === last) return;
        last = q;
        try {
          const res = await fetch('/api/products/suggest?q=' + encodeURIComponent(q));
          const items = await res.json();
          active = -1;
          box.innerHTML = items.length ? items.map((p) => `
            <a href="/products/${esc(p.id)}"><img src="${esc(p.image)}" alt="" data-fallback="${placeholder}"><span>${esc(p.name)}<br><small class="muted">${esc(p.categoryLabel)}</small></span><span class="price">${money(p.price)}</span></a>`).join('')
            + `<a href="/products?q=${encodeURIComponent(q)}" style="justify-content:center"><b>See all results</b></a>`
            : '<div style="padding:12px" class="muted">No matches</div>';
          box.classList.add('open');
        } catch (_) { /* ignore */ }
      }, 250);
    });
    input.addEventListener('keydown', (e) => {
      const links = $$('a', box);
      if (!box.classList.contains('open') || !links.length) return;
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        active = (active + (e.key === 'ArrowDown' ? 1 : -1) + links.length) % links.length;
        links.forEach((l, i) => l.classList.toggle('active', i === active));
      } else if (e.key === 'Enter' && active >= 0) { e.preventDefault(); location.href = links[active].href; }
    });
    document.addEventListener('click', (e) => { if (!e.target.closest('.search')) box.classList.remove('open'); });
  }

  window.EZ = { api, toast, money, esc, addMany, openDrawer, setCount, signedIn, $, $$ };
})();
