<script lang="ts">
	import { ArrowRightIcon } from 'phosphor-svelte';

	interface Props {
		href: string;
		label: string;
	}

	let { href, label }: Props = $props();
</script>

<a
	{href}
	class="flex items-center justify-center gap-1 pt-1 text-center text-xs font-semibold tracking-widest text-muted-foreground uppercase hover:text-foreground"
>
	{label}
	<ArrowRightIcon size={12} weight="bold" />
</a>
