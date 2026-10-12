/*
 * Shop filters: state lives in the URL, results swap without reloads, skeletons while loading, back button
 * works.
 */
(function () {
  'use strict';
  const form = document.getElementById('filter-form');
  if (!form || !document.getElementById('results')) return;
  const { $, $$, money } = window.EZ;
  let controller = null;

  function params(page) {
    const p = new URLSearchParams();
    new FormData(form).forEach((v, k) => { if (String(v).trim() !== '') p.append(k, v); });
    const sort = $('[data-sort]').value;
    p.set('sort', sort);
    if (sort === 'relevance') p.delete('sort');
    if (page) p.set('page', page);
    return p;
  }

  async function load(page, push = true) {
    const p = params(page);
    const url = '/products' + (p.toString() ? '?' + p : '');
    if (push) history.pushState({}, '', url);
    const results = document.getElementById('results');
    results.classList.add('loading');
    if (controller) controller.abort();
    controller = new AbortController();
    try {
      const res = await fetch(url + (url.includes('?') ? '&' : '?') + 'partial=1', { signal: controller.signal });
      results.outerHTML = await res.text();
      renderChips();
      // category change needs a new sidebar (different spec filters): do a full navigation
    } catch (e) {
      if (e.name !== 'AbortError') window.EZ.toast('Could not load products', 'err');
    } finally {
      document.getElementById('results')?.classList.remove('loading');
    }
  }

  let timer = null;
  form.addEventListener('change', (e) => {
    if (e.target.name === 'category') { location.href = '/products' + (e.target.value ? '?category=' + e.target.value : ''); return; }
    clearTimeout(timer);
    timer = setTimeout(() => load(), e.target.type === 'number' ? 450 : 80);
  });
  form.addEventListener('submit', (e) => { e.preventDefault(); load(); });
  $('[data-sort]').addEventListener('change', () => load());
  document.addEventListener('click', (e) => {
    const a = e.target.closest('.pager a[data-page]');
    if (a) { e.preventDefault(); load(a.dataset.page); window.scrollTo({ top: 0, behavior: 'smooth' }); }
    const chip = e.target.closest('[data-chip]');
    if (chip) {
      const [name, value] = chip.dataset.chip.split('|');
      $$(`[name="${CSS.escape(name)}"]`, form).forEach((el) => {
        if (el.type === 'checkbox' && (value === undefined || el.value === value)) el.checked = false;
        else if (el.type === 'number') el.value = '';
      });
      syncSlider();
      load();
    }
    if (e.target.closest('[data-filters-open]')) { $('#filters').classList.add('open'); $('[data-overlay]').classList.add('open'); }
    if (e.target.closest('[data-filters-close]')) { $('#filters').classList.remove('open'); $('[data-overlay]').classList.remove('open'); }
  });
  window.addEventListener('popstate', () => location.reload());

  // active filter chips
  function renderChips() {
    const box = $('[data-active-chips]');
    if (!box) return;
    const chips = [];
    $$('input[type=checkbox]:checked', form).forEach((el) => {
      const label = el.closest('label')?.querySelector('span')?.textContent || el.value;
      const text = el.name === 'inStock' ? 'In stock' : el.name === 'sale' ? 'On discount' : label;
      chips.push(`<span class="chip" data-chip="${el.name}|${el.value}">${window.EZ.esc(text)} ✕</span>`);
    });
    const min = form.elements.min.value, max = form.elements.max.value;
    if (min || max) chips.push(`<span class="chip" data-chip="min">${min ? money(min) : '৳0'} – ${max ? money(max) : 'any'} ✕</span>`);
    box.innerHTML = chips.join('');
  }

  // prices can't go below zero: clean typed/pasted values before they reach the URL
  form.addEventListener('input', (e) => {
    if (!e.target.matches('[data-nonneg]')) return;
    const v = e.target.value;
    if (v !== '' && (+v < 0 || v.includes('-'))) e.target.value = String(Math.abs(Math.trunc(+v)) || '');
  }, true);
  form.addEventListener('keydown', (e) => {
    if (e.target.matches('[data-nonneg]') && ['-', 'e', 'E', '+'].includes(e.key)) e.preventDefault();
  }, true);

  // dual-thumb price slider bound to the min/max inputs
  const range = $('.range');
  const lo = $('[data-lo]'), hi = $('[data-hi]'), fill = $('.slider .fill');
  function syncFill() {
    const a = +range.dataset.min, b = +range.dataset.max || 1;
    const l = ((+lo.value - a) / (b - a || 1)) * 100, r = ((+hi.value - a) / (b - a || 1)) * 100;
    fill.style.left = l + '%'; fill.style.width = Math.max(0, r - l) + '%';
  }
  function syncSlider() {
    lo.value = form.elements.min.value || range.dataset.min;
    hi.value = form.elements.max.value || range.dataset.max;
    syncFill();
  }
  if (range && lo && hi) {
    [lo, hi].forEach((s) => s.addEventListener('input', () => {
      if (+lo.value > +hi.value) { if (s === lo) lo.value = hi.value; else hi.value = lo.value; }
      form.elements.min.value = +lo.value <= +range.dataset.min ? '' : lo.value;
      form.elements.max.value = +hi.value >= +range.dataset.max ? '' : hi.value;
      syncFill();
    }));
    [lo, hi].forEach((s) => s.addEventListener('change', () => load()));
    syncFill();
  }
  // collapsible groups
  $$('.fgroup h4').forEach((h) => h.addEventListener('click', () => h.parentElement.classList.toggle('collapsed')));
  renderChips();
})();
