// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docsSidebar: [
    'intro',
    'quick-start',
    {
      type: 'category',
      label: 'Built-in Actions',
      collapsed: false,
      items: [
        'actions/direct',
        'actions/noop',
        'actions/container',
        'actions/parallel',
      ],
    },
    {
      type: 'category',
      label: 'Usage Guide',
      collapsed: false,
      items: [
        'usage/action-composition',
        'usage/argument-testing',
        'usage/context',
        'usage/error-handling',
        'usage/listener',
        'usage/reporting',
        'usage/cleanup',
        'usage/maven-plugin',
        'usage/gradle-plugin',
        'usage/integration-testing',
        'usage/discovery',
        
        'usage/migration-guide',
        'usage/migration-1-to-3',
        'usage/migration-2-to-3',
      ],
    },
    'configuration',
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
