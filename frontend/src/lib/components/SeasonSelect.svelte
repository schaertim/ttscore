<script lang="ts">
	import * as Select from '$lib/components/ui/select/index.js';
	import { _ } from 'svelte-i18n';

	export type SeasonOption = { value: string; label: string };

	interface Props {
		/** Options to choose from, ordered newest-first by the caller. */
		seasons: SeasonOption[];
		/** Currently selected option value (bindable). */
		value: string;
		/** Fired after the selection changes to a non-empty value. */
		onChange?: (value: string) => void;
		/** Trigger width / extra classes. */
		class?: string;
	}

	let { seasons, value = $bindable(), onChange, class: className = 'w-32' }: Props = $props();

	const selected = $derived(seasons.find((s) => s.value === value));
</script>

<Select.Root
	type="single"
	{value}
	onValueChange={(v) => {
		if (v) {
			value = v;
			onChange?.(v);
		}
	}}
>
	<Select.Trigger class={className}>
		{selected?.label ?? $_('leagues.season')}
	</Select.Trigger>
	<Select.Content>
		{#each seasons as season (season.value)}
			<Select.Item value={season.value}>{season.label}</Select.Item>
		{/each}
	</Select.Content>
</Select.Root>
