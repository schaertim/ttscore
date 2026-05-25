const API = '/api/v1/push';

function urlBase64ToUint8Array(base64String: string): Uint8Array {
	const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
	const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
	const rawData = atob(base64);
	return Uint8Array.from([...rawData].map((c) => c.charCodeAt(0)));
}

async function getVapidPublicKey(): Promise<string> {
	const res = await fetch(`${API}/vapid-public-key`);
	const { publicKey } = await res.json();
	return publicKey as string;
}

/** Returns the current push subscription, or null if not subscribed. */
export async function getSubscription(): Promise<PushSubscription | null> {
	if (!('serviceWorker' in navigator) || !('PushManager' in window)) return null;
	const reg = await navigator.serviceWorker.ready;
	return reg.pushManager.getSubscription();
}

/** Requests permission, subscribes to push, and saves the subscription on the backend. */
export async function subscribe(authToken: string): Promise<boolean> {
	if (!('serviceWorker' in navigator) || !('PushManager' in window)) return false;

	const permission = await Notification.requestPermission();
	if (permission !== 'granted') return false;

	const reg = await navigator.serviceWorker.ready;
	const vapidPublicKey = await getVapidPublicKey();

	const subscription = await reg.pushManager.subscribe({
		userVisibleOnly: true,
		applicationServerKey: urlBase64ToUint8Array(vapidPublicKey) as unknown as ArrayBuffer
	});

	const { endpoint, keys } = subscription.toJSON() as {
		endpoint: string;
		keys: { p256dh: string; auth: string };
	};

	await fetch(`${API}/subscriptions`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${authToken}` },
		body: JSON.stringify({ endpoint, p256dh: keys.p256dh, auth: keys.auth })
	});

	return true;
}

/** Unsubscribes from push and removes the subscription from the backend. */
export async function unsubscribe(authToken: string): Promise<void> {
	const sub = await getSubscription();
	if (!sub) return;

	const { endpoint } = sub.toJSON() as { endpoint: string };

	await fetch(`${API}/subscriptions`, {
		method: 'DELETE',
		headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${authToken}` },
		body: JSON.stringify({ endpoint })
	});

	await sub.unsubscribe();
}
