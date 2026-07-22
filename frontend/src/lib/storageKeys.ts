/**
 * Central registry of browser-storage keys (localStorage + the locale cookie).
 *
 * The literal values are historical and intentionally inconsistent — renaming them
 * would silently drop existing users' settings, so new keys should follow the
 * `ttscore:*` scheme while the old ones keep their original spelling.
 */
export const STORAGE_KEYS = {
	/** Dark-mode override ('dark' | 'light'). */
	theme: 'theme',
	/** UI language; also the name of the server-side locale cookie. */
	locale: 'ttscore_locale',
	/** Set once the onboarding carousel has been seen/dismissed. */
	onboarded: 'ttscore_onboarded',
	/** Recently viewed players (JSON array). */
	recentPlayers: 'ttscore:recent-players',
	/** Set once the home-page set-player banner has been dismissed. */
	setPlayerBannerDismissed: 'ttscore_set_player_banner_dismissed',
	/** Set once the install primer (iOS instructions / Android install prompt) has been shown. */
	installPrimerDismissed: 'ttscore_install_primer_dismissed'
} as const;
