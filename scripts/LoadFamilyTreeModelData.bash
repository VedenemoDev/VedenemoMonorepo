#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

API_BASE_URL="${VEDENEMO_API_BASE_URL:-http://127.0.0.1:8080}"
MODEL_AZ_NAME="FamilyTree"
MODEL_INSTANCE_ROOT_NAME="Charles III Family Tree"
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
    {"key": "buckingham_palace", "Name": "Buckingham Palace, London", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "st_pauls", "Name": "St Paul's Cathedral, London", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "windsor_guildhall", "Name": "Windsor Guildhall, Windsor", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "westminster_abbey", "Name": "Westminster Abbey, London", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "clarence_house", "Name": "Clarence House, London", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "bruton_street", "Name": "17 Bruton Street, Mayfair, London", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "villa_mon_repos", "Name": "Mon Repos, Corfu", "Country": "Greece", "Coordinates": ""},
    {"key": "glamis_castle", "Name": "Glamis Castle, Angus", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "sandringham", "Name": "Sandringham House, Norfolk", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "king_edward_vii_hospital", "Name": "King Edward VII's Hospital, London", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "royal_lodge", "Name": "Royal Lodge, Windsor", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "balmoral", "Name": "Balmoral Castle, Aberdeenshire", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "windsor_castle", "Name": "Windsor Castle, Windsor", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "park_house", "Name": "Park House, Sandringham", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "pitie_salpetriere", "Name": "Pitie-Salpetriere Hospital, Paris", "Country": "France", "Coordinates": ""},
    {"key": "st_marys", "Name": "St Mary's Hospital, London", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "royal_berkshire", "Name": "Royal Berkshire Hospital, Reading", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "canoga_park", "Name": "Canoga Park, Los Angeles, California", "Country": "United States", "Coordinates": ""},
    {"key": "portland_hospital", "Name": "The Portland Hospital, London", "Country": "United Kingdom", "Coordinates": ""},
    {"key": "santa_barbara_cottage", "Name": "Santa Barbara Cottage Hospital, Santa Barbara, California", "Country": "United States", "Coordinates": ""},
]

