/* Manual PC builder: live compatibility check via POST /api/builds/validate. */
(function () {
  'use strict';
  const { $, $$, api, money, esc, toast, addMany, signedIn } = window.EZ;
  const slots = $$('[data-slot]');
  if (!slots.length) return;

  const selected = () => slots.map((s) => $('select', s).value).filter(Boolean);

  function slotUi(slot) {
    const sel = $('select', slot);
    const opt = sel.selectedOptions[0];
    const img = $('img', slot);
    slot.classList.toggle('filled', !!sel.value);
    $('[data-slot-price]', slot).textContent = sel.value ? money(opt.dataset.price) : 'Not selected';
    img.dataset.failed = '';
    img.dataset.fallback = img.dataset.placeholder;
    img.src = sel.value ? `/media/p/${sel.value}.webp` : img.dataset.placeholder;
  }

  /** Hides options that clash with what's already picked (socket, memory type). */
  function filterOptions() {
    const only = $('[data-compatible-only]').checked;
    const get = (name) => { const s = slots.find((x) => x.dataset.slot === name); return s ? $('select', s).selectedOptions[0] : null; };
    const cpu = get('CPU'), mb = get('MOTHERBOARD');
    const socket = (cpu && cpu.value && cpu.dataset.socket) || (mb && mb.value && mb.dataset.socket) || '';
    const ram = (mb && mb.value && mb.dataset.ram) || '';
    slots.forEach((slot) => {
      $$('option', $('select', slot)).forEach((o) => {
        if (!o.value) return;
        let ok = true;
        if (only && (slot.dataset.slot === 'CPU' || slot.dataset.slot === 'MOTHERBOARD') && socket && o.dataset.socket && o.dataset.socket !== socket) ok = false;
        if (only && slot.dataset.slot === 'MOTHERBOARD' && cpu && cpu.value && cpu.dataset.ram && o.dataset.ram && !cpu.dataset.ram.includes(o.dataset.ram)) ok = false;
        if (only && slot.dataset.slot === 'RAM' && ram && o.dataset.ram && o.dataset.ram !== ram) ok = false;
        o.hidden = !ok && !o.selected;
      });
    });
  }

  async function check() {
    const ids = selected();
    $('[data-count]').textContent = ids.length;
    const box = $('[data-compat]');
    if (!ids.length) {
      $('[data-total]').textContent = money(0);
      $('[data-watt]').textContent = '—';
      box.className = 'compat ok';
      box.textContent = 'Pick parts to start the compatibility check.';
      return;
    }
    try {
      const plan = await api('POST', '/api/builds/validate', { productIds: ids });
      $('[data-total]').textContent = money(plan.total);
      $('[data-watt]').textContent = '~' + plan.compat.estimatedWattage + ' W';
      box.className = 'compat ' + (plan.compat.ok ? 'ok' : 'bad');
      const list = [...plan.compat.errors.map((e) => '✕ ' + e), ...plan.compat.warnings.map((w) => '! ' + w)];
      box.innerHTML = `<b>${plan.compat.ok ? (plan.compat.warnings.length ? 'Compatible, with notes' : '✓ Everything fits') : 'Not compatible yet'}</b>`
        + (list.length ? '<ul>' + list.map((x) => `<li>${esc(x)}</li>`).join('') + '</ul>' : '');
    } catch (e) { toast('Compatibility check failed', 'err'); }
    history.replaceState({}, '', '/builder?ids=' + ids.join(','));
  }

  slots.forEach((slot) => {
    $('select', slot).addEventListener('change', () => { slotUi(slot); filterOptions(); check(); });
    $('[data-clear]', slot).addEventListener('click', (e) => { e.preventDefault(); $('select', slot).value = ''; slotUi(slot); filterOptions(); check(); });
    slotUi(slot);
  });
  $('[data-compatible-only]').addEventListener('change', filterOptions);

  $('[data-build-cart]').addEventListener('click', () => {
    const ids = selected();
    if (!ids.length) { toast('Pick some parts first', 'err'); return; }
    addMany(ids).catch((e) => toast(e.message, 'err'));
  });
  $('[data-save-form]').addEventListener('submit', (e) => {
    if (!signedIn) { e.preventDefault(); location.href = '/sign-in'; return; }
    const ids = selected();
    if (!ids.length) { e.preventDefault(); toast('Pick some parts first', 'err'); return; }
    $('[data-ids]').value = ids.join(',');
  });
  $('[data-share]').addEventListener('click', async () => {
    const url = location.origin + '/builder?ids=' + selected().join(',');
    try { await navigator.clipboard.writeText(url); toast('Link copied'); } catch (_) { prompt('Copy this link', url); }
  });

  filterOptions();
  check();
})();
