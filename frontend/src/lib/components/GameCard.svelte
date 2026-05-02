<script lang="ts">
	import type { Game, PlayerGame } from '$lib/api';
	import KlassBadge from '$lib/components/KlassBadge.svelte';
	import * as Card from '$lib/components/ui/card/index.js';

	let {
		mode = 'match',
		game
	}: {
		mode?: 'match' | 'player';
		game: Game | PlayerGame;
	} = $props();

	function lastName(name: string | null): string {
		if (!name) return '—';
		return name.split(' ').at(-1) ?? name;
	}

	function formatDate(dateStr: string | null | undefined): string {
		if (!dateStr) return '—';
		return new Date(dateStr).toLocaleDateString('de-CH', { day: '2-digit', month: '2-digit' });
	}

	function formatDelta(delta: number | null): string {
		if (delta == null) return '';
		const rounded = Math.round(delta);
		return rounded >= 0 ? `+${rounded}` : String(rounded);
	}

	function isWin(g: PlayerGame): boolean {
		return (
			(g.result === 'HOME' && g.playerSide === 'home') ||
			(g.result === 'AWAY' && g.playerSide === 'away')
		);
	}

	function playerSets(g: PlayerGame): number {
		return g.playerSide === 'home' ? (g.homeSets ?? 0) : (g.awaySets ?? 0);
	}

	function opponentSets(g: PlayerGame): number {
		return g.playerSide === 'home' ? (g.awaySets ?? 0) : (g.homeSets ?? 0);
	}
</script>

