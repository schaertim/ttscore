<script lang="ts">
	import type { FavoriteResponse } from '$lib/api';
	import { resolveFeed } from '$lib/feed';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import FeedItemCard from '$lib/components/home/FeedItemCard.svelte';
	import { RssIcon, StarIcon } from 'phosphor-svelte';
	import ShowAllLink from '$lib/components/ShowAllLink.svelte';

	interface Props {
		favorites: Promise<FavoriteResponse[]>;
	}

	let { favorites }: Props = $props();

	const PREVIEW_COUNT = 5;

	const feedPromise = favorites.then(resolveFeed);
</script>

{#await favorites then favs}
	{#if favs.length > 0}
		<section class="space-y-3">
			<SectionLabel label="Favorite Feed" icon={StarIcon} />
			{#await feedPromise}
				<div class="space-y-2">
					{#each [1, 2, 3] as i (i)}
						<div class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3.5">
							<Skeleton class="h-10 w-10 shrink-0 rounded-xl" />
							<div class="flex-1 space-y-1.5">
								<Skeleton class="h-3.5 w-32 rounded" />
								<Skeleton class="h-3 w-48 rounded" />
							</div>
							<Skeleton class="h-9 w-9 shrink-0 rounded-xl" />
						</div>
					{/each}
				</div>
			{:then events}
				{@const preview = events.slice(0, PREVIEW_COUNT)}
				{#if preview.length > 0}
					<div class="space-y-2">
						{#each preview as event (event.key)}
							<FeedItemCard
								entityType={event.entityType}
								entityName={event.entityName}
								entityHref={event.entityHref}
								item={event.item}
							/>
						{/each}
					</div>
					{#if events.length > PREVIEW_COUNT}
						<ShowAllLink href="/feed" label="Show full feed" />
					{/if}
				{:else}
					<p class="px-1 text-sm text-muted-foreground">No recent activity in your feed.</p>
				{/if}
			{/await}
		</section>
	{/if}
{/await}
