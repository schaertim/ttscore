<script lang="ts">
	import type { Game, PlayerGame } from '$lib/api';
	import { cn, formatName } from '$lib/utils';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import * as Card from '$lib/components/ui/card/index.js';
	import { locale, _ } from 'svelte-i18n';

	let {
		mode = 'match',
		game
	}: {
		mode?: 'match' | 'player';
		game: Game | PlayerGame;
	} = $props();

	function toPlayerGame(g: Game | PlayerGame): PlayerGame { return g as PlayerGame; }
	function toGame(g: Game | PlayerGame): Game { return g as Game; }

	// For compact doubles display: return the family name portion (all but last word).
	// Storage format is "Lastname Firstname", so family name = everything except the last word.
	function lastName(name: string | null): string {
		if (!name) return '—';
		const parts = name.trim().split(/\s+/);
		return parts.length > 1 ? parts.slice(0, -1).join(' ') : parts[0];
	}

	function formatDate(dateStr: string | null | undefined): string {
		if (!dateStr) return '—';
		return new Date(dateStr).toLocaleDateString($locale ?? 'de', { day: '2-digit', month: '2-digit' });
	}

	function formatDelta(delta: number | null): string {
		if (delta == null) return '';
		const abs = Math.abs(delta).toFixed(1);
		return delta >= 0 ? `+${abs}` : `-${abs}`;
	}

	function playerSets(g: PlayerGame): number {
		return g.playerSide === 'home' ? (g.homeSets ?? 0) : (g.awaySets ?? 0);
	}

	function opponentSets(g: PlayerGame): number {
		return g.playerSide === 'home' ? (g.awaySets ?? 0) : (g.homeSets ?? 0);
	}
</script>