{#if mode === 'player'}
	{@const pg = game as PlayerGame}
	{@const won = isWin(pg)}
	{@const notPlayed = pg.result === 'NOT_PLAYED'}
	{@const scoreColor = won ? 'text-win' : notPlayed ? 'text-muted-foreground' : 'text-loss'}
	<a href="/matches/{pg.matchId}" class="block">
		<Card.Root>
			<div class="space-y-2 px-6">
				<p class="truncate text-[10px] font-bold tracking-widest text-muted-foreground uppercase">
					{formatDate(pg.playedAt)} · {pg.homeTeam} vs {pg.awayTeam}
				</p>

				<div class="flex items-baseline gap-4">
					<div class="flex min-w-0 flex-1 items-center gap-1.5">
						<svelte:element
							this={pg.opponentId ? 'a' : 'p'}
							href={pg.opponentId ? `/players/${pg.opponentId}` : undefined}
							class="min-w-0 truncate text-lg font-normal {pg.opponentId
								? 'hover:underline'
								: ''}"
							onclick={pg.opponentId ? (e: MouseEvent) => e.stopPropagation() : undefined}
						>{pg.opponentName ?? '—'}</svelte:element>
						<KlassBadge klass={pg.opponentKlass} />
					</div>

					<div class="flex shrink-0 flex-col items-end gap-0.5">
						<p class="text-3xl font-black tabular-nums {scoreColor}">
							{playerSets(pg)}:{opponentSets(pg)}
						</p>
						{#if pg.eloDelta != null}
							<span class="text-xs font-black text-muted-foreground">
								{formatDelta(pg.eloDelta)} ELO
							</span>
						{/if}
					</div>
				</div>

				{#if pg.sets.length > 0}
					<div class="flex flex-wrap gap-1.5">
						{#each pg.sets as set}
							{@const playerWonSet =
								pg.playerSide === 'home'
									? set.homePoints > set.awayPoints
									: set.awayPoints > set.homePoints}
							<span
								class="rounded px-2.5 py-1 text-xs font-medium tabular-nums
							{playerWonSet ? 'bg-win/10 text-win' : 'bg-loss/10 text-loss'}"
							>
								{pg.playerSide === 'home'
									? `${set.homePoints}:${set.awayPoints}`
									: `${set.awayPoints}:${set.homePoints}`}
							</span>
						{/each}
					</div>
				{/if}
			</div>
		</Card.Root>
	</a>
{:else}
	{@const mg = game as Game}
	{@const homeWon = mg.result === 'HOME'}
	{@const awayWon = mg.result === 'AWAY'}
	<Card.Root>
		<div class="space-y-3 px-5">
			<p class="text-[10px] font-bold tracking-widest text-muted-foreground uppercase">
				Game #{mg.orderInMatch} · {mg.gameType}
			</p>

			<div class="flex items-center gap-4">
				<div class="min-w-0 flex-1 space-y-1.5">
					<!-- Home player(s) -->
					<div class="flex min-w-0 items-center gap-1.5">
						{#if mg.homePlayer2Name}
							<div class="flex min-w-0 shrink items-center gap-1">
								<svelte:element
									this={mg.homePlayerId ? 'a' : 'span'}
									href={mg.homePlayerId ? `/players/${mg.homePlayerId}` : undefined}
									class="truncate {homeWon
										? 'text-lg font-semibold text-foreground'
										: 'text-base font-normal text-muted-foreground'} {mg.homePlayerId
										? 'hover:underline'
										: ''}">{lastName(mg.homePlayerName)}</svelte:element
								>
								<KlassBadge klass={mg.homePlayerKlass} />
							</div>
							<span class="shrink-0 text-muted-foreground/40">/</span>
							<div class="flex min-w-0 shrink items-center gap-1">
								<svelte:element
									this={mg.homePlayer2Id ? 'a' : 'span'}
									href={mg.homePlayer2Id ? `/players/${mg.homePlayer2Id}` : undefined}
									class="truncate {homeWon
										? 'text-lg font-semibold text-foreground'
										: 'text-base font-normal text-muted-foreground'} {mg.homePlayer2Id
										? 'hover:underline'
										: ''}">{lastName(mg.homePlayer2Name)}</svelte:element
								>
								<KlassBadge klass={mg.homePlayer2Klass} />
							</div>
						{:else}
							<svelte:element
								this={mg.homePlayerId ? 'a' : 'span'}
								href={mg.homePlayerId ? `/players/${mg.homePlayerId}` : undefined}
								class="min-w-0 truncate {homeWon
									? 'text-lg font-semibold text-foreground'
									: 'text-base font-normal text-muted-foreground'} {mg.homePlayerId
									? 'hover:underline'
									: ''}">{mg.homePlayerName ?? '—'}</svelte:element
							>
							<KlassBadge klass={mg.homePlayerKlass} />
						{/if}
					</div>
					<!-- Away player(s) -->
					<div class="flex min-w-0 items-center gap-1.5">
						{#if mg.awayPlayer2Name}
							<div class="flex min-w-0 shrink items-center gap-1">
								<svelte:element
									this={mg.awayPlayerId ? 'a' : 'span'}
									href={mg.awayPlayerId ? `/players/${mg.awayPlayerId}` : undefined}
									class="truncate {awayWon
										? 'text-lg font-semibold text-foreground'
										: 'text-base font-normal text-muted-foreground'} {mg.awayPlayerId
										? 'hover:underline'
										: ''}">{lastName(mg.awayPlayerName)}</svelte:element
								>
								<KlassBadge klass={mg.awayPlayerKlass} />
							</div>
							<span class="shrink-0 text-muted-foreground/40">/</span>
							<div class="flex min-w-0 shrink items-center gap-1">
								<svelte:element
									this={mg.awayPlayer2Id ? 'a' : 'span'}
									href={mg.awayPlayer2Id ? `/players/${mg.awayPlayer2Id}` : undefined}
									class="truncate {awayWon
										? 'text-lg font-semibold text-foreground'
										: 'text-base font-normal text-muted-foreground'} {mg.awayPlayer2Id
										? 'hover:underline'
										: ''}">{lastName(mg.awayPlayer2Name)}</svelte:element
								>
								<KlassBadge klass={mg.awayPlayer2Klass} />
							</div>
						{:else}
							<svelte:element
								this={mg.awayPlayerId ? 'a' : 'span'}
								href={mg.awayPlayerId ? `/players/${mg.awayPlayerId}` : undefined}
								class="min-w-0 truncate {awayWon
									? 'text-lg font-semibold text-foreground'
									: 'text-base font-normal text-muted-foreground'} {mg.awayPlayerId
									? 'hover:underline'
									: ''}">{mg.awayPlayerName ?? '—'}</svelte:element
							>
							<KlassBadge klass={mg.awayPlayerKlass} />
						{/if}
					</div>
				</div>

				<p class="shrink-0 text-3xl font-black text-muted-foreground tabular-nums">
					{mg.homeSets ?? 0}:{mg.awaySets ?? 0}
				</p>
			</div>

			{#if mg.sets && mg.sets.length > 0}
				<div class="flex flex-wrap gap-1.5">
					{#each mg.sets as set}
						<span
							class="rounded bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground tabular-nums"
						>
							{set.homePoints}:{set.awayPoints}
						</span>
					{/each}
				</div>
			{/if}
		</div>
	</Card.Root>
{/if}
