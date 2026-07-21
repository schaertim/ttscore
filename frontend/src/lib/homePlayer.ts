import { PUBLIC_API_URL } from '$env/static/public';
import type { SupabaseClient } from '@supabase/supabase-js';
import { subscribe } from '$lib/push';

/**
 * Sets the signed-in user's home player from the browser.
 *
 * The account page does this via a server form action, but reusable components
 * (e.g. the set-player card on the league browser) run client-side, so they
 * forward the Supabase access token to Ktor directly — the same pattern used by
 * the push subscription helpers.
 *
 * Also requests push permission and subscribes, right alongside the PUT so the
 * browser prompt fires as close to the triggering click as possible (needed on
 * strict browsers like Safari, which only honor Notification.requestPermission()
 * shortly after a user gesture). Best-effort: denial or an unsupported browser must
 * never block setting the home player itself.
 */
export async function setHomePlayer(supabase: SupabaseClient, playerId: string): Promise<void> {
	const {
		data: { session }
	} = await supabase.auth.getSession();
	const token = session?.access_token ?? '';

	const [res] = await Promise.all([
		fetch(`${PUBLIC_API_URL}/api/v1/users/me/home-player`, {
			method: 'PUT',
			headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
			body: JSON.stringify({ playerId })
		}),
		subscribe(token).catch(() => false)
	]);

	if (!res.ok) {
		throw new Error(`Failed to set home player: ${res.status}`);
	}
}
