# Release notes

## Breaking change in the next release

Seed4J CLI now uses the Seed4J engine and the packaged core modules. Loading runtime extension JARs and the associated
`--debug` option have been removed from both channels.

If `~/.config/seed4j-cli/config.yml` still contains `seed4j.runtime.mode: extension`, the launcher exits with code `1`
before Spring starts. Set `seed4j.runtime.mode: standard` in that file and run `seed4j list` again. The CLI leaves your
configuration, installed JAR, metadata and cache untouched.

`seed4j extension --help` describes the transition. The former `extension install`, `extension enable` and
`extension disable` invocations exit with code `2`. The stable channel continues to offer the `seed4j-extension`
generator. Adoption of custom extensions through Maven coordinates is tracked in
[Seed4J #14926](https://github.com/seed4j/seed4j/issues/14926) and [Seed4J CLI #6](https://github.com/seed4j/seed4j-cli/issues/6).
