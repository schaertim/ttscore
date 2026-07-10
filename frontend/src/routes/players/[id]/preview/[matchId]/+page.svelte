<script lang="ts">
	import type { PageData } from './$types';
	import { api, type PlayerMatchPreview } from '$lib/api';
	import BackButton from '$lib/components/BackButton.svelte';
	import PreviewHeader from '$lib/components/match/PreviewHeader.svelte';
	import OpponentDuelCard from '$lib/components/match/OpponentDuelCard.svelte';
	import PaywallTeaser from '$lib/components/PaywallTeaser.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { TargetIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';

	let { data }: { data: PageData } = $props();

	let preview = $state<PlayerMatchPreview | null>(null);
	let loading = $state(false);
	let error = $state(false);

	// Pro-gated, fetched client-side so we can forward the Supabase access token — same
	// pattern as the team match preview.
	$effect(() => {
		if (!data.isPro) return;
		const playerId = data.player.id;
		const matchId = data.matchId;
		loading = true;
		error = false;
		preview = null;
		data.supabase.auth
			.getSession()
			.then(({ data: s }) =>
				api.players.matchPreview(playerId, matchId, s.session?.access_token ?? '')
			)
			.then((res) => (preview = res))
			.catch(() => (error = true))
			.finally(() => (loading = false));
	});
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<BackButton class="" />
	</header>

	{#if !data.isPro}
		<PaywallTeaser title={$_('preview.pro_title')} description={$_('preview.pro_desc')} />
	{:else if loading}
		<div class="space-y-4">
			<Skeleton class="h-48 w-full rounded-2xl" />
			<Skeleton class="h-24 w-full rounded-xl" />
			<Skeleton class="h-40 w-full rounded-xl" />
		</div>
	{:else if error || !preview}
		<p class="py-12 text-center text-sm text-muted-foreground">{$_('preview.unavailable')}</p>
	{:else}
		<PreviewHeader fixture={preview} />

		<!-- One duel per opponent-roster player, strongest first -->
		{#if preview.duels.length > 0}
			<section class="space-y-2">
				<SectionLabel label={$_('preview.duels')} icon={TargetIcon} />
				<div class="space-y-2.5">
					{#each preview.duels as duel (duel.awayPlayer.id)}
						<OpponentDuelCard {duel} />
					{/each}
				</div>
			</section>
		{/if}
	{/if}
</div>
