<script lang="ts">
	import type { PageData } from './$types';
	import { api, type MatchPreview as MatchPreviewData } from '$lib/api';
	import BackButton from '$lib/components/BackButton.svelte';
	import GameCard from '$lib/components/GameCard.svelte';
	import MatchPreview from '$lib/components/match/MatchPreview.svelte';
	import PaywallTeaser from '$lib/components/PaywallTeaser.svelte';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { _ } from 'svelte-i18n';

	let { data }: { data: PageData } = $props();

	const isScheduled = $derived(data.match.status === 'SCHEDULED');

	let preview = $state<MatchPreviewData | null>(null);
	let loading = $state(false);
	let error = $state(false);

	// Preview is Pro-gated — only Pro users fetch it (non-Pro see the paywall). Fetched client-side
	// so we can forward the Supabase access token, mirroring the H2H drawer.
	$effect(() => {
		if (!isScheduled || !data.isPro) return;
		const id = data.match.id;
		loading = true;
		error = false;
		preview = null;
		data.supabase.auth
			.getSession()
			.then(({ data: s }) => api.matches.preview(id, s.session?.access_token ?? ''))
			.then((res) => (preview = res))
			.catch(() => (error = true))
			.finally(() => (loading = false));
	});
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<BackButton class="" />

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
				<GameCard mode="match" {game} />
			{/each}
		</div>
	{:else if !data.isPro}
		<PaywallTeaser title={$_('preview.pro_title')} description={$_('preview.pro_desc')} />
	{:else if loading}
		<div class="space-y-4">
			<Skeleton class="h-24 w-full rounded-xl" />
			<Skeleton class="h-32 w-full rounded-xl" />
			<Skeleton class="h-40 w-full rounded-xl" />
		</div>
	{:else if error || !preview}
		<p class="py-12 text-center text-sm text-muted-foreground">{$_('preview.unavailable')}</p>
	{:else}
		<MatchPreview {preview} />
	{/if}
</div>