PERSONS = [
    {
        "key": "george_vi",
        "GivenNames": "Albert Frederick Arthur George",
        "FamilyName": "Windsor",
        "BirthDate": "1895-12-14",
        "DeathDate": "1952-02-06",
        "Gender": "Male",
        "Notes": "King George VI; father of Queen Elizabeth II.",
        "birthPlace": "sandringham",
        "deathPlace": "sandringham",
    },
    {
        "key": "queen_mother",
        "GivenNames": "Elizabeth Angela Marguerite",
        "FamilyName": "Bowes-Lyon",
        "BirthDate": "1900-08-04",
        "DeathDate": "2002-03-30",
        "Gender": "Female",
        "Notes": "Queen Elizabeth The Queen Mother; mother of Queen Elizabeth II.",
        "birthPlace": "glamis_castle",
        "deathPlace": "royal_lodge",
    },
    {
        "key": "elizabeth_ii",
        "GivenNames": "Elizabeth Alexandra Mary",
        "FamilyName": "Windsor",
        "BirthDate": "1926-04-21",
        "DeathDate": "2022-09-08",
        "Gender": "Female",
        "Notes": "Queen Elizabeth II; mother of King Charles III.",
        "birthPlace": "bruton_street",
        "deathPlace": "balmoral",
    },
    {
        "key": "margaret",
        "GivenNames": "Margaret Rose",
        "FamilyName": "Windsor",
        "BirthDate": "1930-08-21",
        "DeathDate": "2002-02-09",
        "Gender": "Female",
        "Notes": "Princess Margaret, Countess of Snowdon; sister of Queen Elizabeth II.",
        "birthPlace": "glamis_castle",
        "deathPlace": "king_edward_vii_hospital",
    },
    {
        "key": "philip",
        "GivenNames": "Philip",
        "FamilyName": "Mountbatten",
        "BirthDate": "1921-06-10",
        "DeathDate": "2021-04-09",
        "Gender": "Male",
        "Notes": "Prince Philip, Duke of Edinburgh; husband of Queen Elizabeth II.",
        "birthPlace": "villa_mon_repos",
        "deathPlace": "windsor_castle",
    },
    {
        "key": "charles",
        "GivenNames": "Charles Philip Arthur George",
        "FamilyName": "Mountbatten-Windsor",
        "BirthDate": "1948-11-14",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "King Charles III; became King on 2022-09-08.",
        "birthPlace": "buckingham_palace",
    },
    {
        "key": "anne",
        "GivenNames": "Anne Elizabeth Alice Louise",
        "FamilyName": "Mountbatten-Windsor",
        "BirthDate": "1950-08-15",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "The Princess Royal; sister of King Charles III.",
        "birthPlace": "clarence_house",
    },
    {
        "key": "andrew",
        "GivenNames": "Andrew Albert Christian Edward",
        "FamilyName": "Mountbatten-Windsor",
        "BirthDate": "1960-02-19",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Brother of King Charles III.",
        "birthPlace": "buckingham_palace",
    },
    {
        "key": "edward",
        "GivenNames": "Edward Antony Richard Louis",
        "FamilyName": "Mountbatten-Windsor",
        "BirthDate": "1964-03-10",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Duke of Edinburgh; brother of King Charles III.",
        "birthPlace": "buckingham_palace",
    },
    {
        "key": "diana",
        "GivenNames": "Diana Frances",
        "FamilyName": "Spencer",
        "BirthDate": "1961-07-01",
        "DeathDate": "1997-08-31",
        "Gender": "Female",
        "Notes": "Diana, Princess of Wales; first wife of King Charles III.",
        "birthPlace": "park_house",
        "deathPlace": "pitie_salpetriere",
    },
    {
        "key": "camilla",
        "GivenNames": "Camilla Rosemary",
        "FamilyName": "Shand",
        "BirthDate": "1947-07-17",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Queen Camilla; wife of King Charles III.",
        "birthPlace": "king_edward_vii_hospital",
    },
    {
        "key": "william",
        "GivenNames": "William Arthur Philip Louis",
        "FamilyName": "Mountbatten-Windsor",
        "BirthDate": "1982-06-21",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Prince of Wales; elder son of King Charles III and Diana, Princess of Wales.",
        "birthPlace": "st_marys",
    },
    {
        "key": "harry",
        "GivenNames": "Henry Charles Albert David",
        "FamilyName": "Mountbatten-Windsor",
        "BirthDate": "1984-09-15",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Duke of Sussex; younger son of King Charles III and Diana, Princess of Wales.",
        "birthPlace": "st_marys",
    },
    {
        "key": "catherine",
        "GivenNames": "Catherine Elizabeth",
        "FamilyName": "Middleton",
        "BirthDate": "1982-01-09",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Princess of Wales; wife of Prince William.",
        "birthPlace": "royal_berkshire",
    },
    {
        "key": "meghan",
        "GivenNames": "Rachel Meghan",
        "FamilyName": "Markle",
        "BirthDate": "1981-08-04",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Duchess of Sussex; wife of Prince Harry.",
        "birthPlace": "canoga_park",
    },
    {
        "key": "george",
        "GivenNames": "George Alexander Louis",
        "FamilyName": "Mountbatten-Windsor",
        "BirthDate": "2013-07-22",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Prince George of Wales; child of Prince William and Catherine, Princess of Wales.",
        "birthPlace": "st_marys",
    },
    {
        "key": "charlotte",
        "GivenNames": "Charlotte Elizabeth Diana",
        "FamilyName": "Mountbatten-Windsor",
        "BirthDate": "2015-05-02",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Princess Charlotte of Wales; child of Prince William and Catherine, Princess of Wales.",
        "birthPlace": "st_marys",
    },
    {
        "key": "louis",
        "GivenNames": "Louis Arthur Charles",
        "FamilyName": "Mountbatten-Windsor",
        "BirthDate": "2018-04-23",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Prince Louis of Wales; child of Prince William and Catherine, Princess of Wales.",
        "birthPlace": "st_marys",
    },
    {
        "key": "archie",
        "GivenNames": "Archie Harrison",
        "FamilyName": "Mountbatten-Windsor",
        "BirthDate": "2019-05-06",
        "DeathDate": "",
        "Gender": "Male",
        "Notes": "Prince Archie of Sussex; child of Prince Harry and Meghan, Duchess of Sussex.",
        "birthPlace": "portland_hospital",
    },
    {
        "key": "lilibet",
        "GivenNames": "Lilibet Diana",
        "FamilyName": "Mountbatten-Windsor",
        "BirthDate": "2021-06-04",
        "DeathDate": "",
        "Gender": "Female",
        "Notes": "Princess Lilibet of Sussex; child of Prince Harry and Meghan, Duchess of Sussex.",
        "birthPlace": "santa_barbara_cottage",
    },
]

