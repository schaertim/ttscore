<script lang="ts">
	import type { FeedItem } from './feed-types';
	import { cn, classificationColors, timeAgo } from '$lib/utils';
	import { _ } from 'svelte-i18n';
	import {
		UserIcon,
		UsersThreeIcon,
		TrophyIcon,
		TrendUpIcon,
		TrendDownIcon,
		ThumbsUpIcon,
		ThumbsDownIcon,
		HandshakeIcon,
		MedalIcon,
		PingPongIcon
	} from 'phosphor-svelte';

	interface Props {
		entityType: 'player' | 'team' | 'division_group';
		entityName: string;
		entityHref: string;
		item: FeedItem;
	}

	let { entityType, entityName, entityHref, item }: Props = $props();

	// ── Entity icon ──────────────────────────────────────────────────────────────

	const EntityIcon = $derived(
		entityType === 'player' ? UserIcon : entityType === 'team' ? UsersThreeIcon : TrophyIcon
	);

	// ── Event badge ───────────────────────────────────────────────────────────────

	type BadgeSpec = { icon: typeof TrendUpIcon; bg: string; text: string };

	function getBadge(i: FeedItem): BadgeSpec {
		if (i.kind === 'player_match') {
			const wins = parseInt(i.matchScore.split('–')[0]);
			const up = !isNaN(wins) && wins >= 2;
			return up
				? { icon: TrendUpIcon, bg: 'bg-win/15', text: 'text-win' }
				: { icon: TrendDownIcon, bg: 'bg-loss/15', text: 'text-loss' };
		}
		if (i.kind === 'team_match') {
			if (i.result === 'WIN') return { icon: ThumbsUpIcon, bg: 'bg-win/15', text: 'text-win' };
			if (i.result === 'LOSS') return { icon: ThumbsDownIcon, bg: 'bg-loss/15', text: 'text-loss' };
			return { icon: HandshakeIcon, bg: 'bg-muted', text: 'text-muted-foreground' };
		}
		if (i.kind === 'class_change') {
			const kc = classificationColors(i.to).split(' ');
			const bg = kc.find((c) => c.startsWith('bg-')) ?? 'bg-muted';
			const text = kc.find((c) => c.startsWith('text-')) ?? 'text-muted-foreground';
			return { icon: MedalIcon, bg, text };
		}
		return { icon: PingPongIcon, bg: 'bg-muted', text: 'text-muted-foreground' };
	}

	function getDescription(i: FeedItem): string {
		switch (i.kind) {
			case 'player_match': {
				const [myStr, oppStr] = i.matchScore.split('–');
				const myWins = parseInt(myStr);
				const oppWins = parseInt(oppStr);
				const won = !isNaN(myWins) && myWins >= 2;
				const higher = !isNaN(myWins) && !isNaN(oppWins) ? Math.max(myWins, oppWins) : null;
				const prefix = won ? $_('feed.won') : $_('feed.lost');
				return `${prefix}${higher !== null ? ` ${higher}` : ''} ${$_('feed.vs')} ${i.opponentTeam}`;
			}
			case 'class_change':
				return i.direction === 'UP'
					? `${$_('feed.promoted')} ${i.from} → ${i.to}`
					: `${$_('feed.relegated')} ${i.from} → ${i.to}`;
			case 'team_match': {
				const prefix = i.result === 'WIN' ? $_('feed.won') : i.result === 'LOSS' ? $_('feed.lost') : $_('feed.drew');
				return `${prefix} ${i.score} ${$_('feed.vs')} ${i.opponent}`;
			}
			case 'group_match':
				return `${i.homeTeam} ${i.score} ${i.awayTeam}`;
		}
	}

	function getTimestamp(i: FeedItem): string | null {
		if (i.kind === 'class_change') return null;
		return i.playedAt ?? null;
	}

	const badge = $derived(getBadge(item));
	const description = $derived(getDescription(item));
	const timestamp = $derived(getTimestamp(item));
</script>

<a href={entityHref} class="group flex items-center bg-card gap-3 rounded-xl border border-border  px-4 py-3 hover:bg-accent">
	<div class="flex h-9 w-7 shrink-0 items-center justify-center rounded-lg">
		<EntityIcon size="20" class="text-muted-foreground" />
	</div>

	<!-- Center: name + description + timestamp -->
	<div class="min-w-0 flex-1">
		<div class="flex items-baseline justify-between gap-2">
			<p class="truncate text-sm font-semibold">{entityName}</p>
			{#if timestamp}
				<p class="shrink-0 text-xs text-muted-foreground">{timeAgo(timestamp)}</p>
			{/if}
		</div>
		<p class="truncate text-xs text-muted-foreground">{description}</p>
	</div>

	<!-- Right: event type badge -->
	<div class={cn('flex h-8 w-8 shrink-0 items-center justify-center rounded-sm ring-1 ring-transparent transition-all', badge.bg, badge.bg === 'bg-muted' && 'group-hover:ring-border')}>
		<badge.icon size="16" class={badge.text} weight="bold" />
	</div>
</a>
