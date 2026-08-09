const defaultLocation = {
  lon: 37.620393,
  lat: 55.75396,
  zoom: 11,
};

const addressInput = document.getElementById("addressInput");
const suggestionsList = document.getElementById("suggestions");
const selectedAddress = document.getElementById("selectedAddress");
const bluetoothStatus = document.getElementById("bluetoothStatus");
const appStatus = document.getElementById("appStatus");
const connectBluetoothButton = document.getElementById("connectBluetoothButton");
const mapRoot = document.getElementById("map");

const state = {
  currentLocation: { ...defaultLocation },
  suggestAbortController: null,
  geocodeAbortController: null,
  map: null,
  marker: null,
};

initializeMap().catch(() => {
  setStatus("Не удалось загрузить карту.");
});

addressInput.addEventListener("input", debounce(handleAddressInput, 220));
addressInput.addEventListener("focus", () => {
  if (!addressInput.value.trim()) {
    setStatus("Введите адрес, и мы покажем его на карте.");
  }
});

document.addEventListener("click", (event) => {
  if (!suggestionsList.contains(event.target) && event.target !== addressInput) {
    clearSuggestions();
  }
});

connectBluetoothButton.addEventListener("click", async () => {
  if (!("bluetooth" in navigator)) {
    bluetoothStatus.textContent = "Bluetooth недоступен в этом браузере";
    setStatus("Не удалось открыть выбор Bluetooth-устройства.");
    return;
  }

  try {
    const device = await navigator.bluetooth.requestDevice({
      acceptAllDevices: true,
      optionalServices: [],
    });

    bluetoothStatus.textContent = device.name
      ? `Подключено: ${device.name}`
      : "Устройство выбрано";
    setStatus("Bluetooth-устройство подключено.");
  } catch (error) {
    if (error?.name === "NotFoundError") {
      bluetoothStatus.textContent = "Выбор устройства отменен";
      setStatus("Подключение Bluetooth было отменено.");
      return;
    }

    bluetoothStatus.textContent = "Подключение не выполнено";
    setStatus("Не удалось подключить Bluetooth-устройство.");
  }
});

async function handleAddressInput(event) {
  const query = event.target.value.trim();

  if (query.length < 3) {
    clearSuggestions();
    if (!query) {
      selectedAddress.textContent = "Ожидает выбора";
      setStatus("Готово к поиску адреса");
    }
    return;
  }

  if (state.suggestAbortController) {
    state.suggestAbortController.abort();
  }

  state.suggestAbortController = new AbortController();

  try {
    const response = await fetch(`/api/suggest?q=${encodeURIComponent(query)}`, {
      signal: state.suggestAbortController.signal,
    });

    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      throw new Error(payload.error || "suggest_failed");
    }

    const payload = await response.json();
    renderSuggestions(Array.isArray(payload.items) ? payload.items : []);
    setStatus(payload.items?.length ? "Выберите адрес из списка." : "Совпадений не найдено.");
  } catch (error) {
    if (error.name !== "AbortError") {
      clearSuggestions();
      setStatus(`Не удалось загрузить подсказки адресов: ${formatError(error.message)}`);
    }
  }
}

function renderSuggestions(items) {
  clearSuggestions();

  for (const item of items) {
    const entry = document.createElement("li");
    const button = document.createElement("button");
    const title = document.createElement("span");
    const subtitle = document.createElement("span");

    button.type = "button";
    button.className = "suggestion-item";
    title.className = "suggestion-title";
    subtitle.className = "suggestion-subtitle";

    title.textContent = item.title || "Без названия";
    subtitle.textContent = item.subtitle || "";

    button.append(title, subtitle);
    button.addEventListener("click", () => {
      selectSuggestion(item);
    });

    entry.append(button);
    suggestionsList.append(entry);
  }
}

