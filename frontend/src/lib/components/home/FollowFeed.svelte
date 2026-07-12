<script lang="ts">
	import type { FollowResponse } from '$lib/api';
	import { resolveFeed } from '$lib/feed';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import FeedItemCard from '$lib/components/home/FeedItemCard.svelte';
	import FeedItemSkeleton from '$lib/components/home/FeedItemSkeleton.svelte';
	import { StarIcon } from 'phosphor-svelte';
	import ShowAllLink from '$lib/components/ShowAllLink.svelte';
	import { _ } from 'svelte-i18n';

	interface Props {
		follows: Promise<FollowResponse[]>;
	}

	let { follows }: Props = $props();

	const PREVIEW_COUNT = 5;

	const feedPromise = $derived(follows.then(resolveFeed));
</script>

{#await follows then items}
	{#if items.length > 0}
		<section class="space-y-3">
			<SectionLabel label={$_('home.favorite_feed')} icon={StarIcon} />
			{#await feedPromise}
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
