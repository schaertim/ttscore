<script lang="ts">
	import type { Group } from '$lib/api';
	import * as Accordion from '$lib/components/ui/accordion/index.js';
	import { Separator } from '$lib/components/ui/separator/index.js';
	import { CaretDownIcon, CaretRightIcon, GlobeIcon, MapTrifoldIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';

	type GroupCategory = { name: string; groups: Group[] };

	interface Props {
		fed: { id: string; name: string; groups: Group[]; categories: GroupCategory[] | null };
		isExpanded: boolean;
	}

	let { fed, isExpanded }: Props = $props();

	const isNational = $derived(fed.name === 'STT');

	// Local to this federation's own accordion item — categories are a second, independent
	// accordion nested inside it, so each federation gets its own fresh, isolated expand state
	// rather than sharing one keyed by federation id at the parent level.
	let expandedCategories = $state<string[]>([]);
</script>

{#snippet groupRow(group: Group)}
	<a
		href="/groups/{group.id}"
		class="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-accent"
	>
		<div class="min-w-0 flex-1">
			<p class="truncate text-sm font-semibold">{group.name}</p>
			{#if group.teamCount > 0 || group.totalRounds > 0}
				<div
					class="mt-1 flex items-center gap-1.5 text-2xs font-semibold tracking-widest text-muted-foreground uppercase"
				>
					{#if group.teamCount > 0}
						<span>{$_('leagues.teams', { values: { count: group.teamCount } })}</span>
					{/if}
					{#if group.teamCount > 0 && group.totalRounds > 0}
						<Separator
							orientation="vertical"
							class="bg-muted-foreground/40 data-[orientation=vertical]:h-2.5"
						/>
					{/if}
					{#if group.totalRounds > 0}
						<span
							>{$_('leagues.round', {
								values: { played: group.roundsPlayed, total: group.totalRounds }
							})}</span
						>
					{/if}
				</div>
			{/if}
		</div>
		<CaretRightIcon
			size={16}
			class="shrink-0 text-muted-foreground transition-colors group-hover:text-foreground"
		/>
	</a>
{/snippet}

<Accordion.Item value={fed.id} class="overflow-hidden rounded-xl border border-border bg-card">
	<Accordion.Trigger
		class="w-full items-center rounded-none px-4 py-4 transition-colors hover:bg-accent hover:no-underline [&_[data-slot=accordion-trigger-icon]]:hidden"
	>
		<div class="flex items-center gap-3">
			{#if isNational}
				<GlobeIcon size={20} class="text-muted-foreground" />
			{:else}
				<MapTrifoldIcon size={20} class="text-muted-foreground" />
			{/if}
			<span class="font-semibold">{fed.name}</span>
		</div>
		<CaretDownIcon
			size={20}
			class="text-muted-foreground transition-transform duration-200
			{isExpanded ? 'rotate-180' : ''}"
		/>
	</Accordion.Trigger>
	<Accordion.Content class="p-0">
		{#if fed.categories === null}
			<div class="divide-y divide-border/40 bg-background/50">
				{#each fed.groups as group (group.id)}
					{@render groupRow(group)}
				{/each}
			</div>
		{:else}
			<Accordion.Root type="multiple" bind:value={expandedCategories} class="space-y-2 p-2">
				{#each fed.categories as category (category.name)}
					{@const isCategoryExpanded = expandedCategories.includes(category.name)}
					<Accordion.Item
						value={category.name}
						class="overflow-hidden rounded-lg border border-border/60 bg-background/50"
					>
						<Accordion.Trigger
							class="w-full items-center rounded-none px-3 py-2.5 text-sm transition-colors hover:bg-accent hover:no-underline [&_[data-slot=accordion-trigger-icon]]:hidden"
						>
							<span class="font-medium">{category.name}</span>
							<div class="flex items-center gap-2">
								<span class="text-2xs font-semibold text-muted-foreground">
									{category.groups.length}
								</span>
								<CaretDownIcon
									size={14}
									class="text-muted-foreground transition-transform duration-200
									{isCategoryExpanded ? 'rotate-180' : ''}"
								/>
							</div>
						</Accordion.Trigger>
						<Accordion.Content class="p-0">
							<div class="divide-y divide-border/40">
								{#each category.groups as group (group.id)}
									{@render groupRow(group)}
								{/each}
							</div>
						</Accordion.Content>
					</Accordion.Item>
				{/each}
			</Accordion.Root>
		{/if}
	</Accordion.Content>
</Accordion.Item>
