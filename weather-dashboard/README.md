# Weather Dashboard

Proyecto pequeño que muestra un dashboard meteorológico usando Open-Meteo (sin API key).

Cómo ejecutar localmente:

1. Instala dependencias:

   cd weather-dashboard
   npm install

2. Ejecuta en desarrollo:

   npm run dev

3. Construir:

   npm run build

Notas:
- El workflow CI fija Node.js 18 para evitar problemas con la deprecación de Node 20 en runners.
- API usada: https://open-meteo.com/ (sin API key)
