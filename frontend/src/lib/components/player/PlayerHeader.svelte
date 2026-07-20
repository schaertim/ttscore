<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { Player } from '$lib/api';
	import { Spinner } from '$lib/components/ui/spinner/index.js';
	import BackButton from '$lib/components/BackButton.svelte';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import FollowButton from '$lib/components/FollowButton.svelte';
	import NotifyButton from '$lib/components/NotifyButton.svelte';
	import InfoItem from '$lib/components/InfoItem.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import { Separator } from '$lib/components/ui/separator/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { compareWithMe } from '$lib/h2h.svelte';
	import { formatName } from '$lib/utils';
	import { ScalesIcon } from 'phosphor-svelte';

	interface Props {
		player: Player;
		/** Shows the live spinner under the ELO while the on-demand click-tt sync runs. */
		syncing: boolean;
		/** Whether the signed-in user has a home player other than this one. */
		canCompare: boolean;
		authenticated: boolean;
		following: boolean;
		followId: string | null;
		notify: boolean;
	}

	let {
		player,
		syncing,
		canCompare,
		authenticated,
		following = $bindable(),
		followId = $bindable(),
		notify = $bindable()
	}: Props = $props();

	// Class/ELO from the player's *current* rating (live where available) — match rows
	// keep their historical classes.
	const currentClass = $derived(player.liveClassification ?? player.classification);
	const displayElo = $derived(player.liveElo ?? player.currentElo);
	const displayName = $derived(formatName(player.fullName));
</script>

<header class="space-y-4">
	<div class="flex items-center justify-between">
		<BackButton />
		<div class="flex items-center">
			<FollowButton
				bind:following
				bind:followId
				bind:notify
				targetType="player"
				targetId={player.id}
				{authenticated}
			/>
			<NotifyButton {following} {followId} bind:notify {authenticated} />
		</div>
	</div>

	<!-- Mobile (below md): name is the hero with the class badge inlined (em-sized, so it
	     stays proportional) and wrapping alongside the last word on long names. Club, ELO,
	     and the live-sync indicator share a wrapping meta row underneath. -->
	<div class="space-y-1.5 md:hidden">
		<PageTitle class="leading-tight">
			{displayName}<ClassBadge
				classification={currentClass}
				size="lg"
				class="ml-2 align-[0.15em] text-[0.6em]"
			/>
		</PageTitle>

		<div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground">
			<span>{player.currentClubName ?? $_('player.no_club')}</span>
			{#if player.category}
				<Separator
					orientation="vertical"
					class="bg-muted-foreground/40 data-[orientation=vertical]:h-4"
				/>
				<span>{player.category}</span>
			{/if}
			{#if displayElo}
				<Separator
					orientation="vertical"
					class="bg-muted-foreground/50 data-[orientation=vertical]:h-3.5"
				/>
				<span>
					<span>{displayElo}</span> Elo
				</span>
			{:else if syncing}
				<Separator
					orientation="vertical"
					class="bg-muted-foreground/40 data-[orientation=vertical]:h-4"
				/>
				<Skeleton class="h-4 w-14 rounded" />
			{/if}
			{#if syncing}
				<Separator
					orientation="vertical"
					class="bg-muted-foreground/40 data-[orientation=vertical]:h-4"
				/>
				<span class="flex items-center gap-1">
					<Spinner class="size-3.5" aria-label={$_('player.syncing')} />
					{$_('player.syncing')}
				</span>
			{/if}
		</div>
	</div>

	<!-- Tablet and up (md+): the wider two-column layout — name + badge on the left, the
	     ELO stat pinned to the right. Extra horizontal room means long names can wrap here
	     without squeezing the ELO. -->
	<div class="hidden items-start justify-between gap-4 md:flex">
		<div class="min-w-0">
			<div class="mb-1 flex items-start gap-2">
				<PageTitle class="wrap-break-word">
					{displayName}
				</PageTitle>
				<ClassBadge classification={currentClass} size="lg" class="mt-0.5 shrink-0" />
			</div>
			<div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground">
				<span>{player.currentClubName ?? $_('player.no_club')}</span>
				{#if player.category}
					<Separator
						orientation="vertical"
						class="bg-muted-foreground/40 data-[orientation=vertical]:h-4"
					/>
					<span>{player.category}</span>
				{/if}
			</div>
		</div>

		<div class="flex shrink-0 flex-col items-end gap-1">
			{#if displayElo}
				<span class="font-mono text-4xl leading-none font-black tabular-nums">{displayElo}</span>
			{:else if syncing}
				<Skeleton class="h-9 w-20 rounded" />
			{/if}
			{#if syncing}
				<span class="flex items-center gap-1 text-xs tracking-widest text-muted-foreground">
					<Spinner class="size-3.5" aria-label={$_('player.syncing')} />
					{$_('player.syncing')}
				</span>
			{:else if displayElo}
				<span class="text-xs tracking-widest text-muted-foreground uppercase"> ELO </span>
			{/if}
		</div>
	</div>

	{#if canCompare}
		<InfoItem
			variant="muted"
			size="sm"
			icon={ScalesIcon}
			title={$_('h2h.compare_with_me')}
			onclick={() => compareWithMe(player.id)}
		/>
	{/if}
</header>
