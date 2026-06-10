// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docsSidebar: [
    'intro',
    {
      type: 'category',
      label: 'Getting Started',
      collapsed: false,
      items: [
        'getting-started/introduction',
        'getting-started/when-to-use',
        'getting-started/first-test',
        'getting-started/installation',
        'getting-started/project-setup',
      ],
    },
    {
      type: 'category',
      label: 'Concepts',
      collapsed: false,
      items: [
        'core-concepts',
        'core-concepts/elements',
        'core-concepts/actions',
        'core-concepts/discovery',
        'core-concepts/data-driven-testing',
      ],
    },
    {
      type: 'category',
      label: 'Patterns',
      collapsed: false,
      items: [
        'test-shapes',
        'guides/parallel-execution',
        'guides/best-practices',
      ],
    },
    {
      type: 'category',
      label: 'Configuration',
      collapsed: true,
      items: [
        'configuration/properties',
        'configuration/system-properties',
        'configuration/profiles',
      ],
    },
    {
      type: 'category',
      label: 'Integrations',
      collapsed: true,
      items: [
        'integrations/maven-plugin',
        'integrations/gradle',
        'integrations/cicd',
        'integrations/reporting',
      ],
    },
    {
      type: 'category',
      label: 'Guides',
      collapsed: true,
      items: [
        'guides/quick-reference',
        'guides/troubleshooting',
        'guides/examples',
        'guides/migration-5.1-to-6',
        'guides/migration-5-to-6',
        'guides/migration-4-to-6',
        'guides/migration-3-to-6',
        'guides/migration-2-to-6',
        'guides/migration-1-to-6',
      ],
    },
    {
      type: 'category',
      label: 'API Reference',
      collapsed: true,
      items: [
        'api/intro',
        'api/status',
        'api/result',
        'api/action',
        'api/builder',
        'api/named-builders',
        'api/descriptor-and-metadata',
        'api/runner',
        'api/configuration',
        'api/listener',
        'api/selector',
        'api/execution-context',
        'api/annotation-resolver',
        'api/retry-and-cleanup',
        'api/exception-reference',
        'api/javadocs',
      ],
    },
    'release-notes',
  ],
};

module.exports = sidebars;
