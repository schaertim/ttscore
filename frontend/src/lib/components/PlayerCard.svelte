<script lang="ts">
	import PlayerAvatar from './PlayerAvatar.svelte';
	import KlassBadge from './KlassBadge.svelte';

	interface Props {
		id: string;
		fullName: string;
		klass?: string | null;
		wins?: number;
		losses?: number;
	}

	let { id, fullName, klass, wins, losses }: Props = $props();
</script>

<a
	href="/players/{id}"
	class="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-accent"
>
	<PlayerAvatar {fullName} size="md" />

	<div class="min-w-0 flex-1">
		<p class="truncate text-sm font-semibold">{fullName}</p>
		<KlassBadge {klass} />
	</div>

	{#if wins !== undefined && losses !== undefined}
		<div class="shrink-0 text-right">
			<p class="text-[10px] font-bold tracking-widest text-muted-foreground uppercase">Games</p>
			<p class="text-sm font-black text-foreground tabular-nums">{wins}:{losses}</p>
		</div>
	{/if}
</a>
