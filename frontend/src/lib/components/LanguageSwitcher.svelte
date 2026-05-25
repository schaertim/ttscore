<script lang="ts">
	import { locale, _ } from 'svelte-i18n';
	import { GlobeIcon } from 'phosphor-svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';

	const languages: { code: string; label: string; native: string }[] = [
		{ code: 'de', label: 'German', native: 'Deutsch' },
		{ code: 'fr', label: 'French', native: 'Français' },
		{ code: 'it', label: 'Italian', native: 'Italiano' },
		{ code: 'en', label: 'English', native: 'English' }
	];

	async function setLocale(code: string) {
		// Persist to cookie via API
		await fetch('/api/locale', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ locale: code })
		});
		// Also mirror in localStorage for browser-side detection
		localStorage.setItem('ttfeed_locale', code);
		// Update the reactive store — components re-render immediately
		locale.set(code);
	}
</script>

<section class="space-y-3">
	<SectionLabel label={$_('account.language')} icon={GlobeIcon} />
	<div class="divide-y divide-border/50 overflow-hidden rounded-xl border border-border bg-card">
		{#each languages as lang (lang.code)}
			{@const active = $locale === lang.code}
			<button
				onclick={() => setLocale(lang.code)}
				class="flex w-full items-center justify-between px-4 py-3 text-left transition-colors hover:bg-accent"
			>
				<span class="font-semibold">{lang.native}</span>
				{#if active}
					<span class="text-xs font-bold text-primary">✓</span>
				{/if}
			</button>
		{/each}
	</div>
</section>
