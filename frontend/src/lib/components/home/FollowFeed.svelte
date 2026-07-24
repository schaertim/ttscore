<script lang="ts">
	import type { FollowResponse } from '$lib/api';
	import { toResolvedEvent, type FeedEvent } from '$lib/feed';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import FeedItemCard from '$lib/components/home/FeedItemCard.svelte';
	import FeedItemSkeleton from '$lib/components/home/FeedItemSkeleton.svelte';
	import { ListStarIcon, StarIcon } from 'phosphor-svelte';
	import ShowAllLink from '$lib/components/ShowAllLink.svelte';
	import { _ } from 'svelte-i18n';

	interface Props {
		follows: Promise<FollowResponse[]>;
		// Pre-computed server-side (top-N across all followed entities, newest first) rather than
		// resolved client-side from each entity's full history — see $lib/feed.ts.
		feedEvents: Promise<FeedEvent[]>;
	}

	let { follows, feedEvents }: Props = $props();

	const PREVIEW_COUNT = 5;

	const resolvedPromise = $derived(feedEvents.then((events) => events.map(toResolvedEvent)));
</script>

{#await follows then items}
	{#if items.length > 0}
		<section class="space-y-3">
			<SectionLabel label={$_('home.favorite_feed')} icon={ListStarIcon} />
			{#await resolvedPromise}
				<div class="space-y-3">
					{#each [1, 2, 3] as i (i)}
						<FeedItemSkeleton />
					{/each}
				</div>
			{:then events}
				{@const preview = events.slice(0, PREVIEW_COUNT)}
				{#if preview.length > 0}
					<div class="space-y-3">
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
						<ShowAllLink href="/feed" label={$_('home.show_full_feed')} />
					{/if}
				{:else}
					<p class="px-1 text-sm text-muted-foreground">{$_('home.no_recent_activity')}</p>
				{/if}
			{/await}
		</section>
	{/if}
{/await}
