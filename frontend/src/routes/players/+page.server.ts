import { fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { authedKtor } from '$lib/server/ktor';
import type { Player, FavoriteResponse } from '$lib/api';

export type FavoritePlayer = Player & { favoriteId: string };

export const load: PageServerLoad = async ({ locals }) => {
	const { session } = await locals.safeGetSession();

	let favoritePlayers: FavoritePlayer[] = [];

	if (session) {
		try {
			const [playersRes, favoritesRes] = await Promise.all([
				authedKtor(session.access_token).get('/favorites/players'),
				authedKtor(session.access_token).get('/favorites')
			]);

			const players: Player[] = playersRes.ok ? await playersRes.json() : [];
			const allFavorites: FavoriteResponse[] = favoritesRes.ok ? await favoritesRes.json() : [];

			const favoriteIdMap = new Map(
				allFavorites
					.filter((f) => f.targetType === 'player')
					.map((f) => [f.targetId, f.id])
			);

			favoritePlayers = players.map((p) => ({
				...p,
				favoriteId: favoriteIdMap.get(p.id) ?? ''
			}));
		} catch {
			// Not critical — search still works without favorites
		}
	}

	return { favoritePlayers };
};

export const actions: Actions = {
	unfavorite: async ({ request, locals }) => {
		const { session } = await locals.safeGetSession();
		if (!session) return fail(401, { message: 'Not authenticated' });

		const data = await request.formData();
		const favoriteId = data.get('favoriteId') as string;
		if (!favoriteId) return fail(400, { message: 'Missing favoriteId' });

		try {
			const res = await authedKtor(session.access_token).delete(`/favorites/${favoriteId}`);
			if (!res.ok) return fail(res.status, { message: 'Failed to remove favourite' });
		} catch {
			return fail(500, { message: 'Server error' });
		}

		return { success: true };
	}
};