FAMILY_UNITS = [
    {
        "key": "george_vi_queen_mother",
        "StartDate": "1923-04-26",
        "EndDate": "1952-02-06",
        "RelationType": "Marriage",
        "Notes": "Marriage of King George VI and Elizabeth Bowes-Lyon.",
        "spouses": ["george_vi", "queen_mother"],
        "children": ["elizabeth_ii", "margaret"],
    },
    {
        "key": "elizabeth_philip",
        "StartDate": "1947-11-20",
        "EndDate": "2021-04-09",
        "RelationType": "Marriage",
        "Notes": "Marriage of Queen Elizabeth II and Prince Philip.",
        "spouses": ["elizabeth_ii", "philip"],
        "children": ["charles", "anne", "andrew", "edward"],
    },
    {
        "key": "charles_diana",
        "StartDate": "1981-07-29",
        "EndDate": "1996-08-28",
        "RelationType": "Marriage",
        "Notes": "Marriage of King Charles III and Diana, Princess of Wales; dissolved in 1996.",
        "spouses": ["charles", "diana"],
        "children": ["william", "harry"],
    },
    {
        "key": "charles_camilla",
        "StartDate": "2005-04-09",
        "EndDate": "",
        "RelationType": "Marriage",
        "Notes": "Marriage of King Charles III and Queen Camilla.",
        "spouses": ["charles", "camilla"],
        "children": [],
    },
    {
        "key": "william_catherine",
        "StartDate": "2011-04-29",
        "EndDate": "",
        "RelationType": "Marriage",
        "Notes": "Marriage of Prince William and Catherine, Princess of Wales.",
        "spouses": ["william", "catherine"],
        "children": ["george", "charlotte", "louis"],
    },
    {
        "key": "harry_meghan",
        "StartDate": "2018-05-19",
        "EndDate": "",
        "RelationType": "Marriage",
        "Notes": "Marriage of Prince Harry and Meghan, Duchess of Sussex.",
        "spouses": ["harry", "meghan"],
        "children": ["archie", "lilibet"],
    },
]

