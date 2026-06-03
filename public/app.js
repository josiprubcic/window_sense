'use strict';

const dom = {
  topbar: document.querySelector('.topbar'),
  appNav: document.querySelector('#appNav'),
  loginView: document.querySelector('#loginView'),
  dashboardView: document.querySelector('#dashboardView'),
  roomsView: document.querySelector('#roomsView'),
  siteArea: document.querySelector('#siteArea'),
  siteName: document.querySelector('#siteName'),
  automationMode: document.querySelector('#automationMode'),
  iotStatus: document.querySelector('#iotStatus'),
  lastUpdated: document.querySelector('#lastUpdated'),
  rainValue: document.querySelector('#rainValue'),
  rainDetail: document.querySelector('#rainDetail'),
  lightValue: document.querySelector('#lightValue'),
  lightDetail: document.querySelector('#lightDetail'),
  windowValue: document.querySelector('#windowValue'),
  windowDetail: document.querySelector('#windowDetail'),
  blindsValue: document.querySelector('#blindsValue'),
  blindsDetail: document.querySelector('#blindsDetail'),
  weatherLine: document.querySelector('#weatherLine'),
  summaryWindowPercent: document.querySelector('#summaryWindowPercent'),
  summaryWindowOpen: document.querySelector('#summaryWindowOpen'),
  summaryBlindsPercent: document.querySelector('#summaryBlindsPercent'),
  summaryBlindsDown: document.querySelector('#summaryBlindsDown'),
  summaryRain: document.querySelector('#summaryRain'),
  summaryWind: document.querySelector('#summaryWind'),
  autoModeButton: document.querySelector('#autoModeButton'),
  manualModeButton: document.querySelector('#manualModeButton'),
  windowSlider: document.querySelector('#windowSlider'),
  windowSliderValue: document.querySelector('#windowSliderValue'),
  blindsSlider: document.querySelector('#blindsSlider'),
  blindsSliderValue: document.querySelector('#blindsSliderValue'),
  rainToggle: document.querySelector('#rainToggle'),
  luxInput: document.querySelector('#luxInput'),
  luxInputValue: document.querySelector('#luxInputValue'),
  rainProbabilityInput: document.querySelector('#rainProbabilityInput'),
  rainProbabilityValue: document.querySelector('#rainProbabilityValue'),
  windInput: document.querySelector('#windInput'),
  windInputValue: document.querySelector('#windInputValue'),
  temperatureInput: document.querySelector('#temperatureInput'),
  temperatureInputValue: document.querySelector('#temperatureInputValue'),
  tempValue: document.querySelector('#tempValue'),
  tempDetail: document.querySelector('#tempDetail'),
  applyTelemetryButton: document.querySelector('#applyTelemetryButton'),
  saveThresholdsButton: document.querySelector('#saveThresholdsButton'),
  thresholdRain: document.querySelector('#thresholdRain'),
  thresholdLux: document.querySelector('#thresholdLux'),
  thresholdTemp: document.querySelector('#thresholdTemp'),
  thresholdWind: document.querySelector('#thresholdWind'),
  iotPlatform: document.querySelector('#iotPlatform'),
  deviceId: document.querySelector('#deviceId'),
  lastSync: document.querySelector('#lastSync'),
  iotError: document.querySelector('#iotError'),
  eventList: document.querySelector('#eventList'),
  toast: document.querySelector('#toast'),
  loginLink: document.querySelector('#loginLink'),
  loginCta: document.querySelector('#loginCta'),
  userName: document.querySelector('#userName'),
  logoutLink: document.querySelector('#logoutLink'),
  roomNameInput: document.querySelector('#roomNameInput'),
  addRoomButton: document.querySelector('#addRoomButton'),
  roomsMessage: document.querySelector('#roomsMessage'),
  roomsList: document.querySelector('#roomsList')
};

let currentState = null;
let toastTimer = null;
let editingRoomId = null;
let currentUser = null;
let streamStarted = false;
let roomsLoaded = false;
let roomsCache = [];
let roomTelemetry = new Map();
let roomTelemetryPollTimer = null;
let connectingPhysicalRoomId = null;
let physicalConnectDeveloperMode = false;

function formatPercent(value) {
  return `${Math.round(Number(value) || 0)}%`;
}

