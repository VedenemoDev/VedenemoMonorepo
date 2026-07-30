#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

API_BASE_URL="${VEDENEMO_API_BASE_URL:-http://127.0.0.1:8080}"
MODEL_AZ_NAME="AlbumCollectionSimple"
VDOS_FILE="${REPO_ROOT}/.vedenemo/LevykokoelmaSimple.vdos"
CSV_FILE="${REPO_ROOT}/model_test_data/LevykokoelmaSimpleModelData.csv"

python3 - "$API_BASE_URL" "$MODEL_AZ_NAME" "$VDOS_FILE" "$CSV_FILE" <<'PY'
import csv
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

api_base_url, model_az_name, vdos_file, csv_file = sys.argv[1:]
api_base_url = api_base_url.rstrip("/")


def request(method, path, body=None, content_type="application/json"):
    data = None
    headers = {}
    if body is not None:
        if isinstance(body, str):
            data = body.encode("utf-8")
        else:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = content_type
    req = urllib.request.Request(
        api_base_url + path,
        data=data,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            response_body = response.read().decode("utf-8")
            return response.status, response_body
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8")
    except urllib.error.URLError as error:
        raise SystemExit(f"Could not connect to {api_base_url}: {error.reason}") from error


def require_success(status, body, action):
    if status < 200 or status >= 300:
        raise SystemExit(f"{action} failed with HTTP {status}: {body}")


def json_body(status, body, action):
    require_success(status, body, action)
    return json.loads(body)


def quoted(value):
    return urllib.parse.quote(value, safe="")


def find_first_instance(entity_az_name, filters):
    query = urllib.parse.urlencode(filters)
    status, body = request("GET", f"/data/{quoted(model_az_name)}/{quoted(entity_az_name)}?{query}")
    instances = json_body(status, body, f"Listing {entity_az_name} instances")
    return instances[0] if instances else None


def query_album(album_name, album_comment, artist_name):
    body = {
        "where": {"equals": {"Name": album_name, "Comment": album_comment}},
        "relationships": [
            {
                "associationAzName": "Albumilla_on_esittajia",
                "direction": "outgoing",
                "entityAzName": "Artist",
                "where": {"equals": {"Name": artist_name}},
            }
        ],
    }
    status, response = request("POST", f"/data/{quoted(model_az_name)}/Album/_query", body)
    instances = json_body(status, response, "Querying existing Album instance")
    return instances[0] if instances else None


def require_expected_model_shape(api_description):
    entities = {entity["azName"]: entity for entity in api_description.get("entities", [])}
    associations = {association["azName"]: association for association in api_description.get("associations", [])}

    def require_attribute(entity_az_name, attribute_az_name):
        entity = entities.get(entity_az_name)
        if not entity:
            raise SystemExit(f"Loaded model is missing entity {entity_az_name}")
        attributes = {attribute["azName"] for attribute in entity.get("attributes", [])}
        if attribute_az_name not in attributes:
            raise SystemExit(f"Loaded model is missing attribute {entity_az_name}.{attribute_az_name}")

    require_attribute("Artist", "Name")
    require_attribute("Album", "Name")
    require_attribute("Album", "Comment")
    require_attribute("Album", "year")
    association = associations.get("Albumilla_on_esittajia")
    if not association:
        raise SystemExit("Loaded model is missing association Albumilla_on_esittajia")
    if association.get("sourceEntityAzName") != "Album" or association.get("targetEntityAzName") != "Artist":
        raise SystemExit("Loaded model association Albumilla_on_esittajia has unexpected endpoints")


def ensure_model_loaded():
    status, body = request("GET", f"/data/{quoted(model_az_name)}/_api")
    if status == 200:
        require_expected_model_shape(json.loads(body))
        return
    if status != 404:
        raise SystemExit(f"Checking model {model_az_name} failed with HTTP {status}: {body}")

    with open(vdos_file, "r", encoding="utf-8") as handle:
        script = handle.read()
    status, body = request("POST", "/models/script", script, "text/plain; charset=utf-8")
    if status == 409:
        status, body = request("GET", f"/data/{quoted(model_az_name)}/_api")
        require_success(status, body, f"Checking model {model_az_name} after duplicate import response")
        return
    require_success(status, body, f"Loading model from {vdos_file}")

    status, body = request("GET", f"/data/{quoted(model_az_name)}/_api")
    require_success(status, body, f"Checking loaded model {model_az_name}")
    require_expected_model_shape(json.loads(body))


def ensure_artist(artist_name):
    existing = find_first_instance("Artist", {"Name": artist_name})
    if existing:
        return existing["id"], False
    status, body = request("POST", f"/data/{quoted(model_az_name)}/Artist", {"Name": artist_name})
    created = json_body(status, body, f"Creating Artist {artist_name}")
    return created["id"], True


def ensure_album(album_name, album_comment, artist_name):
    existing = query_album(album_name, album_comment, artist_name)
    if existing:
        return existing["id"], False
    status, body = request(
        "POST",
        f"/data/{quoted(model_az_name)}/Album",
        {"Name": album_name, "Comment": album_comment},
    )
    created = json_body(status, body, f"Creating Album {album_name}")
    return created["id"], True


def link_exists(album_id, artist_id):
    status, body = request("GET", f"/data/{quoted(model_az_name)}/_links/Albumilla_on_esittajia")
    links = json_body(status, body, "Listing Albumilla_on_esittajia links")
    return any(
        link.get("sourceInstanceId") == album_id and link.get("targetInstanceId") == artist_id
        for link in links
    )


def ensure_album_artist_link(album_id, artist_id):
    if link_exists(album_id, artist_id):
        return False
    status, body = request(
        "POST",
        f"/data/{quoted(model_az_name)}/_links/Albumilla_on_esittajia",
        {"sourceInstanceId": album_id, "targetInstanceId": artist_id},
    )
    require_success(status, body, "Creating Albumilla_on_esittajia link")
    return True


ensure_model_loaded()

created_artists = 0
created_albums = 0
created_links = 0
skipped_rows = 0
processed_rows = 0
rows_with_ignored_extra_values = 0

with open(csv_file, newline="", encoding="utf-8-sig") as handle:
    for row_number, row in enumerate(csv.reader(handle), start=1):
        if not row or all(not column.strip() for column in row):
            skipped_rows += 1
            continue
        if len(row) < 3:
            raise SystemExit(f"CSV row {row_number} has fewer than 3 columns")

        artist_name = row[0].strip()
        album_name = row[1].strip()
        album_comment = row[2].strip()
        extra_values = [column.strip() for column in row[3:]]
        if any(extra_values):
            rows_with_ignored_extra_values += 1
        if not artist_name or not album_name:
            raise SystemExit(f"CSV row {row_number} must have Artist.Name and Album.Name")

        artist_id, artist_created = ensure_artist(artist_name)
        album_id, album_created = ensure_album(album_name, album_comment, artist_name)
        link_created = ensure_album_artist_link(album_id, artist_id)

        processed_rows += 1
        created_artists += int(artist_created)
        created_albums += int(album_created)
        created_links += int(link_created)

print(f"Model: {model_az_name}")
print(f"CSV rows processed: {processed_rows}")
print(f"Rows skipped: {skipped_rows}")
print(f"Artists created: {created_artists}")
print(f"Albums created: {created_albums}")
print(f"Album-artist links created: {created_links}")
print(f"Rows with ignored extra columns: {rows_with_ignored_extra_values}")
PY
