<script lang="ts">
	import type { Player, LeagueContext } from '$lib/api';
	import { ordinal } from '$lib/utils';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { User, ListBullets, Trophy, UsersThree } from 'phosphor-svelte';

	interface Props {
		player: Player;
		leagueContext: Promise<LeagueContext | null>;
		recentMatchCount: Promise<number>;
	}

	let { player, leagueContext, recentMatchCount }: Props = $props();

	type Link = {
		href: string;
		label: string;
		subtitle?: string;
		icon: typeof User;
	};

	const staticLinks: Link[] = [
		{
			href: `/players/${player.id}`,
			label: 'My Profile',
			icon: User
		}
	];
</script>

<div class="space-y-2">
	<!-- Static: My Profile -->
	<a
		href="/players/{player.id}"
		class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 hover:bg-accent"
	>
		<User size={18} class="shrink-0 text-muted-foreground" />
		<span class="text-sm font-semibold">My Profile</span>
	</a>

	<!-- Static: My Matches -->
	<a
		href="/players/{player.id}/games"
		class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 hover:bg-accent"
	>
		<ListBullets size={18} class="shrink-0 text-muted-foreground" />
		<div class="flex flex-1 items-center justify-between">
			<span class="text-sm font-semibold">My Matches</span>
			{#await recentMatchCount then count}
				{#if count > 0}
					<span class="rounded-full bg-muted px-2 py-0.5 text-xs font-bold tabular-nums text-muted-foreground">
						{count}
					</span>
				{/if}
			{/await}
		</div>
	</a>

	<!-- Async: My League -->
	{#await leagueContext}
		<Skeleton class="h-12 w-full rounded-xl" />
	{:then ctx}
		{#if ctx}
			<a
				href="/groups/{ctx.groupId}"
				class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 hover:bg-accent"
			>
				<Trophy size={18} class="shrink-0 text-muted-foreground" />
				<div class="min-w-0">
					<p class="text-sm font-semibold">My League</p>
					<p class="text-xs text-muted-foreground truncate">
						{ordinal(ctx.position)} · {ctx.won}W {ctx.drawn}D {ctx.lost}L · {ctx.groupName}
					</p>
				</div>
			</a>
		{/if}
	{/await}

	<!-- Async: My Team -->
	{#await leagueContext}
		<Skeleton class="h-12 w-full rounded-xl" />
	{:then ctx}
		{#if ctx}
			<a
				href="/teams/{ctx.teamId}"
				class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 hover:bg-accent"
			>
				<UsersThree size={18} class="shrink-0 text-muted-foreground" />
				<div class="min-w-0">
					<p class="text-sm font-semibold">My Team</p>
					<p class="text-xs text-muted-foreground truncate">
						{ctx.teamName}
						{#if ctx.scheduledMatchCount > 0}
							· {ctx.scheduledMatchCount} upcoming
						{/if}
					</p>
				</div>
			</a>
		{/if}
	{/await}
</div>
