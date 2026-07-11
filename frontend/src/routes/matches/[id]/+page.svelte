<script lang="ts">
	import type { PageData } from './$types';
	import { api, type MatchPreview as MatchPreviewData } from '$lib/api';
	import { ProResource } from '$lib/proResource.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import GameCard from '$lib/components/GameCard.svelte';
	import MatchPreview from '$lib/components/match/MatchPreview.svelte';
	import PaywallTeaser from '$lib/components/PaywallTeaser.svelte';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { _ } from 'svelte-i18n';

	let { data }: { data: PageData } = $props();

	const isScheduled = $derived(data.match.status === 'SCHEDULED');

	// Preview is Pro-gated — only Pro users fetch it (non-Pro see the paywall). Fetched
	// client-side so we can forward the Supabase access token, mirroring the H2H drawer.
	const preview = new ProResource<MatchPreviewData>();
	$effect(() => {
		if (!isScheduled || !data.isPro) return;
		const id = data.match.id;
		preview.load(data.supabase, (token) => api.matches.preview(id, token));
	});
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<BackButton />

		{#if !isScheduled}
			<div class="flex items-center gap-4">
				<p class="min-w-0 flex-1 text-right text-2xl font-black wrap-break-word">
					<a href="/teams/{data.match.homeTeamId}" class="hover:underline">{data.match.homeTeam}</a>
				</p>
				<span class="shrink-0 font-mono text-5xl font-black tracking-tight tabular-nums">
					{data.match.homeScore ?? '?'}<span class="mx-1">:</span>{data.match.awayScore ?? '?'}
				</span>
				<p class="min-w-0 flex-1 text-left text-2xl font-black wrap-break-word">
					<a href="/teams/{data.match.awayTeamId}" class="hover:underline">{data.match.awayTeam}</a>
				</p>
			</div>
		{/if}
	</header>

	{#if !isScheduled}
		<div class="space-y-3">
			{#each data.match.games as game, i (i)}
				<GameCard {game} />
			{/each}
		</div>
	{:else if !data.isPro}
		<PaywallTeaser title={$_('preview.pro_title')} description={$_('preview.pro_desc')} />
	{:else if preview.loading}
		<div class="space-y-4">
			<Skeleton class="h-24 w-full rounded-xl" />
			<Skeleton class="h-32 w-full rounded-xl" />
			<Skeleton class="h-40 w-full rounded-xl" />
		</div>
	{:else if preview.error || !preview.data}
		<p class="py-12 text-center text-sm text-muted-foreground">{$_('preview.unavailable')}</p>
	{:else}
		<MatchPreview preview={preview.data} />
	{/if}
</div>
