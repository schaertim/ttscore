import posthog from 'posthog-js';
import { env } from '$env/dynamic/public';

// Sources for the two hurdles worth conversion attribution — see
// context/IMPLEMENTATION_PLAN_ANALYTICS.md for the full design.
export type SignupSource =
	| 'onboarding_modal'
	| 'follow_button'
	| 'notify_button'
	| 'set_player_prompt'
	| 'home_signin_banner'
	| 'navbar'
	| 'account_gate'
	| 'pro_gate';

export type ProSource =
	| 'follow_limit'
	| 'notify_limit'
	| 'h2h_paywall'
	| 'career_paywall'
	| 'account_upgrade';

let initialized = false;

/** Browser-only. No-op (and safe to call) when PUBLIC_POSTHOG_KEY is unset, e.g. local dev. */
export function initAnalytics(): void {
	const key = env.PUBLIC_POSTHOG_KEY;
	if (!key) return;

	posthog.init(key, {
		api_host: env.PUBLIC_POSTHOG_HOST || 'https://eu.i.posthog.com',
		person_profiles: 'identified_only',
		autocapture: false,
		capture_pageview: false,
		capture_pageleave: true,
		disable_session_recording: true
	});
	initialized = true;
}

function capture(event: string, properties?: Record<string, unknown>): void {
	if (!initialized) return;
	posthog.capture(event, properties);
}

export const analytics = {
	pageview: () => capture('$pageview'),

	identify: (userId: string, properties?: Record<string, unknown>) => {
		if (!initialized) return;
		posthog.identify(userId, properties);
	},
	reset: () => {
		if (!initialized) return;
		posthog.reset();
	},

	playerSearched: (queryLength: number, resultCount: number) =>
		capture('player_searched', { query_length: queryLength, result_count: resultCount }),

	// FollowButton is generic across players, teams, and division groups — keep target_type
	// alongside target_id so a team follow isn't mislabeled as a player follow.
	followed: (targetType: string, targetId: string) =>
		capture('followed', { target_type: targetType, target_id: targetId }),
	unfollowed: (targetType: string, targetId: string) =>
		capture('unfollowed', { target_type: targetType, target_id: targetId }),

	notificationsEnabled: (followId: string) =>
		capture('notifications_enabled', { follow_id: followId }),
	notificationsDisabled: (followId: string) =>
		capture('notifications_disabled', { follow_id: followId }),

	homePlayerSet: (playerId: string) => capture('home_player_set', { player_id: playerId }),

	h2hOpened: (opponentId: string, isPro: boolean) =>
		capture('h2h_opened', { opponent_id: opponentId, is_pro: isPro }),

	// Hurdle 1 — sign up. `signupPrompted` carries the entry point; `signedIn`/`signedUp`
	// are plain completion events — PostHog's funnel breakdown does the correlation.
	signupPrompted: (source: SignupSource) => capture('signup_prompted', { source }),
	signedIn: (method: 'google' | 'password') => capture('signed_in', { method }),
	signedUp: (method: 'google' | 'password') => capture('signed_up', { method }),

	// Hurdle 2 — subscribe to Pro. Same pattern.
	proPrompted: (source: ProSource) => capture('pro_prompted', { source }),
	proCheckoutStarted: (plan: 'monthly' | 'yearly') =>
		capture('pro_checkout_started', { plan })
};
