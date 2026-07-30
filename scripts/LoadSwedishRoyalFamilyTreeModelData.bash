#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

API_BASE_URL="${VEDENEMO_API_BASE_URL:-http://127.0.0.1:8080}"
MODEL_AZ_NAME="FamilyTree"
MODEL_INSTANCE_ROOT_NAME="Carl XVI Gustaf Family Tree"
VDOS_FILE="${REPO_ROOT}/.vedenemo/FamilyTree.vdos"

python3 - "$API_BASE_URL" "$MODEL_AZ_NAME" "$MODEL_INSTANCE_ROOT_NAME" "$VDOS_FILE" <<'PY'
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

api_base_url, model_az_name, model_instance_root_name, vdos_file = sys.argv[1:]
api_base_url = api_base_url.rstrip("/")

PLACES = [
    {"key": "haga_palace", "Name": "Haga Palace, Solna", "Country": "Sweden", "Coordinates": ""},
    {"key": "royal_palace_stockholm", "Name": "Royal Palace, Stockholm", "Country": "Sweden", "Coordinates": ""},
    {"key": "stockholm_cathedral", "Name": "Stockholm Cathedral, Stockholm", "Country": "Sweden", "Coordinates": ""},
    {"key": "drottningholm_palace", "Name": "Drottningholm Palace, Ekero", "Country": "Sweden", "Coordinates": ""},
    {"key": "karolinska_solna", "Name": "Karolinska Hospital, Solna", "Country": "Sweden", "Coordinates": ""},
    {"key": "danderyd_hospital", "Name": "Danderyd Hospital, Stockholm", "Country": "Sweden", "Coordinates": ""},
    {"key": "heidelberg", "Name": "Heidelberg", "Country": "Germany", "Coordinates": ""},
    {"key": "copenhagen_airport", "Name": "Copenhagen Airport, Kastrup", "Country": "Denmark", "Coordinates": ""},
    {"key": "new_york", "Name": "New York City, New York", "Country": "United States", "Coordinates": ""},
]

