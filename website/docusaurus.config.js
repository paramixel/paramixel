// @ts-check
const {themes} = require('prism-react-renderer');
const lightCodeTheme = themes.github;
const darkCodeTheme = themes.dracula;

const baseUrl = '/';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Paramixel',
  tagline: 'Paramixel turns complex Java tests into readable, composable execution trees.',
  favicon: 'img/favicon.ico',

  url: 'https://www.paramixel.org',
  baseUrl,

  onBrokenLinks: 'throw',

  headTags: [
    {
      tagName: 'link',
      attributes: {
        rel: 'icon',
        type: 'image/svg+xml',
        href: `${baseUrl}img/logo.svg`,
      },
    },
  ],

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
          routeBasePath: '/',
          sidebarPath: require.resolve('./sidebars.js'),
          editUrl: 'https://github.com/paramixel/paramixel/tree/main/website/',
          lastVersion: '6.1.0',
          versions: {
            current: {
              label: 'Unreleased',
              path: 'unreleased',
              banner: 'unreleased',
            },
            '6.1.0': {
              banner: 'none',
            },
            '6.0.0': {
              banner: 'unmaintained',
              label: '6.0.0 (Legacy)',
              className: 'notice-legacy',
            },
            '5.1.1': {
              banner: 'unmaintained',
              label: '5.1.1 (Legacy)',
              className: 'notice-legacy',
            },
            '5.1.0': {
              banner: 'unmaintained',
              label: '5.1.0 (Legacy)',
              className: 'notice-legacy',
            },
            '5.0.0': {
              banner: 'unmaintained',
              label: '5.0.0 (Legacy)',
              className: 'notice-legacy',
            },
            '4.0.0': {
              banner: 'unmaintained',
              label: '4.0.0 (Legacy)',
              className: 'notice-legacy',
            },
            '3.0.1': {
              banner: 'unmaintained',
              label: '3.0.1 (Obsolete)',
              className: 'notice-unmaintained',
            },
            '2.0.0': {
              banner: 'unmaintained',
              label: '2.0.0 (Obsolete)',
              className: 'notice-unmaintained',
            },
            '1.0.2': {
              banner: 'unmaintained',
              label: '1.0.2 (Obsolete)',
              className: 'notice-unmaintained',
            },
          },
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  plugins: [
    [
      '@docusaurus/plugin-client-redirects',
      {
        redirects: [
          {
            from: '/4.0.0/',
            to: '/4.0.0/intro',
          },
          {
            from: '/3.0.1/',
            to: '/3.0.1/intro',
          },
          {
            from: '/2.0.0/',
            to: '/2.0.0/intro',
          },
          {
            from: '/1.0.2/',
            to: '/1.0.2/intro',
          },
          {
            from: '/unreleased/api/spec',
            to: '/unreleased/api/builder',
          },
        ],
      },
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
              { label: 'Getting Started', to: '/getting-started/introduction' },
              { label: 'Core Concepts', to: '/core-concepts' },
              { label: 'API Reference', to: '/api/intro' },
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
