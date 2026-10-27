// AgentLedger dashboard. Every value interpolated into HTML goes through the `html`
// tagged template, which escapes it unless explicitly wrapped with `raw()`.
(function () {
  'use strict';

  const $ = (selector, scope = document) => scope.querySelector(selector);
  const $$ = (selector, scope = document) => Array.from(scope.querySelectorAll(selector));

  // ---------- safe HTML ----------
  const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;', '`': '&#96;' };
  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"'`]/g, (c) => ESCAPES[c]);

  class Raw {
    constructor(value) { this.value = value; }
    toString() { return this.value; }
  }
  const raw = (value) => new Raw(value);
  const render = (value) =>
    value instanceof Raw ? value.value : Array.isArray(value) ? value.map(render).join('') : escapeHtml(value);
  const html = (strings, ...values) =>
    raw(strings.reduce((out, s, i) => out + s + (i < values.length ? render(values[i]) : ''), ''));

  // ---------- formatting ----------
  const RISKS = ['low', 'medium', 'high', 'critical'];
  const STATUSES = ['pending', 'approved', 'rejected', 'executed', 'blocked'];
  const known = (value, allowed) => (allowed.includes(value) ? value : 'unknown');

  const parseDate = (value) => {
    if (!value) return null;
    // Older API versions returned "YYYY-MM-DD HH:MM:SS" in UTC without a zone.
    const iso = /[zZ]|[+-]\d\d:?\d\d$/.test(value) ? value : value.replace(' ', 'T') + 'Z';
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? null : date;
  };
  const shortTime = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
  const longTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' });
  const timeTag = (value, formatter) => {
    const date = parseDate(value);
    return date ? html`<time datetime="${date.toISOString()}">${formatter.format(date)}</time>` : '—';
  };

  // ---------- API ----------
  class ApiError extends Error {
    constructor(message, status) { super(message); this.status = status; }
  }

  async function api(url, options = {}) {
    let response;
    try {
      response = await fetch(url, {
        ...options,
        headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}) },
      });
    } catch {
      throw new ApiError('Network error. Check your connection and try again.', 0);
    }
    let data = null;
    const type = response.headers.get('content-type') || '';
    if (type.includes('json')) {
      try { data = await response.json(); } catch { data = null; }
    }
    if (!response.ok) {
      const message = (data && (data.detail || data.error)) ||
        (response.status === 429 ? 'Too many requests. Wait a moment and try again.' : `Request failed (${response.status}).`);
      throw new ApiError(message, response.status);
    }
    return data;
  }

  // ---------- UI helpers ----------
  let toastTimer;
  function toast(message, tone = 'info') {
    const el = $('#toast');
    el.textContent = message;
    el.dataset.tone = tone;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 5000);
  }

  function setFormError(form, message) {
    const el = $('.form-error', form);
    if (!el) return;
    el.textContent = message || '';
    el.hidden = !message;
  }

  /** Disables the submit button while a request is in flight; returns false if already busy. */
  function beginBusy(form, label) {
    if (form.dataset.busy === 'true') return false;
    form.dataset.busy = 'true';
    form.setAttribute('aria-busy', 'true');
    const button = $('button[type="submit"]', form);
    button.dataset.label = button.textContent;
    button.textContent = label;
    button.disabled = true;
    return true;
  }

  function endBusy(form) {
    form.dataset.busy = 'false';
    form.removeAttribute('aria-busy');
    const button = $('button[type="submit"]', form);
    button.textContent = button.dataset.label || button.textContent;
    button.disabled = false;
  }

  function openDialog(dialog) {
    setFormError(dialog, '');
    if (!dialog.open) dialog.showModal();
  }

  $$('dialog').forEach((dialog) => {
    $$('[data-close]', dialog).forEach((button) => button.addEventListener('click', () => dialog.close()));
    // Close when the backdrop (the dialog element itself) is clicked.
    dialog.addEventListener('click', (event) => { if (event.target === dialog) dialog.close(); });
  });

  // ---------- state ----------
  let actions = [];
  let loadError = null;
  let loading = true;

  async function refresh() {
    const rows = $('#rows');
    rows.setAttribute('aria-busy', 'true');
    try {
      const [list, summary] = await Promise.all([api('/api/actions'), api('/api/summary')]);
      actions = Array.isArray(list) ? list : [];
      loadError = null;
      for (const key of ['total', 'pending', 'executed', 'blocked']) {
        $('#' + key).textContent = String(Number(summary?.[key]) || 0);
      }
    } catch (error) {
      loadError = error.message;
    } finally {
      loading = false;
      rows.setAttribute('aria-busy', 'false');
      draw();
      verifyChain();
    }
  }

  async function verifyChain() {
    const el = $('#integrity');
    try {
      const result = await api('/api/audit/verify');
      if (result.valid) {
        el.dataset.state = 'ok';
        el.textContent = `Audit chain verified · ${result.checked} entries`;
        el.title = `Head hash ${result.head_hash}`;
      } else {
        el.dataset.state = 'broken';
        el.textContent = `Audit chain broken at entry ${result.broken_at}`;
        el.title = result.reason || '';
      }
    } catch {
      el.dataset.state = 'unknown';
      el.textContent = 'Audit chain could not be verified';
      el.removeAttribute('title');
    }
  }

  function rowTemplate(x) {
    const risk = known(x.risk, RISKS);
    const status = known(x.status, STATUSES);
    const pending = status === 'pending';
    return html`
      <article class="row" aria-labelledby="row-${x.id}">
        <div class="cell cell-intent">
          <h3 class="agent" id="row-${x.id}">${x.agent}</h3>
          <p class="intent">${x.action} <span aria-hidden="true">·</span> ${x.reason}</p>
        </div>
        <div class="cell cell-target"><span class="cell-label">Target</span><code class="target">${x.target}</code></div>
        <div class="cell cell-risk"><span class="cell-label">Risk</span><span class="badge risk-${risk}">${risk}</span></div>
        <div class="cell cell-status"><span class="cell-label">Status</span><span class="badge st-${status}">${status}</span></div>
        <div class="cell cell-time"><span class="cell-label">Time</span><span class="time">${timeTag(x.created_at, shortTime)}</span></div>
        <div class="cell actions">
          <button type="button" class="button button-ghost button-small" data-view="${x.id}" aria-label="View audit trail for ${x.action}">Trail</button>
          ${pending ? html`
            <button type="button" class="button button-approve button-small" data-review="approve" data-id="${x.id}" aria-label="Approve ${x.action}"><span aria-hidden="true">✓</span></button>
            <button type="button" class="button button-deny button-small" data-review="deny" data-id="${x.id}" aria-label="Deny ${x.action}"><span aria-hidden="true">×</span></button>` : ''}
        </div>
      </article>`;
  }

  function draw() {
    const rows = $('#rows');
    if (loading) {
      rows.innerHTML = String(html`<p class="state">Loading actions…</p>`);
      return;
    }
    if (loadError) {
      rows.innerHTML = String(html`
        <div class="state state-error" role="alert">
          <p>Could not load actions. ${loadError}</p>
          <button type="button" class="button button-secondary button-small" data-retry>Try again</button>
        </div>`);
      return;
    }
    const filter = $('#filter').value;
    const visible = actions.filter((x) => filter === 'all' || x.status === filter);
    rows.innerHTML = String(visible.length
      ? html`${visible.map(rowTemplate)}`
      : html`<p class="state">${actions.length ? 'No actions in this view.' : 'No actions yet. Propose one to see the policy engine at work.'}</p>`);
  }

  // Event delegation: one listener survives every re-render.
  $('#rows').addEventListener('click', (event) => {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.hasAttribute('data-retry')) { loading = true; draw(); refresh(); return; }
    if (button.dataset.view) { trail(Number(button.dataset.view), button); return; }
    if (button.dataset.review) openReview(Number(button.dataset.id), button.dataset.review);
  });
  $('#filter').addEventListener('change', draw);

  // ---------- review ----------
  const reviewForm = $('#review-form');
  let reviewTarget = null;

  function openReview(id, decision) {
    const action = actions.find((a) => a.id === id);
    if (!action) return;
    reviewTarget = { id, decision };
    const approve = decision === 'approve';
    $('#review-title').textContent = approve ? 'Approve action' : 'Deny action';
    $('#review-subject').textContent = `${action.action} · ${action.target} · requested by ${action.agent}`;
    $('#review-note-label').textContent = approve ? 'Approval note (optional)' : 'Why is this action being denied? (optional)';
    const submit = $('#review-submit');
    submit.textContent = approve ? 'Approve' : 'Deny';
    submit.classList.toggle('button-danger', !approve);
    reviewForm.elements.note.value = '';
    openDialog($('#review-modal'));
  }

  reviewForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!reviewTarget || !beginBusy(reviewForm, 'Saving…')) return;
    setFormError(reviewForm, '');
    const { id, decision } = reviewTarget;
    try {
      await api(`/api/actions/${encodeURIComponent(id)}/review`, {
        method: 'POST',
        body: JSON.stringify({
          decision,
          reviewer: reviewForm.elements.reviewer.value.trim() || undefined,
          note: reviewForm.elements.note.value.trim() || undefined,
        }),
      });
      $('#review-modal').close();
      toast(decision === 'approve' ? 'Action approved and recorded.' : 'Action denied and recorded.', 'success');
      await refresh();
    } catch (error) {
      setFormError(reviewForm, error.message);
      if (error.status === 409) refresh();
    } finally {
      endBusy(reviewForm);
    }
  });

  // ---------- audit trail ----------
  async function trail(id, button) {
    const action = actions.find((a) => a.id === id);
    if (!action || button.disabled) return;
    button.disabled = true;
    const list = $('#event-list');
    $('#event-title').textContent = `${action.action} · ${action.agent}`;
    list.innerHTML = String(html`<p class="state">Loading history…</p>`);
    openDialog($('#events'));
    try {
      const events = await api(`/api/actions/${encodeURIComponent(id)}/events`);
      list.innerHTML = String(events.length
        ? html`<ol class="timeline">${events.map((e) => html`
            <li class="event">
              <p class="event-head"><span class="event-name">${e.event}</span> <span class="event-actor">by ${e.actor}</span></p>
              <p class="event-detail">${e.detail}</p>
              <p class="event-meta">${timeTag(e.created_at, longTime)}${e.hash ? html` <span aria-hidden="true">·</span> <code class="hash" title="${e.hash}">#${e.id} ${String(e.hash).slice(0, 12)}…</code>` : ''}</p>
            </li>`)}</ol>
          ${action.policy_detail ? html`<p class="policy-note">${action.policy_detail}</p>` : ''}`
        : html`<p class="state">No events recorded.</p>`);
    } catch (error) {
      list.innerHTML = String(html`<p class="state state-error" role="alert">Could not load history. ${error.message}</p>`);
    } finally {
      button.disabled = false;
    }
  }

  // ---------- proposals ----------
  const proposalForm = $('#form');
  $('#propose').addEventListener('click', () => {
    openDialog($('#modal'));
    proposalForm.elements.agent.focus();
  });

  proposalForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(proposalForm));
    const missing = ['agent', 'action', 'target', 'reason'].filter((k) => !String(data[k] || '').trim());
    if (missing.length) {
      setFormError(proposalForm, 'Fill in every field before submitting.');
      proposalForm.elements[missing[0]].focus();
      return;
    }
    if (!beginBusy(proposalForm, 'Evaluating…')) return;
    setFormError(proposalForm, '');
    try {
      const created = await api('/api/actions', { method: 'POST', body: JSON.stringify(data) });
      proposalForm.reset();
      $('#modal').close();
      const messages = {
        blocked: ['Policy blocked this critical action. The attempt is recorded in the audit trail.', 'danger'],
        pending: [`Recorded as ${created.risk} risk. Waiting for a human decision.`, 'info'],
        executed: ['Low risk: approved and simulated automatically.', 'success'],
      };
      const [message, tone] = messages[created.status] || ['Action recorded.', 'info'];
      toast(message, tone);
      $('#filter').value = 'all';
      await refresh();
    } catch (error) {
      setFormError(proposalForm, error.message);
    } finally {
      endBusy(proposalForm);
    }
  });

  // ---------- policy ----------
  const policyForm = $('#policy-form');
  const splitTerms = (value) => String(value || '').split(',').map((x) => x.trim()).filter(Boolean);

  $('#policy-settings').addEventListener('click', async (event) => {
    const button = event.currentTarget;
    if (button.disabled) return;
    button.disabled = true;
    try {
      const policy = await api('/api/policies');
      policyForm.elements.medium.value = policy.medium.join(', ');
      policyForm.elements.high.value = policy.high.join(', ');
      if (Array.isArray(policy.critical)) $('#critical-terms').textContent = policy.critical.join(', ');
      openDialog($('#policy-modal'));
    } catch (error) {
      toast(`Could not load the policy. ${error.message}`, 'danger');
    } finally {
      button.disabled = false;
    }
  });

  policyForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!beginBusy(policyForm, 'Saving…')) return;
    setFormError(policyForm, '');
    try {
      await api('/api/policies', {
        method: 'PUT',
        body: JSON.stringify({
          medium: splitTerms(policyForm.elements.medium.value),
          high: splitTerms(policyForm.elements.high.value),
        }),
      });
      $('#policy-modal').close();
      toast('Policy saved. New proposals use the updated terms.', 'success');
      verifyChain();
    } catch (error) {
      setFormError(policyForm, error.message);
    } finally {
      endBusy(policyForm);
    }
  });

  draw();
  refresh();
})();
