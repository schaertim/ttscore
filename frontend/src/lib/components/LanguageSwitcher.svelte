<script lang="ts">
	import { locale, _ } from 'svelte-i18n';
	import { GlobeIcon } from 'phosphor-svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import * as Select from '$lib/components/ui/select/index.js';
	import { STORAGE_KEYS } from '$lib/storageKeys';

	const languages: { code: string; native: string }[] = [
		{ code: 'de', native: 'Deutsch' },
		{ code: 'fr', native: 'Français' },
		{ code: 'it', native: 'Italiano' },
		{ code: 'en', native: 'English' }
	];

	async function setLocale(code: string | undefined) {
		if (!code) return;
		await fetch('/api/locale', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ locale: code })
		});
		localStorage.setItem(STORAGE_KEYS.locale, code);
		locale.set(code);
	}

	// Seeded from the active locale once; the select owns the value afterwards.
	let selectedLocale = $state($locale ?? 'de');
	const currentNative = $derived(
		languages.find((l) => l.code === selectedLocale)?.native ?? selectedLocale
	);
</script>

<section class="space-y-3">
	<SectionLabel label={$_('account.language')} icon={GlobeIcon} />
	<Select.Root type="single" bind:value={selectedLocale} onValueChange={setLocale}>
		<Select.Trigger
			class="h-auto w-full rounded-xl border border-border bg-card px-4 py-3 font-semibold"
		>
			{currentNative}
		</Select.Trigger>
		<Select.Content>
			{#each languages as lang (lang.code)}
				<Select.Item value={lang.code}>{lang.native}</Select.Item>
			{/each}
		</Select.Content>
	</Select.Root>
</section>
