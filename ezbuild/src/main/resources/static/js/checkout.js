/*
 * Checkout: live totals (zone + COD fee), saved-address chips, SSLCommerz limit hint. The server recomputes
 * everything.
 */
(function () {
  'use strict';
  const form = document.getElementById('checkout-form');
  if (!form) return;
  const { $, $$, money } = window.EZ;
  const d = form.dataset;

  function update() {
    const zone = $('input[data-zone]:checked', form)?.value || 'INSIDE_DHAKA';
    const method = $('input[data-method]:checked', form)?.value || 'COD';
    const ship = zone === 'OUTSIDE_DHAKA' ? +d.outside : +d.inside;
    const cod = method === 'COD' ? +d.cod : 0;
    const total = +d.subtotal + ship + cod;
    $('[data-ship]').textContent = money(ship);
    $('[data-cod-row]').style.display = cod ? '' : 'none';
    $('[data-total]').textContent = money(total);
    const tooBig = method === 'SSLCOMMERZ' && total > +d.sslmax;
    $('[data-ssl-warning]').hidden = !tooBig;
    $('[data-submit]').disabled = tooBig;
    $('[data-submit]').textContent = method === 'COD' ? 'Place order' : 'Continue to payment';
  }

  form.addEventListener('change', update);
  form.addEventListener('submit', () => $('[data-submit]').classList.add('loading'));
  $$('[data-address]', form).forEach((chip) => chip.addEventListener('click', () => {
    const set = (id, v) => { const el = form.querySelector(id); if (el) el.value = v || ''; };
    set('#fn', chip.dataset.name); set('#ph', chip.dataset.phone); set('#l1', chip.dataset.line1);
    set('#ar', chip.dataset.area); set('#ct', chip.dataset.city); set('#pc', chip.dataset.postcode);
    const z = form.querySelector(`input[data-zone][value="${chip.dataset.zone}"]`);
    if (z) z.checked = true;
    update();
  }));
  update();
})();
