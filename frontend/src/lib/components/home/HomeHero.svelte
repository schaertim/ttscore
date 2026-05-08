<script lang="ts">
	import type { Player, PlayerGame } from '$lib/api';

	interface Props {
		player: Player;
		recentMatches: Promise<PlayerGame[]>;
	}

	let { player, recentMatches }: Props = $props();

	const firstName = $derived(player.fullName.split(' ')[0]);
	const klassLetter = $derived((player.klass?.[0] ?? '').toUpperCase());
	const klassVar = $derived(klassLetter ? `var(--klass-${klassLetter.toLowerCase()})` : 'var(--muted-foreground)');
	const klassSubtle = $derived(klassLetter ? `var(--klass-${klassLetter.toLowerCase()}-subtle)` : 'var(--muted)');

	function buildSparklinePoints(matches: PlayerGame[], currentElo: number): number[] {
		const games = matches.filter((g) => g.eloDelta != null).slice(0, 10);
		if (games.length === 0) return [currentElo];
		// Games are newest-first. Reconstruct: walk from currentElo backwards.
		const eloPoints: number[] = new Array(games.length + 1);
		eloPoints[games.length] = currentElo;
		for (let i = games.length - 1; i >= 0; i--) {
			eloPoints[i] = eloPoints[i + 1] - (games[i].eloDelta ?? 0);
		}
		return eloPoints;
	}

	function buildLinePath(points: number[], w = 200, h = 40): string {
		if (points.length < 2) return '';
		const min = Math.min(...points);
		const max = Math.max(...points);
		const range = max - min || 10;
		return points
			.map((p, i) => {
				const x = (i / (points.length - 1)) * w;
				const y = h - ((p - min) / range) * (h - 4) - 2;
				return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
			})
			.join(' ');
	}

	function buildFillPath(points: number[], w = 200, h = 40): string {
		const line = buildLinePath(points, w, h);
		if (!line) return '';
		return `${line} L${w},${h} L0,${h} Z`;
	}

	function dotY(points: number[], h = 40): number {
		const min = Math.min(...points);
		const max = Math.max(...points);
		const range = max - min || 10;
		const last = points[points.length - 1];
		return h - ((last - min) / range) * (h - 4) - 2;
	}

	function deltaSum(matches: PlayerGame[], n = 5): number {
		return matches
			.filter((g) => g.eloDelta != null)
			.slice(0, n)
			.reduce((sum, g) => sum + (g.eloDelta ?? 0), 0);
	}
</script>

<div class="space-y-4 py-4">
	<p class="px-1 text-sm font-semibold text-muted-foreground">Hey, {firstName}</p>

	<a href="/players/{player.id}" class="block">
		<div
			class="rounded-2xl border border-border/50 p-5"
			style="background: {klassSubtle}"
		>
			<div class="flex items-start gap-4">
				<!-- Klass circle -->
				{#if klassLetter}
					<div
						class="flex h-16 w-16 shrink-0 items-center justify-center rounded-xl text-2xl font-black text-white"
						style="background: {klassVar}"
					>
						{klassLetter}
					</div>
				{/if}

				<!-- ELO + delta -->
				<div class="min-w-0 flex-1">
					<div class="flex items-baseline gap-2">
						<span class="text-4xl font-black tabular-nums leading-none">
							{player.currentElo ?? '—'}
						</span>
						<span class="text-xs font-bold tracking-widest text-muted-foreground uppercase">ELO</span>
					</div>
					{#await recentMatches then matches}
						{@const delta = deltaSum(matches)}
						{@const rounded = Math.round(delta)}
						{#if rounded !== 0}
							<p class="mt-1 text-sm font-bold {delta > 0 ? 'text-emerald-500' : 'text-red-500'}">
								{delta > 0 ? '+' : ''}{rounded} last 5
							</p>
						{/if}
					{/await}
					<p class="mt-1 text-xs text-muted-foreground">{player.currentClubName ?? ''}</p>
				</div>
			</div>

			<!-- Sparkline -->
			{#if player.currentElo}
				{#await recentMatches then matches}
					{@const pts = buildSparklinePoints(matches, player.currentElo)}
					{#if pts.length >= 2}
						<div class="mt-4 h-10 w-full">
							<svg
								viewBox="0 0 200 40"
								preserveAspectRatio="none"
								class="h-full w-full"
								aria-hidden="true"
							>
								<defs>
									<linearGradient id="hero-spark-{klassLetter}" x1="0" y1="0" x2="0" y2="1">
										<stop offset="0%" style="stop-color:{klassVar}; stop-opacity:0.35" />
										<stop offset="100%" style="stop-color:{klassVar}; stop-opacity:0" />
									</linearGradient>
								</defs>
								<path d={buildFillPath(pts)} fill="url(#hero-spark-{klassLetter})" />
								<path
									d={buildLinePath(pts)}
									fill="none"
									stroke={klassVar}
									stroke-width="2"
									stroke-linecap="round"
									stroke-linejoin="round"
								/>
								<circle cx="200" cy={dotY(pts)} r="3" fill={klassVar} />
							</svg>
						</div>
					{/if}
				{/await}
			{/if}
		</div>
	</a>
</div>
