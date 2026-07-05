import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MessageAttachment } from '../messages/message.models';

interface PresignUploadResponse {
  blobKey: string;
  uploadUrl: string;
  contentType: string;
  expiresInSeconds: number;
}
interface DownloadUrlResponse {
  url: string;
  expiresInSeconds: number;
}

/**
 * Direct-to-blob media (Day 10, ADR-0005). The bytes NEVER go through our API: we ask the
 * backend for a presigned URL, then PUT the file straight to object storage (MinIO/S3), and
 * send a chat message carrying only the returned `blobKey`. Display works the same way in
 * reverse — a presigned GET URL, cached so we don't re-sign on every render.
 */
@Injectable({ providedIn: 'root' })
export class MediaService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;
  private urlCache = new Map<string, string>();

  /**
   * Upload a file directly to blob storage and return the attachment reference to send.
   * Probes image dimensions / audio duration client-side so the bubble can lay out instantly.
   */
  async upload(file: File): Promise<MessageAttachment> {
    const meta = await this.probe(file);

    const presign = await firstValueFrom(
      this.http.post<PresignUploadResponse>(`${this.base}/v1/media/presign`, {
        filename: file.name,
        contentType: file.type,
        sizeBytes: file.size,
      }),
    );

    // PUT the raw bytes to storage — bypasses our app tier entirely. The Content-Type MUST
    // match what was signed, or the storage rejects the signature.
    const res = await fetch(presign.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': presign.contentType },
      body: file,
    });
    if (!res.ok) {
      throw new Error(`Upload failed (${res.status})`);
    }

    return {
      blobKey: presign.blobKey,
      mimeType: file.type,
      sizeBytes: file.size,
      width: meta.width,
      height: meta.height,
      durationMs: meta.durationMs,
    };
  }

  /** Resolve (and cache) a short-lived presigned GET URL for displaying private media. */
  async resolveUrl(blobKey: string): Promise<string> {
    const cached = this.urlCache.get(blobKey);
    if (cached) return cached;
    const res = await firstValueFrom(
      this.http.get<DownloadUrlResponse>(`${this.base}/v1/media/download-url`, {
        params: { key: blobKey },
      }),
    );
    this.urlCache.set(blobKey, res.url);
    return res.url;
  }

  /** Pull image dimensions / audio duration locally (best-effort — never blocks a send). */
  private async probe(file: File): Promise<{ width?: number; height?: number; durationMs?: number }> {
    const url = URL.createObjectURL(file);
    try {
      if (file.type.startsWith('image/')) {
        const img = await this.loadImage(url);
        return { width: img.naturalWidth, height: img.naturalHeight };
      }
      if (file.type.startsWith('audio/')) {
        const ms = await this.loadAudioDuration(url);
        return { durationMs: ms };
      }
      return {};
    } catch {
      return {}; // metadata is a nicety, not a requirement
    } finally {
      URL.revokeObjectURL(url);
    }
  }

  private loadImage(url: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = reject;
      img.src = url;
    });
  }

  private loadAudioDuration(url: string): Promise<number | undefined> {
    return new Promise((resolve) => {
      const audio = new Audio();
      audio.preload = 'metadata';
      audio.onloadedmetadata = () => {
        const d = audio.duration;
        resolve(Number.isFinite(d) ? Math.round(d * 1000) : undefined);
      };
      audio.onerror = () => resolve(undefined);
      audio.src = url;
    });
  }
}
