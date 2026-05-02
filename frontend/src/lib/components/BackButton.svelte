<script lang="ts">
	import { CaretLeft } from 'phosphor-svelte';
</script>

<button
	onclick={() => history.back()}
	class="mb-6 flex items-center gap-1 text-muted-foreground transition-colors hover:text-foreground"
>
	<CaretLeft class="h-4 w-4" />
	<span class="text-xs font-bold tracking-widest uppercase">Back</span>
</button>
