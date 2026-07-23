<script lang="ts">
	import { _, locale } from 'svelte-i18n';
	import type { Match } from '$lib/api';
	import { dateNumeric } from '$lib/date';
	import { CaretRightIcon } from 'phosphor-svelte';
	import { Separator } from '$lib/components/ui/separator/index.js';

	interface Props {
		match: Match;
	}

	let { match }: Props = $props();
</script>

<a
	href="/matches/{match.id}"
	class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 transition-colors hover:bg-accent"
>
	<div class="flex min-w-0 flex-1 flex-col gap-1">
		<span
			class="flex items-center gap-1.5 text-2xs font-semibold tracking-widest text-muted-foreground uppercase"
		>
			<span>{$_('group.round_label', { values: { round: match.round } })}</span>
			<Separator
				orientation="vertical"
				class="bg-muted-foreground/40 data-[orientation=vertical]:h-2.5"
			/>
			<span>{dateNumeric(match.playedAt, $locale) ?? $_('common.tbd')}</span>
		</span>
		<div class="flex min-w-0 items-center gap-2 text-sm">
			<span class="min-w-0 truncate font-semibold">{match.homeTeam}</span>
			<span class="shrink-0 text-muted-foreground">vs</span>
			<span class="min-w-0 truncate font-semibold">{match.awayTeam}</span>
		</div>
	</div>
	<CaretRightIcon size={16} class="shrink-0 text-muted-foreground" />
</a>
