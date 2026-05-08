/**
 * Server-side Ktor API client.
 *
 * Protected routes forward the Supabase access token directly to Ktor.
 * Ktor verifies the token against the Supabase JWT secret (HS256) — no
 * custom signing needed.
 *
 * Usage in a +page.server.ts or +server.ts:
 *   const { session } = await locals.safeGetSession();
 *   const ktor = authedKtor(session.access_token);
 *   await ktor.post('/users/me/home-player', { playerId });
 */

import { PUBLIC_API_URL } from '$env/static/public';

const BASE = PUBLIC_API_URL + '/api/v1';

async function authedFetch(
	path: string,
	accessToken: string,
	options: RequestInit = {}
): Promise<Response> {
	return fetch(`${BASE}${path}`, {
		...options,
		headers: {
			'Content-Type': 'application/json',
			...options.headers,
			Authorization: `Bearer ${accessToken}`
		}
	});
}

export function authedKtor(accessToken: string) {
	return {
		get: (path: string) => authedFetch(path, accessToken),
		post: (path: string, body: unknown) =>
			authedFetch(path, accessToken, {
				method: 'POST',
				body: JSON.stringify(body)
			}),
		put: (path: string, body: unknown) =>
			authedFetch(path, accessToken, {
				method: 'PUT',
				body: JSON.stringify(body)
			}),
		delete: (path: string) => authedFetch(path, accessToken, { method: 'DELETE' })
	};
}
