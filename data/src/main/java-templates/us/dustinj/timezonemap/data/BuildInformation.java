package us.dustinj.timezonemap.data;

final class BuildInformation {
    static final String MAP_VERSION = "${map.archive.version}";
    static final String MAP_FILENAME = "${map.zstd.filename}";

    private BuildInformation() { }
}