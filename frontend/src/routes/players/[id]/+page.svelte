<script lang="ts">
	import type { PageData } from './$types';
	import { enhance } from '$app/forms';
	import * as Card from '$lib/components/ui/card/index.js';
	import * as Tabs from '$lib/components/ui/tabs/index.js';
	import InfoItem from '$lib/components/InfoItem.svelte';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { Spinner } from '$lib/components/ui/spinner/index.js';
	import EloChart from '$lib/components/EloChart.svelte';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import GameCard from '$lib/components/GameCard.svelte';
	import FollowButton from '$lib/components/FollowButton.svelte';
	import NotifyButton from '$lib/components/NotifyButton.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import StatsTab from '$lib/components/player/StatsTab.svelte';
	import CareerTab from '$lib/components/player/CareerTab.svelte';
	import PaywallTeaser from '$lib/components/PaywallTeaser.svelte';
	import { h2h } from '$lib/h2h.svelte';

	import {
		ChartLineIcon,
		ClockCounterClockwiseIcon,
		UserCirclePlusIcon,
		ScalesIcon
	} from 'phosphor-svelte';
	import ShowAllLink from '$lib/components/ShowAllLink.svelte';
	import { page } from '$app/state';
	import { _ } from 'svelte-i18n';
	import { formatName } from '$lib/utils';
	import { api } from '$lib/api';
	import type { Player, EloEntry, PlayerGame, PlayerSeasonStats } from '$lib/api';
	import { analytics } from '$lib/analytics';
	let { data }: { data: PageData } = $props();

	let settingHomePlayer = $state(false);

	let following = $state(data.following);
	let followId = $state(data.followId);
	let notify = $state(data.notify);

	// Data refetched after the on-demand sync, tagged with the player id it belongs to so an override
	// never bleeds onto a different profile after navigation. Until it is set the page shows the streamed
	// DB snapshot; once set it replaces that snapshot in place — same value type, so no skeleton flash.
	type FreshData = {
		id: string;
		player: Player | null;
		elo: EloEntry[] | null;
		matches: PlayerGame[] | null;
		stats: PlayerSeasonStats | null;
	};
	let fresh = $state<FreshData | null>(null);
	// Player id for which the sync attempt (success or failure) has finished — set only once the
	// client-side effect below has run, unlike `data.player.isSyncing` which is known synchronously
	// from `load()` (and during SSR). Deriving `syncing` from that instead means the indicator is
	// already correct on the very first paint, instead of waiting for hydration to flip it on.
	let syncDone = $state<string | null>(null);
	let handledSyncFor = $state<string | null>(null);

	const freshFor = $derived(fresh && fresh.id === data.player.id ? fresh : null);
	const player = $derived(freshFor?.player ?? data.player);
	const syncing = $derived(data.player.isSyncing && syncDone !== data.player.id);

	// Class derived from the player's *current* ELO (live where available). This is what we show on
	// the profile/home/search surfaces and use to colour the graph — match rows keep historical classes.
	const currentClass = $derived(player.liveClassification ?? player.classification);
	const displayElo = $derived(player.liveElo ?? player.currentElo);

	const canCompare = $derived(!!data.homePlayerId && data.homePlayerId !== player.id);

	// On-demand sync, once per player: the profile renders stale DB data instantly, then we ask the
	// backend to scrape click-tt (awaiting completion) and refetch the affected sections into `fresh`.
	$effect(() => {
		const target = data.player;
		if (!target.isSyncing || handledSyncFor === target.id) return;
		handledSyncFor = target.id;
		const id = target.id;
		(async () => {
			try {
				await api.players.sync(id);
				const [p, elo, matches, stats] = await Promise.all([
					api.players.get(id).catch(() => null),
					api.players.elo(id).catch(() => null),
					api.players.matches(id).catch(() => null),
					api.players.seasonStats(id).catch(() => null)
				]);
				fresh = { id, player: p, elo, matches, stats };
			} finally {
				syncDone = id;
			}
		})();
	});

	function compareWithMe() {
		h2h.opponentId = data.player.id;
		analytics.h2hOpened(data.player.id, data.isPro);
	}

	function classificationStroke(classification: string | null | undefined): string {
		if (!classification) return 'var(--color-primary)';
		const letter = classification[0].toLowerCase();
		return ['a', 'b', 'c', 'd', 'e'].includes(letter)
			? `var(--class-${letter})`
			: 'var(--color-primary)';
	}
</script>

