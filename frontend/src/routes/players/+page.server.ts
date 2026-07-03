import { fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { authedKtor } from '$lib/server/ktor';
import type { Player, FollowResponse } from '$lib/api';

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
	unfollow: async ({ request, locals }) => {
		const { session } = await locals.safeGetSession();
		if (!session) return fail(401, { message: 'Not authenticated' });

		const data = await request.formData();
		const followId = data.get('followId') as string;
		if (!followId) return fail(400, { message: 'Missing followId' });

		try {
			const res = await authedKtor(session.access_token).delete(`/follows/${followId}`);
			if (!res.ok) return fail(res.status, { message: 'Failed to unfollow' });
		} catch {
			return fail(500, { message: 'Server error' });
		}

		return { success: true };
	}
};