function formatLux(value) {
  const number = Number(value) || 0;
  if (number >= 1000) {
    return `${Math.round(number / 1000)}k lx`;
  }

  return `${Math.round(number)} lx`;
}

function formatTemp(value) {
  return `${Math.round((Number(value) || 0) * 10) / 10} °C`;
}

function formatDate(value) {
  if (!value) {
    return '--';
  }

  return new Intl.DateTimeFormat('hr-HR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(new Date(value));
}

function showToast(message) {
  dom.toast.textContent = message;
  dom.toast.classList.add('is-visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => dom.toast.classList.remove('is-visible'), 2600);
}

function showRoomsMessage(message, level = 'info') {
  dom.roomsMessage.textContent = message;
  dom.roomsMessage.dataset.level = level;
}

function setVisible(element, visible) {
  if (!element) {
    return;
  }
  element.hidden = !visible;
  element.classList.toggle('is-hidden', !visible);
}

function renderUser(user) {
  if (!user.authenticated) {
    setVisible(dom.loginLink, user.oidcEnabled);
    setVisible(dom.loginCta, user.oidcEnabled);
    setVisible(dom.userName, false);
    setVisible(dom.logoutLink, false);
    return;
  }

  setVisible(dom.loginLink, false);
  setVisible(dom.loginCta, false);
  dom.userName.textContent = user.email || user.name;
  setVisible(dom.userName, true);
  setVisible(dom.logoutLink, true);
}

function routeName() {
  const hash = window.location.hash.replace(/^#\/?/, '');
  return hash === 'rooms' ? 'rooms' : hash === 'dashboard' ? 'dashboard' : '';
}

function setActiveRoute(route) {
  document.querySelectorAll('[data-route-link]').forEach((link) => {
    link.classList.toggle('is-active', link.dataset.routeLink === route);
  });
}

async function ensureDashboardStarted() {
  if (!currentState) {
    render(await api('/api/state'));
  }

  if (!streamStarted) {
    streamStarted = true;
    startStream();
  }
}

async function showRoute() {
  if (!currentUser) {
    return;
  }

  const authRequired = currentUser.oidcEnabled && !currentUser.authenticated;
  if (authRequired) {
    setVisible(dom.topbar, false);
    setVisible(dom.loginView, true);
    setVisible(dom.dashboardView, false);
    setVisible(dom.roomsView, false);
    setVisible(dom.appNav, false);
    return;
  }

  setVisible(dom.topbar, true);
  setVisible(dom.appNav, !authRequired);
  setVisible(dom.loginView, false);

  let route = routeName();
  if (!route) {
    window.location.hash = '#/dashboard';
    route = 'dashboard';
  }

  setActiveRoute(route);
  setVisible(dom.dashboardView, route === 'dashboard');
  setVisible(dom.roomsView, route === 'rooms');

  if (route !== 'rooms') {
    stopRoomsTelemetryPolling();
  }

  if (route === 'dashboard') {
    await ensureDashboardStarted();
  }

  if (route === 'rooms') {
    if (!roomsLoaded) {
      await loadRooms();
    } else if (roomsCache.length > 0) {
      await loadTelemetryForRooms();
    }
    startRoomsTelemetryPolling();
  }
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: {
      'content-type': 'application/json'
    },
    ...options
  });

  const data = response.status === 204 ? null : await response.json();
  if (!response.ok) {
    const validationMessage = typeof data === 'object' && data !== null
      ? Object.values(data).find((value) => typeof value === 'string')
      : null;
    throw new Error(data.error || validationMessage || 'API zahtjev nije uspio.');
  }

  return data;
}

function shortId(value) {
  return value ? value.slice(0, 8) : '--';
}

function roomErrorMessage(error) {
  return error.message.includes('Soba s tim nazivom')
    ? 'Soba s tim nazivom već postoji.'
    : error.message;
}

function hasActivePhysicalDevice(room) {
  return (room.devices || []).some((device) => device.deviceType === 'PHYSICAL' && device.status === 'ACTIVE');
}

function telemetryValue(telemetry, key, formatter = (value) => value) {
  if (!telemetry || telemetry[key] === undefined || telemetry[key] === null || telemetry[key] === '') {
    return '--';
  }
  return formatter(telemetry[key]);
}

function renderRoomTelemetry(room) {
  const telemetryResponse = roomTelemetry.get(room.id);
  const telemetry = telemetryResponse?.telemetry || {};
  const section = document.createElement('section');
  section.className = 'room-telemetry';

  if (!telemetryResponse || Object.keys(telemetry).length === 0) {
    const empty = document.createElement('p');
    empty.className = 'form-message';
    empty.textContent = telemetryResponse?.message || 'Telemetrija još nije dostupna.';
    section.append(empty);
    return section;
  }

  const rows = [
    ['Kiša', telemetryValue(telemetry, 'rainDetected', (value) => value ? 'Pada' : 'Ne pada')],
    ['Intenzitet kiše', telemetryValue(telemetry, 'rainIntensity', (value) => `${Math.round(Number(value))}`)],
    ['Rizik kiše', telemetryValue(telemetry, 'rainRiskPercent', (value) => formatPercent(value))],
    ['Svjetlo', telemetryValue(telemetry, 'lux', (value) => formatLux(value))],
    ['Temperatura', telemetryValue(telemetry, 'indoorTempC', (value) => formatTemp(value))],
    ['Vjetar', telemetryValue(telemetry, 'windKmh', (value) => `${Math.round(Number(value))} km/h`)],
    ['Prozor', telemetryValue(telemetry, 'windowOpenPercent', (value) => formatPercent(value))],
    ['Roleta', telemetryValue(telemetry, 'blindClosedPercent', (value) => formatPercent(value))]
  ];

  const grid = document.createElement('dl');
  grid.className = 'telemetry-grid';
  for (const [label, value] of rows) {
    const item = document.createElement('div');
    const term = document.createElement('dt');
    term.textContent = label;
    const description = document.createElement('dd');
    description.textContent = value;
    item.append(term, description);
    grid.append(item);
  }

  section.append(grid);
  return section;
}

function renderRooms(rooms) {
  dom.roomsList.innerHTML = '';

  if (rooms.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'form-message';
    empty.textContent = 'Jos nema dodanih soba.';
    dom.roomsList.append(empty);
    return;
  }

  for (const room of rooms) {
    const item = document.createElement('section');
    item.className = 'room-item';

    const header = document.createElement('header');
    const roomMeta = document.createElement('div');
    roomMeta.className = 'room-meta';
    const title = document.createElement('strong');
    title.textContent = room.name;
    const id = document.createElement('small');
    id.textContent = `ID ${shortId(room.id)}`;
    roomMeta.append(title, id);

    const actions = document.createElement('div');
    actions.className = 'room-actions';
    const editButton = document.createElement('button');
    editButton.type = 'button';
    editButton.className = 'button-secondary';
    editButton.textContent = 'Uredi';
    editButton.addEventListener('click', () => {
      editingRoomId = room.id;
      connectingPhysicalRoomId = null;
      renderRooms(rooms);
    });
    const connectButton = document.createElement('button');
    connectButton.type = 'button';
    connectButton.className = 'button-secondary';
    connectButton.textContent = 'Poveži uređaj';
    connectButton.disabled = hasActivePhysicalDevice(room);
    connectButton.addEventListener('click', () => {
      connectingPhysicalRoomId = room.id;
      editingRoomId = null;
      renderRooms(rooms);
    });
    const deleteButton = document.createElement('button');
    deleteButton.type = 'button';
    deleteButton.className = 'button-danger';
    deleteButton.textContent = 'Obriši';
    deleteButton.addEventListener('click', () => deleteRoom(room));
    actions.append(editButton, connectButton, deleteButton);
    header.append(roomMeta, actions);

    if (editingRoomId === room.id) {
      const editForm = document.createElement('div');
      editForm.className = 'room-edit';
      const nameInput = document.createElement('input');
      nameInput.type = 'text';
      nameInput.maxLength = 120;
      nameInput.value = room.name;
      nameInput.setAttribute('aria-label', 'Novi naziv sobe');
      const saveButton = document.createElement('button');
      saveButton.type = 'button';
      saveButton.className = 'button-secondary';
      saveButton.textContent = 'Spremi';
      saveButton.addEventListener('click', () => updateRoom(room.id, nameInput.value));
      const cancelButton = document.createElement('button');
      cancelButton.type = 'button';
      cancelButton.textContent = 'Odustani';
      cancelButton.addEventListener('click', () => {
        editingRoomId = null;
        renderRooms(rooms);
      });
      nameInput.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
          event.preventDefault();
          updateRoom(room.id, nameInput.value);
        }
      });
      editForm.append(nameInput, saveButton, cancelButton);
      item.append(header, editForm);
      setTimeout(() => nameInput.focus(), 0);
    } else if (connectingPhysicalRoomId === room.id) {
      const connectForm = document.createElement('div');
      connectForm.className = 'physical-connect';
      const nameInput = document.createElement('input');
      nameInput.type = 'text';
      nameInput.maxLength = 120;
      nameInput.placeholder = 'ESP32 - Fizički prototip';
      nameInput.setAttribute('aria-label', 'Naziv fizičkog uređaja');
      const pairingCodeInput = document.createElement('input');
      pairingCodeInput.type = 'text';
      pairingCodeInput.maxLength = physicalConnectDeveloperMode ? 128 : 64;
      pairingCodeInput.placeholder = physicalConnectDeveloperMode ? 'ThingsBoard Device ID' : 'Kod za povezivanje';
      pairingCodeInput.setAttribute('aria-label', physicalConnectDeveloperMode ? 'ThingsBoard Device ID' : 'Kod za povezivanje');
      const saveButton = document.createElement('button');
      saveButton.type = 'button';
      saveButton.className = 'button-secondary';
      saveButton.textContent = 'Poveži';
      saveButton.addEventListener('click', () => connectPhysicalDevice(room.id, nameInput.value, pairingCodeInput.value));
      const cancelButton = document.createElement('button');
      cancelButton.type = 'button';
      cancelButton.textContent = 'Odustani';
      cancelButton.addEventListener('click', () => {
        connectingPhysicalRoomId = null;
        renderRooms(rooms);
      });
      const developerButton = document.createElement('button');
      developerButton.type = 'button';
      developerButton.className = 'button-secondary';
      developerButton.textContent = physicalConnectDeveloperMode ? 'Korisnički kod' : 'Developer ID';
      developerButton.addEventListener('click', () => {
        physicalConnectDeveloperMode = !physicalConnectDeveloperMode;
        renderRooms(rooms);
      });
      for (const input of [nameInput, pairingCodeInput]) {
        input.addEventListener('keydown', (event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            connectPhysicalDevice(room.id, nameInput.value, pairingCodeInput.value);
          }
        });
      }
      connectForm.append(nameInput, pairingCodeInput, saveButton, cancelButton, developerButton);
      item.append(header, connectForm);
      setTimeout(() => nameInput.focus(), 0);
    } else {
      item.append(header);
    }

    const devices = document.createElement('div');
    devices.className = 'device-list';
    for (const device of room.devices || []) {
      const deviceRow = document.createElement('div');
      const deviceName = document.createElement('strong');
      deviceName.textContent = device.name;
      const meta = document.createElement('span');
      meta.textContent = `${device.deviceType} / ${device.status} / isVirtual=${device.isVirtual}`;
      deviceRow.append(deviceName, meta);
      devices.append(deviceRow);
    }

    item.append(devices);
    item.append(renderRoomTelemetry(room));
    dom.roomsList.append(item);
  }
}

