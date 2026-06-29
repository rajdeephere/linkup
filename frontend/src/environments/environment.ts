/**
 * Dev environment. apiBaseUrl points at the backend on 8081 (we moved off 8080 to
 * dodge a native Apache). A prod build would file-replace this with environment.prod.ts.
 */
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8081',
  // Native WebSocket STOMP endpoint (no SockJS — only dep is @stomp/stompjs).
  wsUrl: 'ws://localhost:8081/ws',
};
