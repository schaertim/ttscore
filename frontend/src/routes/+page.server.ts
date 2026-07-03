import type { PageServerLoad } from './$types';
import { redirect } from '@sveltejs/kit';
import { api } from '$lib/api';
import { authedKtor } from '$lib/server/ktor';

export const load: PageServerLoad = async ({ locals }) => {
	const { session } = await locals.safeGetSession();

	if (!session) {
		redirect(303, '/divisions');
	}

	const ktor = authedKtor(session.access_token);
	const profileRes = await ktor.get('/users/me');
	const profile = profileRes.ok ? await profileRes.json() : { homePlayerId: null };

	if (!profile.homePlayerId) {
		redirect(303, '/divisions');
	}

	const homePlayerId: string = profile.homePlayerId;
	const player = await api.players.get(homePlayerId).catch(() => null);

	return {
		state: 'dashboard' as const,
		player,
		streamed: {
			recentMatches: api.players.matches(homePlayerId),
			eloHistory: api.players.elo(homePlayerId),
			leagueContext: api.players.leagueContext(homePlayerId).catch(() => null),
			follows: ktor
				.get('/follows')
				.then((res) => (res.ok ? res.json() : []))
				.catch(() => [])
		}
	};
};
