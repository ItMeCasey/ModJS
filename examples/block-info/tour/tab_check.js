// Development only: a creative tab with an item in it, so the tour also catches creative tab content being built twice.
StartupEvents.registry('creative_tab', event => event.create('info_tab').displayName('Info Tab').icon('modjs:info_lamp'));
StartupEvents.registry('item', event => event.create('info_tabbed').displayName('Info Tabbed').tab('modjs:info_tab'));
