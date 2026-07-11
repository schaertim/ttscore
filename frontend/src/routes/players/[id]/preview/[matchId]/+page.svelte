<script lang="ts">
	import type { PageData } from './$types';
	import { api, type PlayerMatchPreview } from '$lib/api';
	import { ProResource } from '$lib/proResource.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import PreviewHeader from '$lib/components/match/PreviewHeader.svelte';
	import OpponentDuelCard from '$lib/components/match/OpponentDuelCard.svelte';
	import PaywallTeaser from '$lib/components/PaywallTeaser.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { TargetIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';

	let { data }: { data: PageData } = $props();

	// Pro-gated, fetched client-side so we can forward the Supabase access token — same
	// pattern as the team match preview.
	const preview = new ProResource<PlayerMatchPreview>();
	$effect(() => {
		if (!data.isPro) return;
		const playerId = data.player.id;
		const matchId = data.matchId;
		preview.load(data.supabase, (token) => api.players.matchPreview(playerId, matchId, token));
	});
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<BackButton />
	</header>

	{#if !data.isPro}
		<PaywallTeaser title={$_('preview.pro_title')} description={$_('preview.pro_desc')} />
	{:else if preview.loading}
		<div class="space-y-4">
			<Skeleton class="h-48 w-full rounded-2xl" />
			<Skeleton class="h-24 w-full rounded-xl" />
			<Skeleton class="h-40 w-full rounded-xl" />
		</div>
	{:else if preview.error || !preview.data}
		<p class="py-12 text-center text-sm text-muted-foreground">{$_('preview.unavailable')}</p>
	{:else}
		{@const previewData = preview.data}
		<PreviewHeader fixture={previewData} />

		<!-- One duel per opponent-roster player, strongest first -->
		{#if previewData.duels.length > 0}
			<section class="space-y-2">
				<SectionLabel label={$_('preview.duels')} icon={TargetIcon} />
				<div class="space-y-2.5">
					{#each previewData.duels as duel (duel.awayPlayer.id)}
						<OpponentDuelCard {duel} />
					{/each}
				</div>
			</section>
		{/if}
	{/if}
</div>
