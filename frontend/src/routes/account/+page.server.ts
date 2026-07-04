import { fail, redirect } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { authedKtor } from '$lib/server/ktor';
import { unfollowAction, setNotifyAction } from '$lib/server/followActions';

export const load: PageServerLoad = async ({ locals }) => {
	const { session } = await locals.safeGetSession();
	if (!session) redirect(303, '/signin?redirectTo=/account');

	const ktor = authedKtor(session.access_token);

	const [profileRes, followsRes] = await Promise.all([
		ktor.get('/users/me'),
		ktor.get('/follows')
	]);

	const profile = profileRes.ok
		? await profileRes.json()
		: { homePlayerId: null, homePlayerName: null };

	const follows = followsRes.ok ? await followsRes.json() : [];

	const homePlayer = profile.homePlayerId
		? await ktor.get(`/players/${profile.homePlayerId}`).then(r => r.ok ? r.json() : null)
		: null;

	return { profile, follows, homePlayer };
};

export const actions: Actions = {
	setHomePlayer: async ({ locals, request }) => {
		const { session } = await locals.safeGetSession();
		if (!session) redirect(303, '/signin?redirectTo=/account');

		const formData = await request.formData();
		const playerId = formData.get('playerId') as string;
		if (!playerId) return fail(400, { error: 'No player selected' });

		let res: Response;
		try {
			res = await authedKtor(session.access_token).put('/users/me/home-player', { playerId });
		} catch (e) {
			return fail(500, { error: `Network error — is Ktor running? ${e}` });
		}

		if (!res.ok) {
			const body = await res.text().catch(() => '(no body)');
			return fail(500, { error: `Ktor ${res.status}: ${body}` });
		}

		return { success: true };
	},

	removeHomePlayer: async ({ locals }) => {
		const { session } = await locals.safeGetSession();
		if (!session) redirect(303, '/signin?redirectTo=/account');

		await authedKtor(session.access_token).delete('/users/me/home-player');
		return { success: true };
	},

	unfollow: unfollowAction,

	setNotify: setNotifyAction
};
