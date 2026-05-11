<script lang="ts">
	interface Props {
		href: string;
		label: string;
	}

	let { href, label }: Props = $props();
</script>

<a
	{href}
	class="block pt-1 text-center text-xs font-medium tracking-widest text-muted-foreground uppercase hover:text-foreground"
>
	{label} →
</a>
