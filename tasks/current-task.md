# Current Task

## Create Ritosentie model-instance dump

Status: executed

### Goal

Create a `.vdmp` model-instance dump for the `Tontti` model containing only the
main parcel record for `Ritosentie 26`.

The dump should use the previously fetched Maanmittauslaitos
`PalstanSijaintitiedot` polygon for kiinteistötunnus `297-17-2-29` as the
`Tontti.alue` `LOCATION_AREA` value.

### Scope

- Add `.vedenemo/Ritosentie.vdmp`.
- Use model metadata for `Tontti` version `1.0.0`.
- Create one `Tontti` record.
- Set `nimi` to `Ritosentie 26`.
- Set `tunnus` to `297-17-2-29`.
- Set `alue.boundary` to the seven MML-derived Vedenemo point coordinates.
- Keep `links` empty.

### Out Of Scope

- `Puu` records.
- Association links.
- Extra `Tontti` attribute values such as `hehtaarit`.
- Backend, core, CLI, UX, `.vdos`, or model-structure changes.

### Acceptance Criteria

- `.vedenemo/Ritosentie.vdmp` exists and is valid JSON.
- The dump contains exactly one `Tontti` record.
- The record has only `nimi`, `tunnus`, and `alue` values.
- The `alue.boundary` value uses Vedenemo `{ "latitude": ..., "longitude": ... }`
  point objects and does not repeat the closing coordinate.
- The dump imports successfully after loading `.vedenemo/Tontti.vdos`.

### Completion Notes

- Added `.vedenemo/Ritosentie.vdmp`.
- Included one `Tontti` record with `nimi`, `tunnus`, and `alue` only.
- Used the seven exterior boundary points fetched from Maanmittauslaitos for
  kiinteistötunnus `297-17-2-29`.
- Left `Puu` records and association links empty as requested.
- Validated the file as JSON and checked that the single record contains only
  `nimi`, `tunnus`, and `alue`.
- Validated import through local Vedenemo backend and CLI after loading
  `.vedenemo/Tontti.vdos`: one `Tontti` record was created and zero association
  links were created.
