import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals: { safeGetSession }, cookies }) => {
	const { session, user } = await safeGetSession();
	return {
		session,
		user,
		// Pass raw cookies to +layout.ts so it can reconstruct the Supabase client
		// server-side without a browser environment.
		cookies: cookies.getAll()
	};
};
