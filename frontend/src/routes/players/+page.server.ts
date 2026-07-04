import type { Actions, PageServerLoad } from './$types';
import { authedKtor } from '$lib/server/ktor';
import type { Player, FollowResponse } from '$lib/api';
import { unfollowAction } from '$lib/server/followActions';

export type FollowedPlayer = Player & { followId: string };

export const load: PageServerLoad = async ({ locals }) => {
	const { session } = await locals.safeGetSession();

	let favoritePlayers: FollowedPlayer[] = [];

	if (session) {
		try {
			const [playersRes, followsRes] = await Promise.all([
				authedKtor(session.access_token).get('/follows/players'),
				authedKtor(session.access_token).get('/follows')
			]);

			const players: Player[] = playersRes.ok ? await playersRes.json() : [];
			const allFollows: FollowResponse[] = followsRes.ok ? await followsRes.json() : [];

			const followIdMap = new Map(
				allFollows.filter((f) => f.targetType === 'player').map((f) => [f.targetId, f.id])
			);

			favoritePlayers = players.map((p) => ({
				...p,
				followId: followIdMap.get(p.id) ?? ''
			}));
		} catch {
			// Not critical — search still works without follows
		}
	}

	return { favoritePlayers };
};

export const actions: Actions = {
	unfollow: unfollowAction
};
