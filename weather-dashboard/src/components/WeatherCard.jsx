import React from 'react';

export default function WeatherCard({ place, forecast }) {
  if (!forecast) return null;

  const temp = forecast.hourly?.temperature_2m?.[0];
  const humidity = forecast.hourly?.relativehumidity_2m?.[0];
  const weathercode = forecast.hourly?.weathercode?.[0];

  return (
    <div className="card">
      <h2>{place.name}, {place.country}</h2>
      <p>Lat: {place.latitude.toFixed(3)} — Lon: {place.longitude.toFixed(3)}</p>
      <div className="metrics">
        <div><strong>Temp (°C)</strong><div>{temp ?? '—'}</div></div>
        <div><strong>Humedad (%)</strong><div>{humidity ?? '—'}</div></div>
        <div><strong>Código</strong><div>{weathercode ?? '—'}</div></div>
      </div>
    </div>
  );
}