PERSONS = [
    {
        "key": "gustaf_adolf",
        "GivenNames": "Gustaf Adolf Oscar Fredrik Arthur Edmund",
        "FamilyName": "Bernadotte",
        "BirthDate": "1906-04-22",
        "DeathDate": "1947-01-26",
        "Gender": "Male",
        "Notes": "Hereditary Prince of Sweden; father of King Carl XVI Gustaf.",
        "deathPlace": "copenhagen_airport",
    },
    {
        "key": "sibylla",
        "GivenNames": "Sibylla Calma Marie Alice Bathildis Feodora",
        "FamilyName": "Saxe-Coburg and Gotha",
        "BirthDate": "1908-01-18",
        "DeathDate": "1972-11-28",
        "Gender": "Female",
        "Notes": "Princess Sibylla; mother of King Carl XVI Gustaf.",
    },
    {
        "key": "margaretha",
        "GivenNames": "Margaretha Desiree Victoria",
        "FamilyName": "Bernadotte",
        "BirthDate": "1934-10-31",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Oldest sister of King Carl XVI Gustaf.",
    },
    {
        "key": "birgitta",
        "GivenNames": "Birgitta Ingeborg Alice",
        "FamilyName": "Bernadotte",
        "BirthDate": "1937-01-19",
        "DeathDate": "2024-12-04",
        "Gender": "Female",
        "Notes": "Sister of King Carl XVI Gustaf.",
    },
    {
        "key": "desiree",
        "GivenNames": "Desiree Elisabeth Sibylla",
        "FamilyName": "Bernadotte",
        "BirthDate": "1938-06-02",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Sister of King Carl XVI Gustaf.",
    },
    {
        "key": "christina",
        "GivenNames": "Christina Louise Helena",
        "FamilyName": "Bernadotte",
        "BirthDate": "1943-08-03",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Youngest sister of King Carl XVI Gustaf.",
    },
    {
        "key": "carl_xvi_gustaf",
        "GivenNames": "Carl Gustaf Folke Hubertus",
        "FamilyName": "Bernadotte",
        "BirthDate": "1946-04-30",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "King Carl XVI Gustaf; King of Sweden since 1973-09-15.",
        "birthPlace": "haga_palace",
    },
    {
        "key": "silvia",
        "GivenNames": "Silvia Renate",
        "FamilyName": "Sommerlath",
        "BirthDate": "1943-12-23",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Queen Silvia of Sweden; married King Carl XVI Gustaf in 1976.",
        "birthPlace": "heidelberg",
    },
    {
        "key": "victoria",
        "GivenNames": "Victoria Ingrid Alice Desiree",
        "FamilyName": "Bernadotte",
        "BirthDate": "1977-07-14",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Crown Princess Victoria; heir to the Swedish throne.",
    },
    {
        "key": "daniel",
        "GivenNames": "Olof Daniel",
        "FamilyName": "Westling",
        "BirthDate": "1973-09-15",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Prince Daniel; husband of Crown Princess Victoria.",
    },
    {
        "key": "estelle",
        "GivenNames": "Estelle Silvia Ewa Mary",
        "FamilyName": "Bernadotte",
        "BirthDate": "2012-02-23",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Princess Estelle; oldest child of Crown Princess Victoria and Prince Daniel.",
        "birthPlace": "karolinska_solna",
    },
    {
        "key": "oscar",
        "GivenNames": "Oscar Carl Olof",
        "FamilyName": "Bernadotte",
        "BirthDate": "2016-03-02",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Prince Oscar; second child of Crown Princess Victoria and Prince Daniel.",
        "birthPlace": "karolinska_solna",
    },
    {
        "key": "carl_philip",
        "GivenNames": "Carl Philip Edmund Bertil",
        "FamilyName": "Bernadotte",
        "BirthDate": "1979-05-13",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Prince Carl Philip; son of King Carl XVI Gustaf and Queen Silvia.",
    },
    {
        "key": "sofia",
        "GivenNames": "Sofia Kristina",
        "FamilyName": "Hellqvist",
        "BirthDate": "1984-12-06",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Princess Sofia; wife of Prince Carl Philip.",
    },
    {
        "key": "alexander",
        "GivenNames": "Alexander Erik Hubertus Bertil",
        "FamilyName": "Bernadotte",
        "BirthDate": "2016-04-19",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Prince Alexander; child of Prince Carl Philip and Princess Sofia.",
    },
    {
        "key": "gabriel",
        "GivenNames": "Gabriel Carl Walther",
        "FamilyName": "Bernadotte",
        "BirthDate": "2017-08-31",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Prince Gabriel; child of Prince Carl Philip and Princess Sofia.",
    },
    {
        "key": "julian",
        "GivenNames": "Julian Herbert Folke",
        "FamilyName": "Bernadotte",
        "BirthDate": "2021-03-26",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Prince Julian; child of Prince Carl Philip and Princess Sofia.",
    },
    {
        "key": "ines",
        "GivenNames": "Ines Marie Lilian Silvia",
        "FamilyName": "Bernadotte",
        "BirthDate": "2025-02-07",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Princess Ines; youngest child of Prince Carl Philip and Princess Sofia.",
    },
    {
        "key": "madeleine",
        "GivenNames": "Madeleine Therese Amelie Josephine",
        "FamilyName": "Bernadotte",
        "BirthDate": "1982-06-10",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Princess Madeleine; daughter of King Carl XVI Gustaf and Queen Silvia.",
        "birthPlace": "drottningholm_palace",
    },
    {
        "key": "christopher",
        "GivenNames": "Christopher Paul",
        "FamilyName": "O'Neill",
        "BirthDate": "1974-06-27",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Christopher O'Neill; husband of Princess Madeleine.",
    },
    {
        "key": "leonore",
        "GivenNames": "Leonore Lilian Maria",
        "FamilyName": "Bernadotte",
        "BirthDate": "2014-02-20",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Princess Leonore; child of Princess Madeleine and Christopher O'Neill.",
        "birthPlace": "new_york",
    },
    {
        "key": "nicolas",
        "GivenNames": "Nicolas Paul Gustaf",
        "FamilyName": "Bernadotte",
        "BirthDate": "2015-06-15",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Prince Nicolas; child of Princess Madeleine and Christopher O'Neill.",
        "birthPlace": "danderyd_hospital",
    },
    {
        "key": "adrienne",
        "GivenNames": "Adrienne Josephine Alice",
        "FamilyName": "Bernadotte",
        "BirthDate": "2018-03-09",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Princess Adrienne; child of Princess Madeleine and Christopher O'Neill.",
        "birthPlace": "danderyd_hospital",
    },
]

