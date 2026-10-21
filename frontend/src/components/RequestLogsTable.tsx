import { RecentLogsTable, type RecentLogsTableProps } from './RecentLogsTable';

/**
 * Alias de compatibilidad que re-exporta `RecentLogsTable` bajo el nombre `RequestLogsTable`.
 */
export type RequestLogsTableProps = RecentLogsTableProps;

/**
 * Re-exportación de `RecentLogsTable` como `RequestLogsTable` para mantener compatibilidad modular.
 */
export const RequestLogsTable = RecentLogsTable;
