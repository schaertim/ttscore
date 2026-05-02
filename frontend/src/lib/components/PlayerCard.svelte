<script lang="ts">
	import { klassColors } from '$lib/utils';

	interface Props {
		id: string;
		fullName: string;
		klass?: string | null;
		wins?: number;
		losses?: number;
	}

	let { id, fullName, klass, wins, losses }: Props = $props();

	const initials = fullName
		.split(' ')
		.filter(Boolean)
		.slice(0, 2)
		.map((w) => w[0].toUpperCase())
		.join('');
</script>

<a
	href="/players/{id}"
	class="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-accent"
>
	<div
		class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-muted
		       text-[11px] font-black tracking-tight text-muted-foreground"
	>
		{initials}
	</div>

	<div class="min-w-0 flex-1">
		<p class="truncate text-sm font-semibold">{fullName}</p>
		{#if klass}
			<span
				class="mt-0.5 inline-block rounded px-1.5 py-0.5 text-[10px] font-black
			             tracking-wide {klassColors(klass)}"
			>
				{klass}
			</span>
		{/if}
	</div>

	{#if wins !== undefined && losses !== undefined}
		<div class="shrink-0 text-right">
			<p class="text-[10px] font-bold tracking-widest text-muted-foreground uppercase">Games</p>
			<p class="text-sm font-black text-foreground tabular-nums">{wins}:{losses}</p>
		</div>
	{/if}
</a>
