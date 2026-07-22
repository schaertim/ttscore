/// <reference types="@sveltejs/kit/types/service-worker" />
/// <reference lib="webworker" />

import { build, files, version } from '$service-worker';

declare const self: ServiceWorkerGlobalScope;

const CACHE = `ttscore-${version}`;
const ASSETS = [...build, ...files];

// Cache static assets on install
self.addEventListener('install', (event) => {
	event.waitUntil(
		caches
			.open(CACHE)
			.then((cache) => cache.addAll(ASSETS))
			.then(() => self.skipWaiting())
	);
});

// Remove old caches on activate
self.addEventListener('activate', (event) => {
	event.waitUntil(
		caches.keys().then(async (keys) => {
			for (const key of keys) {
				if (key !== CACHE) await caches.delete(key);
			}
			await self.clients.claim();
		})
	);
});

// Network-first for all requests; fall back to cache for navigation
self.addEventListener('fetch', (event) => {
	if (event.request.method !== 'GET') return;

	const url = new URL(event.request.url);
	// Only handle same-origin requests
	if (url.origin !== self.location.origin) return;

	event.respondWith(
		fetch(event.request)
			.then((response) => {
				if (response.ok) {
					const clone = response.clone();
					caches.open(CACHE).then((cache) => cache.put(event.request, clone));
				}
				return response;
			})
			.catch(() => caches.match(event.request).then((cached) => cached ?? Response.error()))
	);
});

// Show push notification
self.addEventListener('push', (event) => {
	if (!event.data) return;

	const { title, body, url } = event.data.json() as { title: string; body: string; url?: string };

	event.waitUntil(
		self.registration.showNotification(title, {
			body,
			icon: '/favicon.svg',
			data: { url: url ?? '/' }
		})
	);
});

// Open the app (or focus existing window) when notification is clicked
self.addEventListener('notificationclick', (event) => {
	event.notification.close();

	const targetUrl = (event.notification.data as { url: string }).url;

	event.waitUntil(
		self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
			for (const client of clientList) {
				if (client.url === targetUrl && 'focus' in client) {
					return client.focus();
				}
			}
			return self.clients.openWindow(targetUrl);
		})
	);
});