{#if mode === 'player'}
	{@const pg = toPlayerGame(game)}
	{@const pSets = playerSets(pg)}
	{@const oSets = opponentSets(pg)}
	{@const scoreColor = pSets === oSets ? 'text-muted-foreground' : pSets > oSets ? 'text-win' : 'text-loss'}
	<svelte:element
		this={pg.matchId ? 'a' : 'div'}
		href={pg.matchId ? `/matches/${pg.matchId}` : undefined}
		class="block"
	>
		<Card.Root class="py-3 transition-colors {pg.matchId ? 'hover:bg-accent' : ''}">
			<div class="flex items-stretch gap-3 px-4">
				<!-- left: meta + opponent + sets -->
				<div class="flex min-w-0 flex-1 flex-col gap-2">
					<p class="truncate text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
						{formatDate(pg.playedAt)} · {pg.competitionName ?? '—'}
					</p>
					<div class="flex min-w-0 items-center gap-2">
							{#if pg.opponentId}
								<a
									href="/players/{pg.opponentId}"
									class="min-w-0 truncate text-lg font-normal hover:underline"
									onclick={(e) => e.stopPropagation()}
								>{formatName(pg.opponentName)}</a>
							{:else}
								<span class="min-w-0 truncate text-lg font-normal">{formatName(pg.opponentName)}</span>
							{/if}
							<ClassBadge classification={pg.opponentClassification} />
						</div>
						{#if pg.sets.length > 0}
							<div class="flex flex-wrap gap-1">
								{#each pg.sets as set, i (i)}
									{@const playerWonSet =
										pg.playerSide === 'home'
											? set.homePoints > set.awayPoints
											: set.awayPoints > set.homePoints}
									<span class={cn(
										'rounded px-2 py-1 font-mono text-2xs tracking-tight font-semibold tabular-nums',
										playerWonSet ? 'bg-win/15 text-win' : 'bg-loss/15 text-loss'
									)}>
										{pg.playerSide === 'home'
											? `${set.homePoints}:${set.awayPoints}`
											: `${set.awayPoints}:${set.homePoints}`}
									</span>
								{/each}
							</div>
						{/if}
				</div>
				<!-- divider -->
				<div class="w-px shrink-0 self-stretch bg-border"></div>
				<!-- right: score + ELO centered as group -->
				<div class="flex w-11 shrink-0 flex-col items-center justify-center gap-1">
					<p class={cn('font-mono text-2xl font-black tracking-tighter leading-none tabular-nums', scoreColor)}>
						{playerSets(pg)}:{opponentSets(pg)}
					</p>
					{#if pg.eloDelta != null}
						<span
							class="whitespace-nowrap font-mono text-2xs font-semibold text-muted-foreground"
							class:italic={pg.eloDeltaProvisional}
							title={pg.eloDeltaProvisional ? 'Provisional — not yet officially rated' : undefined}
						>
							{formatDelta(pg.eloDelta)} ELO
						</span>
					{/if}
				</div>
			</div>
		</Card.Root>
	</svelte:element>
{:else}
	{@const mg = toGame(game)}
	{@const homeWon = mg.result === 'HOME'}
	{@const awayWon = mg.result === 'AWAY'}
	<Card.Root class="py-3">
		<div class="flex items-stretch gap-3 px-4">
			<!-- left: game label + home player + away player + sets -->
			<div class="flex min-w-0 flex-1 flex-col gap-2">
				<p class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
					{$_('match.game_label', { values: { number: mg.orderInMatch } })} · {mg.gameType === 'SINGLES' ? $_('match.singles') : $_('match.doubles')}
				</p>

				<!-- home -->
				<div class="flex min-w-0 items-center gap-2">
					{#if mg.homePlayer2Name}
						<div class="flex min-w-0 shrink items-center gap-1">
							{#if mg.homePlayerId}
								<a href="/players/{mg.homePlayerId}" class={cn('truncate hover:underline', homeWon ? 'text-base font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{lastName(mg.homePlayerName)}</a>
							{:else}
								<span class={cn('truncate', homeWon ? 'text-base font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{lastName(mg.homePlayerName)}</span>
							{/if}
							<ClassBadge classification={mg.homePlayerClassification} />
						</div>
						<span class="shrink-0 text-muted-foreground/40">/</span>
						<div class="flex min-w-0 shrink items-center gap-1">
							{#if mg.homePlayer2Id}
								<a href="/players/{mg.homePlayer2Id}" class={cn('truncate hover:underline', homeWon ? 'text-base font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{lastName(mg.homePlayer2Name)}</a>
							{:else}
								<span class={cn('truncate', homeWon ? 'text-base font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{lastName(mg.homePlayer2Name)}</span>
							{/if}
							<ClassBadge classification={mg.homePlayer2Classification} />
						</div>
					{:else}
						{#if mg.homePlayerId}
							<a href="/players/{mg.homePlayerId}" class={cn('min-w-0 truncate hover:underline', homeWon ? 'text-base font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{formatName(mg.homePlayerName)}</a>
						{:else}
							<span class={cn('min-w-0 truncate', homeWon ? 'text-base font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{formatName(mg.homePlayerName)}</span>
						{/if}
						<ClassBadge classification={mg.homePlayerClassification} />
					{/if}
				</div>

				<!-- away -->
				<div class="flex min-w-0 items-center gap-2">
					{#if mg.awayPlayer2Name}
						<div class="flex min-w-0 shrink items-center gap-1">
							{#if mg.awayPlayerId}
								<a href="/players/{mg.awayPlayerId}" class={cn('truncate hover:underline', awayWon ? 'text-base font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{lastName(mg.awayPlayerName)}</a>
							{:else}
								<span class={cn('truncate', awayWon ? 'text-base font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{lastName(mg.awayPlayerName)}</span>
							{/if}
							<ClassBadge classification={mg.awayPlayerClassification} />
						</div>
						<span class="shrink-0 text-muted-foreground/40">/</span>
						<div class="flex min-w-0 shrink items-center gap-1">
							{#if mg.awayPlayer2Id}
								<a href="/players/{mg.awayPlayer2Id}" class={cn('truncate hover:underline', awayWon ? 'text-base font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{lastName(mg.awayPlayer2Name)}</a>
							{:else}
								<span class={cn('truncate', awayWon ? 'text-base font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{lastName(mg.awayPlayer2Name)}</span>
							{/if}
							<ClassBadge classification={mg.awayPlayer2Classification} />
						</div>
					{:else}
						{#if mg.awayPlayerId}
							<a href="/players/{mg.awayPlayerId}" class={cn('min-w-0 truncate hover:underline', awayWon ? 'text-base font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{formatName(mg.awayPlayerName)}</a>
						{:else}
							<span class={cn('min-w-0 truncate', awayWon ? 'text-base font-semibold text-foreground' : 'text-base font-normal text-muted-foreground')}>{formatName(mg.awayPlayerName)}</span>
						{/if}
						<ClassBadge classification={mg.awayPlayerClassification} />
					{/if}
				</div>

				{#if mg.sets && mg.sets.length > 0}
					<div class="flex flex-wrap gap-1">
						{#each mg.sets as set, i (i)}
							<span class="rounded bg-muted px-2 py-1 font-mono text-xs tracking-tight font-semibold text-muted-foreground tabular-nums">
								{set.homePoints}:{set.awayPoints}
							</span>
						{/each}
					</div>
				{/if}
			</div>
			<!-- divider -->
			<div class="w-px shrink-0 self-stretch bg-border"></div>
			<!-- right: score, vertically centered -->
			<div class="flex w-11 shrink-0 flex-col items-center justify-center">
				<p class="font-mono text-3xl font-black tracking-tighter tabular-nums text-muted-foreground">
					{mg.homeSets ?? 0}:{mg.awaySets ?? 0}
				</p>
			</div>
		</div>
	</Card.Root>
{/if}
