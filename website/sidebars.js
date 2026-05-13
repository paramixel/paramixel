// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docsSidebar: [
    'intro',
    'quick-start',
    {
      type: 'category',
      label: 'Usage Guide',
      collapsed: true,
      items: [
        'usage/action-composition',
        'usage/argument-testing',
        'usage/context',
        'usage/error-handling',
        'usage/listener',
        'usage/reporting',
        'usage/cleanup',
        'usage/retry',
        'usage/maven-plugin',
        'usage/gradle-plugin',
        'usage/integration-testing',
        'usage/discovery',

        'usage/migration-3-to-4',
        'usage/migration-2-to-4',
        'usage/migration-1-to-4',
      ],
    },
    'configuration',
    {
      type: 'category',
      label: 'Built-in Actions',
      collapsed: true,
      items: [
        'actions/direct',
        'actions/noop',
        'actions/container',
        'actions/parallel',
      ],
    },
    'architecture',
    'releasing',
    {
      type: 'category',
      label: 'API Reference',
      collapsed: true,
      items: ['api/intro', 'api/status', 'api/store'],
    },
  ],
};

module.exports = sidebars;
