<script lang="ts">
	import { locale, _ } from 'svelte-i18n';
	import { GlobeIcon } from 'phosphor-svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { Select, SelectContent, SelectItem, SelectTrigger } from '$lib/components/ui/select/index.js';

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
		localStorage.setItem('ttscore_locale', code);
		locale.set(code);
	}

	let selectedLocale = $state($locale ?? 'de');
	const currentNative = $derived(languages.find((l) => l.code === selectedLocale)?.native ?? selectedLocale);
</script>

<section class="space-y-3">
	<SectionLabel label={$_('account.language')} icon={GlobeIcon} />
	<Select type="single" bind:value={selectedLocale} onValueChange={setLocale}>
		<SelectTrigger class="w-full rounded-xl border border-border bg-card px-4 py-3 h-auto font-semibold">
			{currentNative}
		</SelectTrigger>
		<SelectContent>
			{#each languages as lang (lang.code)}
				<SelectItem value={lang.code}>{lang.native}</SelectItem>
			{/each}
		</SelectContent>
	</Select>
</section>
