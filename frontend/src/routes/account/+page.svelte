<script lang="ts">
	import type { PageData } from './$types';
	import { goto, invalidate } from '$app/navigation';
	import { MoonIcon, PaintBrushHouseholdIcon, SunIcon } from 'phosphor-svelte';
	import { _, locale } from 'svelte-i18n';
	import { Button } from '$lib/components/ui/button/index.js';
	import { dateLong } from '$lib/date';
	import { theme } from '$lib/theme.svelte';
	import InfoItem from '$lib/components/InfoItem.svelte';
	import LanguageSwitcher from '$lib/components/LanguageSwitcher.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import FavoritesSection from '$lib/components/account/FavoritesSection.svelte';
	import MyPlayerSection from '$lib/components/account/MyPlayerSection.svelte';
	import ProSection from '$lib/components/account/ProSection.svelte';
	import PushSection from '$lib/components/account/PushSection.svelte';

	let { data }: { data: PageData } = $props();

	async function signOut() {
		await data.supabase.auth.signOut();
		await invalidate('supabase:auth');
		await goto('/');
	}

	const proRenewDate = $derived(dateLong(data.profile.proUntil, $locale));
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<div>
			<p class="mb-1 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
				{$_('account.subtitle')}
			</p>
			<PageTitle>{$_('account.title')}</PageTitle>
		</div>
	</header>

	<MyPlayerSection
		homePlayerId={data.profile.homePlayerId}
		homePlayerName={data.profile.homePlayerName}
		homePlayer={data.homePlayer}
		supabase={data.supabase}
	/>

	<FavoritesSection follows={data.follows} />

	<PushSection supabase={data.supabase} />

	<ProSection isPro={!!data.profile.isPro} renewDate={proRenewDate} />

	<section class="space-y-3">
		<SectionLabel label={$_('account.appearance')} icon={PaintBrushHouseholdIcon} />
		<InfoItem
			variant="muted"
			title={$_(theme.dark ? 'account.dark_mode' : 'account.light_mode')}
			onclick={() => theme.toggle()}
		>
			{#snippet trailing()}
				{#if theme.dark}
					<MoonIcon size={20} class="text-muted-foreground" />
				{:else}
					<SunIcon size={20} class="text-muted-foreground" />
				{/if}
			{/snippet}
		</InfoItem>
	</section>

	<LanguageSwitcher />

	<Button variant="destructive" onclick={signOut} class="w-full">{$_('account.sign_out')}</Button>
</div>
