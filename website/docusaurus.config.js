// @ts-check
const {themes} = require('prism-react-renderer');
const lightCodeTheme = themes.github;
const darkCodeTheme = themes.dracula;

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Paramixel Test Framework',
  tagline: 'An action-based test framework for Java 17+ with composable action trees, lifecycle management, and parallel execution.',
  favicon: 'img/favicon.ico',

  url: 'https://paramixel.github.io',
  baseUrl: '/paramixel/',

  onBrokenLinks: 'throw',

  markdown: {
    hooks: {
      onBrokenMarkdownLinks: 'throw',
    },
  },

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          routeBasePath: 'docs',
          sidebarPath: require.resolve('./sidebars.js'),
          editUrl: 'https://github.com/paramixel/paramixel/tree/main/website/',
          lastVersion: '2.0.0',
          versions: {
            current: {
              label: 'Unreleased',
              path: 'unreleased',
              banner: 'unreleased',
            },
            '2.0.0': {
              banner: 'none',
            },
          },
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      docs: {
        sidebar: {
          hideable: true,
          autoCollapseCategories: true,
        },
      },
      navbar: {
        title: 'Paramixel',
        logo: {
          alt: 'Paramixel Logo',
          src: 'img/logo.svg',
          href: 'https://paramixel.github.io/paramixel/',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'docsSidebar',
            position: 'left',
            label: 'Documentation',
          },
          {
            type: 'docsVersionDropdown',
            position: 'left',
          },
          {
            href: 'https://github.com/paramixel/paramixel',
            label: 'GitHub',
            position: 'right',
          },
          {
            href: 'https://central.sonatype.com/search?namespace=org.paramixel',
            label: 'Maven Central',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Documentation',
            items: [
              {
                label: 'Quick Start',
                to: '/docs/quick-start',
              },
              {
                label: 'Usage Guide',
                to: '/docs/usage/action-composition',
              },
              {
                label: 'API Reference',
                to: '/docs/api/intro',
              },
            ],
          },
          {
            title: 'Community',
            items: [
              {
                label: 'GitHub',
                href: 'https://github.com/paramixel/paramixel',
              },
              {
                label: 'Issues',
                href: 'https://github.com/paramixel/paramixel/issues',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} Douglas Hoard. Licensed under Apache License 2.0.`,
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
        additionalLanguages: ['java', 'bash', 'yaml', 'json', 'markdown', 'gradle', 'properties'],
      },
      colorMode: {
        defaultMode: 'light',
        disableSwitch: false,
        respectPrefersColorScheme: true,
      },
    }),
};

module.exports = config;
