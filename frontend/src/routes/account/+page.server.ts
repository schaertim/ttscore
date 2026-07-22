import { redirect, fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { authedKtor } from '$lib/server/ktor';
import { unfollowAction, setNotifyAction } from '$lib/server/followActions';

export const load: PageServerLoad = async ({ locals, depends }) => {
	// Re-run when auth state changes so the profile refreshes after the
	// home player is set/removed client-side (SetPlayerSearch invalidates this).
	depends('supabase:auth');

	const { session } = await locals.safeGetSession();
	if (!session) redirect(303, '/signin');

	const ktor = authedKtor(session.access_token);

	const [profileRes, followsRes] = await Promise.all([ktor.get('/users/me'), ktor.get('/follows')]);

	const profile = profileRes.ok
		? await profileRes.json()
		: { homePlayerId: null, homePlayerName: null };

	const follows = followsRes.ok ? await followsRes.json() : [];

	const homePlayer = profile.homePlayerId
		? await ktor.get(`/players/${profile.homePlayerId}`).then((r) => (r.ok ? r.json() : null))
		: null;

	return { profile, follows, homePlayer };
};

export const actions: Actions = {
	removeHomePlayer: async ({ locals }) => {
		const { session } = await locals.safeGetSession();
		if (!session) redirect(303, '/signin');

		await authedKtor(session.access_token).delete('/users/me/home-player');
		return { success: true };
	},

	billingPortal: async ({ locals }) => {
		const { session } = await locals.safeGetSession();
		if (!session) redirect(303, '/signin');

		const res = await authedKtor(session.access_token).post('/billing/portal', {});
		if (!res.ok) return fail(res.status, { error: 'portal_failed' });

		const { url } = await res.json();
		redirect(303, url);
	},

	unfollow: unfollowAction,

	setNotify: setNotifyAction
};
