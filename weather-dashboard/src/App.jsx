import React, { useState, useEffect } from 'react';
import { geocode, fetchForecast } from './api/weather';
import WeatherCard from './components/WeatherCard';
import './styles.css';

function useLocalCache(key, ttlMs = 5 * 60 * 1000) {
  const get = () => {
    try {
      const raw = localStorage.getItem(key);
      if (!raw) return null;
      const obj = JSON.parse(raw);
      if (Date.now() - obj.t > ttlMs) {
        localStorage.removeItem(key);
        return null;
      }
      return obj.v;
    } catch {
      return null;
    }
  };
  const set = (v) => {
    localStorage.setItem(key, JSON.stringify({ t: Date.now(), v }));
  };
  return { get, set };
}

export default function App() {
  const [q, setQ] = useState('Madrid');
  const [loading, setLoading] = useState(false);
  const [place, setPlace] = useState(null);
  const [forecast, setForecast] = useState(null);
  const [error, setError] = useState(null);

  const cacheGeo = useLocalCache('geo:' + q);
  const cacheForecast = useLocalCache('forecast:' + q);

  useEffect(() => {
    handleSearch(q);
    // eslint-disable-next-line
  }, []);

  async function handleSearch(term) {
    setError(null);
    setLoading(true);
    setForecast(null);
    try {
      const cachedGeo = cacheGeo.get();
      let placeData = cachedGeo;
      if (!cachedGeo) {
        placeData = await geocode(term);
        cacheGeo.set(placeData);
      }
      setPlace(placeData);

      const cachedForecast = cacheForecast.get();
      if (cachedForecast) {
        setForecast(cachedForecast);
      } else {
        const f = await fetchForecast({ latitude: placeData.latitude, longitude: placeData.longitude });
        cacheForecast.set(f);
        setForecast(f);
      }

    } catch (err) {
      setError(err.message || 'Error desconocido');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="app">
      <header>
        <h1>Weather Dashboard</h1>
        <form onSubmit={(e) => { e.preventDefault(); handleSearch(q); }}>
          <input aria-label="Buscar ciudad" value={q} onChange={e => setQ(e.target.value)} />
          <button type="submit" disabled={loading}>Buscar</button>
        </form>
      </header>

      {loading && <p>Buscando...</p>}
      {error && <p className="error">Error: {error}</p>}
      {place && forecast && <WeatherCard place={place} forecast={forecast} />}

      <footer>
        <small>Datos: Open‑Meteo — Sin API key</small>
      </footer>
    </div>
  );
}
