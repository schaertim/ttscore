/**
 * Shared "why we need notifications" primer dialog — shown once, right before the native
 * permission prompt, so it doesn't fire out of the blue the instant a player is set as home
 * player. Mirrors the H2H drawer's pattern (`h2h.svelte.ts`): a shared rune store plus a single
 * dialog mounted in the root layout, driven imperatively from wherever push is requested.
 */

type PendingResolve = (accepted: boolean) => void;

export const notificationPrimer = $state<{ open: boolean }>({ open: false });

let resolvePending: PendingResolve | null = null;

function pushSupported(): boolean {
	return 'Notification' in window && 'serviceWorker' in navigator && 'PushManager' in window;
}

/**
 * Shows the primer dialog and resolves once the user picks an option.
 *
 * Skips the dialog (and resolves immediately) when push isn't supported, or when permission has
 * already been decided one way or the other — there's nothing to explain once granted, and
 * re-prompting after a denial wouldn't show a native prompt anyway.
 */
export function requestNotificationPrimer(): Promise<boolean> {
	if (!pushSupported() || Notification.permission !== 'default') {
		return Promise.resolve(pushSupported() && Notification.permission === 'granted');
	}

	notificationPrimer.open = true;
	return new Promise((resolve) => {
		resolvePending = resolve;
	});
}

/** Called by the dialog itself — on a button choice, or on backdrop/ESC dismissal. */
export function resolveNotificationPrimer(accepted: boolean) {
	notificationPrimer.open = false;
	resolvePending?.(accepted);
	resolvePending = null;
}