async function loadRooms(announce = true) {
  const rooms = await api('/api/rooms');
  roomsCache = rooms;
  renderRooms(rooms);
  await loadTelemetryForRooms(rooms);
  roomsLoaded = true;
  if (announce && rooms.length > 0) {
    showRoomsMessage(`${rooms.length} soba ucitano.`);
  }
}

async function loadTelemetryForRooms(rooms = roomsCache) {
  await Promise.all(rooms.map(async (room) => {
    try {
      const telemetry = await api(`/api/rooms/${room.id}/telemetry/latest`);
      roomTelemetry.set(room.id, telemetry);
    } catch (error) {
      roomTelemetry.set(room.id, {
        telemetry: {},
        message: error.message || 'Telemetrija još nije dostupna.'
      });
    }
  }));
  renderRooms(rooms);
}

function startRoomsTelemetryPolling() {
  if (roomTelemetryPollTimer) {
    return;
  }

  roomTelemetryPollTimer = setInterval(() => {
    if (routeName() === 'rooms' && roomsCache.length > 0) {
      loadTelemetryForRooms().catch((error) => showRoomsMessage(error.message, 'error'));
    }
  }, 5000);
}

function stopRoomsTelemetryPolling() {
  if (!roomTelemetryPollTimer) {
    return;
  }

  clearInterval(roomTelemetryPollTimer);
  roomTelemetryPollTimer = null;
}