{#snippet eloCard(history: EloEntry[])}
	<div class="h-52">
		<EloChart series={[{ label: 'ELO', color: classificationStroke(currentClass), history }]} />
	</div>
{/snippet}

{#snippet gameHistory(matches: PlayerGame[])}
	<section class="space-y-3">
		<SectionLabel label={$_('player.game_history')} icon={ClockCounterClockwiseIcon} />

		{#if matches.length === 0}
			<p class="py-8 text-center text-sm text-muted-foreground">{$_('player.no_matches')}</p>
		{:else}
			<div class="space-y-3">
				{#each matches.slice(0, 3) as game (game.gameId)}
					<GameCard mode="player" {game} />
				{/each}
			</div>
			{#if matches.length > 3}
				<ShowAllLink
					href="/players/{data.player.id}/games"
					label={$_('player.show_full_history')}
				/>
			{/if}
		{/if}
	</section>
{/snippet}

<div class="space-y-6">
	<header class="space-y-4">
		<div class="flex items-center justify-between">
			<BackButton class="" />
			<div class="flex items-center">
				<FollowButton
					bind:following
					bind:followId
					bind:notify
					targetType="player"
					targetId={data.player.id}
					authenticated={!!data.user}
				/>
				<NotifyButton
					{following}
					{followId}
					bind:notify
					authenticated={!!data.user}
				/>
			</div>
		</div>

		<div class="flex items-start justify-between gap-4">
			<div class="min-w-0">
				<div class="mb-1 flex items-start gap-2">
					<PageTitle class="wrap-break-word">
						{formatName(player.fullName)}
					</PageTitle>
					<ClassBadge classification={currentClass} size="lg" class="mt-0.5 shrink-0" />
				</div>
				<p class="text-sm text-muted-foreground">
					{player.currentClubName ?? 'No Club'}
				</p>
			</div>

			<div class="flex shrink-0 flex-col items-end gap-1">
				{#if displayElo}
					<span class="font-mono text-4xl leading-none font-black tabular-nums">{displayElo}</span>
					{#if syncing}
						<span class="flex items-center gap-1 text-xs tracking-widest text-muted-foreground">
							<Spinner class="size-3.5" aria-label={$_('player.syncing')} />
							{$_('player.syncing')}
						</span>
					{:else}
						<span class="text-xs tracking-widest text-muted-foreground uppercase"> ELO </span>
					{/if}
				{/if}
			</div>
		</div>

		{#if canCompare}
			<InfoItem
				variant="muted"
				size="sm"
				icon={ScalesIcon}
				title={$_('h2h.compare_with_me')}
				onclick={compareWithMe}
			/>
		{/if}
	</header>

	{#if !data.user}
		<InfoItem
			href="/signin?redirectTo={encodeURIComponent(page.url.pathname)}"
			onclick={() => analytics.signupPrompted('set_player_prompt')}
			icon={UserCirclePlusIcon}
			title={$_('set_player.is_this_you')}
			description={$_('set_player.sign_in_prompt')}
		/>
	{:else if !data.hasHomePlayer}
		<form
			method="POST"
			action="?/setHomePlayer"
			use:enhance={() => {
				settingHomePlayer = true;
				return async ({ result, update }) => {
					settingHomePlayer = false;
					if (result.type === 'success') {
						analytics.homePlayerSet(data.player.id);
					}
					// Re-run load functions so `hasHomePlayer` flips to true and this item unmounts.
					await update();
				};
			}}
		>
			<InfoItem
				type="submit"
				disabled={settingHomePlayer}
				icon={UserCirclePlusIcon}
				title={$_('set_player.set_title')}
				description={$_('set_player.set_desc')}
			/>
		</form>
	{/if}

	<Tabs.Root value="overview">
		<Tabs.List class="w-full">
			<Tabs.Trigger value="overview" class="flex-1">{$_('player.tab_overview')}</Tabs.Trigger>
			<Tabs.Trigger value="stats" class="flex-1">{$_('player.tab_stats')}</Tabs.Trigger>
			<Tabs.Trigger value="career" class="flex-1">{$_('player.tab_career')}</Tabs.Trigger>
		</Tabs.List>

		<Tabs.Content value="overview" class="mt-4 space-y-6">
			<section class="space-y-3">
				<SectionLabel label={$_('player.elo_history')} icon={ChartLineIcon} />
				<Card.Root class="border-border/50 p-4">
					{#if freshFor?.elo}
						{@render eloCard(freshFor.elo)}
					{:else}
						{#await data.streamed.elo}
							<Skeleton class="h-52 rounded-none" />
						{:then eloHistory}
							{@render eloCard(eloHistory)}
						{/await}
					{/if}
				</Card.Root>
			</section>

			{#if freshFor?.matches}
				{@render gameHistory(freshFor.matches)}
			{:else}
				{#await data.streamed.matches}
					<section class="space-y-3">
						<Skeleton class="h-3 w-24 rounded" />
						{#each [1, 2, 3, 4] as n (n)}
							<Skeleton class="h-16 w-full rounded-2xl" />
						{/each}
					</section>
				{:then matches}
					{@render gameHistory(matches)}
				{/await}
			{/if}
		</Tabs.Content>

		<Tabs.Content value="stats" class="mt-4">
			{#if freshFor?.stats}
				<StatsTab stats={freshFor.stats} />
			{:else}
				{#await data.streamed.seasonStats}
					<div class="space-y-6">
						<Skeleton class="h-24 w-full rounded-2xl" />
						<Skeleton class="h-72 w-full rounded-2xl" />
						<Skeleton class="h-52 w-full rounded-2xl" />
					</div>
				{:then seasonStats}
					<StatsTab stats={seasonStats} />
				{/await}
			{/if}
		</Tabs.Content>

		<Tabs.Content value="career" class="mt-4">
			{#if data.isPro}
				{#await data.streamed.career}
					<div class="space-y-6">
						<Skeleton class="h-56 w-full rounded-2xl" />
						<Skeleton class="h-24 w-full rounded-2xl" />
						<Skeleton class="h-40 w-full rounded-2xl" />
					</div>
				{:then career}
					{#if career}
						<CareerTab {career} />
					{:else}
						<p class="py-12 text-center text-sm text-muted-foreground">{$_('career.no_data')}</p>
					{/if}
				{/await}
			{:else}
				<PaywallTeaser
					title={$_('career.paywall_title')}
					description={$_('career.paywall_desc')}
					source="career_paywall"
				/>
			{/if}
		</Tabs.Content>
	</Tabs.Root>
</div>

