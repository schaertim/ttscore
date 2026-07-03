import { api } from '$lib/api';
import { error, fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { authedKtor } from '$lib/server/ktor';

export const load: PageServerLoad = async ({ params, locals }) => {
	const id = params.id;

	const [group, standings, matches] = await Promise.all([
		api.groups.get(id).catch(() => null),
		api.groups.standings(id).catch(() => null),
		api.groups.matches(id).catch(() => null)
	]);

	if (!group || !standings) error(404, 'Group not found');

	const { session } = await locals.safeGetSession();

	let following = false;
	let followId: string | null = null;
	let notify = false;

	if (session) {
		const ktor = authedKtor(session.access_token);
		const followRes = await ktor
			.get(`/follows/check?targetType=division_group&targetId=${id}`)
			.catch(() => null);
		if (followRes && followRes.ok) {
			const d = await followRes.json();
			following = d.following;
			followId = d.followId ?? null;
			notify = d.notify;
		}
	}

	return { group, standings, matches: matches ?? [], following, followId, notify };
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