FAMILY_UNITS = [
    {
        "key": "gustaf_adolf_sibylla",
        "StartDate": "1932-10-20",
        "EndDate": "1947-01-26",
        "RelationType": "Marriage",
        "Notes": "Marriage of Prince Gustaf Adolf and Princess Sibylla.",
        "spouses": ["gustaf_adolf", "sibylla"],
        "children": ["margaretha", "birgitta", "desiree", "christina", "carl_xvi_gustaf"],
    },
    {
        "key": "carl_silvia",
        "StartDate": "1976-06-19",
        "EndDate": "",
        "RelationType": "Marriage",
        "Notes": "Marriage of King Carl XVI Gustaf and Queen Silvia.",
        "spouses": ["carl_xvi_gustaf", "silvia"],
        "children": ["victoria", "carl_philip", "madeleine"],
    },
    {
        "key": "victoria_daniel",
        "StartDate": "2010-06-19",
        "EndDate": "",
        "RelationType": "Marriage",
        "Notes": "Marriage of Crown Princess Victoria and Prince Daniel.",
        "spouses": ["victoria", "daniel"],
        "children": ["estelle", "oscar"],
    },
    {
        "key": "carl_philip_sofia",
        "StartDate": "2015-06-13",
        "EndDate": "",
        "RelationType": "Marriage",
        "Notes": "Marriage of Prince Carl Philip and Princess Sofia.",
        "spouses": ["carl_philip", "sofia"],
        "children": ["alexander", "gabriel", "julian", "ines"],
    },
    {
        "key": "madeleine_christopher",
        "StartDate": "2013-06-08",
        "EndDate": "",
        "RelationType": "Marriage",
        "Notes": "Marriage of Princess Madeleine and Christopher O'Neill.",
        "spouses": ["madeleine", "christopher"],
        "children": ["leonore", "nicolas", "adrienne"],
    },
]

LIFE_EVENTS = [
    {
        "key": "king_birth",
        "person": "carl_xvi_gustaf",
        "place": "haga_palace",
        "EventType": "Birth",
        "EventDate": "1946-04-30",
        "Description": "Birth of Carl Gustaf Folke Hubertus at Haga Palace.",
    },
    {
        "key": "king_accession",
        "person": "carl_xvi_gustaf",
        "place": "royal_palace_stockholm",
        "EventType": "Accession",
        "EventDate": "1973-09-15",
        "Description": "Accession of King Carl XVI Gustaf.",
    },
    {
        "key": "king_silvia_wedding",
        "person": "carl_xvi_gustaf",
        "place": "stockholm_cathedral",
        "EventType": "Marriage",
        "EventDate": "1976-06-19",
        "Description": "Wedding of King Carl XVI Gustaf and Silvia Sommerlath.",
    },
    {
        "key": "victoria_daniel_wedding",
        "person": "victoria",
        "place": "stockholm_cathedral",
        "EventType": "Marriage",
        "EventDate": "2010-06-19",
        "Description": "Wedding of Crown Princess Victoria and Daniel Westling.",
    },
    {
        "key": "estelle_birth",
        "person": "estelle",
        "place": "karolinska_solna",
        "EventType": "Birth",
        "EventDate": "2012-02-23",
        "Description": "Birth of Princess Estelle at Karolinska Hospital in Solna.",
    },
    {
        "key": "ines_birth",
        "person": "ines",
        "place": "danderyd_hospital",
        "EventType": "Birth",
        "EventDate": "2025-02-07",
        "Description": "Birth of Princess Ines, youngest child of Prince Carl Philip and Princess Sofia.",
    },
]

SOURCE_RECORDS = [
    {
        "key": "royal_house",
        "Title": "The Royal House",
        "RecordType": "Official royal family overview",
        "Repository": "Swedish Royal Court",
        "Url": "https://www.kungahuset.se/english/royal-house",
    },
    {
        "key": "king_biography",
        "Title": "HM The King biography",
        "RecordType": "Official biography",
        "Repository": "Swedish Royal Court",
        "Url": "https://www.kungahuset.se/english/royal-house/hm-the-king",
    },
    {
        "key": "crown_princess_biography",
        "Title": "HRH The Crown Princess biography",
        "RecordType": "Official biography",
        "Repository": "Swedish Royal Court",
        "Url": "https://www.kungahuset.se/english/royal-house/hrh-the-crown-princess",
    },
    {
        "key": "princess_sofia_biography",
        "Title": "HRH Princess Sofia biography",
        "RecordType": "Official biography",
        "Repository": "Swedish Royal Court",
        "Url": "https://www.kungahuset.se/english/royal-house/hrh-princess-sofia",
    },
    {
        "key": "princess_madeleine_biography",
        "Title": "HRH Princess Madeleine biography",
        "RecordType": "Official biography",
        "Repository": "Swedish Royal Court",
        "Url": "https://www.kungahuset.se/english/royal-house/hrh-princess-madeleine",
    },
]


