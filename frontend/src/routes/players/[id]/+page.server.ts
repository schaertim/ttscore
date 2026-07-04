import { error, fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { api } from '$lib/api';
import { authedKtor } from '$lib/server/ktor';
import { followAction, unfollowAction, setNotifyAction } from '$lib/server/followActions';

export const load: PageServerLoad = async ({ params, locals }) => {
	const player = await api.players.get(params.id).catch(() => null);
	if (!player) throw error(404, 'Player not found');

	const { session } = await locals.safeGetSession();

	let following = false;
	let followId: string | null = null;
	let notify = false;

	let isHomePlayer = false;
	let isPro = false;

	if (session) {
		const ktor = authedKtor(session.access_token);
		const [followRes, profileRes] = await Promise.allSettled([
			ktor.get(`/follows/check?targetType=player&targetId=${params.id}`),
			ktor.get('/users/me')
		]);
		if (followRes.status === 'fulfilled' && followRes.value.ok) {
			const d = await followRes.value.json();
			following = d.following;
			followId = d.followId ?? null;
			notify = d.notify;
		}
		if (profileRes.status === 'fulfilled' && profileRes.value.ok) {
			const profile = await profileRes.value.json();
			isHomePlayer = profile.homePlayerId === params.id;
			isPro = profile.isPro ?? false;
		}
	}

	// Career is a Pro feature — fetch it authenticated (server-side) only for Pro users,
	// since the Ktor endpoint is Pro-gated and the public api client sends no token.
	const career =
		session && isPro
			? authedKtor(session.access_token)
					.get(`/players/${params.id}/career`)
					.then((r) => (r.ok ? r.json() : null))
					.catch(() => null)
			: Promise.resolve(null);

	return {
		player,
		following,
		followId,
		notify,
		isHomePlayer,
		streamed: {
			elo: api.players.elo(params.id),
			matches: api.players.matches(params.id),
			seasonStats: api.players.seasonStats(params.id),
			career
		}
	};
};

export const actions: Actions = {
	follow: followAction,
	unfollow: unfollowAction,
	setNotify: setNotifyAction,

	setHomePlayer: async ({ locals, params }) => {
		const { session } = await locals.safeGetSession();
		if (!session) return fail(401, { error: 'Not authenticated' });
		const res = await authedKtor(session.access_token).put('/users/me/home-player', {
			playerId: params.id
		});
		if (!res.ok) return fail(500, { error: 'Failed to set home player' });
		return { success: true };
	}
};
