<script lang="ts">
	import { CaretLeft } from 'phosphor-svelte';
</script>

<a
	href="javascript:history.back()"
	class="mb-6 flex items-center gap-1 text-muted-foreground hover:text-foreground transition-colors"
>
	<CaretLeft class="w-4 h-4" />
	<span class="text-xs font-bold uppercase tracking-widest">Back</span>
</a>
