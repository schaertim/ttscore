<script lang="ts">
	import type { PageData } from './$types';
	import type { ResolvedEvent } from '$lib/feed';
	import { resolveFeed } from '$lib/feed';
	import FeedItemCard from '$lib/components/home/FeedItemCard.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';

	let { data }: { data: PageData } = $props();

	// Group events by calendar date for the section headers
	function groupByDate(events: ResolvedEvent[]): { label: string; events: ResolvedEvent[] }[] {
		const groups = new Map<string, ResolvedEvent[]>();
		for (const event of events) {
			const label =
				event.sortKey === '9999'
					? 'Latest'
					: event.sortKey
						? new Date(event.sortKey).toLocaleDateString('en-GB', {
								day: 'numeric',
								month: 'long',
								year: 'numeric'
							})
						: 'Unknown date';
			if (!groups.has(label)) groups.set(label, []);
			groups.get(label)!.push(event);
		}
		return Array.from(groups.entries()).map(([label, events]) => ({ label, events }));
	}

	const feedPromise = data.favorites.then(resolveFeed);
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<BackButton />
		<h1 class="text-3xl leading-none font-black tracking-tighter">Feed</h1>
	</header>

	{#await feedPromise}
		<div class="space-y-6">
			{#each [1, 2, 3] as i (i)}
				<div class="space-y-2">
					<Skeleton class="h-4 w-24 rounded" />
					<div
						class="divide-y divide-border overflow-hidden rounded-xl border border-border bg-card"
					>
						{#each [1, 2, 3] as j (j)}
							<div class="flex items-center gap-3 px-4 py-3.5">
								<Skeleton class="h-10 w-10 shrink-0 rounded-xl" />
								<div class="flex-1 space-y-1.5">
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
			<div class="py-16 text-center">
				<p class="text-sm font-semibold">Nothing in your feed yet</p>
				<p class="mt-1 text-sm text-muted-foreground">
					Star players, teams, or divisions to see their activity here.
				</p>
				<a
					href="/account"
					class="mt-4 inline-block text-xs font-bold tracking-widest text-muted-foreground uppercase hover:text-foreground"
				>
					Manage favorites →
				</a>
			</div>
		{:else}
			{@const groups = groupByDate(events)}
			<div class="space-y-6">
				{#each groups as group (group.label)}
					<div class="space-y-2">
						<p class="px-1 text-xs font-bold tracking-widest text-muted-foreground uppercase">
							{group.label}
						</p>
						<div
							class="divide-y divide-border overflow-hidden rounded-xl border border-border bg-card"
						>
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
