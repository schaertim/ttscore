<script lang="ts">
	import type { Player, PlayerGame, EloEntry } from '$lib/api';
	import { line as d3line } from 'd3-shape';
	import { scaleLinear } from 'd3-scale';
	import { curveMonotoneX } from 'd3-shape';
	import { ArrowUpIcon, ArrowDownIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';
	import { formatName } from '$lib/utils';

	interface Props {
		player: Player;
		recentMatches: Promise<PlayerGame[]>;
		eloHistory: Promise<EloEntry[]>;
	}

	let { player, recentMatches, eloHistory }: Props = $props();

	const currentClass = $derived(player.liveClassification ?? player.classification);
	const classificationLetter = $derived((currentClass?.[0] ?? '').toLowerCase());
	const classificationVar = $derived(
		classificationLetter ? `var(--class-${classificationLetter})` : 'var(--muted-foreground)'
	);

	const CHART_HEIGHT = 90;
	const RIGHT_PAD = 28; // gap between line end and card edge

	let chartWidth = $state(0);

	function buildSparklineData(history: EloEntry[]) {
		return history.slice(-10).map((e, i) => ({ index: i, value: e.eloValue }));
	}

	function buildSparklineSvg(pts: { index: number; value: number }[], width: number) {
		if (pts.length < 2 || width === 0) return null;
		const minVal = Math.min(...pts.map((p) => p.value));
		const maxVal = Math.max(...pts.map((p) => p.value));
		const range = Math.max(maxVal - minVal, 10);

		const xScale = scaleLinear().domain([0, pts.length - 1]).range([0, width - RIGHT_PAD]);
		const yScale = scaleLinear()
			.domain([minVal - range * 0.3, maxVal + range * 0.08])
			.range([CHART_HEIGHT, 0]);

		const pathFn = d3line<{ index: number; value: number }>()
			.x((d) => xScale(d.index))
			.y((d) => yScale(d.value))
			.curve(curveMonotoneX);

		const lastPt = pts[pts.length - 1];
		return {
			d: pathFn(pts) ?? '',
			dotX: xScale(lastPt.index),
			dotY: yScale(lastPt.value)
		};
	}

	function monthDelta(matches: PlayerGame[]): number {
		const lastRatedDate = matches
			.filter((g) => !g.eloDeltaProvisional && g.eloDelta != null && g.playedAt != null)
			.map((g) => g.playedAt!)
			.sort()
			.at(-1);
		return matches
			.filter(
				(g) =>
					g.eloDeltaProvisional &&
					g.eloDelta != null &&
					(lastRatedDate == null || g.playedAt! > lastRatedDate)
			)
			.reduce((sum, g) => sum + Math.round(g.eloDelta ?? 0), 0);
	}

	const displayElo = $derived(player.liveElo ?? player.currentElo);
</script>

<a href="/players/{player.id}" class="block">
	<div
		class="relative overflow-hidden rounded-2xl border border-border/50 bg-card"
		style="background-image: radial-gradient(circle at calc(100% - 3.25rem) 3.25rem, color-mix(in srgb, {classificationVar} 32%, transparent) 0%, color-mix(in srgb, {classificationVar} 7%, transparent) 30%, transparent 56%);"
	>
		<div class="p-6 pb-0">
			<!-- Top row: name/club left, class badge right -->
			<div class="flex items-start justify-between gap-4">
				<div class="min-w-0">
					<h1 class="text-3xl leading-tight font-black tracking-tight">
						{formatName(player.fullName)}
					</h1>
					{#if player.currentClubName}
						<p class="text-sm text-muted-foreground">{player.currentClubName}</p>
					{/if}
				</div>

				{#if currentClass}
					<div
						class="flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl text-xl font-black"
						style="background: {classificationVar}; color: var(--card);"
					>
						{currentClass}
					</div>
				{/if}
			</div>

			<!-- Centered ELO -->
			{#if displayElo}
				<div class="py-6 text-center">
					<p class="mb-1 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
						{$_('home.elo_rating')}
					</p>
					<p class="text-6xl font-black tabular-nums leading-none">{displayElo}</p>
					{#await recentMatches then matches}
						{@const delta = monthDelta(matches)}
						{@const rounded = Math.round(delta)}
						{#if rounded !== 0}
							<p
								class="mt-2 inline-flex items-center gap-1 text-sm font-semibold {delta > 0
									? 'text-emerald-500'
									: 'text-red-500'}"
							>
								{#if delta > 0}
									<ArrowUpIcon size="13" weight="bold" />+{rounded} {$_('home.this_month')}
								{:else}
									<ArrowDownIcon size="13" weight="bold" />{rounded} {$_('home.this_month')}
								{/if}
							</p>
						{/if}
					{/await}
				</div>
			{/if}
		</div>

		<!-- Edge-to-edge sparkline with dot -->
		{#if displayElo}
			{#await eloHistory then history}
				{@const pts = buildSparklineData(history)}
				{#if pts.length >= 2}
					<div bind:clientWidth={chartWidth} class="w-full" aria-hidden="true">
						{#if chartWidth > 0}
							{@const sparkline = buildSparklineSvg(pts, chartWidth)}
							{#if sparkline}
								<svg width={chartWidth} height={CHART_HEIGHT} class="block">
									<path
										d={sparkline.d}
										stroke={classificationVar}
										stroke-width="2.5"
										fill="none"
										stroke-linecap="round"
									/>
									<!-- solid dot -->
									<circle
										cx={sparkline.dotX}
										cy={sparkline.dotY}
										r="6"
										fill={classificationVar}
									/>
								</svg>
							{/if}
						{/if}
					</div>
				{/if}
			{/await}
		{/if}
	</div>
</a>
