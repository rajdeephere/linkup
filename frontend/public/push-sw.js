/* LinkUp push service worker (Day 11).
 *
 * Minimal by design: it handles a `push` event by showing a notification and setting the app
 * badge, and focuses the app on click. With a real FCM/VAPID subscription this receives live
 * pushes unchanged; in dev (stub token) it simply stays idle. It intentionally has NO fetch
 * handler, so it never interferes with app requests. */

self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = { title: 'LinkUp', body: event.data ? event.data.text() : 'New message' };
  }
  const title = data.title || 'LinkUp';
  const body = data.body || 'New message';
  const unread = Number(data.unreadCount || 0);

  event.waitUntil(
    (async () => {
      await self.registration.showNotification(title, {
        body,
        tag: data.conversationId || 'linkup',
        data: { conversationId: data.conversationId },
      });
      if (navigator.setAppBadge && unread > 0) {
        try {
          await navigator.setAppBadge(unread);
        } catch {
          /* badging is best-effort */
        }
      }
    })(),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    (async () => {
      const all = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
      const existing = all.find((c) => 'focus' in c);
      if (existing) return existing.focus();
      return self.clients.openWindow('/');
    })(),
  );
});
