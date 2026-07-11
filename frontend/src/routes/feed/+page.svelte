<script lang="ts">
	import type { PageData } from './$types';
	import type { ResolvedEvent } from '$lib/feed';
	import { resolveFeed } from '$lib/feed';
	import FeedItemCard from '$lib/components/home/FeedItemCard.svelte';
	import FeedItemSkeleton from '$lib/components/home/FeedItemSkeleton.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { locale, _ } from 'svelte-i18n';
	import { dateLong } from '$lib/date';

	let { data }: { data: PageData } = $props();

	function groupByDate(events: ResolvedEvent[]): { label: string; events: ResolvedEvent[] }[] {
		const result: { label: string; events: ResolvedEvent[] }[] = [];
		for (const event of events) {
			const label = dateLong(event.sortKey, $locale) ?? $_('common.tbd');
			const existing = result.find((g) => g.label === label);
			if (existing) existing.events.push(event);
			else result.push({ label, events: [event] });
		}
		return result;
	}

	const feedPromise = $derived(data.follows.then(resolveFeed));
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<BackButton />
		<PageTitle>{$_('feed.title')}</PageTitle>
	</header>

	{#await feedPromise}
		<div class="space-y-6">
			{#each [1, 2, 3] as i (i)}
				<div class="space-y-3">
					<Skeleton class="h-4 w-24 rounded" />
					<div class="space-y-3">
						{#each [1, 2, 3] as j (j)}
							<FeedItemSkeleton />
						{/each}
					</div>
				</div>
			{/each}
		</div>
	{:then events}
		{#if events.length === 0}
			<div class="py-12 text-center">
				<p class="text-sm font-semibold">{$_('feed.empty_title')}</p>
				<p class="mt-1 text-sm text-muted-foreground">
					{$_('feed.empty_desc')}
				</p>
				<a
					href="/account"
					class="mt-4 inline-block text-xs font-semibold tracking-widest text-muted-foreground uppercase hover:text-foreground"
				>
					{$_('feed.manage_favorites')} →
				</a>
			</div>
		{:else}
			{@const groups = groupByDate(events)}
			<div class="space-y-6">
				{#each groups as group (group.label)}
					<div class="space-y-3">
						<SectionLabel label={group.label} />
						<div class="space-y-3">
							{#each group.events as event (event.key)}
								<FeedItemCard
									entityType={event.entityType}
									entityName={event.entityName}
									entityHref={event.entityHref}
									item={event.item}
								/>
							{/each}
						</div>
					</div>
				{/each}
			</div>
		{/if}
	{/await}
</div>
