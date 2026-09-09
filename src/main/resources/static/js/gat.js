// Front-end helpers for the GAT 2027 Spring Boot app (Thymeleaf pages).
const GAT = (() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const api = async (url) => { const r = await fetch(url); if (!r.ok) throw new Error("Request failed"); return r.json(); };
  const csrf = () => ({ token: $('meta[name="_csrf"]')?.content, header: $('meta[name="_csrf_header"]')?.content });

  function fillSelect(sel, rows, placeholder, labelFn) {
    sel.innerHTML = `<option value="">${placeholder}</option>` + rows.map((r) => `<option value="${r.id}">${esc(labelFn ? labelFn(r) : r.name)}</option>`).join("");
    sel.disabled = false;
  }
  function resetSelect(sel, placeholder) { sel.innerHTML = `<option value="">${placeholder}</option>`; sel.disabled = true; }
  const label = (o) => (o.code ? o.code + " - " + o.name : o.name);
  const tick = (ms = 250) => new Promise((r) => setTimeout(r, ms));

  // Cascading zone -> state -> LGA -> ward -> polling unit picker (selects by id).
  function locationPicker(ids, opts = {}) {
    const el = {}; for (const k in ids) el[k] = document.getElementById(ids[k]);
    const allowNew = opts.allowNew !== false;
    let pus = [];
    const hideNew = () => { el.newWard?.classList.add("hidden"); el.newPu?.classList.add("hidden"); };
    async function loadZones() { fillSelect(el.zone, await api("/api/locations/zones"), "Select geopolitical zone"); }
    el.zone.addEventListener("change", async () => {
      resetSelect(el.state, "Select state"); resetSelect(el.lga, "Select LGA"); resetSelect(el.ward, "Select ward"); resetSelect(el.pu, "Select polling unit"); hideNew();
      if (el.zone.value) fillSelect(el.state, await api("/api/locations/states?zone_id=" + el.zone.value), "Select state");
    });
    el.state.addEventListener("change", async () => {
      resetSelect(el.lga, "Select LGA"); resetSelect(el.ward, "Select ward"); resetSelect(el.pu, "Select polling unit"); hideNew();
      if (el.state.value) fillSelect(el.lga, await api("/api/locations/lgas?state_id=" + el.state.value), "Select Local Government Area");
    });
    el.lga.addEventListener("change", async () => {
      resetSelect(el.ward, "Select ward"); resetSelect(el.pu, "Select polling unit"); hideNew();
      if (!el.lga.value) return;
      const wards = await api("/api/locations/wards?lga_id=" + el.lga.value);
      fillSelect(el.ward, wards, wards.length ? "Select ward" : "No wards listed yet", label);
      if (allowNew) el.ward.insertAdjacentHTML("beforeend", '<option value="__new">+ My ward is not listed (type it)</option>');
    });
    el.ward.addEventListener("change", async () => {
      resetSelect(el.pu, "Select polling unit");
      el.newWard?.classList.toggle("hidden", el.ward.value !== "__new"); el.newPu?.classList.add("hidden");
      if (!el.ward.value) return;
      if (el.ward.value === "__new") { el.pu.innerHTML = '<option value="__new">+ Type my polling unit</option>'; el.pu.disabled = false; el.pu.value = "__new"; el.newPu?.classList.remove("hidden"); opts.onPU?.(null); return; }
      pus = await api("/api/locations/polling-units?ward_id=" + el.ward.value);
      fillSelect(el.pu, pus, pus.length ? "Select polling unit" : "No polling units listed yet", label);
      if (allowNew) el.pu.insertAdjacentHTML("beforeend", '<option value="__new">+ My polling unit is not listed (type it)</option>');
    });
    el.pu.addEventListener("change", () => { el.newPu?.classList.toggle("hidden", el.pu.value !== "__new"); opts.onPU?.(pus.find((p) => String(p.id) === el.pu.value) || null); });
    async function preset(v) {
      await loadZones(); if (!v.zone) return;
      el.zone.value = v.zone; el.zone.dispatchEvent(new Event("change")); await tick();
      if (!v.state) return; el.state.value = v.state; el.state.dispatchEvent(new Event("change")); await tick();
      if (!v.lga) return; el.lga.value = v.lga; el.lga.dispatchEvent(new Event("change")); await tick();
      if (!v.ward) return; el.ward.value = v.ward; el.ward.dispatchEvent(new Event("change")); await tick(400);
      if (v.pu) { el.pu.value = v.pu; el.newPu?.classList.toggle("hidden", v.pu !== "__new"); }
    }
    const text = (s) => (s.selectedIndex > 0 ? s.options[s.selectedIndex].text : "");
    return { loadZones, preset, el, labels: () => ({ zone: text(el.zone), state: text(el.state), lga: text(el.lga), ward: el.ward.value === "__new" ? $("[name=newWardName]")?.value : text(el.ward), pu: el.pu.value === "__new" ? $("[name=newPuName]")?.value : text(el.pu) }) };
  }

  // Leaflet map with a draggable pin bound to latitude / longitude inputs.
  function coordPicker(mapId, latInput, lngInput, opts = {}) {
    const box = document.getElementById(mapId);
    if (!box) return null;
    if (typeof L === "undefined") { box.innerHTML = '<p class="muted" style="padding:1rem">Map unavailable offline. Enter latitude and longitude manually or use "Use my location".</p>'; return null; }
    const map = L.map(mapId).setView([9.082, 8.675], 6);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 19, attribution: "&copy; OpenStreetMap" }).addTo(map);
    let marker = null;
    function set(lat, lng, zoom) {
      lat = Number(lat); lng = Number(lng); if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;
      if (!marker) { marker = L.marker([lat, lng], { draggable: true }).addTo(map); marker.on("dragend", () => { const p = marker.getLatLng(); latInput.value = p.lat.toFixed(6); lngInput.value = p.lng.toFixed(6); }); }
      else marker.setLatLng([lat, lng]);
      latInput.value = lat.toFixed(6); lngInput.value = lng.toFixed(6);
      map.setView([lat, lng], zoom || Math.max(map.getZoom(), 15));
    }
    map.on("click", (e) => set(e.latlng.lat, e.latlng.lng, map.getZoom()));
    const sync = () => { if (latInput.value && lngInput.value) set(latInput.value, lngInput.value, map.getZoom()); };
    latInput.addEventListener("change", sync); lngInput.addEventListener("change", sync);
    if (latInput.value && lngInput.value) set(latInput.value, lngInput.value, 15);
    opts.locateBtn?.addEventListener("click", () => {
      if (!navigator.geolocation) return alert("Geolocation is not supported on this device");
      opts.locateBtn.disabled = true; opts.locateBtn.textContent = "Locating…";
      const done = () => { opts.locateBtn.disabled = false; opts.locateBtn.textContent = "📍 Use my current location"; };
      navigator.geolocation.getCurrentPosition((p) => { set(p.coords.latitude, p.coords.longitude, 17); done(); }, (err) => { alert("Could not get your location: " + err.message + ". You can click the map or type the coordinates instead."); done(); }, { enableHighAccuracy: true, timeout: 15000 });
    });
    setTimeout(() => map.invalidateSize(), 200);
    return { map, set };
  }

  // Resize a chosen image and put it into a hidden input as a data URL.
  function photoInput(fileInput, hiddenInput, preview) {
    fileInput?.addEventListener("change", (e) => {
      const f = e.target.files[0]; if (!f) return;
      const reader = new FileReader();
      reader.onload = () => { const img = new Image(); img.onload = () => {
        const scale = Math.min(1, 480 / Math.max(img.width, img.height));
        const c = document.createElement("canvas"); c.width = Math.round(img.width * scale); c.height = Math.round(img.height * scale);
        c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
        const url = c.toDataURL("image/jpeg", 0.82); hiddenInput.value = url; if (preview) { preview.src = url; preview.classList.remove("hidden"); }
      }; img.src = reader.result; };
      reader.readAsDataURL(f);
    });
  }

  // Referral share boxes (fragments/share.html)
  function shareBoxes() {
    $$(".refbox-wrap").forEach((w) => {
      const code = w.dataset.code, link = `${location.origin}/register?ref=${code}`;
      const text = `Join me in Grassroot Advocacy for Tinubu (GAT) 2027 — Forward Together with PBAT! Register here: ${link}`;
      $(".ref-link", w).value = link;
      $(".ref-wa", w).href = "https://wa.me/?text=" + encodeURIComponent(text);
      $(".ref-sms", w).href = "sms:?&body=" + encodeURIComponent(text);
      $(".ref-copy", w).addEventListener("click", (e) => navigator.clipboard.writeText(link).then(() => { e.target.textContent = "Copied!"; setTimeout(() => (e.target.textContent = "Copy"), 1500); }));
    });
  }

  async function registerPage() {
    const form = $("#regForm");
    const coords = coordPicker("map", $("#lat"), $("#lng"), { locateBtn: $("#locateBtn") });
    const picker = locationPicker({ zone: "zone", state: "state", lga: "lga", ward: "ward", pu: "pu", newWard: "newWard", newPu: "newPu" }, {
      onPU(pu) { const note = $("#puCoordNote"); if (pu && pu.latitude && pu.longitude) { coords?.set(pu.latitude, pu.longitude, 16); note.textContent = "Known location loaded for this polling unit. Adjust the pin if it is not exact."; } else note.textContent = pu ? "No GPS location recorded yet for this unit — please add it." : ""; },
    });
    const d = form.dataset;
    await picker.preset({ zone: d.zone, state: d.state, lga: d.lga, ward: d.ward, pu: d.pu });
    photoInput($("#photo"), $("[name=photo]"), $("#photoPreview"));

    const refInput = $("#referral_code"), refHelp = $("#refHelp");
    const checkRef = async () => { const v = refInput.value.trim().toUpperCase(); refInput.value = v; if (!v) { refHelp.textContent = "Leave blank if nobody referred you."; refHelp.style.color = ""; return; }
      try { const r = await api("/api/referral/" + encodeURIComponent(v)); refHelp.textContent = `✔ Referred by ${r.name} (${r.memberCode})`; refHelp.style.color = "var(--green-dark)"; } catch { refHelp.textContent = "✖ Referral code not found"; refHelp.style.color = "var(--red)"; } };
    refInput.addEventListener("change", checkRef); if (refInput.value) checkRef();

    const msg = $("#msg");
    function show(step) {
      $$("[data-panel]").forEach((p) => p.classList.toggle("hidden", p.dataset.panel !== String(step)));
      $$(".steps .s").forEach((s) => { s.classList.toggle("active", s.dataset.step === String(step)); s.classList.toggle("done", Number(s.dataset.step) < step); });
      window.scrollTo({ top: 0, behavior: "smooth" });
      if (step === 3) review();
    }
    function valid(step) {
      const panel = $(`[data-panel="${step}"]`);
      for (const el of panel.querySelectorAll("input, select")) if (!el.checkValidity()) { el.reportValidity(); return false; }
      if (step === 1) {
        if (!picker.el.ward.value || (picker.el.ward.value === "__new" && !$("[name=newWardName]").value)) { msg.textContent = "Select your ward, or type it if it is not listed."; msg.classList.remove("hidden"); return false; }
        if (!picker.el.pu.value || (picker.el.pu.value === "__new" && !$("[name=newPuName]").value)) { msg.textContent = "Select your polling unit, or type it if it is not listed."; msg.classList.remove("hidden"); return false; }
      }
      msg.classList.add("hidden"); return true;
    }
    $$("[data-next]").forEach((b) => b.addEventListener("click", () => { const to = Number(b.dataset.next), cur = Number($(".steps .s.active").dataset.step); if (to < cur || valid(cur)) show(to); }));
    function review() {
      const l = picker.labels(), f = form;
      const rows = [["Name", `${f.firstName.value} ${f.otherName.value} ${f.lastName.value}`], ["Phone", f.phone.value], ["Gender", f.gender.value], ["Zone", l.zone], ["State", l.state], ["LGA", l.lga], ["Ward", l.ward], ["Polling unit", l.pu], ["PU location", f.puLatitude.value ? `${f.puLatitude.value}, ${f.puLongitude.value}` : "not set"]];
      $("#review").innerHTML = rows.map(([k, v]) => `<dt>${k}</dt><dd>${esc(v || "—")}</dd>`).join("");
    }
    form.addEventListener("submit", (e) => { if (form.password.value !== form.password2.value) { e.preventDefault(); msg.textContent = "Passwords do not match"; msg.classList.remove("hidden"); return; } $("#submitBtn").disabled = true; $("#submitBtn").textContent = "Registering…"; });
    // If the server re-rendered with an error, jump to the step that has the problem.
    if (!msg.classList.contains("hidden")) { const t = msg.textContent.toLowerCase(); show(/password|referral|consent/.test(t) ? 3 : /ward|polling|zone|state|lga|latitude|longitude/.test(t) ? 1 : 2); }
  }

  async function profilePage() {
    const form = $("#profileForm");
    const coords = coordPicker("editMap", $("#elat"), $("#elng"), { locateBtn: $("#elocate") });
    const picker = locationPicker({ zone: "ezone", state: "estate", lga: "elga", ward: "eward", pu: "epu", newWard: "enewWard", newPu: "enewPu" }, { onPU(pu) { if (pu && pu.latitude && coords && !$("#elat").value) coords.set(pu.latitude, pu.longitude, 16); } });
    const d = form.dataset;
    await picker.preset({ zone: d.zone, state: d.state, lga: d.lga, ward: d.ward, pu: d.pu });
    photoInput($("#ephoto"), $("[name=photo]"), $("#ephotoPreview"));
  }

  function reportPage() {
    const f = $("form[action='/report']") || $("main form");
    $("#attachLoc")?.addEventListener("change", (e) => {
      const st = $("#locStatus");
      if (!e.target.checked) { f.latitude.value = ""; f.longitude.value = ""; st.textContent = ""; return; }
      st.textContent = "Getting location…";
      navigator.geolocation.getCurrentPosition((p) => { f.latitude.value = p.coords.latitude; f.longitude.value = p.coords.longitude; st.textContent = `Location attached (${p.coords.latitude.toFixed(5)}, ${p.coords.longitude.toFixed(5)})`; },
        (err) => { st.textContent = "Could not get location: " + err.message; e.target.checked = false; }, { enableHighAccuracy: true, timeout: 15000 });
    });
  }

  // Read-only map with one pin or many circle markers (data from a JSON script tag).
  function pointMap(mapId, points, opts = {}) {
    const box = document.getElementById(mapId); if (!box || typeof L === "undefined") return;
    const map = L.map(mapId, { scrollWheelZoom: points.length > 1 }).setView([9.082, 8.675], 6);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 19, attribution: "&copy; OpenStreetMap" }).addTo(map);
    const layer = L.layerGroup(points.map((p) => (opts.pin
      ? L.marker([p.lat, p.lng]).bindPopup(`<b>${esc(p.label)}</b>${p.sub ? "<br>" + esc(p.sub) : ""}`)
      : L.circleMarker([p.lat, p.lng], { radius: Math.min(22, 5 + Math.sqrt(p.members || 1) * 2.5), color: "#085c2c", fillColor: "#0b7a3b", fillOpacity: 0.55, weight: 1 })
          .bindPopup(`<b>${esc(p.label)}</b>${p.sub ? "<br>" + esc(p.sub) : ""}${p.members !== undefined ? `<br><b>${p.members}</b> member(s)` : ""}`)))).addTo(map);
    if (points.length === 1) { map.setView([points[0].lat, points[0].lng], 16); layer.getLayers()[0].openPopup?.(); }
    else if (points.length > 1) map.fitBounds(L.latLngBounds(points.map((p) => [p.lat, p.lng])).pad(0.2));
    setTimeout(() => map.invalidateSize(), 200);
  }

  document.addEventListener("DOMContentLoaded", shareBoxes);
  return { $, $$, esc, api, csrf, locationPicker, coordPicker, photoInput, registerPage, profilePage, reportPage, pointMap, shareBoxes };
})();
