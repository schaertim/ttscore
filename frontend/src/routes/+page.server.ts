import type { PageServerLoad } from './$types';
import { api } from '$lib/api';
import { authedKtor } from '$lib/server/ktor';

export const load: PageServerLoad = async ({ locals }) => {
	const { session } = await locals.safeGetSession();

	const [seasons, federations] = await Promise.all([api.seasons.list(), api.federations.list()]);

	if (!session) {
		return { state: 'unauthenticated' as const, seasons, federations, player: null, streamed: null };
	}

	const ktor = authedKtor(session.access_token);
	const profileRes = await ktor.get('/users/me');
	const profile = profileRes.ok ? await profileRes.json() : { homePlayerId: null };

	if (!profile.homePlayerId) {
		return { state: 'no-home-player' as const, seasons, federations, player: null, streamed: null };
	}

	const homePlayerId: string = profile.homePlayerId;
	const player = await api.players.get(homePlayerId).catch(() => null);

	return {
		state: 'dashboard' as const,
		seasons,
		federations,
		player,
		streamed: {
			recentMatches: api.players.matches(homePlayerId),
			nextMatch: ktor
				.get(`/players/${homePlayerId}/next-match`)
				.then((res) => (res.status === 204 ? null : res.json())),
			follows: ktor.get('/follows').then((res) => (res.ok ? res.json() : []))
		}
	};
};
