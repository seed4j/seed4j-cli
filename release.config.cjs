module.exports = {
  branches: ['main'],
  tagFormat: 'v${version}',
  plugins: [
    './scripts/release-analyzer.cjs',
    [
      '@semantic-release/exec',
      {
        prepareCmd: 'node scripts/prepare-release.js ${nextRelease.version}',
        successCmd: 'printf "released=true\\nversion=%s\\n" "${nextRelease.version}" >> "$GITHUB_OUTPUT"',
      },
    ],
    '@semantic-release/npm',
  ],
};
