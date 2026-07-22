import { STORAGE_KEYS } from '$lib/storageKeys';

/**
 * Platform-detection helpers used by the onboarding flow (OnboardingModal.svelte) to show an
 * install step before account creation:
 *
 * - iOS Safari has no install API at all. Push notifications only work once the site is added
 *   to the home screen, so this is a hard requirement there, not a nicety — only instructions
 *   (Share → Add to Home Screen) can be shown, never triggered programmatically.
 * - Android/Chrome fires `beforeinstallprompt`, which we can trigger for real via
 *   `acceptInstall()`. Push already works there without installing, so it's a soft, skippable
 *   convenience step, not a gate.
 *
 * The `beforeinstallprompt` event itself is captured in app.html — a plain synchronous <script>
 * in <head>, before any framework code loads. Chrome can dispatch it as soon as it parses the
 * manifest link, often before this module (or any component's onMount) ever runs; a listener
 * attached that late can miss the one-time event entirely. app.html stashes it on
 * `window.__installPromptEvent`, which the functions below just read.
 */

interface BeforeInstallPromptEvent extends Event {
	prompt(): Promise<void>;
	userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

declare global {
	interface Window {
		__installPromptEvent?: BeforeInstallPromptEvent | null;
	}
}

function isStandalone(): boolean {
	return (
		window.matchMedia('(display-mode: standalone)').matches ||
		(navigator as unknown as { standalone?: boolean }).standalone === true
	);
}

function isIOS(): boolean {
	// iPadOS 13+ reports as "MacIntel" in the UA, same as a real Mac — touch support is what
	// actually tells them apart.
	return (
		/iPad|iPhone|iPod/.test(navigator.userAgent) ||
		(navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
	);
}

function isAndroid(): boolean {
	return /Android/.test(navigator.userAgent);
}

function alreadyDismissed(): boolean {
	return localStorage.getItem(STORAGE_KEYS.installPrimerDismissed) === '1';
}

/**
 * Which install step (if any) should be shown right now. Null means skip entirely — already
 * installed, already dismissed once before, on desktop, or a mobile browser with no install path
 * at all (Chrome that hasn't fired the event yet this session). A manual install via the
 * browser's own menu is always still available regardless.
 *
 * Desktop Chrome fires `beforeinstallprompt` too (that's what used to power the address-bar
 * install chip), so `window.__installPromptEvent` alone isn't enough to tell mobile Android
 * apart from desktop — hence the explicit `isAndroid()` check below.
 */
export function installTarget(): 'ios' | 'android' | null {
	if (isStandalone() || alreadyDismissed()) return null;
	if (isIOS()) return 'ios';
	if (isAndroid() && window.__installPromptEvent) return 'android';
	return null;
}

/** Android only — triggers the real browser install prompt. */
export async function acceptInstall() {
	const event = window.__installPromptEvent;
	if (event) {
		await event.prompt();
		await event.userChoice;
		window.__installPromptEvent = null;
	}
}

/** Marks the install step as seen so it's never shown again. */
export function markInstallPrimerSeen() {
	localStorage.setItem(STORAGE_KEYS.installPrimerDismissed, '1');
}
