<script lang="ts">
	import { LockKeyIcon, SparkleIcon } from 'phosphor-svelte';
	import { Button } from '$lib/components/ui/button/index.js';
	import { _ } from 'svelte-i18n';

	interface Props {
		title: string;
		description: string;
	}

	let { title, description }: Props = $props();
</script>

<div
	class="flex flex-col items-center gap-3 rounded-2xl border border-primary/20 bg-primary/5 p-8 text-center"
>
	<div class="rounded-full bg-primary/10 p-3">
		<LockKeyIcon size={24} class="text-primary" weight="fill" />
	</div>
	<div class="space-y-1">
		<p class="text-base font-semibold">{title}</p>
		<p class="mx-auto max-w-xs text-sm text-muted-foreground">{description}</p>
	</div>
	<Button href="/pro" class="mt-1 rounded-full font-semibold">
		<SparkleIcon size={16} weight="fill" />
		{$_('pro.unlock')}
	</Button>
</div>