LIFE_EVENTS = [
    {
        "key": "charles_birth",
        "person": "charles",
        "place": "buckingham_palace",
        "EventType": "Birth",
        "EventDate": "1948-11-14",
        "Description": "Birth of Charles Philip Arthur George at Buckingham Palace.",
    },
    {
        "key": "charles_accession",
        "person": "charles",
        "place": "balmoral",
        "EventType": "Accession",
        "EventDate": "2022-09-08",
        "Description": "Accession of King Charles III on the death of Queen Elizabeth II.",
    },
    {
        "key": "charles_coronation",
        "person": "charles",
        "place": "westminster_abbey",
        "EventType": "Coronation",
        "EventDate": "2023-05-06",
        "Description": "Coronation of King Charles III at Westminster Abbey.",
    },
    {
        "key": "charles_diana_wedding",
        "person": "charles",
        "place": "st_pauls",
        "EventType": "Marriage",
        "EventDate": "1981-07-29",
        "Description": "Marriage of Charles, Prince of Wales and Lady Diana Spencer.",
    },
    {
        "key": "charles_camilla_wedding",
        "person": "charles",
        "place": "windsor_guildhall",
        "EventType": "Marriage",
        "EventDate": "2005-04-09",
        "Description": "Marriage of Charles, Prince of Wales and Camilla Parker Bowles.",
    },
    {
        "key": "elizabeth_ii_birth",
        "person": "elizabeth_ii",
        "place": "bruton_street",
        "EventType": "Birth",
        "EventDate": "1926-04-21",
        "Description": "Birth of Elizabeth Alexandra Mary at 17 Bruton Street.",
    },
    {
        "key": "elizabeth_ii_accession",
        "person": "elizabeth_ii",
        "place": "sandringham",
        "EventType": "Accession",
        "EventDate": "1952-02-06",
        "Description": "Accession of Queen Elizabeth II on the death of King George VI.",
    },
    {
        "key": "elizabeth_ii_death",
        "person": "elizabeth_ii",
        "place": "balmoral",
        "EventType": "Death",
        "EventDate": "2022-09-08",
        "Description": "Death of Queen Elizabeth II at Balmoral Castle.",
    },
    {
        "key": "philip_birth",
        "person": "philip",
        "place": "villa_mon_repos",
        "EventType": "Birth",
        "EventDate": "1921-06-10",
        "Description": "Birth of Prince Philip at Mon Repos, Corfu.",
    },
    {
        "key": "philip_death",
        "person": "philip",
        "place": "windsor_castle",
        "EventType": "Death",
        "EventDate": "2021-04-09",
        "Description": "Death of Prince Philip at Windsor Castle.",
    },
    {
        "key": "diana_birth",
        "person": "diana",
        "place": "park_house",
        "EventType": "Birth",
        "EventDate": "1961-07-01",
        "Description": "Birth of Diana Frances Spencer at Park House, Sandringham.",
    },
    {
        "key": "diana_death",
        "person": "diana",
        "place": "pitie_salpetriere",
        "EventType": "Death",
        "EventDate": "1997-08-31",
        "Description": "Death of Diana, Princess of Wales at Pitie-Salpetriere Hospital.",
    },
    {
        "key": "william_birth",
        "person": "william",
        "place": "st_marys",
        "EventType": "Birth",
        "EventDate": "1982-06-21",
        "Description": "Birth of William Arthur Philip Louis at St Mary's Hospital.",
    },
    {
        "key": "william_catherine_wedding",
        "person": "william",
        "place": "westminster_abbey",
        "EventType": "Marriage",
        "EventDate": "2011-04-29",
        "Description": "Marriage of Prince William and Catherine Middleton at Westminster Abbey.",
    },
    {
        "key": "harry_birth",
        "person": "harry",
        "place": "st_marys",
        "EventType": "Birth",
        "EventDate": "1984-09-15",
        "Description": "Birth of Henry Charles Albert David at St Mary's Hospital.",
    },
    {
        "key": "harry_meghan_wedding",
        "person": "harry",
        "place": "windsor_castle",
        "EventType": "Marriage",
        "EventDate": "2018-05-19",
        "Description": "Marriage of Prince Harry and Meghan Markle at Windsor Castle.",
    },
    {
        "key": "george_birth",
        "person": "george",
        "place": "st_marys",
        "EventType": "Birth",
        "EventDate": "2013-07-22",
        "Description": "Birth of Prince George of Wales at St Mary's Hospital.",
    },
    {
        "key": "charlotte_birth",
        "person": "charlotte",
        "place": "st_marys",
        "EventType": "Birth",
        "EventDate": "2015-05-02",
        "Description": "Birth of Princess Charlotte of Wales at St Mary's Hospital.",
    },
    {
        "key": "louis_birth",
        "person": "louis",
        "place": "st_marys",
        "EventType": "Birth",
        "EventDate": "2018-04-23",
        "Description": "Birth of Prince Louis of Wales at St Mary's Hospital.",
    },
]

SOURCE_RECORDS = [
    {
        "key": "royal_king",
        "Title": "The Royal Family: The King biography",
        "RecordType": "Official biography",
        "Repository": "The Royal Family",
        "Url": "https://www.royal.uk/the-king",
    },
    {
        "key": "canada_kings_queens",
        "Title": "Canada.ca kings and queens royal family entries",
        "RecordType": "Government reference",
        "Repository": "Government of Canada",
        "Url": "https://www.canada.ca/en/canadian-heritage/services/royal-family/kings-queens.html",
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
    if person_key == "charles":
        return ["royal_king", "canada_kings_queens"]
    return ["canada_kings_queens"]


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
    for source_id in source_record_ids.values():
        created_links += int(ensure_link("FamilyUnit_Sources", family_unit_id, source_id))

for event in LIFE_EVENTS:
    event_id = life_event_ids[event["key"]]
    created_links += int(ensure_link("Person_LifeEvents", person_ids[event["person"]], event_id))
    created_links += int(ensure_link("LifeEvent_Place", event_id, place_ids[event["place"]]))
    created_links += int(ensure_link("LifeEvent_Sources", event_id, source_record_ids["royal_king"]))

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