async function selectSuggestion(item) {
  const address = [item.title, item.subtitle].filter(Boolean).join(", ");
  addressInput.value = address;
  selectedAddress.textContent = address || "Адрес выбран";
  clearSuggestions();
  setStatus("Обновляем карту...");

  if (state.geocodeAbortController) {
    state.geocodeAbortController.abort();
  }

  state.geocodeAbortController = new AbortController();

  try {
    const response = await fetch(`/api/geocode?address=${encodeURIComponent(address)}`, {
      signal: state.geocodeAbortController.signal,
    });

    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      throw new Error(payload.error || "geocode_failed");
    }

    const payload = await response.json();
    if (typeof payload.lon !== "number" || typeof payload.lat !== "number") {
      throw new Error("invalid_coordinates");
    }

    state.currentLocation = {
      lon: payload.lon,
      lat: payload.lat,
      zoom: 16,
    };

    updateMap(state.currentLocation);
    setStatus("Адрес выбран и показан на карте.");
  } catch (error) {
    if (error.name !== "AbortError") {
      setStatus(`Не удалось определить координаты: ${formatError(error.message)}`);
    }
  }
}

function updateMap(location) {
  if (!state.map || !state.marker) {
    return;
  }

  const center = [location.lon, location.lat];
  state.map.update({
    location: {
      center,
      zoom: location.zoom ?? 16,
      duration: 250,
    },
  });

  state.marker.update({
    coordinates: center,
  });
}

function clearSuggestions() {
  suggestionsList.innerHTML = "";
}

function setStatus(message) {
  appStatus.textContent = message;
}

function debounce(fn, delay) {
  let timeoutId;

  return (...args) => {
    window.clearTimeout(timeoutId);
    timeoutId = window.setTimeout(() => fn(...args), delay);
  };
}

async function initializeMap() {
  const apiKey = window.__APP_CONFIG__?.yandexMapsApiKey?.trim();
  if (!apiKey) {
    throw new Error("missing_api_key");
  }

  await loadYandexMapsScript(apiKey);

  await window.ymaps3.ready;

  const { YMap, YMapDefaultSchemeLayer, YMapDefaultFeaturesLayer, YMapMarker } = window.ymaps3;
  const center = [defaultLocation.lon, defaultLocation.lat];
  const pin = document.createElement("div");
  pin.className = "map-pin";
  pin.innerHTML = '<span class="map-pin-core"></span>';

  state.map = new YMap(mapRoot, {
    location: {
      center,
      zoom: defaultLocation.zoom,
    },
    behaviors: ["drag", "scrollZoom", "pinchZoom", "dblClick"],
  });

  state.map.addChild(new YMapDefaultSchemeLayer());
  state.map.addChild(new YMapDefaultFeaturesLayer());

  state.marker = new YMapMarker(
    {
      coordinates: center,
    },
    pin
  );

  state.map.addChild(state.marker);
}

function loadYandexMapsScript(apiKey) {
  if (window.ymaps3) {
    return Promise.resolve();
  }

  return new Promise((resolve, reject) => {
    const existing = document.querySelector('script[data-yandex-maps-script="true"]');
    if (existing) {
      existing.addEventListener("load", () => resolve(), { once: true });
      existing.addEventListener("error", () => reject(new Error("script_load_failed")), { once: true });
      return;
    }

    const script = document.createElement("script");
    script.src = `https://api-maps.yandex.ru/v3/?apikey=${encodeURIComponent(apiKey)}&lang=ru_RU`;
    script.async = true;
    script.dataset.yandexMapsScript = "true";
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("script_load_failed"));
    document.head.append(script);
  }).catch((error) => {
    if (error.message === "script_load_failed") {
      setStatus("Yandex Maps API не загрузился. Проверьте ключ и разрешения referer для localhost.");
    }
    throw error;
  });
}

function formatError(code) {
  switch (code) {
    case "missing_api_key":
      return "ключ Yandex API не задан на сервере";
    case "address_not_found":
      return "адрес не найден";
    case "invalid_coordinates":
      return "API вернул некорректные координаты";
    case "script_load_failed":
      return "скрипт карты не загрузился";
    case "suggest_failed":
      return "ошибка сервиса подсказок";
    case "geocode_failed":
      return "ошибка сервиса геокодирования";
    default:
      return code || "неизвестная ошибка";
  }
}
