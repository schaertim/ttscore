import { error, fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { api } from '$lib/api';
import { authedKtor } from '$lib/server/ktor';

export const load: PageServerLoad = async ({ params, locals }) => {
	try {
		const team = await api.teams.get(params.id);
		if (!team) throw error(404, 'Team not found');

		const { session } = await locals.safeGetSession();

		let following = false;
		let followId: string | null = null;
		let notify = false;

		if (session) {
			const ktor = authedKtor(session.access_token);
			const followRes = await ktor
				.get(`/follows/check?targetType=team&targetId=${params.id}`)
				.catch(() => null);
			if (followRes && followRes.ok) {
				const d = await followRes.json();
				following = d.following;
				followId = d.followId ?? null;
				notify = d.notify;
			}
		}

		return {
			team,
			following,
			followId,
			notify,
			streamed: {
				roster: api.teams.roster(params.id),
				matches: api.teams.matches(params.id)
			}
		};
	} catch (e) {
		throw error(404, 'Team not found or backend unavailable');
	}
};

export const actions: Actions = {
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
		return { followId: body.id };
	},

	unfollow: async ({ locals, request }) => {
		const { session } = await locals.safeGetSession();
		if (!session) return fail(401, { error: 'Not authenticated' });
		const formData = await request.formData();
		await authedKtor(session.access_token).delete(`/follows/${formData.get('followId')}`);
		return { success: true };
	},

	setNotify: async ({ locals, request }) => {
		const { session } = await locals.safeGetSession();
		if (!session) return fail(401, { error: 'Not authenticated' });
		const formData = await request.formData();
		await authedKtor(session.access_token).patch(`/follows/${formData.get('followId')}`, {
			notify: formData.get('notify') === 'true'
		});
		return { success: true };
	}
};
