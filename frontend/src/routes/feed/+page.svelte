<script lang="ts">
	import type { PageData } from './$types';
	import type { ResolvedEvent } from '$lib/feed';
	import { resolveFeed } from '$lib/feed';
	import FeedItemCard from '$lib/components/home/FeedItemCard.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { locale, _ } from 'svelte-i18n';

	let { data }: { data: PageData } = $props();

	function groupByDate(events: ResolvedEvent[]): { label: string; events: ResolvedEvent[] }[] {
		const result: { label: string; events: ResolvedEvent[] }[] = [];
		for (const event of events) {
			const label =
				event.sortKey === '9999'
					? $_('feed.latest')
					: event.sortKey
						? new Date(event.sortKey).toLocaleDateString($locale ?? 'de', {
								day: 'numeric',
								month: 'long',
								year: 'numeric'
							})
						: $_('feed.unknown_date');
			const existing = result.find(g => g.label === label);
			if (existing) existing.events.push(event);
			else result.push({ label, events: [event] });
		}
		return result;
	}

	const feedPromise = $derived(data.favorites.then(resolveFeed));
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<BackButton />
		<PageTitle>Feed</PageTitle>
	</header>

	{#await feedPromise}
		<div class="space-y-6">
			{#each [1, 2, 3] as i (i)}
				<div class="space-y-3">
					<Skeleton class="h-4 w-24 rounded" />
					<div class="space-y-3">
						{#each [1, 2, 3] as j (j)}
							<div class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3">
								<Skeleton class="h-10 w-10 shrink-0 rounded-xl" />
								<div class="flex-1 space-y-2">
									<Skeleton class="h-3.5 w-32 rounded" />
									<Skeleton class="h-3 w-48 rounded" />
								</div>
								<Skeleton class="h-9 w-9 shrink-0 rounded-xl" />
							</div>
						{/each}
					</div>
				</div>
			{/each}
		</div>
	{:then events}
		{#if events.length === 0}
			<div class="py-12 text-center">
				<p class="text-sm font-semibold">Nothing in your feed yet</p>
				<p class="mt-1 text-sm text-muted-foreground">
					Star players, teams, or divisions to see their activity here.
				</p>
				<a
					href="/account"
					class="mt-4 inline-block text-xs font-semibold tracking-widest text-muted-foreground uppercase hover:text-foreground"
				>
					Manage favorites →
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
