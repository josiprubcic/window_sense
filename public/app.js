'use strict';

const dom = {
  topbar: document.querySelector('.topbar'),
  appNav: document.querySelector('#appNav'),
  loginView: document.querySelector('#loginView'),
  dashboardView: document.querySelector('#dashboardView'),
  roomsView: document.querySelector('#roomsView'),
  dashboardRoomSelect: document.querySelector('#dashboardRoomSelect'),
  dashboardSource: document.querySelector('#dashboardSource'),
  siteArea: document.querySelector('#siteArea'),
  siteName: document.querySelector('#siteName'),
  automationMode: document.querySelector('#automationMode'),
  iotStatus: document.querySelector('#iotStatus'),
  lastUpdated: document.querySelector('#lastUpdated'),
  rainValue: document.querySelector('#rainValue'),
  rainDetail: document.querySelector('#rainDetail'),
  lightValue: document.querySelector('#lightValue'),
  lightDetail: document.querySelector('#lightDetail'),
  windValue: document.querySelector('#windValue'),
  windDetail: document.querySelector('#windDetail'),
  weatherLine: document.querySelector('#weatherLine'),
  summaryRain: document.querySelector('#summaryRain'),
  summaryRainRisk: document.querySelector('#summaryRainRisk'),
  summaryLight: document.querySelector('#summaryLight'),
  summaryTemp: document.querySelector('#summaryTemp'),
  summaryWind: document.querySelector('#summaryWind'),
  dashboardDeviceStates: document.querySelector('#dashboardDeviceStates'),
  autoModeButton: document.querySelector('#autoModeButton'),
  manualModeButton: document.querySelector('#manualModeButton'),
  dashboardDeviceSelect: document.querySelector('#dashboardDeviceSelect'),
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
  controlsPanel: document.querySelector('#controlsPanel'),
  simulationPanel: document.querySelector('#simulationPanel'),
  automationPanel: document.querySelector('#automationPanel'),
  simulationAutoButton: document.querySelector('#simulationAutoButton'),
  simulationManualButton: document.querySelector('#simulationManualButton'),
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

let toastTimer = null;
let editingRoomId = null;
let currentUser = null;
let roomsLoaded = false;
let roomsCache = [];
let roomTelemetry = new Map();
let roomTelemetryPollTimer = null;
let dashboardTelemetryPollTimer = null;
let dashboardEventsPollTimer = null;
let selectedDashboardRoomId = null;
let selectedDashboardDeviceIdByRoom = new Map();
let currentRoomSimulation = null;
let currentRoomThresholds = null;
let connectingPhysicalRoomId = null;
let physicalConnectDeveloperMode = false;
const DEVICE_KIND_OPTIONS = [
  { value: 'window', label: 'Prozor' },
  { value: 'blinds', label: 'Roleta' },
  { value: 'combined', label: 'Prozor + roleta' }
];

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

function formatConnectionStatus(status) {
  const labels = {
    connected: 'povezano',
    configured: 'konfigurirano',
    not_configured: 'nije konfigurirano',
    error: 'greška',
    physical: 'fizički',
    no_telemetry: 'nema telemetrije'
  };
  return labels[status] || String(status || '--').replaceAll('_', ' ');
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
  await ensureRoomsLoaded(false);
  renderDashboardRoomOptions();
  await loadEvents();
  startDashboardEventsPolling();

  if (selectedDashboardRoomId && roomsCache.some((room) => room.id === selectedDashboardRoomId)) {
    dom.dashboardRoomSelect.value = selectedDashboardRoomId;
    await loadSelectedDashboardRoomTelemetry();
    startDashboardTelemetryPolling();
    return;
  }

  if (roomsCache.length > 0) {
    selectedDashboardRoomId = roomsCache[0].id;
    dom.dashboardRoomSelect.value = selectedDashboardRoomId;
    await loadSelectedDashboardRoomTelemetry();
    startDashboardTelemetryPolling();
    return;
  }

  selectedDashboardRoomId = null;
  renderDashboardPlaceholders(null, 'Nema soba. Prvo dodajte sobu.');
  setDashboardControlsEnabled(false);
  setVisible(dom.simulationPanel, false);
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
    window.location.hash = '#/rooms';
    route = 'rooms';
  }

  setActiveRoute(route);
  setVisible(dom.dashboardView, route === 'dashboard');
  setVisible(dom.roomsView, route === 'rooms');

  if (route !== 'dashboard') {
    stopDashboardTelemetryPolling();
    stopDashboardEventsPolling();
  }

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

  const text = response.status === 204 ? '' : await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch (error) {
      data = { error: text };
    }
  }
  if (!response.ok) {
    const validationMessage = typeof data === 'object' && data !== null
      ? Object.values(data).find((value) => typeof value === 'string')
      : null;
    throw new Error(apiErrorMessage(data?.error || validationMessage || 'API zahtjev nije uspio.'));
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

function apiErrorMessage(message) {
  const mapped = {
    MULTIPLE_DEVICES_FOR_CAPABILITY: 'Više uređaja u sobi podržava tu komandu. Odaberite uređaj za komande.',
    DEVICE_DOES_NOT_SUPPORT_CAPABILITY: 'Odabrani uređaj ne podržava tu komandu.',
    NO_DEVICE_FOR_CAPABILITY: 'Soba nema aktivni uređaj koji podržava tu komandu.'
  };
  return mapped[message] || message;
}

function hasActivePhysicalDevice(room) {
  return (room.devices || []).some((device) => device.deviceType === 'PHYSICAL' && device.status === 'ACTIVE');
}

function hasActiveControllableDevice(room) {
  return (room.devices || []).some((device) => device.status === 'ACTIVE'
    && (device.deviceType === 'PHYSICAL' || device.deviceType === 'VIRTUAL'));
}

function hasActiveVirtualDevice(room) {
  return (room.devices || []).some((device) => device.deviceType === 'VIRTUAL' && device.status === 'ACTIVE');
}

function deviceSupportsTarget(device, target) {
  const capabilities = device?.capabilities || [];
  if (target === 'window') {
    return capabilities.includes('WINDOW_CONTROL');
  }
  if (target === 'blinds') {
    return capabilities.includes('BLINDS_CONTROL');
  }
  return false;
}

function selectedDeviceCapabilities(kind) {
  if (kind === 'window') {
    return ['window'];
  }
  if (kind === 'blinds') {
    return ['blinds'];
  }
  return ['window', 'blinds'];
}

function createDeviceKindSelect() {
  const select = document.createElement('select');
  select.setAttribute('aria-label', 'Tip uređaja');
  const placeholder = document.createElement('option');
  placeholder.value = '';
  placeholder.textContent = 'Tip uređaja';
  placeholder.disabled = true;
  placeholder.selected = true;
  select.append(placeholder);
  for (const option of DEVICE_KIND_OPTIONS) {
    const item = document.createElement('option');
    item.value = option.value;
    item.textContent = option.label;
    select.append(item);
  }
  return select;
}

function deviceKindLabel(device) {
  const capabilities = device?.capabilities || [];
  const supportsWindow = capabilities.includes('WINDOW_CONTROL');
  const supportsBlinds = capabilities.includes('BLINDS_CONTROL');
  if (supportsWindow && supportsBlinds) {
    return 'Prozor + roleta';
  }
  if (supportsWindow) {
    return 'Prozor';
  }
  if (supportsBlinds) {
    return 'Roleta';
  }
  return 'Senzorski uređaj';
}

function orderedDevices(room) {
  return [...(room.devices || [])].sort((left, right) => {
    const leftPhysical = left.deviceType === 'PHYSICAL' && left.status === 'ACTIVE';
    const rightPhysical = right.deviceType === 'PHYSICAL' && right.status === 'ACTIVE';
    if (leftPhysical !== rightPhysical) {
      return leftPhysical ? -1 : 1;
    }
    return left.name.localeCompare(right.name, 'hr');
  });
}

function commandDevices(room) {
  if (!room) {
    return [];
  }
  return orderedDevices(room).filter((device) => device.status === 'ACTIVE'
    && (deviceSupportsTarget(device, 'window') || deviceSupportsTarget(device, 'blinds')));
}

function telemetryValue(telemetry, key, formatter = (value) => value) {
  if (!telemetry || telemetry[key] === undefined || telemetry[key] === null || telemetry[key] === '') {
    return '--';
  }
  return formatter(telemetry[key]);
}

function rainProbabilityValue(telemetry) {
  if (!telemetry) {
    return 0;
  }
  return Number(telemetry.rainProbability ?? telemetry.rainRiskPercent) || 0;
}

function deviceTelemetryItems(device) {
  const telemetry = device?.telemetry || {};
  const items = [];
  if (deviceSupportsTarget(device, 'window')) {
    const windowOpen = telemetryValue(telemetry, 'windowOpenPercent', (value) => Number(value));
    items.push(['Otvorenost prozora', windowOpen === '--' ? '--' : formatPercent(windowOpen)]);
    items.push(['Prozor otvoren', windowOpen === '--' ? '--' : windowOpen > 0 ? 'Da' : 'Ne']);
  }
  if (deviceSupportsTarget(device, 'blinds')) {
    const blindClosed = telemetryValue(telemetry, 'blindClosedPercent', (value) => Number(value));
    items.push(['Spuštenost rolete', blindClosed === '--' ? '--' : formatPercent(blindClosed)]);
    items.push(['Roleta spuštena', blindClosed === '--' ? '--' : blindClosed > 0 ? 'Da' : 'Ne']);
  }
  return items;
}

function renderDashboardDeviceStates(room, telemetryResponse) {
  dom.dashboardDeviceStates.innerHTML = '';
  const telemetryDevices = Array.isArray(telemetryResponse?.devices) ? telemetryResponse.devices : [];
  const roomDevicesById = new Map((room?.devices || []).map((device) => [device.id, device]));
  const devices = telemetryDevices.length > 0
    ? telemetryDevices
    : orderedDevices(room || {}).map((device) => ({ ...device, deviceId: device.id, deviceName: device.name, telemetry: {} }));

  if (devices.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'form-message';
    empty.textContent = 'Soba nema povezanih uređaja.';
    dom.dashboardDeviceStates.append(empty);
    return;
  }

  for (const telemetryDevice of devices) {
    const roomDevice = roomDevicesById.get(telemetryDevice.deviceId) || {};
    const device = {
      ...roomDevice,
      ...telemetryDevice,
      id: telemetryDevice.deviceId || roomDevice.id,
      name: telemetryDevice.deviceName || roomDevice.name,
      capabilities: telemetryDevice.capabilities || roomDevice.capabilities || []
    };
    const card = document.createElement('article');
    card.className = 'dashboard-device-state';

    const header = document.createElement('header');
    const title = document.createElement('strong');
    title.textContent = device.name || 'Uređaj';
    const meta = document.createElement('span');
    meta.textContent = `${device.deviceType === 'PHYSICAL' ? 'Fizički' : 'Virtualni'} / ${deviceKindLabel(device)}`;
    header.append(title, meta);

    const grid = document.createElement('dl');
    grid.className = 'device-state-grid';
    const stateItems = deviceTelemetryItems(device);
    if (stateItems.length === 0) {
      const empty = document.createElement('p');
      empty.className = 'form-message';
      empty.textContent = 'Ovaj uređaj nema stanje prozora ili rolete.';
      card.append(header, empty);
      dom.dashboardDeviceStates.append(card);
      continue;
    }

    for (const [label, value] of stateItems) {
      const item = document.createElement('div');
      const term = document.createElement('dt');
      term.textContent = label;
      const description = document.createElement('dd');
      description.textContent = value;
      item.append(term, description);
      grid.append(item);
    }

    card.append(header, grid);
    dom.dashboardDeviceStates.append(card);
  }
}

function renderRoomTelemetry(room) {
  const telemetryResponse = roomTelemetry.get(room.id);
  const telemetry = telemetryResponse?.telemetry || {};
  const section = document.createElement('section');
  section.className = 'room-telemetry';
  section.dataset.roomTelemetryId = room.id;

  if (!telemetryResponse || Object.keys(telemetry).length === 0) {
    const empty = document.createElement('p');
    empty.className = 'form-message';
    empty.textContent = telemetryResponse?.message || 'Telemetrija još nije dostupna.';
    section.append(empty);
    return section;
  }

  const source = document.createElement('p');
  source.className = 'telemetry-source';
  source.textContent = telemetryResponse.isVirtual
    ? `Izvor: ${telemetryResponse.deviceName} / simulacija`
    : `Izvor: ${telemetryResponse.deviceName} / fizički ESP32`;

  const rows = [
    ['Kiša', telemetryValue(telemetry, 'rainDetected', (value) => value ? 'Pada' : 'Ne pada')],
    ['Intenzitet kiše', telemetryValue(telemetry, 'rainIntensity', (value) => `${Math.round(Number(value))}`)],
    ['Rizik kiše', formatPercent(rainProbabilityValue(telemetry))],
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

  section.append(source, grid);
  return section;
}

function renderDashboardRoomOptions() {
  dom.dashboardRoomSelect.innerHTML = '';
  for (const room of roomsCache) {
    const option = document.createElement('option');
    option.value = room.id;
    option.textContent = room.activeDevice ? `Soba: ${room.name}` : `Soba: ${room.name} (bez uredjaja)`;
    dom.dashboardRoomSelect.append(option);
  }
  dom.dashboardRoomSelect.disabled = roomsCache.length === 0;

  if (selectedDashboardRoomId && roomsCache.some((room) => room.id === selectedDashboardRoomId)) {
    dom.dashboardRoomSelect.value = selectedDashboardRoomId;
  } else if (roomsCache.length > 0) {
    selectedDashboardRoomId = roomsCache[0].id;
    dom.dashboardRoomSelect.value = selectedDashboardRoomId;
  } else {
    selectedDashboardRoomId = null;
  }
  renderDashboardDeviceOptions();
}

function renderDashboardDeviceOptions() {
  const room = selectedDashboardRoom();
  const selectedDeviceId = selectedDashboardDeviceId(room);
  dom.dashboardDeviceSelect.innerHTML = '';

  const automatic = document.createElement('option');
  automatic.value = '';
  automatic.textContent = 'Automatski';
  dom.dashboardDeviceSelect.append(automatic);

  const devices = room ? commandDevices(room) : [];
  for (const device of devices) {
    const option = document.createElement('option');
    option.value = device.id;
    option.textContent = `${device.name} (${device.deviceType})`;
    dom.dashboardDeviceSelect.append(option);
  }

  dom.dashboardDeviceSelect.value = selectedDeviceId;
  dom.dashboardDeviceSelect.disabled = devices.length === 0;
}

function selectedDashboardDeviceId(room = selectedDashboardRoom()) {
  if (!room) {
    return '';
  }
  const devices = commandDevices(room);
  const selectedDeviceId = selectedDashboardDeviceIdByRoom.get(room.id) || '';
  if (!selectedDeviceId) {
    return '';
  }
  if (devices.some((device) => device.id === selectedDeviceId)) {
    return selectedDeviceId;
  }
  selectedDashboardDeviceIdByRoom.delete(room.id);
  return '';
}

function selectedDashboardTelemetry(room, telemetryResponse) {
  const selectedDeviceId = selectedDashboardDeviceId(room);
  if (!selectedDeviceId || !Array.isArray(telemetryResponse?.devices)) {
    return telemetryResponse;
  }

  const selectedDevice = telemetryResponse.devices.find((device) => device.deviceId === selectedDeviceId);
  if (!selectedDevice) {
    return telemetryResponse;
  }

  const hasTelemetry = selectedDevice.telemetry && Object.keys(selectedDevice.telemetry).length > 0;
  return {
    ...telemetryResponse,
    deviceId: selectedDevice.deviceId,
    deviceName: selectedDevice.deviceName,
    deviceType: selectedDevice.deviceType,
    isVirtual: selectedDevice.isVirtual,
    telemetry: selectedDevice.telemetry || {},
    updatedAt: selectedDevice.updatedAt || telemetryResponse.updatedAt,
    status: hasTelemetry ? 'AVAILABLE' : 'UNAVAILABLE',
    code: hasTelemetry ? null : 'NO_TELEMETRY',
    message: hasTelemetry ? null : `${selectedDevice.deviceName} još nema telemetriju.`
  };
}

function updateRenderedRoomTelemetry(rooms) {
  for (const room of rooms) {
    const current = dom.roomsList.querySelector(`[data-room-telemetry-id="${room.id}"]`);
    if (current) {
      current.replaceWith(renderRoomTelemetry(room));
    }
  }
}

async function ensureRoomsLoaded(announce = true) {
  if (!roomsLoaded) {
    await loadRooms(announce);
  }
}

function renderRooms(rooms) {
  dom.roomsList.innerHTML = '';

  if (rooms.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'form-message';
    empty.textContent = 'Još nema dodanih soba.';
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
    connectButton.textContent = 'Dodaj fizički uređaj';
    connectButton.addEventListener('click', () => {
      connectingPhysicalRoomId = room.id;
      editingRoomId = null;
      physicalConnectDeveloperMode = false;
      renderRooms(rooms);
    });
    const virtualWindowButton = document.createElement('button');
    virtualWindowButton.type = 'button';
    virtualWindowButton.className = 'button-secondary';
    virtualWindowButton.textContent = 'Virtualni prozor';
    virtualWindowButton.addEventListener('click', () => addVirtualDevice(room, 'window'));
    const virtualBlindsButton = document.createElement('button');
    virtualBlindsButton.type = 'button';
    virtualBlindsButton.className = 'button-secondary';
    virtualBlindsButton.textContent = 'Virtualna roleta';
    virtualBlindsButton.addEventListener('click', () => addVirtualDevice(room, 'blinds'));
    const deleteButton = document.createElement('button');
    deleteButton.type = 'button';
    deleteButton.className = 'button-danger';
    deleteButton.textContent = 'Obriši';
    deleteButton.addEventListener('click', () => deleteRoom(room));
    actions.append(editButton, virtualWindowButton, virtualBlindsButton, connectButton, deleteButton);
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
      const deviceIdentifierInput = document.createElement('input');
      deviceIdentifierInput.type = 'text';
      deviceIdentifierInput.maxLength = physicalConnectDeveloperMode ? 128 : 255;
      deviceIdentifierInput.placeholder = physicalConnectDeveloperMode ? 'ThingsBoard Device ID' : 'ESP ThingsBoard token';
      deviceIdentifierInput.setAttribute('aria-label', physicalConnectDeveloperMode ? 'ThingsBoard Device ID' : 'ESP ThingsBoard token');
      const deviceKindSelect = createDeviceKindSelect();
      const saveButton = document.createElement('button');
      saveButton.type = 'button';
      saveButton.className = 'button-secondary';
      saveButton.textContent = physicalConnectDeveloperMode ? 'Poveži' : 'Dodaj tokenom';
      saveButton.addEventListener('click', () => {
        if (physicalConnectDeveloperMode) {
          connectPhysicalDevice(room.id, nameInput.value, deviceIdentifierInput.value, '', deviceKindSelect.value);
          return;
        }
        connectPhysicalDeviceByToken(room.id, nameInput.value, deviceIdentifierInput.value);
      });
      const cancelButton = document.createElement('button');
      cancelButton.type = 'button';
      cancelButton.textContent = 'Odustani';
      cancelButton.addEventListener('click', () => {
        connectingPhysicalRoomId = null;
        physicalConnectDeveloperMode = false;
        renderRooms(rooms);
      });
      const developerButton = document.createElement('button');
      developerButton.type = 'button';
      developerButton.className = 'button-secondary';
      developerButton.textContent = physicalConnectDeveloperMode ? 'ESP token' : 'Admin / Developer ID';
      developerButton.addEventListener('click', () => {
        physicalConnectDeveloperMode = !physicalConnectDeveloperMode;
        renderRooms(rooms);
      });
      const formInputs = physicalConnectDeveloperMode
        ? [nameInput, deviceIdentifierInput, deviceKindSelect]
        : [nameInput, deviceIdentifierInput];
      for (const input of formInputs) {
        input.addEventListener('keydown', (event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            saveButton.click();
          }
        });
      }
      if (physicalConnectDeveloperMode) {
        connectForm.append(nameInput, deviceIdentifierInput, deviceKindSelect);
      } else {
        connectForm.classList.add('physical-connect--token');
        connectForm.append(nameInput, deviceIdentifierInput);
      }
      connectForm.append(saveButton, cancelButton, developerButton);
      item.append(header, connectForm);
      setTimeout(() => nameInput.focus(), 0);
    } else {
      item.append(header);
    }

    const devices = document.createElement('div');
    devices.className = 'device-list';
    const roomDevices = orderedDevices(room);
    const devicesHeader = document.createElement('div');
    devicesHeader.className = 'device-list__header';
    const devicesTitle = document.createElement('strong');
    devicesTitle.textContent = `Uređaji u sobi (${roomDevices.length})`;
    devicesHeader.append(devicesTitle);
    devices.append(devicesHeader);
    if (roomDevices.length === 0) {
      const noDevice = document.createElement('p');
      noDevice.className = 'form-message';
      noDevice.textContent = 'Nema povezanog uređaja.';
      devices.append(noDevice);
    }
    for (const device of roomDevices) {
      const deviceRow = document.createElement('div');
      deviceRow.className = 'device-row';
      const deviceInfo = document.createElement('div');
      const deviceName = document.createElement('strong');
      deviceName.textContent = device.name;
      const meta = document.createElement('span');
      const deviceTypeLabel = device.deviceType === 'PHYSICAL' ? 'Fizički' : 'Virtualni';
      meta.textContent = `${deviceTypeLabel} / ${device.status} / ${deviceKindLabel(device)}`;
      deviceInfo.append(deviceName, meta);
      const deviceDeleteButton = document.createElement('button');
      deviceDeleteButton.type = 'button';
      deviceDeleteButton.className = 'button-danger';
      deviceDeleteButton.textContent = 'Obriši uređaj';
      deviceDeleteButton.addEventListener('click', () => deleteRoomDevice(room, device));
      deviceRow.append(deviceInfo, deviceDeleteButton);
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
    showRoomsMessage(`${rooms.length} soba učitano.`);
  }
}

function setDashboardControlsEnabled(enabled) {
  const controls = [
    dom.autoModeButton,
    dom.manualModeButton,
    dom.dashboardDeviceSelect,
    dom.windowSlider,
    dom.blindsSlider,
    dom.rainToggle,
    dom.luxInput,
    dom.rainProbabilityInput,
    dom.windInput,
    dom.temperatureInput,
    dom.applyTelemetryButton,
    dom.saveThresholdsButton,
    dom.thresholdRain,
    dom.thresholdLux,
    dom.thresholdTemp,
    dom.thresholdWind
  ];

  document.querySelectorAll('[data-target][data-action]').forEach((button) => {
    button.disabled = !enabled;
  });

  for (const control of controls) {
    control.disabled = !enabled;
  }
  setVisible(dom.simulationPanel, true);
  dom.simulationAutoButton.disabled = true;
  dom.simulationManualButton.disabled = true;
  dom.simulationAutoButton.classList.remove('is-active');
  dom.simulationManualButton.classList.remove('is-active');
}

function setRoomDashboardControlsEnabled(enabled) {
  document.querySelectorAll('[data-target][data-action]').forEach((button) => {
    button.disabled = !enabled;
  });

  dom.windowSlider.disabled = !enabled;
  dom.blindsSlider.disabled = !enabled;
  dom.dashboardDeviceSelect.disabled = !enabled || commandDevices(selectedDashboardRoom()).length === 0;
  dom.autoModeButton.disabled = true;
  dom.manualModeButton.disabled = true;
  dom.saveThresholdsButton.disabled = false;
  dom.thresholdRain.disabled = false;
  dom.thresholdLux.disabled = false;
  dom.thresholdTemp.disabled = false;
  dom.thresholdWind.disabled = false;
}

function setRoomSimulationUi(room) {
  const hasVirtual = hasActiveVirtualDevice(room);
  setVisible(dom.simulationPanel, hasVirtual);
  if (!hasVirtual || !currentRoomSimulation) {
    return;
  }

  const manual = currentRoomSimulation.mode === 'MANUAL';
  dom.simulationAutoButton.disabled = false;
  dom.simulationManualButton.disabled = false;
  dom.simulationAutoButton.classList.toggle('is-active', !manual);
  dom.simulationManualButton.classList.toggle('is-active', manual);
  dom.rainToggle.disabled = !manual;
  dom.luxInput.disabled = !manual;
  dom.rainProbabilityInput.disabled = !manual;
  dom.windInput.disabled = !manual;
  dom.temperatureInput.disabled = !manual;
  dom.applyTelemetryButton.disabled = !manual;
}

function syncRoomThresholdInputs(thresholds) {
  if (!thresholds) {
    return;
  }
  dom.thresholdRain.value = thresholds.rainProbabilityClose;
  dom.thresholdLux.value = thresholds.lightLuxShade;
  dom.thresholdTemp.value = thresholds.indoorTempShadeC;
  dom.thresholdWind.value = thresholds.windKphClose;
}

function resetDashboardControlValues() {
  dom.windowSlider.value = 0;
  dom.blindsSlider.value = 0;
  dom.luxInput.value = 0;
  dom.rainProbabilityInput.value = 0;
  dom.windInput.value = 0;
  dom.temperatureInput.value = 24;
  dom.windowSliderValue.textContent = '--';
  dom.blindsSliderValue.textContent = '--';
  dom.luxInputValue.textContent = '--';
  dom.rainProbabilityValue.textContent = '--';
  dom.windInputValue.textContent = '--';
  dom.temperatureInputValue.textContent = '--';
  dom.rainToggle.checked = false;
  dom.thresholdRain.value = '';
  dom.thresholdLux.value = '';
  dom.thresholdTemp.value = '';
  dom.thresholdWind.value = '';
  dom.autoModeButton.classList.remove('is-active');
  dom.manualModeButton.classList.remove('is-active');
}

function syncRoomDashboardInputs(telemetry) {
  const rainRisk = rainProbabilityValue(telemetry);
  const lux = Number(telemetry.lux) || 0;
  const indoorTemp = Number(telemetry.indoorTempC) || 0;
  const wind = Number(telemetry.windKmh) || 0;
  const windowOpen = Number(telemetry.windowOpenPercent) || 0;
  const blindClosed = Number(telemetry.blindClosedPercent) || 0;

  dom.windowSlider.value = windowOpen;
  dom.windowSliderValue.textContent = formatPercent(windowOpen);
  dom.blindsSlider.value = blindClosed;
  dom.blindsSliderValue.textContent = formatPercent(blindClosed);
  dom.rainToggle.checked = Boolean(telemetry.rainDetected);
  dom.luxInput.value = lux;
  dom.luxInputValue.textContent = formatLux(lux);
  dom.rainProbabilityInput.value = rainRisk;
  dom.rainProbabilityValue.textContent = formatPercent(rainRisk);
  dom.windInput.value = wind;
  dom.windInputValue.textContent = `${Math.round(wind)} km/h`;
  dom.temperatureInput.value = indoorTemp;
  dom.temperatureInputValue.textContent = formatTemp(indoorTemp);
  syncRoomThresholdInputs(currentRoomThresholds);
  dom.autoModeButton.classList.remove('is-active');
  dom.manualModeButton.classList.remove('is-active');
}

function selectedDashboardRoom() {
  return roomsCache.find((room) => room.id === selectedDashboardRoomId) || null;
}

async function loadSelectedDashboardRoomTelemetry(options = {}) {
  const {
    refreshDeviceOptions = true,
    refreshControls = true,
    refreshThresholds = true,
    refreshSimulation = true
  } = options;
  const room = selectedDashboardRoom();
  if (!room) {
    return;
  }

  if (refreshDeviceOptions) {
    renderDashboardDeviceOptions();
  }
  if (refreshControls) {
    setRoomDashboardControlsEnabled(hasActiveControllableDevice(room));
  }
  if (refreshThresholds) {
    await loadSelectedRoomThresholds();
  }
  if (refreshSimulation) {
    await loadSelectedRoomSimulation(room);
  }
  try {
    const telemetry = await api(`/api/rooms/${room.id}/telemetry/latest`);
    roomTelemetry.set(room.id, telemetry);
    renderDashboardTelemetry(room, telemetry, { syncControls: refreshControls });
  } catch (error) {
    renderDashboardPlaceholders(room, error.message || 'Telemetrija sobe nije dostupna.');
  }
}

async function loadSelectedRoomSimulation(room = selectedDashboardRoom()) {
  currentRoomSimulation = null;
  if (!room || !hasActiveVirtualDevice(room)) {
    setVisible(dom.simulationPanel, false);
    return;
  }

  try {
    currentRoomSimulation = await api(`/api/rooms/${room.id}/simulation`);
    setRoomSimulationUi(room);
  } catch (error) {
    setVisible(dom.simulationPanel, false);
    dom.dashboardSource.textContent = error.message;
  }
}

async function loadSelectedRoomThresholds() {
  currentRoomThresholds = null;
  if (!selectedDashboardRoomId) {
    return;
  }

  try {
    const response = await api(`/api/rooms/${selectedDashboardRoomId}/automation/thresholds`);
    currentRoomThresholds = response.thresholds;
    syncRoomThresholdInputs(currentRoomThresholds);
  } catch (error) {
    showToast(error.message);
  }
}

function applyRoomActionResponse(response) {
  const room = selectedDashboardRoom();
  if (!room || !response) {
    return;
  }

  if (response.thresholds) {
    currentRoomThresholds = response.thresholds;
    syncRoomThresholdInputs(currentRoomThresholds);
  }

  if (!response.telemetry || Object.keys(response.telemetry).length === 0) {
    return;
  }

  if (hasActiveVirtualDevice(room)) {
    currentRoomSimulation = {
      ...(currentRoomSimulation || {}),
      ...response,
      telemetry: response.telemetry
    };
  }

  const telemetryResponse = {
    roomId: room.id,
    roomName: room.name,
    deviceName: response.deviceId || currentRoomSimulation?.deviceId || room.name,
    deviceType: response.deviceType,
    isVirtual: hasActiveVirtualDevice(room),
    telemetry: response.telemetry,
    updatedAt: response.updatedAt || response.telemetry.lastUpdatedAt,
    message: response.decisions?.length
      ? `Automatizacija: ${response.decisions.length} odluka`
      : null
  };
  roomTelemetry.set(room.id, telemetryResponse);
  renderDashboardTelemetry(room, telemetryResponse);
}

function startDashboardTelemetryPolling() {
  if (dashboardTelemetryPollTimer) {
    return;
  }

  dashboardTelemetryPollTimer = setInterval(() => {
    if (routeName() === 'dashboard' && selectedDashboardRoomId) {
      loadSelectedDashboardRoomTelemetry({
        refreshDeviceOptions: false,
        refreshControls: false,
        refreshThresholds: false,
        refreshSimulation: false
      }).catch((error) => showToast(error.message));
    }
  }, 5000);
}

function stopDashboardTelemetryPolling() {
  if (!dashboardTelemetryPollTimer) {
    return;
  }

  clearInterval(dashboardTelemetryPollTimer);
  dashboardTelemetryPollTimer = null;
}

async function loadEvents() {
  try {
    const data = await api('/api/events');
    renderEvents(data?.events || []);
  } catch (error) {
    renderEvents([]);
    showToast(error.message);
  }
}

function startDashboardEventsPolling() {
  if (dashboardEventsPollTimer) {
    return;
  }

  dashboardEventsPollTimer = setInterval(() => {
    if (routeName() === 'dashboard') {
      loadEvents().catch((error) => showToast(error.message));
    }
  }, 5000);
}

function stopDashboardEventsPolling() {
  if (!dashboardEventsPollTimer) {
    return;
  }

  clearInterval(dashboardEventsPollTimer);
  dashboardEventsPollTimer = null;
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
  updateRenderedRoomTelemetry(rooms);
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

async function addVirtualDevice(room, kind) {
  const label = DEVICE_KIND_OPTIONS.find((option) => option.value === kind)?.label || 'Uređaj';
  try {
    await api(`/api/rooms/${room.id}/devices/virtual`, {
      method: 'POST',
      body: JSON.stringify({
        name: `Virtualni ${label.toLowerCase()} - ${room.name}`,
        capabilities: selectedDeviceCapabilities(kind)
      })
    });
    await loadRooms(false);
    showRoomsMessage(`${label} je dodan kao virtualni uređaj.`);
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
    physicalConnectDeveloperMode = false;
    await loadRooms(false);
    showRoomsMessage('Soba je uspješno ažurirana.');
  } catch (error) {
    showRoomsMessage(roomErrorMessage(error), 'error');
  }
}

async function connectPhysicalDevice(roomId, rawName, rawTbDeviceId, rawSerialNumber = '', kind = 'combined') {
  const name = rawName.trim();
  const deviceIdentifier = rawTbDeviceId.trim();
  const serialNumber = rawSerialNumber.trim();
  if (!name || !deviceIdentifier || (!physicalConnectDeveloperMode && !serialNumber)) {
    showRoomsMessage(physicalConnectDeveloperMode
      ? 'Naziv uređaja i ThingsBoard Device ID su obavezni.'
      : 'Naziv uređaja, serijski broj i kod za povezivanje su obavezni.', 'error');
    return;
  }
  if (!kind) {
    showRoomsMessage('Odaberi tip uređaja: prozor, roleta ili prozor + roleta.', 'error');
    return;
  }
  const capabilities = selectedDeviceCapabilities(kind);

  try {
    const path = physicalConnectDeveloperMode
      ? `/api/rooms/${roomId}/devices/physical`
      : `/api/rooms/${roomId}/devices/entity`;
    const body = physicalConnectDeveloperMode
      ? { name, tbDeviceId: deviceIdentifier, capabilities }
      : { name, serialNumber, pairingCode: deviceIdentifier, capabilities };
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

async function connectPhysicalDeviceByToken(roomId, rawName, rawAccessToken) {
  const name = rawName.trim();
  const accessToken = rawAccessToken.trim();
  if (!name || !accessToken) {
    showRoomsMessage('Naziv uređaja i ESP ThingsBoard token su obavezni.', 'error');
    return;
  }

  try {
    await api(`/api/rooms/${roomId}/devices/token`, {
      method: 'POST',
      body: JSON.stringify({ name, thingsBoardAccessToken: accessToken })
    });
    physicalConnectDeveloperMode = false;
    connectingPhysicalRoomId = null;
    await loadRooms(false);
    showRoomsMessage('Fizički uređaj je dodan prema tvorničkom ESP tokenu.');
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

async function deleteRoomDevice(room, device) {
  if (!window.confirm(`Obrisati uređaj "${device.name}" iz sobe "${room.name}"?`)) {
    return;
  }

  try {
    await api(`/api/rooms/${room.id}/devices/${device.id}`, {
      method: 'DELETE'
    });
    selectedDashboardDeviceIdByRoom.delete(room.id);
    roomTelemetry.delete(room.id);
    await loadRooms(false);
    renderDashboardRoomOptions();
    if (selectedDashboardRoomId === room.id) {
      await loadSelectedDashboardRoomTelemetry();
    }
    showRoomsMessage('Uređaj je obrisan iz sobe.');
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
  const visibleEvents = (events || []).slice(0, 8);
  if (visibleEvents.length === 0) {
    const item = document.createElement('li');
    item.dataset.level = 'info';
    const title = document.createElement('strong');
    title.textContent = 'Nema događaja';
    const details = document.createElement('span');
    details.textContent = 'Novi događaji prikazat će se ovdje.';
    item.append(title, details);
    dom.eventList.append(item);
    return;
  }

  for (const event of visibleEvents) {
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

function renderDashboardPlaceholders(room, message) {
  dom.siteArea.textContent = 'Soba';
  dom.siteName.textContent = room?.name || 'Dashboard';
  dom.automationMode.textContent = 'SOBA';
  dom.weatherLine.textContent = message || 'Telemetrija sobe nije dostupna.';
  dom.lastUpdated.textContent = '--';

  dom.rainValue.textContent = '--';
  dom.rainDetail.textContent = '--';
  dom.lightValue.textContent = '--';
  dom.lightDetail.textContent = '--';
  dom.tempValue.textContent = '--';
  dom.tempDetail.textContent = '--';
  dom.windValue.textContent = '--';
  dom.windDetail.textContent = '--';

  dom.summaryRain.textContent = '--';
  dom.summaryRainRisk.textContent = '--';
  dom.summaryLight.textContent = '--';
  dom.summaryTemp.textContent = '--';
  dom.summaryWind.textContent = '--';
  renderDashboardDeviceStates(room, null);

  dom.iotPlatform.textContent = '--';
  dom.deviceId.textContent = '--';
  dom.lastSync.textContent = '--';
  dom.iotError.textContent = message || '--';
  dom.dashboardSource.textContent = message || 'Telemetrija sobe nije dostupna.';
  dom.iotStatus.textContent = formatConnectionStatus('no_telemetry');
  setStatusClass(dom.iotStatus, 'not_configured');
  resetDashboardControlValues();
}

function renderDashboardTelemetry(room, telemetryResponse, options = {}) {
  const { syncControls = true } = options;
  const dashboardTelemetry = selectedDashboardTelemetry(room, telemetryResponse);
  const controlTelemetry = dashboardTelemetry?.telemetry || {};
  const telemetry = telemetryResponse?.aggregated && Object.keys(telemetryResponse.aggregated).length > 0
    ? telemetryResponse.aggregated
    : controlTelemetry;
  renderDashboardDeviceStates(room, telemetryResponse);
  if (Object.keys(telemetry).length === 0 && Object.keys(controlTelemetry).length === 0) {
    renderDashboardPlaceholders(room, dashboardTelemetry?.message);
    return;
  }

  const isVirtual = dashboardTelemetry?.isVirtual !== false;
  const rainDetected = Boolean(telemetry.rainDetected);
  const rainRisk = rainProbabilityValue(telemetry);
  const rainIntensity = Number(telemetry.rainIntensity) || 0;
  const lux = Number(telemetry.lux) || 0;
  const indoorTemp = Number(telemetry.indoorTempC) || 0;
  const wind = Number(telemetry.windKmh) || 0;

  dom.siteArea.textContent = 'Soba';
  dom.siteName.textContent = room.name;
  dom.automationMode.textContent = 'SOBA';
  dom.weatherLine.textContent = `${rainRisk}% rizik kiše`;
  dom.lastUpdated.textContent = formatDate(dashboardTelemetry?.updatedAt);

  dom.rainValue.textContent = rainDetected ? 'Aktivno' : 'Mirno';
  dom.rainDetail.textContent = `${Math.round(rainIntensity)} intenzitet / ${Math.round(rainRisk)}% rizik`;
  dom.lightValue.textContent = formatLux(lux);
  dom.lightDetail.textContent = 'Telemetrija sobe';
  dom.tempValue.textContent = formatTemp(indoorTemp);
  dom.tempDetail.textContent = 'Telemetrija sobe';
  dom.windValue.textContent = `${Math.round(wind)} km/h`;
  dom.windDetail.textContent = 'Telemetrija sobe';

  dom.summaryRain.textContent = rainDetected ? 'Pada' : 'Ne pada';
  dom.summaryRainRisk.textContent = formatPercent(rainRisk);
  dom.summaryLight.textContent = formatLux(lux);
  dom.summaryTemp.textContent = formatTemp(indoorTemp);
  dom.summaryWind.textContent = `${Math.round(wind)} km/h`;

  dom.iotPlatform.textContent = isVirtual ? 'Simulacija' : 'ThingsBoard';
  dom.deviceId.textContent = dashboardTelemetry?.deviceName || '--';
  dom.lastSync.textContent = formatDate(dashboardTelemetry?.updatedAt);
  dom.iotError.textContent = dashboardTelemetry?.message || '--';
  dom.dashboardSource.textContent = isVirtual
    ? `Izvor: ${dashboardTelemetry?.deviceName || room.name} / simulacija`
    : `Izvor: ${dashboardTelemetry?.deviceName || room.name} / fizički ESP32`;

  dom.iotStatus.textContent = isVirtual ? 'simulacija' : formatConnectionStatus('physical');
  setStatusClass(dom.iotStatus, isVirtual ? 'configured' : 'connected');
  if (syncControls) {
    syncRoomDashboardInputs(Object.keys(controlTelemetry).length > 0 ? controlTelemetry : telemetry);
    const selectedRoom = selectedDashboardRoom();
    if (selectedRoom) {
      setRoomSimulationUi(selectedRoom);
    }
  }
}

async function sendCommand(target, action, positionPercent) {
  if (selectedDashboardRoomId) {
    const room = selectedDashboardRoom();
    const selectedDeviceId = dom.dashboardDeviceSelect.value || null;
    const selectedDevice = selectedDeviceId
      ? commandDevices(room).find((device) => device.id === selectedDeviceId)
      : null;
    if (selectedDevice && !deviceSupportsTarget(selectedDevice, target)) {
      throw new Error(apiErrorMessage('DEVICE_DOES_NOT_SUPPORT_CAPABILITY'));
    }

    const payload = {
      target,
      action,
      positionPercent,
      source: 'room-dashboard'
    };
    if (selectedDeviceId) {
      payload.localDeviceId = selectedDeviceId;
    }

    const result = await api(`/api/rooms/${selectedDashboardRoomId}/commands`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    const delivery = result.delivery === 'THINGSBOARD_RPC'
      ? 'ThingsBoard RPC'
      : result.delivery === 'POLLING' ? 'polling queue' : 'lokalna simulacija';
    showToast(`Sobna komanda: ${result.status} (${delivery}).`);
    dom.dashboardSource.textContent = `Zadnja komanda: ${result.target}/${result.action} -> ${result.deviceId}`;
    await loadSelectedDashboardRoomTelemetry();
    return;
  }

  showToast('Odaberite sobu s povezanim uređajem.');
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
      if (selectedDashboardRoomId) {
        currentRoomSimulation = await api(`/api/rooms/${selectedDashboardRoomId}/simulation`, {
          method: 'PATCH',
          body: JSON.stringify({
            rainDetected: dom.rainToggle.checked,
            rainIntensity: dom.rainToggle.checked ? 70 : 0,
            lux: Number(dom.luxInput.value),
            rainProbability: Number(dom.rainProbabilityInput.value),
            windKmh: Number(dom.windInput.value),
            indoorTempC: Number(dom.temperatureInput.value)
          })
        });
        applyRoomActionResponse(currentRoomSimulation);
        showToast('Simulacija sobe je primijenjena.');
        return;
      }

      showToast('Odaberite virtualnu sobu za simulaciju.');
    } catch (error) {
      showToast(error.message);
    }
  });

  dom.saveThresholdsButton.addEventListener('click', async () => {
    try {
      if (selectedDashboardRoomId) {
        const response = await api(`/api/rooms/${selectedDashboardRoomId}/automation/thresholds`, {
          method: 'PUT',
          body: JSON.stringify({
            rainProbabilityClose: Number(dom.thresholdRain.value),
            lightLuxShade: Number(dom.thresholdLux.value),
            indoorTempShadeC: Number(dom.thresholdTemp.value),
            windKphClose: Number(dom.thresholdWind.value)
          })
        });
        currentRoomThresholds = response.thresholds;
        syncRoomThresholdInputs(currentRoomThresholds);
        applyRoomActionResponse(response);
        showToast('Pravila sobe su spremljena.');
        return;
      }

      showToast('Odaberite sobu za spremanje pravila.');
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

  dom.dashboardRoomSelect.addEventListener('change', async () => {
    const value = dom.dashboardRoomSelect.value;
    selectedDashboardRoomId = value || null;
    await loadSelectedDashboardRoomTelemetry();
    startDashboardTelemetryPolling();
  });

  dom.dashboardDeviceSelect.addEventListener('change', async () => {
    if (!selectedDashboardRoomId) {
      return;
    }
    selectedDashboardDeviceIdByRoom.set(selectedDashboardRoomId, dom.dashboardDeviceSelect.value || '');
    const room = selectedDashboardRoom();
    const telemetry = room ? roomTelemetry.get(room.id) : null;
    if (room && telemetry) {
      renderDashboardTelemetry(room, telemetry);
      return;
    }
    await loadSelectedDashboardRoomTelemetry();
  });

  dom.simulationAutoButton.addEventListener('click', async () => {
    if (!selectedDashboardRoomId) {
      return;
    }
    try {
      currentRoomSimulation = await api(`/api/rooms/${selectedDashboardRoomId}/simulation/mode`, {
        method: 'PATCH',
        body: JSON.stringify({ mode: 'AUTO' })
      });
      setRoomSimulationUi(selectedDashboardRoom());
      showToast('Simulacija sobe je u AUTO načinu.');
    } catch (error) {
      showToast(error.message);
    }
  });

  dom.simulationManualButton.addEventListener('click', async () => {
    if (!selectedDashboardRoomId) {
      return;
    }
    try {
      currentRoomSimulation = await api(`/api/rooms/${selectedDashboardRoomId}/simulation/mode`, {
        method: 'PATCH',
        body: JSON.stringify({ mode: 'MANUAL' })
      });
      setRoomSimulationUi(selectedDashboardRoom());
      showToast('Simulacija sobe je u ručnom načinu.');
    } catch (error) {
      showToast(error.message);
    }
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
