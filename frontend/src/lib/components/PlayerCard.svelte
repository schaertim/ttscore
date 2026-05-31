<script lang="ts">
	import PlayerAvatar from './PlayerAvatar.svelte';
	import ClassBadge from './ClassBadge.svelte';
	import { formatName } from '$lib/utils';
	import { _ } from 'svelte-i18n';

	interface Props {
		id: string;
		fullName: string;
		classification?: string | null;
		wins?: number;
		losses?: number;
	}

	let { id, fullName, classification, wins, losses }: Props = $props();
</script>

<a
	href="/players/{id}"
	class="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-accent"
>
	<PlayerAvatar {fullName} size="md" />

	<div class="min-w-0 flex-1">
		<p class="truncate text-sm font-semibold">{formatName(fullName)}</p>
		<ClassBadge {classification} />
	</div>

	{#if wins !== undefined && losses !== undefined}
		<div class="shrink-0 text-right">
			<p class="text-[10px] font-medium tracking-widest text-muted-foreground uppercase">{$_('team.games')}</p>
			<p class="text-sm font-black text-foreground tabular-nums">{wins}:{losses}</p>
		</div>
	{/if}
</a>
