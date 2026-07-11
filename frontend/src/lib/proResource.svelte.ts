import type { SupabaseClient } from '@supabase/supabase-js';

/**
 * Loading/error/data state for a Pro-gated, client-side fetch. Pro endpoints are fetched
 * from the browser so the Supabase access token can be forwarded to Ktor — this wraps the
 * shared getSession → fetch → state bookkeeping used by the match preview pages and the
 * H2H drawer.
 */
export class ProResource<T> {
	data = $state<T | null>(null);
	loading = $state(false);
	error = $state(false);

	/** Fetches via [fn] with the current access token, tracking loading/error state. */
	async load(supabase: SupabaseClient, fn: (accessToken: string) => Promise<T>): Promise<void> {
		this.loading = true;
		this.error = false;
		this.data = null;
		try {
			const { data: sessionData } = await supabase.auth.getSession();
			this.data = await fn(sessionData.session?.access_token ?? '');
		} catch {
			this.error = true;
		} finally {
			this.loading = false;
		}
	}
}