async function addRoom() {
  const name = dom.roomNameInput.value.trim();
  if (!name) {
    showRoomsMessage('Naziv sobe ne smije biti prazan.', 'error');
    dom.roomNameInput.focus();
    return;
  }

  try {
    await api('/api/rooms', {
      method: 'POST',
      body: JSON.stringify({ name })
    });
    dom.roomNameInput.value = '';
    await loadRooms(false);
    showRoomsMessage('Soba je dodana.');
  } catch (error) {
    showRoomsMessage(roomErrorMessage(error), 'error');
  }
}

async function updateRoom(roomId, rawName) {
  const name = rawName.trim();
  if (!name) {
    showRoomsMessage('Naziv sobe ne smije biti prazan.', 'error');
    return;
  }

  try {
    await api(`/api/rooms/${roomId}`, {
      method: 'PUT',
      body: JSON.stringify({ name })
    });
    editingRoomId = null;
    await loadRooms(false);
    showRoomsMessage('Soba je uspješno ažurirana.');
  } catch (error) {
    showRoomsMessage(roomErrorMessage(error), 'error');
  }
}

async function connectPhysicalDevice(roomId, rawName, rawTbDeviceId) {
  const name = rawName.trim();
  const deviceIdentifier = rawTbDeviceId.trim();
  if (!name || !deviceIdentifier) {
    showRoomsMessage(physicalConnectDeveloperMode
      ? 'Naziv uređaja i ThingsBoard Device ID su obavezni.'
      : 'Naziv uređaja i kod za povezivanje su obavezni.', 'error');
    return;
  }

  try {
    const path = physicalConnectDeveloperMode
      ? `/api/rooms/${roomId}/devices/physical`
      : `/api/rooms/${roomId}/devices/pair`;
    const body = physicalConnectDeveloperMode
      ? { name, tbDeviceId: deviceIdentifier }
      : { name, pairingCode: deviceIdentifier };
    await api(path, {
      method: 'POST',
      body: JSON.stringify(body)
    });
    connectingPhysicalRoomId = null;
    physicalConnectDeveloperMode = false;
    await loadRooms(false);
    showRoomsMessage('Fizički uređaj je povezan.');
  } catch (error) {
    showRoomsMessage(roomErrorMessage(error), 'error');
  }
}

