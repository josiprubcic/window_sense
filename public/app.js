'use strict';

const dom = {
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
  indoorTemp: document.querySelector('#indoorTemp'),
  outdoorTemp: document.querySelector('#outdoorTemp'),
  rainLayer: document.querySelector('#rainLayer'),
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
  toast: document.querySelector('#toast')
};

let currentState = null;
let toastTimer = null;

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

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: {
      'content-type': 'application/json'
    },
    ...options
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || 'API zahtjev nije uspio.');
  }

  return data;
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

  dom.thresholdRain.value = state.automation.thresholds.rainProbabilityClose;
  dom.thresholdLux.value = state.automation.thresholds.lightLuxShade;
  dom.thresholdTemp.value = state.automation.thresholds.indoorTempShadeC;
  dom.thresholdWind.value = state.automation.thresholds.windKphClose;
}

function render(state) {
  currentState = state;
  document.documentElement.style.setProperty('--window-open', state.actuators.window.openPercent);
  document.documentElement.style.setProperty('--blinds-down', state.actuators.blinds.positionPercent);

  dom.siteArea.textContent = state.site.area;
  dom.siteName.textContent = state.site.name;
  dom.automationMode.textContent = state.automation.mode.toUpperCase();
  dom.lastUpdated.textContent = formatDate(state.updatedAt);
  dom.autoModeButton.classList.toggle('is-active', state.automation.mode === 'auto');
  dom.manualModeButton.classList.toggle('is-active', state.automation.mode === 'manual');

  const iotConnection = state.iot.connection || 'not_configured';
  dom.iotStatus.textContent = iotConnection.replaceAll('_', ' ');
  setStatusClass(dom.iotStatus, iotConnection);

  dom.rainValue.textContent = state.sensors.rainDetected ? 'Aktivno' : 'Mirno';
  dom.rainDetail.textContent = `${Math.round(state.sensors.rainIntensity)} intenzitet / ${Math.round(state.weather.rainProbability)}% prognoza`;
  dom.lightValue.textContent = formatLux(state.sensors.lightLux);
  dom.lightDetail.textContent = `Prag zasjene ${formatLux(state.automation.thresholds.lightLuxShade)}`;
  dom.windowValue.textContent = formatPercent(state.actuators.window.openPercent);
  dom.windowDetail.textContent = state.sensors.windowContactOpen ? 'Kontakt: otvoren' : 'Kontakt: zatvoren';
  dom.blindsValue.textContent = formatPercent(state.actuators.blinds.positionPercent);
  dom.blindsDetail.textContent = '0% gore / 100% dolje';

  dom.weatherLine.textContent = `${state.weather.condition} / ${Math.round(state.weather.rainProbability)}% kisa / ${Math.round(state.weather.windKph)} km/h`;
  dom.indoorTemp.textContent = `Unutra ${Number(state.sensors.indoorTempC).toFixed(1)} C`;
  dom.outdoorTemp.textContent = `Vani ${Number(state.sensors.outdoorTempC).toFixed(1)} C`;
  dom.rainLayer.classList.toggle('is-active', state.sensors.rainDetected || state.weather.rainProbability >= 55);

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
          windKph: Number(dom.windInput.value)
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
    render(await api('/api/state'));
    startStream();
  } catch (error) {
    showToast(error.message);
  }
}

boot();
