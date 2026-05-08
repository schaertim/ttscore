import { error, fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { api } from '$lib/api';
import { authedKtor } from '$lib/server/ktor';

export const load: PageServerLoad = async ({ params, locals }) => {
	const player = await api.players.get(params.id).catch(() => null);
	if (!player) throw error(404, 'Player not found');

	const { session } = await locals.safeGetSession();

	let favorited = false;
	let favoriteId: string | null = null;
	let notifying = false;
	let notifyId: string | null = null;

	if (session) {
		const ktor = authedKtor(session.access_token);
		const [favRes, followRes] = await Promise.allSettled([
			ktor.get(`/favorites/check?targetType=player&targetId=${params.id}`),
			ktor.get(`/follows/check?targetType=player&targetId=${params.id}`)
		]);
		if (favRes.status === 'fulfilled' && favRes.value.ok) {
			const d = await favRes.value.json();
			favorited = d.favorited;
			favoriteId = d.favoriteId ?? null;
		}
		if (followRes.status === 'fulfilled' && followRes.value.ok) {
			const d = await followRes.value.json();
			notifying = d.notifying;
			notifyId = d.notifyId ?? null;
		}
	}

	return {
		player,
		favorited,
		favoriteId,
		notifying,
		notifyId,
		streamed: {
			elo: api.players.elo(params.id),
			matches: api.players.matches(params.id)
		}
	};
};

export const actions: Actions = {
	favorite: async ({ locals, request }) => {
		const { session } = await locals.safeGetSession();
		if (!session) return fail(401, { error: 'Not authenticated' });
		const formData = await request.formData();
		const res = await authedKtor(session.access_token).post('/favorites', {
			targetType: formData.get('targetType'),
			targetId: formData.get('targetId')
		});
		if (!res.ok) return fail(500, { error: 'Failed' });
		const body = await res.json();
		return { favoriteId: body.id };
	},

	unfavorite: async ({ locals, request }) => {
		const { session } = await locals.safeGetSession();
		if (!session) return fail(401, { error: 'Not authenticated' });
		const formData = await request.formData();
		await authedKtor(session.access_token).delete(`/favorites/${formData.get('favoriteId')}`);
		return { success: true };
	},

	follow: async ({ locals, request }) => {
		const { session } = await locals.safeGetSession();
		if (!session) return fail(401, { error: 'Not authenticated' });
		const formData = await request.formData();
		const res = await authedKtor(session.access_token).post('/follows', {
			targetType: formData.get('targetType'),
			targetId: formData.get('targetId')
		});
		if (!res.ok) return fail(500, { error: 'Failed' });
		const body = await res.json();
		return { notifyId: body.id };
	},

	unfollow: async ({ locals, request }) => {
		const { session } = await locals.safeGetSession();
		if (!session) return fail(401, { error: 'Not authenticated' });
		const formData = await request.formData();
		await authedKtor(session.access_token).delete(`/follows/${formData.get('notifyId')}`);
		return { success: true };
	}
};