def request(method, path, body=None, content_type="application/json"):
    data = None
    headers = {}
    if body is not None:
        if isinstance(body, str):
            data = body.encode("utf-8")
        else:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = content_type
    req = urllib.request.Request(api_base_url + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, response.read().decode("utf-8")
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


def root_path(path):
    return f"/data/{quoted(model_az_name)}/roots/{quoted(instance_root_id)}{path}"


def find_first_instance(entity_az_name, filters):
    query = urllib.parse.urlencode(filters)
    status, body = request("GET", root_path(f"/{quoted(entity_az_name)}?{query}"))
    instances = json_body(status, body, f"Listing {entity_az_name} instances")
    return instances[0] if instances else None


def require_expected_model_shape(api_description):
    entities = {entity["azName"]: entity for entity in api_description.get("entities", [])}
    associations = {association["azName"]: association for association in api_description.get("associations", [])}

    expected_attributes = {
        "Person": ["GivenNames", "FamilyName", "BirthDate", "DeathDate", "Gender", "Notes"],
        "FamilyUnit": ["StartDate", "EndDate", "RelationType", "Notes"],
        "LifeEvent": ["EventType", "EventDate", "Description"],
        "Place": ["Name", "Country", "Coordinates"],
        "SourceRecord": ["Title", "RecordType", "Repository", "Url"],
    }
    for entity_az_name, attribute_az_names in expected_attributes.items():
        entity = entities.get(entity_az_name)
        if not entity:
            raise SystemExit(f"Loaded model is missing entity {entity_az_name}")
        attributes = {attribute["azName"] for attribute in entity.get("attributes", [])}
        for attribute_az_name in attribute_az_names:
            if attribute_az_name not in attributes:
                raise SystemExit(f"Loaded model is missing attribute {entity_az_name}.{attribute_az_name}")

    expected_associations = {
        "FamilyUnit_Spouses": ("FamilyUnit", "Person"),
        "FamilyUnit_Children": ("FamilyUnit", "Person"),
        "Person_BirthPlace": ("Person", "Place"),
        "Person_DeathPlace": ("Person", "Place"),
        "Person_LifeEvents": ("Person", "LifeEvent"),
        "LifeEvent_Place": ("LifeEvent", "Place"),
        "Person_Sources": ("Person", "SourceRecord"),
        "LifeEvent_Sources": ("LifeEvent", "SourceRecord"),
        "FamilyUnit_Sources": ("FamilyUnit", "SourceRecord"),
    }
    for association_az_name, endpoints in expected_associations.items():
        association = associations.get(association_az_name)
        if not association:
            raise SystemExit(f"Loaded model is missing association {association_az_name}")
        if (association.get("sourceEntityAzName"), association.get("targetEntityAzName")) != endpoints:
            raise SystemExit(f"Loaded model association {association_az_name} has unexpected endpoints")


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
    if status != 409:
        require_success(status, body, f"Loading model from {vdos_file}")

    status, body = request("GET", f"/data/{quoted(model_az_name)}/_api")
    require_success(status, body, f"Checking loaded model {model_az_name}")
    require_expected_model_shape(json.loads(body))


def ensure_entity(entity_az_name, values, identity_keys, action_name):
    filters = {key: values[key] for key in identity_keys}
    existing = find_first_instance(entity_az_name, filters)
    if existing:
        return existing["id"], False
    status, body = request("POST", root_path(f"/{quoted(entity_az_name)}"), values)
    created = json_body(status, body, action_name)
    return created["id"], True


def list_links(association_az_name):
    status, body = request("GET", root_path(f"/_links/{quoted(association_az_name)}"))
    return json_body(status, body, f"Listing {association_az_name} links")


def ensure_link(association_az_name, source_id, target_id):
    links = list_links(association_az_name)
    if any(
        link.get("sourceInstanceId") == source_id and link.get("targetInstanceId") == target_id
        for link in links
    ):
        return False
    status, body = request(
        "POST",
        root_path(f"/_links/{quoted(association_az_name)}"),
        {"sourceInstanceId": source_id, "targetInstanceId": target_id},
    )
    require_success(status, body, f"Creating {association_az_name} link")
    return True


def ensure_model_instance_root():
    status, body = request("GET", f"/data/{quoted(model_az_name)}/roots")
    roots = json_body(status, body, "Listing model instance roots")
    for root in roots:
        if root.get("visName") == model_instance_root_name:
            return root["instanceRootId"], root.get("visName", model_instance_root_name)
    status, body = request(
        "POST",
        f"/data/{quoted(model_az_name)}/roots",
        {"visName": model_instance_root_name},
    )
    created = json_body(status, body, "Creating model instance root")
    return created["instanceRootId"], created.get("visName", model_instance_root_name)


def source_ids_for_person(person_key):
    if person_key == "carl_xvi_gustaf":
        return ["royal_house", "king_biography"]
    if person_key in ("victoria", "daniel", "estelle", "oscar"):
        return ["royal_house", "crown_princess_biography"]
    if person_key in ("carl_philip", "sofia", "alexander", "gabriel", "julian", "ines"):
        return ["royal_house", "princess_sofia_biography"]
    if person_key in ("madeleine", "christopher", "leonore", "nicolas", "adrienne"):
        return ["royal_house", "princess_madeleine_biography"]
    return ["royal_house"]


ensure_model_loaded()
instance_root_id, instance_root_name = ensure_model_instance_root()

place_ids = {}
person_ids = {}
family_unit_ids = {}
life_event_ids = {}
source_record_ids = {}

created_places = 0
created_persons = 0
created_family_units = 0
created_life_events = 0
created_source_records = 0
created_links = 0

for place in PLACES:
    values = {key: place[key] for key in ["Name", "Country", "Coordinates"]}
    place_id, created = ensure_entity("Place", values, ["Name", "Country"], f"Creating Place {place['Name']}")
    place_ids[place["key"]] = place_id
    created_places += int(created)

for source in SOURCE_RECORDS:
    values = {key: source[key] for key in ["Title", "RecordType", "Repository", "Url"]}
    source_id, created = ensure_entity("SourceRecord", values, ["Title", "Url"], f"Creating SourceRecord {source['Title']}")
    source_record_ids[source["key"]] = source_id
    created_source_records += int(created)

for person in PERSONS:
    values = {key: person[key] for key in ["GivenNames", "FamilyName", "BirthDate", "DeathDate", "Gender", "Notes"]}
    person_id, created = ensure_entity(
        "Person",
        values,
        ["GivenNames", "FamilyName", "BirthDate"],
        f"Creating Person {person['GivenNames']} {person['FamilyName']}",
    )
    person_ids[person["key"]] = person_id
    created_persons += int(created)

for family_unit in FAMILY_UNITS:
    values = {key: family_unit[key] for key in ["StartDate", "EndDate", "RelationType", "Notes"]}
    family_unit_id, created = ensure_entity(
        "FamilyUnit",
        values,
        ["StartDate", "RelationType", "Notes"],
        f"Creating FamilyUnit {family_unit['Notes']}",
    )
    family_unit_ids[family_unit["key"]] = family_unit_id
    created_family_units += int(created)

for event in LIFE_EVENTS:
    values = {key: event[key] for key in ["EventType", "EventDate", "Description"]}
    event_id, created = ensure_entity(
        "LifeEvent",
        values,
        ["EventType", "EventDate", "Description"],
        f"Creating LifeEvent {event['Description']}",
    )
    life_event_ids[event["key"]] = event_id
    created_life_events += int(created)

for person in PERSONS:
    person_id = person_ids[person["key"]]
    birth_place_key = person.get("birthPlace")
    death_place_key = person.get("deathPlace")
    if birth_place_key:
        created_links += int(ensure_link("Person_BirthPlace", person_id, place_ids[birth_place_key]))
    if death_place_key:
        created_links += int(ensure_link("Person_DeathPlace", person_id, place_ids[death_place_key]))
    for source_key in source_ids_for_person(person["key"]):
        created_links += int(ensure_link("Person_Sources", person_id, source_record_ids[source_key]))

for family_unit in FAMILY_UNITS:
    family_unit_id = family_unit_ids[family_unit["key"]]
    for spouse_key in family_unit["spouses"]:
        created_links += int(ensure_link("FamilyUnit_Spouses", family_unit_id, person_ids[spouse_key]))
    for child_key in family_unit["children"]:
        created_links += int(ensure_link("FamilyUnit_Children", family_unit_id, person_ids[child_key]))
    created_links += int(ensure_link("FamilyUnit_Sources", family_unit_id, source_record_ids["royal_house"]))

for event in LIFE_EVENTS:
    event_id = life_event_ids[event["key"]]
    created_links += int(ensure_link("Person_LifeEvents", person_ids[event["person"]], event_id))
    created_links += int(ensure_link("LifeEvent_Place", event_id, place_ids[event["place"]]))
    for source_key in source_ids_for_person(event["person"]):
        created_links += int(ensure_link("LifeEvent_Sources", event_id, source_record_ids[source_key]))

print(f"Model: {model_az_name}")
print(f"Model instance root id: {instance_root_id}")
print(f"Model instance root name: {instance_root_name}")
print(f"Places created: {created_places}")
print(f"People created: {created_persons}")
print(f"Family units created: {created_family_units}")
print(f"Life events created: {created_life_events}")
print(f"Source records created: {created_source_records}")
print(f"Association links created: {created_links}")
PY
