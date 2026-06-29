<script lang="ts">
	import type { PageData } from './$types';
	import { enhance } from '$app/forms';
	import * as Card from '$lib/components/ui/card/index.js';
	import * as Tabs from '$lib/components/ui/tabs/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { AnnotationLine, LineChart, Spline } from 'layerchart';
	import { scaleLinear, scaleUtc } from 'd3-scale';
	import * as Chart from '$lib/components/ui/chart/index.js';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import GameCard from '$lib/components/GameCard.svelte';
	import FavoriteButton from '$lib/components/FavoriteButton.svelte';
	import NotifyButton from '$lib/components/NotifyButton.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import StatsTab from '$lib/components/player/StatsTab.svelte';
	import { h2h } from '$lib/h2h.svelte';
	import { curveMonotoneX } from 'd3-shape';

	import {
		ChartLineIcon,
		ClockCounterClockwiseIcon,
		UserCirclePlusIcon,
		ScalesIcon,
		CaretRightIcon
	} from 'phosphor-svelte';
	import ShowAllLink from '$lib/components/ShowAllLink.svelte';
	import { page } from '$app/state';
	import { _, locale } from 'svelte-i18n';
	import { ELO_THRESHOLDS, formatName } from '$lib/utils';
	let { data }: { data: PageData } = $props();

	let settingHomePlayer = $state(false);

	let isFavorite = $state(data.favorited);
	let favoriteId = $state(data.favoriteId);
	let notifying = $state(data.notifying);
	let notifyId = $state(data.notifyId);

	// Class derived from the player's *current* ELO (live where available). This is what we show on
	// the profile/home/search surfaces and use to colour the graph — match rows keep historical classes.
	const currentClass = $derived(data.player.liveClassification ?? data.player.classification);
	const displayElo = $derived(data.player.liveElo ?? data.player.currentElo);

	const canCompare = $derived(!!data.homePlayerId && data.homePlayerId !== data.player.id);

	function compareWithMe() {
		h2h.opponentId = data.player.id;
	}

	function classificationStroke(classification: string | null | undefined): string {
		if (!classification) return 'var(--color-primary)';
		const letter = classification[0].toLowerCase();
		return ['a', 'b', 'c', 'd', 'e'].includes(letter)
			? `var(--class-${letter})`
			: 'var(--color-primary)';
	}
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<div class="flex items-center justify-between">
			<BackButton class="" />
			<div class="flex items-center">
				<FavoriteButton
					bind:isFavorite
					bind:favoriteId
					targetType="player"
					targetId={data.player.id}
					authenticated={!!data.user}
				/>
				<NotifyButton
					bind:notifying
					bind:notifyId
					targetType="player"
					targetId={data.player.id}
					authenticated={!!data.user}
				/>
			</div>
		</div>

		<div class="flex items-start justify-between gap-4">
			<div class="min-w-0">
				<div class="mb-1 flex items-center gap-2">
					<PageTitle class="wrap-break-word">
						{formatName(data.player.fullName)}
					</PageTitle>
					<ClassBadge classification={currentClass} size="lg" />
				</div>
				<p class="text-sm text-muted-foreground">
					{data.player.currentClubName ?? 'No Club'}
				</p>
			</div>

			<div class="flex shrink-0 flex-col items-end gap-1">
				{#if displayElo}
					<span class="text-4xl leading-none font-black tabular-nums">{displayElo}</span>
					<span class="text-xs tracking-widest text-muted-foreground uppercase"> ELO </span>
				{/if}
			</div>
		</div>

		{#if canCompare}
			<button
				type="button"
				onclick={compareWithMe}
				class="flex w-full items-center gap-2 rounded-xl border border-border bg-card px-4 py-2.5 text-sm text-muted-foreground transition-colors hover:bg-accent"
			>
				<ScalesIcon size="16" class="text-primary" />
				<span class="font-medium">{$_('h2h.compare_with_me')}</span>
				<CaretRightIcon size="14" class="ml-auto" />
			</button>
		{/if}
	</header>

	{#if !data.user}
		<a
			href="/signin?redirectTo={encodeURIComponent(page.url.pathname)}"
			class="flex items-center gap-4 rounded-2xl border border-primary/20 bg-primary/5 p-4 transition-colors hover:bg-primary/10"
		>
			<div class="shrink-0 rounded-full bg-primary/10 p-2">
				<UserCirclePlusIcon size="20" class="text-primary" />
			</div>
			<div class="min-w-0 flex-1">
				<p class="text-sm font-semibold">{$_('set_player.is_this_you')}</p>
				<p class="text-xs text-muted-foreground">{$_('set_player.sign_in_prompt')}</p>
			</div>
			<span class="shrink-0 text-xs font-semibold text-primary">{$_('set_player.sign_in_cta')}</span
			>
		</a>
	{:else if !data.hasHomePlayer}
		<form
			method="POST"
			action="?/setHomePlayer"
			use:enhance={() => {
				settingHomePlayer = true;
				return async ({ result, update }) => {
					settingHomePlayer = false;
					if (result.type !== 'success') {
						await update();
					}
				};
			}}
		>
			<button
				type="submit"
				disabled={settingHomePlayer}
				class="flex w-full items-center gap-4 rounded-2xl border border-primary/20 bg-primary/5 p-4 text-left transition-colors hover:bg-primary/10 disabled:opacity-50"
			>
				<div class="shrink-0 rounded-full bg-primary/10 p-2">
					<UserCirclePlusIcon size="20" class="text-primary" />
				</div>
				<div class="min-w-0 flex-1">
					<p class="text-sm font-semibold">{$_('set_player.set_title')}</p>
					<p class="text-xs text-muted-foreground">{$_('set_player.set_desc')}</p>
				</div>
				<span class="shrink-0 text-xs font-semibold text-primary">
					{settingHomePlayer ? $_('set_player.setting') : $_('set_player.set_cta')}
				</span>
			</button>
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
				<Card.Root class="border-border/50 py-4">
					{#await data.streamed.elo}
						<Skeleton class="h-52 rounded-none" />
					{:then eloHistory}
						{#if eloHistory.length < 2}
							<div class="flex h-40 items-center justify-center">
								<p class="text-sm text-muted-foreground">{$_('player.no_elo_data')}</p>
							</div>
						{:else}
							{@const eloPoints = eloHistory.map((e) => ({
								date: new Date(e.recordedAt),
								value: e.eloValue
							}))}
							{@const minElo = Math.min(...eloPoints.map((p) => p.value))}
							{@const maxElo = Math.max(...eloPoints.map((p) => p.value))}
							{@const pad = 30}
							{@const yMin = minElo - pad}
							{@const yMax = maxElo + pad}
							{@const visibleThresholds = ELO_THRESHOLDS.filter(
								([elo]) => elo > yMin && elo < yMax
							)}
							{@const color = classificationStroke(currentClass)}
							{@const chartConfig = { value: { label: 'ELO', color } } satisfies Chart.ChartConfig}
							<div class="h-52 p-4">
								<Chart.Container config={chartConfig} class="aspect-auto h-full">
									<LineChart
										data={eloPoints}
										x="date"
										xScale={scaleUtc()}
										yScale={scaleLinear()}
										yDomain={[yMin, yMax]}
										axis="x"
										series={[{ key: 'value', label: 'ELO', color }]}
										props={{
											xAxis: {
												format: (v: Date) =>
													v.toLocaleDateString($locale ?? 'de', { month: 'short', year: '2-digit' })
											},
											highlight: { points: { r: 4 } }
										}}
									>
										{#snippet marks({ context })}
											{#each context.series.visibleSeries as s (s.key)}
												<Spline seriesKey={s.key} strokeWidth={2} curve={curveMonotoneX} />
											{/each}
											{#each visibleThresholds as [elo, label] (elo)}
												<AnnotationLine
													y={elo}
													{label}
													labelPlacement="right"
													stroke="white"
													strokeOpacity={0.2}
													props={{
														line: { 'stroke-dasharray': '4 3' },
														label: { class: 'fill-white/40 text-2xs' }
													}}
												/>
											{/each}
										{/snippet}
										{#snippet tooltip()}
											<Chart.Tooltip hideLabel />
										{/snippet}
									</LineChart>
								</Chart.Container>
							</div>
						{/if}
					{/await}
				</Card.Root>
			</section>

			{#await data.streamed.matches}
				<section class="space-y-3">
					<Skeleton class="h-3 w-24 rounded" />
					{#each [1, 2, 3, 4] as n (n)}
						<Skeleton class="h-16 w-full rounded-2xl" />
					{/each}
				</section>
			{:then matches}
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
			{/await}
		</Tabs.Content>

		<Tabs.Content value="stats" class="mt-4">
			{#await data.streamed.seasonStats}
				<div class="space-y-6">
					<Skeleton class="h-24 w-full rounded-2xl" />
					<Skeleton class="h-72 w-full rounded-2xl" />
					<Skeleton class="h-52 w-full rounded-2xl" />
				</div>
			{:then seasonStats}
				<StatsTab stats={seasonStats} />
			{/await}
		</Tabs.Content>

		<Tabs.Content value="career" class="mt-4">
			<p class="py-12 text-center text-sm text-muted-foreground">
				{$_('player.career_coming_soon')}
			</p>
		</Tabs.Content>
	</Tabs.Root>
</div>