async function deleteRoom(room) {
  if (!window.confirm(`Obrisati sobu "${room.name}"?`)) {
    return;
  }

  try {
    await api(`/api/rooms/${room.id}`, {
      method: 'DELETE'
    });
    if (editingRoomId === room.id) {
      editingRoomId = null;
    }
    if (connectingPhysicalRoomId === room.id) {
      connectingPhysicalRoomId = null;
    }
    await loadRooms(false);
    showRoomsMessage('Soba je obrisana.');
  } catch (error) {
    showRoomsMessage(roomErrorMessage(error), 'error');
  }
}

function setStatusClass(element, status) {
  element.classList.remove('status-pill--neutral', 'status-pill--error');
  if (status === 'error') {
    element.classList.add('status-pill--error');
  } else if (status !== 'connected' && status !== 'configured') {
    element.classList.add('status-pill--neutral');
  }
}

function renderEvents(events) {
  dom.eventList.innerHTML = '';
  for (const event of events.slice(0, 8)) {
    const item = document.createElement('li');
    item.dataset.level = event.level;

    const title = document.createElement('strong');
    title.textContent = event.title;
    const details = document.createElement('span');
    details.textContent = event.details;
    const meta = document.createElement('small');
    meta.textContent = `${formatDate(event.ts)} / ${event.source}`;

    item.append(title, details, meta);
    dom.eventList.append(item);
  }
}

