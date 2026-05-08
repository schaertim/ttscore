<script lang="ts">
	import type { PageData } from './$types';
	import * as Card from '$lib/components/ui/card/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { LineChart } from 'layerchart';
	import { scaleUtc } from 'd3-scale';
	import * as Chart from '$lib/components/ui/chart/index.js';
	import KlassBadge from '$lib/components/KlassBadge.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import GameCard from '$lib/components/GameCard.svelte';
	import FavoriteButton from '$lib/components/FavoriteButton.svelte';
	import NotifyButton from '$lib/components/NotifyButton.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { Star, ChartLine, ClockCounterClockwise } from 'phosphor-svelte';
	let { data }: { data: PageData } = $props();

	let favorited = $state(data.favorited);
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

<div class="mx-auto max-w-2xl space-y-6 p-4 pb-20">
	<header class="space-y-4">
		<div class="flex items-center justify-between mb-4">
			<BackButton class="mb-0" />
			{#if data.user}
				<div class="flex items-center gap-0.5">
					<FavoriteButton
						bind:favorited
						bind:favoriteId
						targetType="player"
						targetId={data.player.id}
					/>
					<NotifyButton
						bind:notifying
						bind:notifyId
						targetType="player"
						targetId={data.player.id}
					/>
				</div>
			{/if}
		</div>

		<div class="flex items-start justify-between gap-4">
			<div class="min-w-0">
				<h1 class="text-3xl leading-none font-black tracking-tighter break-words">
					{data.player.fullName}
				</h1>
				<p class="mt-1.5 text-sm text-muted-foreground">
					{data.player.currentClubName ?? 'No Club'}
				</p>
			</div>

			<div class="flex shrink-0 flex-col items-end gap-2">
				{#if data.player.currentElo}
					<span class="text-4xl leading-none font-black tabular-nums">{data.player.currentElo}</span>
					<span class="text-[10px] font-bold tracking-widest text-muted-foreground uppercase">ELO</span>
				{/if}
				<KlassBadge klass={data.player.klass} />
			</div>
		</div>
	</header>

	<!-- ELO History Chart -->
	<section class="space-y-2">
		<SectionLabel label="ELO History" icon={ChartLine} class="px-1" />
		<Card.Root class="overflow-hidden border-border/50">
			{#await data.streamed.elo}
				<Skeleton class="h-52 rounded-none" />
			{:then eloHistory}
				{#if eloHistory.length < 2}
					<div class="flex h-40 items-center justify-center">
						<p class="text-sm text-muted-foreground">Not enough ELO data yet.</p>
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
											v.toLocaleDateString('de-CH', { month: 'short', year: '2-digit' })
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

	<!-- Season Stats + Game History (single await) -->
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
			<Card.Root class="bg-card/50">
				<Card.Content class="flex flex-col items-center p-4">
					<span class="text-[10px] font-bold tracking-widest text-muted-foreground uppercase"
						>Wins</span
					>
					<span class="text-2xl font-black text-win">{wins}</span>
				</Card.Content>
			</Card.Root>
			<Card.Root class="bg-card/50">
				<Card.Content class="flex flex-col items-center p-4">
					<span class="text-[10px] font-bold tracking-widest text-muted-foreground uppercase"
						>Losses</span
					>
					<span class="text-2xl font-black text-loss">{losses}</span>
				</Card.Content>
			</Card.Root>
			<Card.Root class="bg-card/50">
				<Card.Content class="flex flex-col items-center p-4">
					<span class="text-[10px] font-bold tracking-widest text-muted-foreground uppercase"
						>Win %</span
					>
					<span class="text-2xl font-black">{winPct}%</span>
				</Card.Content>
			</Card.Root>
		</div>

		<section class="space-y-3">
			<SectionLabel label="Game History" icon={ClockCounterClockwise} class="px-1" />

			{#if matches.length === 0}
				<p class="py-8 text-center text-sm text-muted-foreground">No matches found.</p>
			{:else}
				<div class="space-y-4">
					{#each matches.slice(0, 3) as game (game.gameId)}
						<GameCard mode="player" {game} />
					{/each}
				</div>
				{#if matches.length > 3}
					<a
						href="/players/{data.player.id}/games"
						class="block pt-1 text-center text-xs font-bold tracking-widest text-muted-foreground uppercase hover:text-foreground"
					>
						Show Full History →
					</a>
				{/if}
			{/if}
		</section>
	{/await}
</div>
