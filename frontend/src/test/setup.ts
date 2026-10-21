import '@testing-library/jest-dom';

/**
 * Polyfill de `ResizeObserver` para el entorno `jsdom` de Vitest,
 * requerido por el contenedor responsivo (`ResponsiveContainer`) de `recharts`.
 */
class ResizeObserverMock {
  /** Inicia la observación simulada de un elemento del DOM. */
  observe(): void {}
  /** Detiene la observación simulada de un elemento del DOM. */
  unobserve(): void {}
  /** Desconecta todas las observaciones activas de la instancia. */
  disconnect(): void {}
}

if (typeof window !== 'undefined' && !window.ResizeObserver) {
  window.ResizeObserver = ResizeObserverMock;
}
