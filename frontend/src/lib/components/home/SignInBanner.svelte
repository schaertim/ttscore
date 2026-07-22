<script lang="ts">
	import InfoItem from '$lib/components/InfoItem.svelte';
	import { UserCirclePlusIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';
</script>

<InfoItem
	href="/signin"
	icon={UserCirclePlusIcon}
	title={$_('set_player.banner_title')}
	description={$_('set_player.sign_in_prompt')}
/>
