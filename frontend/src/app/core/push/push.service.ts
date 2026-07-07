import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Web-push wiring (Day 11). Registers a service worker, asks for Notification permission, and
 * registers this device's push token with the backend so the `linkup-push` pipeline can wake it
 * while offline.
 *
 * Without a Firebase project configured we register a **stub token** (dev): the whole backend
 * pipeline — presence dedup → outbox → dispatch — is still exercised and provable, and the SW
 * shows a notification for any real `push` event. Swapping in Firebase Messaging to obtain a real
 * FCM token is the only change needed to receive live pushes (see push-sw.js).
 */
@Injectable({ providedIn: 'root' })
export class PushService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  async init(deviceId: string): Promise<void> {
    try {
      if ('serviceWorker' in navigator) {
        await navigator.serviceWorker.register('push-sw.js');
      }
      if ('Notification' in window && Notification.permission === 'default') {
        await Notification.requestPermission();
      }
      // Real integration would exchange a VAPID/FCM subscription for a token here. In dev we
      // register a stable stub so the device is a valid push target for the pipeline.
      const token = await this.obtainToken(deviceId);
      await firstValueFrom(
        this.http.put<void>(`${this.base}/v1/devices/${deviceId}/push-token`, { pushToken: token }),
      );
    } catch {
      // Push is best-effort; never block the app if permission is denied or SW is unavailable.
    }
  }

  private async obtainToken(deviceId: string): Promise<string> {
    // TODO(Firebase): return getToken(messaging, { vapidKey, serviceWorkerRegistration }).
    return `web-stub-${deviceId}`;
  }
}