function syncInputs(state) {
  dom.windowSlider.value = state.actuators.window.openPercent;
  dom.windowSliderValue.textContent = formatPercent(state.actuators.window.openPercent);
  dom.blindsSlider.value = state.actuators.blinds.positionPercent;
  dom.blindsSliderValue.textContent = formatPercent(state.actuators.blinds.positionPercent);

  dom.rainToggle.checked = state.sensors.rainDetected;
  dom.luxInput.value = state.sensors.lightLux;
  dom.luxInputValue.textContent = formatLux(state.sensors.lightLux);
  dom.rainProbabilityInput.value = state.weather.rainProbability;
  dom.rainProbabilityValue.textContent = formatPercent(state.weather.rainProbability);
  dom.windInput.value = state.weather.windKph;
  dom.windInputValue.textContent = `${Math.round(state.weather.windKph)} km/h`;
  dom.temperatureInput.value = state.sensors.indoorTempC;
  dom.temperatureInputValue.textContent = formatTemp(state.sensors.indoorTempC);

  dom.thresholdRain.value = state.automation.thresholds.rainProbabilityClose;
  dom.thresholdLux.value = state.automation.thresholds.lightLuxShade;
  dom.thresholdTemp.value = state.automation.thresholds.indoorTempShadeC;
  dom.thresholdWind.value = state.automation.thresholds.windKphClose;
}

function render(state) {
  currentState = state;
  const rainProbability = Math.round(state.weather.rainProbability);
  const rainThreshold = Number(state.automation.thresholds.rainProbabilityClose) || 0;
  const rainActive = state.sensors.rainDetected || rainProbability >= rainThreshold;

  dom.siteArea.textContent = state.site.area;
  dom.siteName.textContent = state.site.name;
  dom.automationMode.textContent = state.automation.mode.toUpperCase();
  dom.lastUpdated.textContent = formatDate(state.updatedAt);
  dom.autoModeButton.classList.toggle('is-active', state.automation.mode === 'auto');
  dom.manualModeButton.classList.toggle('is-active', state.automation.mode === 'manual');

  const iotConnection = state.iot.connection || 'not_configured';
  dom.iotStatus.textContent = iotConnection.replaceAll('_', ' ');
  setStatusClass(dom.iotStatus, iotConnection);

  dom.rainValue.textContent = rainActive ? 'Aktivno' : 'Mirno';
  dom.rainDetail.textContent = `${Math.round(state.sensors.rainIntensity)} intenzitet / ${rainProbability}% prognoza`;
  dom.lightValue.textContent = formatLux(state.sensors.lightLux);
  dom.lightDetail.textContent = `Prag zasjene ${formatLux(state.automation.thresholds.lightLuxShade)}`;
  dom.tempValue.textContent = formatTemp(state.sensors.indoorTempC);
  dom.tempDetail.textContent = `Prag zasjene ${formatTemp(state.automation.thresholds.indoorTempShadeC)}`;
  dom.windowValue.textContent = formatPercent(state.actuators.window.openPercent);
  dom.windowDetail.textContent = state.sensors.windowContactOpen ? 'Kontakt: otvoren' : 'Kontakt: zatvoren';
  dom.blindsValue.textContent = formatPercent(state.actuators.blinds.positionPercent);
  dom.blindsDetail.textContent = '0% gore / 100% dolje';

  dom.weatherLine.textContent = `${state.weather.condition} / ${rainProbability}% rizik kise`;
  dom.summaryWindowPercent.textContent = formatPercent(state.actuators.window.openPercent);
  dom.summaryWindowOpen.textContent = state.sensors.windowContactOpen ? 'Da' : 'Ne';
  dom.summaryBlindsPercent.textContent = formatPercent(state.actuators.blinds.positionPercent);
  dom.summaryBlindsDown.textContent = state.actuators.blinds.positionPercent > 0 ? 'Da' : 'Ne';
  dom.summaryRain.textContent = rainActive ? 'Pada' : 'Ne pada';
  dom.summaryWind.textContent = `${Math.round(state.weather.windKph)} km/h`;

  dom.iotPlatform.textContent = state.iot.platform;
  dom.deviceId.textContent = state.site.deviceId;
  dom.lastSync.textContent = formatDate(state.iot.lastSyncAt);
  dom.iotError.textContent = state.iot.lastError || '--';

  syncInputs(state);
  renderEvents(state.events);
}

