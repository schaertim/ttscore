<script lang="ts">
	import type { PageData } from './$types';
	import { enhance } from '$app/forms';
	import * as Card from '$lib/components/ui/card/index.js';
	import * as Tabs from '$lib/components/ui/tabs/index.js';
	import InfoItem from '$lib/components/InfoItem.svelte';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import EloChart from '$lib/components/EloChart.svelte';
	import PlayerGameCard from '$lib/components/PlayerGameCard.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import PlayerHeader from '$lib/components/player/PlayerHeader.svelte';
	import StatsTab from '$lib/components/player/StatsTab.svelte';
	import CareerTab from '$lib/components/player/CareerTab.svelte';

	import { ChartLineIcon, ClockCounterClockwiseIcon, UserCirclePlusIcon } from 'phosphor-svelte';
	import ShowAllLink from '$lib/components/ShowAllLink.svelte';
	import { page } from '$app/state';
	import { replaceState } from '$app/navigation';
	import { _ } from 'svelte-i18n';
	import { classColorVar } from '$lib/utils';
	import { api } from '$lib/api';
	import type { Player, EloEntry, PlayerGame, PlayerSeasonStats } from '$lib/api';
	import { subscribe } from '$lib/push';
	import { requestNotificationPrimer } from '$lib/notificationPrimer.svelte';

	let { data }: { data: PageData } = $props();

	let settingHomePlayer = $state(false);

	// Shows the notification primer dialog first; only on acceptance do we request push
	// permission, fired right after that click so it has the best chance of counting as a
	// user gesture on strict browsers (Safari). Declining, an unsupported browser, or
	// already-decided permission is silently ignored — this must never block setting the
	// home player itself. Installing the app (a hard requirement for push on iOS) is asked
	// earlier, before account creation — see OnboardingModal.svelte's signUp().
	async function requestPushForHomePlayer() {
		const accepted = await requestNotificationPrimer();
		if (!accepted) return;
		const {
			data: { session }
		} = await data.supabase.auth.getSession();
		await subscribe(session?.access_token ?? '').catch(() => false);
	}

	// Synced in an effect (not just initialised) so client-side navigation between
	// players — which reuses this component — picks up the new player's follow state.
	let following = $state(false);
	let followId = $state<string | null>(null);
	let notify = $state(false);

	$effect.pre(() => {
		following = data.following;
		followId = data.followId;
		notify = data.notify;
	});

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

	// Class derived from the player's *current* ELO (live where available) — used to colour
	// the graph; match rows keep historical classes.
	const currentClass = $derived(player.liveClassification ?? player.classification);

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

	// Persist the active tab in the URL (?tab=…) so it survives reloads and back navigation.
	const TABS = ['overview', 'stats', 'career'];
	const activeTab = $derived(
		TABS.includes(page.url.searchParams.get('tab') ?? '')
			? page.url.searchParams.get('tab')!
			: 'overview'
	);

	function setTab(value: string) {
		const url = new URL(page.url);
		if (value === 'overview') url.searchParams.delete('tab');
		else url.searchParams.set('tab', value);
		replaceState(url, page.state);
	}
</script>

{#snippet eloCard(history: EloEntry[])}
	{#if history.length === 0 && syncing}
		<Skeleton class="h-52 rounded-none" />
	{:else}
		<div class="h-52">
			<EloChart series={[{ label: 'ELO', color: classColorVar(currentClass), history }]} />
		</div>
	{/if}
{/snippet}

{#snippet gameHistory(matches: PlayerGame[])}
	<section class="space-y-3">
		<SectionLabel label={$_('player.game_history')} icon={ClockCounterClockwiseIcon} />

		{#if matches.length === 0 && syncing}
			<div class="space-y-3">
				{#each [1, 2, 3] as n (n)}
					<Skeleton class="h-16 w-full rounded-2xl" />
				{/each}
			</div>
		{:else if matches.length === 0}
			<p class="py-8 text-center text-sm text-muted-foreground">{$_('player.no_matches')}</p>
		{:else}
			<div class="space-y-3">
				{#each matches.slice(0, 3) as game (game.gameId)}
					<PlayerGameCard {game} {syncing} />
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
	<PlayerHeader
		{player}
		{syncing}
		{canCompare}
		authenticated={!!data.user}
		bind:following
		bind:followId
		bind:notify
	/>

	{#if !data.user}
		<InfoItem
			href="/signin"
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
				requestPushForHomePlayer();
				return async ({ update }) => {
					settingHomePlayer = false;
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

	<Tabs.Root value={activeTab} onValueChange={setTab}>
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
			{#await data.streamed.career}
				<div class="space-y-6">
					<Skeleton class="h-56 w-full rounded-2xl" />
					<Skeleton class="h-24 w-full rounded-2xl" />
					<Skeleton class="h-40 w-full rounded-2xl" />
				</div>
			{:then career}
				{#if career}
					<CareerTab {career} playerId={player.id} />
				{:else}
					<p class="py-12 text-center text-sm text-muted-foreground">{$_('career.no_data')}</p>
				{/if}
			{/await}
		</Tabs.Content>
	</Tabs.Root>
</div>
