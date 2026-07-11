/**
 * Shared SvelteKit form actions for follow / unfollow / notify, used by every page
 * that renders a FollowButton or NotifyButton (player, team, group, account, search).
 *
 * They forward to Ktor and translate the backend's 403 reasons (`follow_limit`,
 * `notify_pro`) into typed `fail(403, { reason })` results so the shared button
 * components can show the right Pro upsell.
 */
import { fail, type RequestEvent } from '@sveltejs/kit';
import { authedKtor } from './ktor';

export async function followAction({ locals, request }: RequestEvent) {
	const { session } = await locals.safeGetSession();
	if (!session) return fail(401, { error: 'Not authenticated' });

	const formData = await request.formData();
	const res = await authedKtor(session.access_token).post('/follows', {
		targetType: formData.get('targetType'),
		targetId: formData.get('targetId')
	});

	if (res.status === 403) {
		const body = await res.json().catch(() => ({}));
		return fail(403, { reason: (body?.reason as string) ?? 'follow_limit' });
	}
	if (!res.ok) return fail(500, { error: 'Failed' });

	const body = await res.json();
	return { followId: body.id };
}

export async function unfollowAction({ locals, request }: RequestEvent) {
	const { session } = await locals.safeGetSession();
	if (!session) return fail(401, { error: 'Not authenticated' });

	const formData = await request.formData();
	const res = await authedKtor(session.access_token).delete(`/follows/${formData.get('followId')}`);
	if (!res.ok) return fail(500, { error: 'Failed' });
	return { success: true };
}

export async function setNotifyAction({ locals, request }: RequestEvent) {
	const { session } = await locals.safeGetSession();
	if (!session) return fail(401, { error: 'Not authenticated' });

	const formData = await request.formData();
	const res = await authedKtor(session.access_token).patch(`/follows/${formData.get('followId')}`, {
		notify: formData.get('notify') === 'true'
	});

	if (res.status === 403) {
		const body = await res.json().catch(() => ({}));
		return fail(403, { reason: (body?.reason as string) ?? 'notify_pro' });
	}
	if (!res.ok) return fail(500, { error: 'Failed' });

	return { success: true };
}