async function sendCommand(target, action, positionPercent) {
  await api('/api/commands', {
    method: 'POST',
    body: JSON.stringify({
      target,
      action,
      positionPercent
    })
  });
  showToast('Komanda je poslana.');
}

function bindControls() {
  document.querySelectorAll('[data-target][data-action]').forEach((button) => {
    button.addEventListener('click', async () => {
      try {
        const position = button.dataset.position === undefined ? undefined : Number(button.dataset.position);
        await sendCommand(button.dataset.target, button.dataset.action, position);
      } catch (error) {
        showToast(error.message);
      }
    });
  });

  document.querySelectorAll('[data-mode]').forEach((button) => {
    button.addEventListener('click', async () => {
      try {
        await sendCommand('automation', button.dataset.mode);
      } catch (error) {
        showToast(error.message);
      }
    });
  });

  dom.windowSlider.addEventListener('input', () => {
    dom.windowSliderValue.textContent = formatPercent(dom.windowSlider.value);
  });
  dom.windowSlider.addEventListener('change', () => sendCommand('window', 'setPosition', Number(dom.windowSlider.value)).catch((error) => showToast(error.message)));

  dom.blindsSlider.addEventListener('input', () => {
    dom.blindsSliderValue.textContent = formatPercent(dom.blindsSlider.value);
  });
  dom.blindsSlider.addEventListener('change', () => sendCommand('blinds', 'setPosition', Number(dom.blindsSlider.value)).catch((error) => showToast(error.message)));

  dom.luxInput.addEventListener('input', () => {
    dom.luxInputValue.textContent = formatLux(dom.luxInput.value);
  });
  dom.rainProbabilityInput.addEventListener('input', () => {
    dom.rainProbabilityValue.textContent = formatPercent(dom.rainProbabilityInput.value);
  });
  dom.windInput.addEventListener('input', () => {
    dom.windInputValue.textContent = `${Math.round(dom.windInput.value)} km/h`;
  });
  dom.temperatureInput.addEventListener('input', () => {
    dom.temperatureInputValue.textContent = formatTemp(dom.temperatureInput.value);
  });

  dom.applyTelemetryButton.addEventListener('click', async () => {
    try {
      await api('/api/telemetry', {
        method: 'POST',
        body: JSON.stringify({
          source: 'web-simulator',
          rainDetected: dom.rainToggle.checked,
          rainIntensity: dom.rainToggle.checked ? 70 : 0,
          lightLux: Number(dom.luxInput.value),
          rainProbability: Number(dom.rainProbabilityInput.value),
          windKph: Number(dom.windInput.value),
          indoorTempC: Number(dom.temperatureInput.value)
        })
      });
      showToast('Simulacija je primijenjena.');
    } catch (error) {
      showToast(error.message);
    }
  });

  dom.saveThresholdsButton.addEventListener('click', async () => {
    try {
      await api('/api/automation/thresholds', {
        method: 'POST',
        body: JSON.stringify({
          rainProbabilityClose: Number(dom.thresholdRain.value),
          lightLuxShade: Number(dom.thresholdLux.value),
          indoorTempShadeC: Number(dom.thresholdTemp.value),
          windKphClose: Number(dom.thresholdWind.value)
        })
      });
      showToast('Pravila su spremljena.');
    } catch (error) {
      showToast(error.message);
    }
  });

  dom.addRoomButton.addEventListener('click', () => addRoom());
  dom.roomNameInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      addRoom();
    }
  });
}

function startStream() {
  if (!window.EventSource) {
    setInterval(() => api('/api/state').then(render).catch(() => {}), 2500);
    return;
  }

  const stream = new EventSource('/api/stream');
  stream.addEventListener('state', (event) => {
    render(JSON.parse(event.data));
  });
  stream.addEventListener('error', () => {
    showToast('Live veza se obnavlja.');
  });
}

async function boot() {
  bindControls();
  try {
    currentUser = await api('/api/me');
    renderUser(currentUser);
    window.addEventListener('hashchange', () => {
      showRoute().catch((error) => showToast(error.message));
    });
    await showRoute();
  } catch (error) {
    showToast(error.message);
  }
}

boot();
