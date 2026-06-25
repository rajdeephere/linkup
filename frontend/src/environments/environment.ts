/**
 * Dev environment. apiBaseUrl points at the backend on 8081 (we moved off 8080 to
 * dodge a native Apache). A prod build would file-replace this with environment.prod.ts.
 */
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8081',
  // WebSocket endpoint is wired in Day 3; kept here so config lives in one place.
  wsUrl: 'http://localhost:8081/ws',
};
