# Messages between the map page and the app are kotlinx.serialization classes;
# the serialization plugin keeps what it needs. Every other library brings its
# own rules. A missing one doesn't fail the build, in CI or anywhere: R8 strips
# what a library reaches by reflection, and the app crashes when it runs. So
# install a release build and try what changed (AGENTS.md, "Test the release
# build").
