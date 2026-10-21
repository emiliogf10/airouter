import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App';

/**
 * Punto de entrada de la aplicación React 19 para el Dashboard de AI Cost Router.
 * Monta el árbol de componentes dentro del contenedor `#root` en modo estricto.
 */
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
