const GEOCODING_URL = 'https://geocoding-api.open-meteo.com/v1/search';
const FORECAST_URL = 'https://api.open-meteo.com/v1/forecast';

export async function geocode(city) {
  const url = `${GEOCODING_URL}?name=${encodeURIComponent(city)}&count=1&language=es&format=json`;
  const res = await fetch(url);
  if (!res.ok) throw new Error('Error en geocoding');
  const data = await res.json();
  if (!data.results || data.results.length === 0) throw new Error('Ciudad no encontrada');
  return data.results[0];
}

export async function fetchForecast({ latitude, longitude, timezone = 'auto' }) {
  const params = new URLSearchParams({
    latitude: latitude.toString(),
    longitude: longitude.toString(),
    hourly: 'temperature_2m,relativehumidity_2m,weathercode',
    timezone
  });
  const url = `${FORECAST_URL}?${params.toString()}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error('Error obteniendo el pronóstico');
  const data = await res.json();
  return data;
}
