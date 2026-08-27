# Model Instance Dump Format

`.vdmp` files are development-time Vedenemo model-instance data dumps. They
preserve one model-instance root for one loaded model version. They are not the
final durable persistence format.

The initial encoding is JSON:

```json
{
  "format": "vedenemo-instance-dump",
  "formatVersion": 1,
  "savedAt": "2026-08-21T05:00:00Z",
  "model": {
    "azName": "Music",
    "visName": "Music",
    "version": "1.2.3"
  },
  "root": {
    "sourceInstanceRootId": "00000000-0000-0000-0000-000000000000",
    "visName": "Source root"
  },
  "entities": [
    {
      "entityAzName": "Artist",
      "records": [
        {
          "dumpId": "artist-1",
          "values": {
            "Name": "Miles Davis",
            "Location": {
              "latitude": 62.1234567,
              "longitude": 30.1234567
            },
            "Path": {
              "locations": [
                { "latitude": 62.1234567, "longitude": 30.1234567 },
                { "latitude": 62.2234567, "longitude": 30.2234567 }
              ]
            },
            "Boundary": {
              "boundary": [
                { "latitude": 62.1234567, "longitude": 30.1234567 },
                { "latitude": 62.2234567, "longitude": 30.2234567 },
                { "latitude": 62.1234567, "longitude": 30.3234567 }
              ]
            },
            "Rating": null
          }
        }
      ]
    }
  ],
  "links": [
    {
      "associationAzName": "Album_Artist",
      "sourceDumpId": "album-1",
      "targetDumpId": "artist-1"
    }
  ]
}
```

`dumpId` values are local to the dump. Import always creates new runtime entity
instance UUIDs and remaps association links from dump-local ids to the new ids.

`null` attribute values mean the value was omitted in the source root. During
import they are omitted from the create payload before normal instance-data
validation runs. Empty strings are preserved only for string-like data types
when an empty string is the actual submitted value.

Structured spatial values are preserved as JSON objects. `LOCATION` uses
`latitude` and `longitude`. `LOCATION_LINE` uses a `locations` array with at
least two `LOCATION` objects. `LOCATION_AREA` uses a `boundary` array with at
least three distinct `LOCATION` objects. `LOCATION_AREA` is semantically closed;
the first point is not repeated as the final point in Vedenemo dump values.

Before import, Vedenemo checks that the target model is loaded, the dump model
`azName` matches, the dump version is not newer than the loaded model version,
and all referenced entities, attributes, data types, and associations exist.
Older dumps may be loaded into newer models only after confirmation.

Import creates a new model-instance root. It reports created entity counts,
created association-link counts, skipped exact duplicate links, warnings, and
failed insert diagnostics. An exact duplicate link has the same association,
resolved source instance, and resolved target instance after dump-local ids are
mapped.
