<script lang="ts">
	import type { Player, LeagueContext } from '$lib/api';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import QuicklinkRow from '$lib/components/home/QuicklinkRow.svelte';
	import { UserIcon, TrophyIcon, UsersThreeIcon, PingPongIcon, LinkIcon } from 'phosphor-svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { _ } from 'svelte-i18n';

	interface Props {
		player: Player;
		leagueContext: Promise<LeagueContext | null>;
	}

	let { player, leagueContext }: Props = $props();
</script>

<section class="space-y-3">
	<SectionLabel label={$_('home.quick_links')} icon={LinkIcon} />
	<div class="divide-y divide-border overflow-hidden rounded-xl border border-border bg-card">
		<QuicklinkRow
			href="/players/{player.id}"
			icon={UserIcon}
			label={$_('home.my_profile')}
			sublabel={$_('home.profile_sublabel')}
		/>
		<QuicklinkRow
			href="/players/{player.id}/games"
			icon={PingPongIcon}
			label={$_('home.my_games')}
			sublabel={$_('home.games_sublabel')}
		/>
		{#await leagueContext}
			<div class="flex items-center gap-3 px-4 py-3">
				<Skeleton class="h-9 w-9 shrink-0 rounded-lg" />
				<div class="flex-1 space-y-2">
					<Skeleton class="h-3.5 w-24 rounded" />
					<Skeleton class="h-3 w-36 rounded" />
				</div>
			</div>
			<div class="flex items-center gap-3 px-4 py-3">
				<Skeleton class="h-9 w-9 shrink-0 rounded-lg" />
				<div class="flex-1 space-y-2">
					<Skeleton class="h-3.5 w-24 rounded" />
					<Skeleton class="h-3 w-36 rounded" />
				</div>
			</div>
		{:then ctx}
			{#if ctx}
				<QuicklinkRow
					href="/groups/{ctx.groupId}"
					icon={TrophyIcon}
					label={$_('home.my_league')}
					sublabel={ctx.groupName}
				/>
				<QuicklinkRow
					href="/teams/{ctx.teamId}"
					icon={UsersThreeIcon}
					label={$_('home.my_team')}
					sublabel={ctx.teamName}
				/>
			{/if}
		{/await}
	</div>
</section>
