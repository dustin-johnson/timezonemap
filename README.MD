# Time Zone Map
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/us.dustinj.timezonemap/timezonemap/badge.svg)](https://maven-badges.herokuapp.com/maven-central/us.dustinj.timezonemap/timezonemap)
[![Build Status](https://travis-ci.com/dustin-johnson/timezonemap.svg?branch=master)](https://travis-ci.com/dustin-johnson/timezonemap)
[![Code Coverage](https://img.shields.io/codecov/c/github/dustin-johnson/timezonemap/master.svg)](https://codecov.io/gh/dustin-johnson/timezonemap/branch/master)
[![Dependencies](https://img.shields.io/librariesio/github/dustin-johnson/timezonemap.svg)](https://libraries.io/github/dustin-johnson/timezonemap)

The time zone map library provides a convenient way of determining the relevant time zone for any location on earth.
The map hails from [timezone-boundary-builder](https://github.com/evansiroky/timezone-boundary-builder/releases), 
which is derived from [OpenStreetMap](https://www.openstreetmap.org/) data. The idea, and the overall design pattern, 
comes from [Timeshape](https://github.com/RomanIakovlev/timeshape).

This library seeks to:
* Be as fast and small as possible, with minimal dependencies, so it's appropriate for mobile.
* Expose the region covered by each time zone to enable custom logic.
* Handle overlapping time zones deterministically.
* Present a clean and simple API.
* Conserve memory by not loading regions that are not needed.
* Avoid dependencies on Java 8s time library, mostly for mobile.

## Examples
```java
import us.dustinj.timezonemap.TimeZoneIndex;

// Initialize of a region that spans from Germany to Bulgaria.
// Takes some time (~1-5 seconds) to initialize, so try and initialize only once and keep it.
TimeZoneIndex scopedEngine = TimeZoneIndex.forRegion(43.5, 8.0, 53.00, 26.0);

String berlin = scopedEngine.getTimeZoneId(52.518424, 13.404776).get(); // Returns "Europe/Berlin"
String prague = scopedEngine.getTimeZoneId(50.074154, 14.437403).get(); // Returns "Europe/Prague"
String budapest = scopedEngine.getTimeZoneId(47.49642, 19.04970).get(); // Returns "Europe/Budapest"
String milan = scopedEngine.getTimeZoneId(45.466677, 9.188258).get();   // Returns "Europe/Rome"
String adriaticSea = scopedEngine.getTimeZoneId(44.337, 13.8282).get(); // Returns "Etc/GMT-1"
```

If you're on Java 8 or above, you can convert the returned time zone into a proper `java.util.TimeZone`:
```java
java.util.TimeZone timeZone = java.util.TimeZone.getTimeZone(berlin);
timeZone.observesDaylightTime(); // Returns true
```

## Getting the library
#### Maven
```xml
<dependency>
    <groupId>us.dustinj.timezonemap</groupId>
    <artifactId>timezonemap</artifactId>
    <version>2.0</version>
</dependency>
```

#### Android
```gradle
implementation('us.dustinj.timezonemap:timezonemap:2.0') {
    exclude group: 'com.github.luben', module: 'zstd-jni' // Exclude standard compression library
}
implementation 'com.github.luben:zstd-jni:1.3.7-1@aar' // Import aar for native component compilation
```

## Licenses
The code of the library is licensed under the [MIT License](https://opensource.org/licenses/MIT).

The time zone data contained in library is licensed under the [Open Data Commons Open Database License (ODbL)](http://opendatacommons.org/licenses/odbl/).