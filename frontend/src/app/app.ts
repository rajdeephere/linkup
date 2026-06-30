import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { SocketService } from './core/realtime/socket.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private auth = inject(AuthService);
  private socket = inject(SocketService);
  private router = inject(Router);

  constructor() {
    // Global session-expiry handler: if the socket can't authenticate (expired/invalid
    // token), end the session and send the user to log in — instead of an infinite
    // reconnect loop. Wired at the root so it works regardless of the active route.
    this.socket.authFailure$.pipe(takeUntilDestroyed()).subscribe(() => {
      this.socket.disconnect();
      this.auth.logout();
      this.router.navigateByUrl('/login');
    });
  }
}
