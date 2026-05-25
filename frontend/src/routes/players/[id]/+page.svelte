<script lang="ts">
	import type { PageData } from './$types';
	import { enhance } from '$app/forms';
	import * as Card from '$lib/components/ui/card/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { LineChart } from 'layerchart';
	import { scaleUtc } from 'd3-scale';
	import * as Chart from '$lib/components/ui/chart/index.js';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import GameCard from '$lib/components/GameCard.svelte';
	import FavoriteButton from '$lib/components/FavoriteButton.svelte';
	import NotifyButton from '$lib/components/NotifyButton.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { StarIcon, ChartLineIcon, ClockCounterClockwiseIcon, UserCirclePlusIcon } from 'phosphor-svelte';
	import ShowAllLink from '$lib/components/ShowAllLink.svelte';
	import { page } from '$app/state';
	import { _, locale } from 'svelte-i18n';
	let { data }: { data: PageData } = $props();

	let settingHomePlayer = $state(false);

	let isFavorite = $state(data.favorited);
	let favoriteId = $state(data.favoriteId);
	let notifying = $state(data.notifying);
	let notifyId = $state(data.notifyId);

	function klassStroke(klass: string | null | undefined): string {
		if (!klass) return 'var(--color-primary)';
		const letter = klass[0].toLowerCase();
		return ['a', 'b', 'c', 'd', 'e'].includes(letter)
			? `var(--klass-${letter})`
			: 'var(--color-primary)';
	}
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<div class="flex items-center justify-between">
			<BackButton class="" />
			<div class="flex items-center">
				<FavoriteButton bind:isFavorite bind:favoriteId targetType="player" targetId={data.player.id} authenticated={!!data.user} />
				<NotifyButton bind:notifying bind:notifyId targetType="player" targetId={data.player.id} authenticated={!!data.user} />
			</div>
		</div>

		<div class="flex items-start justify-between gap-4">
			<div class="min-w-0">
				<h1 class="text-3xl mb-1.5 leading-none font-black tracking-tighter wrap-break-word">
					{data.player.fullName}
				</h1>
				<p class="text-sm text-muted-foreground">
					{data.player.currentClubName ?? 'No Club'}
				</p>
			</div>

			<div class="flex shrink-0 flex-col items-end gap-1.5">
				{#if data.player.currentElo}
					<span class="text-4xl leading-none font-black tabular-nums">{data.player.currentElo}</span
					>
					<span class="text-[10px] font-medium tracking-widest text-muted-foreground uppercase"
						>ELO</span
					>
				{/if}
				<ClassBadge klass={data.player.klass} />
			</div>
		</div>
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
				<p class="text-sm font-bold">{$_("set_player.is_this_you")}</p>
				<p class="text-xs text-muted-foreground">{$_("set_player.sign_in_prompt")}</p>
			</div>
			<span class="shrink-0 text-xs font-bold text-primary">{$_("set_player.sign_in_cta")}</span>
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
					<p class="text-sm font-bold">{$_("set_player.set_title")}</p>
					<p class="text-xs text-muted-foreground">{$_("set_player.set_desc")}</p>
				</div>
				<span class="shrink-0 text-xs font-bold text-primary">
					{settingHomePlayer ? $_("set_player.setting") : $_("set_player.set_cta")}
				</span>
			</button>
		</form>
	{/if}

	<section class="space-y-2">
		<SectionLabel label={$_("player.elo_history")} icon={ChartLineIcon} />
		<Card.Root class="py-4 border-border/50">
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
					{@const color = klassStroke(data.player.klass)}
					{@const chartConfig = { value: { label: 'ELO', color } } satisfies Chart.ChartConfig}
					<div class="h-52 p-4">
						<Chart.Container config={chartConfig} class="aspect-auto h-full">
							<LineChart
								data={eloPoints}
								x="date"
								xScale={scaleUtc()}
								axis="x"
								series={[{ key: 'value', label: 'ELO', color }]}
								props={{
									spline: { strokeWidth: 2 },
									xAxis: {
										format: (v: Date) =>
											v.toLocaleDateString($locale ?? 'de', { month: 'short', year: '2-digit' })
									},
									highlight: { points: { r: 4 } }
								}}
							>
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
		<div class="grid grid-cols-3 gap-3">
			{#each [1, 2, 3] as _}
				<Card.Root class="bg-card/50">
					<Card.Content class="flex flex-col items-center gap-2 p-4">
						<Skeleton class="h-3 w-12" />
						<Skeleton class="h-7 w-8" />
					</Card.Content>
				</Card.Root>
			{/each}
		</div>
		<section class="space-y-2">
			<Skeleton class="h-3 w-24 rounded" />
			{#each [1, 2, 3, 4] as _}
				<Skeleton class="h-16 w-full rounded-2xl" />
			{/each}
		</section>
	{:then matches}
		{@const played = matches.filter((m) => m.result !== 'NOT_PLAYED')}
		{@const wins = played.filter(
			(m) =>
				(m.result === 'HOME' && m.playerSide === 'home') ||
				(m.result === 'AWAY' && m.playerSide === 'away')
		).length}
		{@const losses = played.length - wins}
		{@const winPct = played.length > 0 ? Math.round((wins / played.length) * 100) : 0}

		<div class="grid grid-cols-3 gap-3">
			<Card.Root class="bg-card/50 py-0">
				<Card.Content class="flex flex-col items-center py-4 px-4">
					<span class="text-[10px] font-medium tracking-widest text-muted-foreground uppercase"
						>{$_("player.wins")}</span
					>
					<span class="text-2xl font-black text-win">{wins}</span>
				</Card.Content>
			</Card.Root>
			<Card.Root class="bg-card/50 py-0">
				<Card.Content class="flex flex-col items-center py-4 px-4">
					<span class="text-[10px] font-medium tracking-widest text-muted-foreground uppercase"
						>{$_("player.losses")}</span
					>
					<span class="text-2xl font-black text-loss">{losses}</span>
				</Card.Content>
			</Card.Root>
			<Card.Root class="bg-card/50 py-0">
				<Card.Content class="flex flex-col items-center py-4 px-4">
					<span class="text-[10px] font-medium tracking-widest text-muted-foreground uppercase"
						>{$_("player.win_pct")}</span
					>
					<span class="text-2xl font-black">{winPct}%</span>
				</Card.Content>
			</Card.Root>
		</div>

		<section class="space-y-3">
			<SectionLabel label={$_("player.game_history")} icon={ClockCounterClockwiseIcon} />

			{#if matches.length === 0}
				<p class="py-8 text-center text-sm text-muted-foreground">{$_("player.no_matches")}</p>
			{:else}
				<div class="space-y-4">
					{#each matches.slice(0, 3) as game (game.gameId)}
						<GameCard mode="player" {game} />
					{/each}
				</div>
				{#if matches.length > 3}
					<ShowAllLink href="/players/{data.player.id}/games" label={$_("player.show_full_history")} />
				{/if}
			{/if}
		</section>
	{/await}
</div>
